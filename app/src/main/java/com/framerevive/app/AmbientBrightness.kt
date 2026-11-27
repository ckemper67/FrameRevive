package com.framerevive.app

import kotlin.math.abs

/**
 * Screen brightness set from the ambient light level, using the device's own calibration and the way Android's
 * automatic brightness reads the sensor.
 *
 * Android devices ship a lux to backlight table in framework-res (config_autoBrightnessLevels and
 * config_autoBrightnessLcdBacklightValues). It matches the panel's real range and rises steadily with lux, as the
 * brightness of a print does in the same room; a lit screen needs somewhat more light than paper to look as bright.
 * Android's own automatic brightness would use this table, but the app sets a window brightness, which overrides it,
 * so the app applies the table itself. [Curve.fromDevice] builds the curve from the device's arrays and refuses a
 * table that is empty or inconsistent; with no usable table the brightness stays at the fixed daytime level.
 *
 * The sensor is read the way Android reads it (AutomaticBrightnessController, with the values this frame's firmware
 * sets, read from dumpsys display): see [Estimator] for the 10 second weighted average, the 3 second debounce and the
 * thresholds, [settle] for the thresholds on the screen level and [glide] for the ramps.
 * The owner's "Daytime brightness" stays the ceiling. The screen is never turned off: in a dark room it stays at the
 * table's lowest level, which is the panel's minimum (Android does not go below it).
 */
object AmbientBrightness {
    /** The light readings that count: the last 10 seconds, newer ones weighing more (firmware: ambient light horizon 10000 ms). */
    const val HORIZON_MS = 10_000L
    /** The weighting is linear in age, from this many ms at "now" down to zero at the horizon (firmware: weighting intercept 10000). */
    const val WEIGHT_INTERCEPT_MS = 10_000L
    /** A change counts only once every reading of this long has stayed past its threshold (firmware: debounce 3000 ms, both ways). */
    const val DEBOUNCE_MS = 3_000L
    /** The light has to rise this share above the estimate to count as brighter... */
    const val BRIGHTEN_SHARE = 0.10f
    /** ...and fall this share below it to count as darker, so a lamp is believed sooner than its going off. */
    const val DARKEN_SHARE = 0.20f
    /** The estimate looks this far ahead of the newest reading, as Android's does. */
    const val PREDICTION_MS = 100L
    /** How fast the screen goes up and down, in the 0..1 brightness per second (firmware: 180 and 60 of 255). */
    const val RAMP_UP_PER_SECOND = 180f / 255f
    const val RAMP_DOWN_PER_SECOND = 60f / 255f
    /** One step of the 0..255 backlight; closer than this the glide snaps to the target. */
    const val ONE_STEP = 1f / 255f

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
     * The level to aim for: [held], the one already aimed at, until [wanted] is [BRIGHTEN_SHARE] above it or
     * [DARKEN_SHARE] below it, as Android does for the screen level. Smaller changes of the wanted level are ignored.
     * With nothing held, [wanted] is taken.
     */
    fun settle(held: Float?, wanted: Float): Float {
        if (held == null) return wanted
        return if (wanted >= held * (1f + BRIGHTEN_SHARE) || wanted <= held * (1f - DARKEN_SHARE)) wanted else held
    }

    /** The Diagnostics line: whether it is on, which table, the room's light, and the screen level against the daytime setting. */
    fun report(enabled: Boolean, curve: Curve?, rawLux: Float?, smoothedLux: Float?, shown: Float?, daytimePercent: Int): String =
        "Ambient brightness: ${if (enabled) "on" else "off"}; table ${curve?.let { "${it.source}, ${it.size} points" } ?: "none, fixed level"}; " +
            "room ${rawLux?.let { "${it.toInt()} lux now" } ?: "no reading"}, ${smoothedLux?.let { "${it.toInt()} lux estimated" } ?: "-"}; " +
            "screen ${shown?.let { "${(it * 100).toInt()}%" } ?: "-"} of a $daytimePercent% daytime setting"

