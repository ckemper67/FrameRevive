package com.framerevive.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper

/**
 * How long the front buttons stay awake after a touch, a key press or a hand movement near the frame (the lights are on
 * for the same time). A hand movement that finds them asleep only wakes them: see [FrameButtonLights.isAwake].
 */
class ButtonWake(private val windowMs: Long = 8000) {
    private var until = 0L
    fun touch(now: Long) { until = now + windowMs }
    fun awake(now: Long) = now < until
}

/**
 * Lenovo button LEDs are separate from the display backlight. They come on for a key press, a touch and a hand movement
 * the gesture sensor reports. The frame's proximity sensor is also listened to, for firmware where it works, but on the
 * tested CD-3L501F it never reports: it registers fine (binary, on change, no permission needed) and gives no event
 * even with a palm on the glass (checked 2026-11-27).
 */
class FrameButtonLights(context: Context) : SensorEventListener {
    private val handler = Handler(Looper.getMainLooper())
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val proximity = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val manager = runCatching { context.getSystemService("led") }.getOrNull()
    private var enabled = false
    private var lit = false
    private val wake = ButtonWake()
    /** True while the buttons are awake. Tracked here, not from the LED, so a firmware without the LED service still works. */
    fun isAwake() = wake.awake(android.os.SystemClock.uptimeMillis())
    private val off = Runnable { setLight(false) }
    private fun setLight(on: Boolean) {
        if (manager == null) return
        try {
            if (on) manager.javaClass.getMethod("LedOn", Int::class.javaPrimitiveType).invoke(manager, 100)
            else manager.javaClass.getMethod("LedOff").invoke(manager)
            lit = on
        } catch (_: Exception) {
            android.util.Log.w("FrameReviveLights", "Button light control unavailable on this firmware")
        }
    }
    fun start() {
        if (enabled) return
        enabled = true
        setLight(false)
        proximity?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }
    fun touch() {
        if (!enabled) return
        wake.touch(android.os.SystemClock.uptimeMillis())
        if (!lit) setLight(true)
        handler.removeCallbacks(off)
        handler.postDelayed(off, 8000)
    }
    fun stop() {
        enabled = false
        sensors.unregisterListener(this)
        handler.removeCallbacks(off)
        setLight(false)
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.isNotEmpty() && event.values[0] < (proximity?.maximumRange ?: 0f)) touch()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
