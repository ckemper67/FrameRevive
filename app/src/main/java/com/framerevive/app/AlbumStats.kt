package com.framerevive.app

import java.time.LocalDate

/**
 * What the album looks like, and what the frame's settings mean for it, in numbers a person can act on. Pure
 * arithmetic on capture times and the play history (no per-photo calendar conversion), so it is cheap enough to
 * compute when the controls page is opened. Timezones and moments are left to tools/album_dates.py.
 */
object AlbumStats {
    private const val DAY_MS = 86_400_000L
    /** A "big day" holds at least this many photos. */
    const val BIG_DAY = 20
    /** A day counts as not shown lately when nothing from it has been on the panel for this long. */
    const val LATELY_DAYS = 30L
    val RECENT_MONTHS = listOf(1, 3, 6, 12, 24)

    data class Settings(val roundSize: Int, val recentPercent: Int, val memoryDays: Int, val secondsPerPhoto: Int, val awakeHours: Double)
    data class Window(val months: Int, val photos: Int, val days: Int)
    data class Year(val year: Int, val photos: Int, val days: Int)
    data class Memories(val windowDays: Int, val photos: Int, val days: Int)
    data class Progress(val photosShown: Int, val daysReached: Int, val daysNotShownLately: Int)
    /** [memoryShare] is the share of screen time that memories take; the return times are in days. */
    data class Pace(val slotsPerDay: Double, val passDays: Double, val memoryShare: Double, val memoryReturnDays: Double?, val otherReturnDays: Double?)
    data class Stats(
        val photos: Int, val undated: Int, val days: Int, val firstYear: Int?, val lastYear: Int?,
        val perDayMedian: Int, val perDayP90: Int, val perDayMax: Int, val singlePhotoDays: Int,
        val bigDays: Int, val bigDayPhotoShare: Double,
        val recent: List<Window>, val byYear: List<Year>, val memories: Memories?, val progress: Progress, val pace: Pace)

    private class Day(var photos: Int = 0, var shown: Int = 0, var lastShownMs: Long = 0L)

    private fun dayOf(p: FramePhoto) = Math.floorDiv(p.takenMs + p.offsetMs, DAY_MS)

    /** [seasonal] is the cached memory-window set for today, or null when it has not been calculated yet. */
    fun compute(photos: List<FramePhoto>, today: LocalDate, history: Map<String, PlayRecord>, nowMs: Long,
                settings: Settings, seasonal: List<FramePhoto>?): Stats {
        val days = HashMap<Long, Day>()
        var undated = 0
        var shownPhotos = 0
        for (p in photos) {
            if (!p.dated) { undated++; if (history.containsKey(p.id)) shownPhotos++; continue }
            val day = days.getOrPut(dayOf(p)) { Day() }
            day.photos++
            history[p.id]?.let { day.shown++; shownPhotos++; day.lastShownMs = maxOf(day.lastShownMs, it.lastShownMs) }
        }
        val counts = days.values.map { it.photos }.sorted()
        val dated = counts.sum()
        val big = counts.filter { it >= BIG_DAY }
        val recent = RECENT_MONTHS.map { months ->
            val from = today.minusMonths(months.toLong()).toEpochDay()
            val inside = days.filterKeys { it >= from }
            Window(months, inside.values.sumOf { it.photos }, inside.size)
        }
        val byYear = days.entries.groupBy({ LocalDate.ofEpochDay(it.key).year }, { it.value.photos })
            .map { (year, per) -> Year(year, per.sum(), per.size) }.sortedBy { it.year }
        val cutoff = nowMs - LATELY_DAYS * DAY_MS
        val progress = Progress(shownPhotos, days.values.count { it.shown > 0 }, days.values.count { it.lastShownMs < cutoff })
        val memories = seasonal?.let { pool ->
            Memories(settings.memoryDays, pool.size, pool.filter { it.dated }.map(::dayOf).toHashSet().size)
        }
        return Stats(
            photos = photos.size, undated = undated, days = days.size,
            firstYear = byYear.firstOrNull()?.year, lastYear = byYear.lastOrNull()?.year,
            perDayMedian = counts.getOrElse(counts.size / 2) { 0 },
            perDayP90 = counts.getOrElse((counts.size * 0.9).toInt().coerceAtMost(counts.size - 1)) { 0 },
            perDayMax = counts.lastOrNull() ?: 0, singlePhotoDays = counts.count { it == 1 },
            bigDays = big.size, bigDayPhotoShare = if (dated > 0) big.sum().toDouble() / dated else 0.0,
            recent = recent, byYear = byYear, memories = memories, progress = progress,
            pace = pace(photos.size, memories?.photos ?: 0, settings))
    }

    /** How often the frame's current rules bring a photo back, from the album size, the memory pool and the settings. */
    fun pace(photoCount: Int, memoryPool: Int, settings: Settings): Pace {
        val slotsPerDay = if (settings.secondsPerPhoto > 0) 3600.0 / settings.secondsPerPhoto * settings.awakeHours else 0.0
        val p = settings.recentPercent
        val share = when {
            memoryPool == 0 || p >= 100 -> 0.0
            p <= 0 -> 1.0
            else -> {
                // A round is the recent photos plus the memory quota; a pool too small for the quota gives what it has
                // and the recent deck takes up the rest, so the round keeps its length.
                val wanted = PhotoSelection.memorySlots(settings.roundSize, p)
                minOf(wanted, memoryPool).toDouble() / (settings.roundSize + wanted)
            }
        }
        fun back(pool: Int, portion: Double) = if (slotsPerDay > 0 && portion > 0 && pool > 0) pool / (slotsPerDay * portion) else null
        return Pace(slotsPerDay, if (slotsPerDay > 0) photoCount / slotsPerDay else 0.0, share,
            back(memoryPool, share), back(photoCount - memoryPool, 1.0 - share))
    }
}
