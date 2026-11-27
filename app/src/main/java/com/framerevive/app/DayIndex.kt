package com.framerevive.app

import java.time.LocalDate
import java.time.Year

/**
 * The album's dated photos grouped by capture day, with plain millisecond arithmetic (a calendar conversion per photo
 * costs about a third of a second on the frame). Immutable and built once per catalog on a worker thread, so the main
 * thread can ask for a day, a window of days or "on this day" without scanning 15,000 photos.
 */
class DayIndex(photos: List<FramePhoto>) {
    private val byDay: Map<Long, List<FramePhoto>>
    init {
        val groups = HashMap<Long, ArrayList<FramePhoto>>()
        for (p in photos) if (p.dated) groups.getOrPut(dayOf(p)) { ArrayList() } += p
        byDay = groups
    }

    /** Every day that has photos, with its photos in album order. */
    val days: Map<Long, List<FramePhoto>> get() = byDay

    /** The photos of the days within [radius] of [centreDay], either side and including it. */
    fun window(centreDay: Long, radius: Int): List<FramePhoto> {
        val out = ArrayList<FramePhoto>()
        for (d in centreDay - radius..centreDay + radius) byDay[d]?.let { out += it }
        return out
    }

    /**
     * The days within [memoryDays] of today's date in an earlier year, "on this day", the window the single-photo
     * memories use, so a moving holiday is caught. There is one window per year, from the year before the first photo (a
     * window around early January reaches back into December) to this year (a window around early January also reaches
     * back into last December), and only days before this year are kept. 29 February falls back to the 28th in years
     * without one. This differs from the circular day-of-year distance the singles use only by a day at the edge of a
     * window around the end of February.
     */
    fun onThisDay(today: LocalDate, memoryDays: Int): Map<Long, List<FramePhoto>> {
        if (byDay.isEmpty()) return emptyMap()
        val firstYear = LocalDate.ofEpochDay(byDay.keys.min()).year
        val thisYearStart = LocalDate.of(today.year, 1, 1).toEpochDay()
        val out = HashMap<Long, List<FramePhoto>>()
        for (year in firstYear - 1..today.year) {
            val centre = LocalDate.of(year, today.month, minOf(today.dayOfMonth, today.month.length(Year.isLeap(year.toLong())))).toEpochDay()
            for (d in centre - memoryDays..centre + memoryDays) if (d < thisYearStart) byDay[d]?.let { out[d] = it }
        }
        return out
    }

    companion object {
        const val DAY_MS = 86_400_000L
        fun dayOf(p: FramePhoto): Long = Math.floorDiv(p.takenMs + p.offsetMs, DAY_MS)
    }
}
