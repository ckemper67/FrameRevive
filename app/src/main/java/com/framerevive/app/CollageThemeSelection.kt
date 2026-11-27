package com.framerevive.app

import java.time.LocalDate

/** One calendar day of the album: its photos in capture order. The day is the photo's own local day. */
class AlbumDay(val epochDay: Long, val photos: List<FramePhoto>) {
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

/**
 * Which days of the album a collage may draw from, best first. Themes build on this: a collage is one day or one
 * stretch of a day, and the day ranking keeps collages walking through the album as the deck does.
 */
object CollageThemeSelection {
    const val MAX_MEMBERS = 4
    /** A day offers more than the four members used, so the layout can pick the shapes that compose. */
    const val MAX_CANDIDATES = 12
    private const val DAY_MS = 86_400_000L

    private class Ranked(val day: AlbumDay, val readyEnough: Boolean, val fresh: Int, val meanLastShown: Long)

    /**
     * Days with at least [minCount] dated photos (at least [count] by default), best first, ranked by how well each
     * can fill [count] photos: a fully downloaded day first, then the day with the most
     * photos not yet shown, then the day shown longest ago, then the bigger day, then the more recent one.
     * [avoidDays] keeps the same date out of two collages in a row and is dropped when no other day qualifies.
     * Days are grouped with plain arithmetic, not a calendar conversion per photo, which stalled the UI thread.
     */
    fun rankedDays(album: List<FramePhoto>, count: Int, cached: Set<String>, avoid: Set<String> = emptySet(),
                   avoidDays: Set<LocalDate> = emptySet(), seen: Map<String, PlayRecord> = emptyMap(), minCount: Int = count): List<AlbumDay> {
        require(count in CollageCadence.MIN_PHOTOS..CollageCadence.MAX_PHOTOS && minCount in CollageCadence.MIN_PHOTOS..count)
        val groups = HashMap<Long, ArrayList<FramePhoto>>()
        for (p in album) {
            if (!p.dated || p.id in avoid) continue
            groups.getOrPut(Math.floorDiv(p.takenMs + p.offsetMs, DAY_MS)) { ArrayList() } += p
        }
        fun rank(excluded: Set<Long>): List<AlbumDay> = groups.entries
            .filter { (day, photos) -> day !in excluded && photos.size >= minCount }
            .map { (day, photos) ->
                Ranked(AlbumDay(day, photos.sortedBy { it.takenMs }), photos.count { it.id in cached } >= count,
                    minOf(count, photos.count { it.id !in seen }), photos.sumOf { seen[it.id]?.lastShownMs ?: 0L } / photos.size)
            }
            .sortedWith(compareByDescending<Ranked> { it.readyEnough }.thenByDescending { it.fresh }.thenBy { it.meanLastShown }
                .thenByDescending { it.day.photos.size }.thenByDescending { it.day.epochDay })
            .map { it.day }
        val avoided = avoidDays.map { it.toEpochDay() }.toSet()
        return rank(avoided).ifEmpty { if (avoided.isEmpty()) emptyList() else rank(emptySet()) }
    }

    /**
     * Candidates from the best single capture date: downloaded photos first, then the ones shown longest ago, so a
     * collage is not handed whatever four photos the date happened to have. See [rankedDays].
     */
    fun bestDay(album: List<FramePhoto>, count: Int, cached: Set<String>, avoid: Set<String> = emptySet(),
                avoidDays: Set<LocalDate> = emptySet(), limit: Int = MAX_CANDIDATES,
                seen: Map<String, PlayRecord> = emptyMap()): List<FramePhoto> {
        require(count in CollageCadence.MIN_PHOTOS..CollageCadence.MAX_PHOTOS && limit in count..MAX_CANDIDATES)
        val day = rankedDays(album, count, cached, avoid, avoidDays, seen).firstOrNull() ?: return emptyList()
        val (ready, other) = day.photos.sortedBy { seen[it.id]?.lastShownMs ?: -1L }.partition { it.id in cached }
        return (ready + other).take(limit)
    }
}
