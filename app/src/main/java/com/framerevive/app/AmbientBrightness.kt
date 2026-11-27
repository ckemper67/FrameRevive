package com.framerevive.app

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Screen brightness that follows the room, using the panel's own calibration.
 *
 * Lenovo ships a lux to backlight table in this frame's firmware (framework-res config_autoBrightnessLevels and
 * config_autoBrightnessLcdBacklightValues, read over adb 2026-11-26). It spans the panel's real range, backlight 9 to
 * 235 of 255 (config_screenBrightnessSettingMinimum/Maximum), and reaches full at 290 lux. Between its points the
 * backlight rises steadily with lux, as a print's luminance does; it runs about 1.3 times a paper-matching model at
 * 80 lux, which suits a lit screen needing more light than paper to look equal. Android's own automatic brightness
 * would use this table; the app overrides it with a window brightness, so it follows the table itself.
 * The owner's "Daytime brightness" stays the ceiling.
 */
object AmbientBrightness {
    /** Lux at which each backlight value in [BACKLIGHT] starts (the firmware's levels with 0 in front). */
    private val LUX = floatArrayOf(0f, 5f, 8f, 11f, 14f, 17f, 20f, 25f, 30f, 40f, 50f, 65f, 80f, 100f, 120f, 150f, 180f, 210f, 240f, 290f)
    private val BACKLIGHT = floatArrayOf(9f, 9f, 19f, 29f, 39f, 49f, 60f, 71f, 79f, 87f, 94f, 104f, 112f, 128f, 137f, 149f, 166f, 188f, 205f, 235f)
    /** The panel's backlight range on a 0..255 scale. */
    const val PANEL_MIN = 9f
    const val PANEL_MAX = 235f
    /** Time constant of the lux smoothing: a lamp switched on is believed within a couple of seconds. */
    const val SMOOTHING_SECONDS = 2f
    /** Time constant of the glide to a new target. Together with the smoothing this settles in about 3 seconds. */
    const val RAMP_SECONDS = 0.5f
    /** One step of the 0..255 backlight; closer than this the glide snaps to the target. */
    const val ONE_STEP = 1f / 255f

    /** Lenovo's backlight value, 9..235, for a room of [lux], interpolated between the table's points. */
    fun panelBacklight(lux: Float): Float {
        if (lux.isNaN() || lux <= LUX.first()) return BACKLIGHT.first()
        if (lux >= LUX.last()) return BACKLIGHT.last()
        var i = 1
        while (LUX[i] < lux) i++
        val t = (lux - LUX[i - 1]) / (LUX[i] - LUX[i - 1])
        return BACKLIGHT[i - 1] + (BACKLIGHT[i] - BACKLIGHT[i - 1]) * t
    }

    /** Window brightness, 0..1: the table's level, never above the daytime setting or below the panel minimum. */
    fun level(daytimePercent: Int, lux: Float?, enabled: Boolean): Float {
        val ceiling = FrameSchedule.awakeBrightness(daytimePercent)
        if (!enabled || lux == null || lux.isNaN()) return ceiling
        // The ceiling is at least 5%, above the panel minimum of 3.5%, so capping after the floor cannot invert them.
        return (panelBacklight(lux) / 255f).coerceAtLeast(PANEL_MIN / 255f).coerceAtMost(ceiling)
    }

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
