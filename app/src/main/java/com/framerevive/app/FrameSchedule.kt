package com.framerevive.app

import java.time.LocalTime
import java.time.ZonedDateTime

object FrameSchedule {
    fun nextOccurrence(now: ZonedDateTime, time: LocalTime): ZonedDateTime {
        val next = now.with(time).withSecond(0).withNano(0)
        return if (next.isAfter(now)) next else now.plusDays(1).with(time).withSecond(0).withNano(0)
    }
    /** Hours a day the frame is awake: all 24 without a schedule, otherwise the day less the sleep window. */
    fun awakeHours(schedule: Boolean, start: LocalTime, end: LocalTime): Double {
        if (!schedule || start == end) return 24.0
        val sleepSeconds = (end.toSecondOfDay() - start.toSecondOfDay() + 86_400) % 86_400
        return 24.0 - sleepSeconds / 3600.0
    }
    fun awakeBrightness(percent: Int): Float = percent.coerceIn(5, 100) / 100f
    fun isSleeping(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start == end) false
        else if (start < end) now >= start && now < end
        else now >= start || now < end
}