    /**
     * Estimates the room's light from the sensor's readings the way Android's automatic brightness does.
     *
     * The estimate is a weighted average of the readings of the last [HORIZON_MS], a reading counting for less the older
     * it is. It changes only when the average has moved [BRIGHTEN_SHARE] above or [DARKEN_SHARE] below the current
     * estimate and every raw reading of the last [DEBOUNCE_MS] has stayed beyond that threshold. The sensor reads whole
     * lux and jumps around by a couple of lux in a dim room, which is more than the shares at that level; one stray
     * reading ends the run, so noise changes nothing, while a lamp switched on or off does.
     * The first reading is taken as it is. Call [update] now and then as well, as readings can stop coming when the
     * light is steady.
     */
    class Estimator {
        private class Sample(val timeMs: Long, val lux: Float)
        private val samples = ArrayList<Sample>()
        private var brighteningLux = 0f
        private var darkeningLux = 0f
        /** The estimate in use, or null before the first reading. */
        var lux: Float? = null
            private set

        /** Takes a reading; true if the estimate changed. */
        fun add(raw: Float, nowMs: Long): Boolean {
            if (raw.isNaN()) return false
            samples.add(Sample(nowMs, raw.coerceAtLeast(0f)))
            // Keep the readings of the horizon, and the newest one before it so the whole horizon is covered.
            while (samples.size > 1 && samples[1].timeMs <= nowMs - HORIZON_MS) samples.removeAt(0)
            return update(nowMs)
        }

        /** Re-evaluates as of [nowMs] without a new reading; true if the estimate changed. */
        fun update(nowMs: Long): Boolean {
            if (samples.isEmpty()) return false
            val average = weightedAverage(nowMs)
            if (lux == null) { set(average); return true }
            val brighter = average >= brighteningLux && stayedBeyond(nowMs, brighteningLux, above = true)
            val darker = average <= darkeningLux && stayedBeyond(nowMs, darkeningLux, above = false)
            if (!brighter && !darker) return false
            set(average)
            return true
        }

        private fun set(value: Float) {
            lux = value
            brighteningLux = value * (1f + BRIGHTEN_SHARE)
            darkeningLux = value * (1f - DARKEN_SHARE)
        }

        /** True when the raw readings have all been beyond [threshold] for the last [DEBOUNCE_MS]. */
        private fun stayedBeyond(nowMs: Long, threshold: Float, above: Boolean): Boolean {
            var earliest = nowMs
            for (i in samples.indices.reversed()) {
                val l = samples[i].lux
                if (if (above) l <= threshold else l >= threshold) break
                earliest = samples[i].timeMs
            }
            return earliest + DEBOUNCE_MS <= nowMs
        }

        /** Each reading weighs by how long it was the latest, times a weight falling linearly to zero at the horizon. */
        private fun weightedAverage(nowMs: Long): Float {
            var sum = 0.0
            var total = 0.0
            var end = PREDICTION_MS.toDouble()
            for (i in samples.indices.reversed()) {
                var start = (samples[i].timeMs - nowMs).toDouble()
                if (start < -HORIZON_MS) start = -HORIZON_MS.toDouble()
                val weight = integral(end) - integral(start)
                total += weight
                sum += samples[i].lux * weight
                end = start
            }
            return if (total > 0.0) (sum / total).toFloat() else samples.last().lux
        }

        private fun integral(x: Double) = x * (0.5 * x + WEIGHT_INTERCEPT_MS)
    }

    /** The brightness [dtMs] after [shown] while going to [target]: a straight ramp, up faster than down, snapping when close. */
    fun glide(shown: Float, target: Float, dtMs: Long): Float {
        val dt = dtMs.coerceAtLeast(0L) / 1000f
        val next = if (target > shown) minOf(target, shown + RAMP_UP_PER_SECOND * dt) else maxOf(target, shown - RAMP_DOWN_PER_SECOND * dt)
        return if (abs(target - next) <= ONE_STEP) target else next
    }
}
