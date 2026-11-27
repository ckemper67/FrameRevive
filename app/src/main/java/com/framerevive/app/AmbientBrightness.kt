package com.framerevive.app

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Screen brightness set from the ambient light level, using the device's own calibration.
 *
 * Android devices ship a lux to backlight table in framework-res (config_autoBrightnessLevels and
 * config_autoBrightnessLcdBacklightValues). It matches the panel's real range and rises steadily with lux, as the
 * brightness of a print does in the same room; a lit screen needs somewhat more light than paper to look as bright.
 * Android's own automatic brightness would use this table, but the app sets a window brightness, which overrides it,
 * so the app applies the table itself. [Curve.fromDevice] builds the curve from the device's arrays and refuses a
 * table that is empty or inconsistent; with no usable table the brightness stays at the fixed daytime level.
 * The owner's "Daytime brightness" stays the ceiling. The screen is never turned off: in a dark room it stays at the
 * table's lowest level, which is the panel's minimum (Android does not go below it).
 */
object AmbientBrightness {
    /** Time constant of the lux smoothing. The sensor reads whole lux and jumps around by a couple of lux in a dim room; the hold in [settle] deals with what is left. */
    const val SMOOTHING_SECONDS = 2f
    /** Time constant of the glide to a new target, after the smoothing has believed the change. */
    const val RAMP_SECONDS = 0.3f
    /** One step of the 0..255 backlight; closer than this the glide snaps to the target. */
    const val ONE_STEP = 1f / 255f
    /** A change of the level smaller than this share of it is ignored, so sensor noise cannot make the screen flicker. */
    const val HOLD_SHARE = 0.15f

    /** A lux to backlight (0..255) table; [backlight] has one entry per entry of [lux], interpolated between. */
    class Curve(private val lux: FloatArray, private val backlight: FloatArray, val source: String) {
        /** Points in the table. */
        val size: Int get() = lux.size

        /** The backlight value for a room of [roomLux], interpolated between the table's points. */
        fun backlight(roomLux: Float): Float {
            if (roomLux.isNaN() || roomLux <= lux.first()) return backlight.first()
            if (roomLux >= lux.last()) return backlight.last()
            var i = 1
            while (lux[i] < roomLux) i++
            val t = (roomLux - lux[i - 1]) / (lux[i] - lux[i - 1])
            return backlight[i - 1] + (backlight[i] - backlight[i - 1]) * t
        }

        companion object {
            /**
             * Builds the curve from Android's two arrays: [levels] are the lux thresholds and [values] the backlight from
             * each one up, with one more value than levels (the first is for a room darker than the first level).
             * Returns null unless the table is usable: non-empty, the right length, lux rising and backlight within 0..255,
             * never falling, and not flat.
             */
            fun fromDevice(levels: IntArray, values: IntArray): Curve? {
                if (levels.isEmpty() || values.size != levels.size + 1) return null
                if (levels.first() < 0 || levels.toList().zipWithNext().any { (a, b) -> b <= a }) return null
                if (values.any { it !in 0..255 } || values.toList().zipWithNext().any { (a, b) -> b < a }) return null
                if (values.first() == values.last()) return null
                return Curve(floatArrayOf(0f) + levels.map { it.toFloat() }, values.map { it.toFloat() }.toFloatArray(), "device table")
            }
        }
    }

    /** Window brightness, 0..1: the curve's level, never above the daytime setting. */
    fun level(daytimePercent: Int, lux: Float?, enabled: Boolean, curve: Curve?): Float {
        val ceiling = FrameSchedule.awakeBrightness(daytimePercent)
        if (!enabled || curve == null || lux == null || lux.isNaN()) return ceiling
        return (curve.backlight(lux) / 255f).coerceAtMost(ceiling)
    }

    /**
     * The level to aim for: [held], the one already aimed at, until [wanted] has moved away from it by more than
     * [HOLD_SHARE] of it (and by at least two backlight steps). The sensor reads whole lux and jumps around by a couple
     * of lux; at a dim level that would otherwise move the backlight by a visible step several times a second.
     * With nothing held, [wanted] is taken.
     */
    fun settle(held: Float?, wanted: Float): Float {
        if (held == null) return wanted
        val band = maxOf(2 * ONE_STEP, HOLD_SHARE * held)
        return if (abs(wanted - held) >= band) wanted else held
    }

    /** The Diagnostics line: whether it is on, which table, the room's light, and the screen level against the daytime setting. */
    fun report(enabled: Boolean, curve: Curve?, rawLux: Float?, smoothedLux: Float?, shown: Float?, daytimePercent: Int): String =
        "Ambient brightness: ${if (enabled) "on" else "off"}; table ${curve?.let { "${it.source}, ${it.size} points" } ?: "none, fixed level"}; " +
            "room ${rawLux?.let { "${it.toInt()} lux now" } ?: "no reading"}, ${smoothedLux?.let { "${it.toInt()} lux smoothed" } ?: "-"}; " +
            "screen ${shown?.let { "${(it * 100).toInt()}%" } ?: "-"} of a $daytimePercent% daytime setting"

    /** Smooths lux readings over time in log space; the first reading is taken as is. */
    class Smoother {
        private var logLux: Float? = null
        private var at = 0L
        val lux: Float? get() = logLux?.let { exp(it) - 1f }
        fun add(raw: Float, nowMs: Long): Float {
            val x = ln(raw.coerceAtLeast(0f) + 1f)
            val previous = logLux
            logLux = if (previous == null) x else {
                val dt = ((nowMs - at).coerceAtLeast(0L)) / 1000f
                val alpha = 1f - exp(-dt / SMOOTHING_SECONDS)
                previous + (x - previous) * alpha
            }
            at = nowMs
            return lux!!
        }
    }

    /** The brightness [dtMs] after [shown] while gliding to [target]: an exponential approach, snapping when close. */
    fun glide(shown: Float, target: Float, dtMs: Long): Float {
        val alpha = 1f - exp(-(dtMs.coerceAtLeast(0L) / 1000f) / RAMP_SECONDS)
        val next = shown + (target - shown) * alpha
        return if (abs(target - next) <= ONE_STEP) target else next
    }
}
