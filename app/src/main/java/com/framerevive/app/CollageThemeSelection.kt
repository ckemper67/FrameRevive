package com.framerevive.app

import java.time.LocalDate

/** One calendar day of the album: its photos in capture order. The day is the photo's own local day. */
class AlbumDay(val epochDay: Long, private val unsorted: List<FramePhoto>) {
    /** How many photos the day has, known without sorting them. */
    val size: Int get() = unsorted.size
    /** Any photo of the day, for a weight that depends only on its date; no sorting needed. */
    val anyPhoto: FramePhoto get() = unsorted.first()
    /** The day's photos in time order, sorted when first asked for: ranking 2,000 days must not sort all their photos. */
    val photos: List<FramePhoto> by lazy { unsorted.sortedBy { it.takenMs } }
    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)
}

/**
 * Which days of the album a collage may draw from, best first. Themes build on this: a collage is one day or one
 * stretch of a day, and the day ranking keeps collages walking through the album as the deck does.
 */
/**
 * How a collage's anchor day is drawn, fixed once for each collage: a recent draw (with probability `recentPercent / 100`)
 * weights the days by the singles' own recency weight, and a plain draw weights them all alike. A collage is wholly one or
 * the other, never a mixture. See docs/collage-mix-plan.md.
 */
class AnchorDraw(val recent: Boolean, val recency: Recency, val todayMs: Long, val random: kotlin.random.Random) {
    companion object {
        fun flip(recentPercent: Int, recency: Recency, todayMs: Long, random: kotlin.random.Random) =
            AnchorDraw(random.nextInt(100) < recentPercent.coerceIn(0, 100), recency, todayMs, random)
    }
}

object CollageThemeSelection {
    const val MAX_MEMBERS = 4
    /** A day offers more than the four members used, so the layout can pick the shapes that compose. */
    const val MAX_CANDIDATES = 12
    private const val DAY_MS = 86_400_000L

    private class Ranked(val day: AlbumDay, val readyEnough: Boolean, val fresh: Int, val meanLastShown: Long, val unseenShare: Double)
    /** The singles' "newest" is a hard window of the newest unseen photos; for days it is the newest few of the best-ranked days. */
    private const val NEWEST_WINDOW = 20
    /** A day's size counts towards its weight up to this many photos, so a huge day does not drown the rest. */
    private const val SIZE_CAP = 20

    /**
     * Days with at least [minCount] dated photos (at least [count] by default), best first, ranked by how well each
     * can fill [count] photos: a fully downloaded day first, then the day with the most
     * photos not yet shown, then the day shown longest ago, then the bigger day, then the more recent one.
     * [avoidDays] keeps the same date out of two collages in a row and is dropped when no other day qualifies.
     * Days are grouped with plain arithmetic, not a calendar conversion per photo, which stalled the UI thread.
     */
    fun rankedDays(album: List<FramePhoto>, count: Int, cached: Set<String>, avoid: Set<String> = emptySet(),
                   avoidDays: Set<LocalDate> = emptySet(), seen: Map<String, PlayRecord> = emptyMap(), minCount: Int = count): List<AlbumDay> =
        rankedDays(DayIndex(album).days, count, cached, avoid, avoidDays, seen, minCount)

    /** As [rankedDays] over [groups], the album's days already grouped (see [DayIndex]), so nothing is grouped here. */
    fun rankedDays(groups: Map<Long, List<FramePhoto>>, count: Int, cached: Set<String>, avoid: Set<String> = emptySet(),
                   avoidDays: Set<LocalDate> = emptySet(), seen: Map<String, PlayRecord> = emptyMap(), minCount: Int = count,
                   draw: AnchorDraw? = null): List<AlbumDay> {
        require(count in CollageCadence.MIN_PHOTOS..CollageCadence.MAX_PHOTOS && minCount in CollageCadence.MIN_PHOTOS..count)
        fun rank(excluded: Set<Long>): List<AlbumDay> {
            val ranked = ArrayList<Ranked>()
            for ((day, all) in groups) {
                if (day in excluded) continue
                val photos = if (avoid.isEmpty()) all else all.filter { it.id !in avoid }
                if (photos.size < minCount) continue
                // One pass over the day for the three figures the ranking needs.
                var ready = 0; var unseen = 0; var lastShown = 0L
                for (p in photos) {
                    if (p.id in cached) ready++
                    val record = seen[p.id]
                    if (record == null) unseen++ else lastShown += record.lastShownMs
                }
                ranked += Ranked(AlbumDay(day, photos), ready >= count, minOf(count, unseen), lastShown / photos.size, unseen.toDouble() / photos.size)
            }
            val sorted = ranked.sortedWith(compareByDescending<Ranked> { it.readyEnough }.thenByDescending { it.fresh }.thenBy { it.meanLastShown }
                .thenByDescending { it.day.size }.thenByDescending { it.day.epochDay })
            return (if (draw == null) sorted else drawFirst(sorted, draw)).map { it.day }
        }
        val avoided = avoidDays.map { it.toEpochDay() }.toSet()
        return rank(avoided).ifEmpty { if (avoided.isEmpty()) emptyList() else rank(emptySet()) }
    }

    /**
     * [ranked] with one day moved to the front, drawn at random from the best class: the days as ready and as unseen as
     * the first (so a day with fewer unseen photos is never preferred), weighted by size (up to [SIZE_CAP]) and by how much
     * of the day is unseen (among the days as unseen as the most unseen). A recent draw also weights each day by [Recency.weigher], the singles' own weight; for "newest",
     * which has none, a recent draw takes only the newest [NEWEST_WINDOW] days of the class. The rest keep their order.
     */
    private fun drawFirst(ranked: List<Ranked>, draw: AnchorDraw): List<Ranked> {
        if (ranked.size < 2) return ranked
        val head = ranked.first()
        var pool = ranked.takeWhile { it.readyEnough == head.readyEnough && it.fresh == head.fresh }
        // Days with unseen photos before days already shown from: only the days as unseen as the most unseen one are drawn from.
        val mostUnseen = pool.maxOf { it.unseenShare }
        pool = pool.filter { it.unseenShare >= mostUnseen - 1e-9 }
        val weigher = if (draw.recent) draw.recency.weigher(draw.todayMs) else null
        if (draw.recent && weigher == null && draw.recency == Recency.NEWEST) pool = pool.sortedByDescending { it.day.epochDay }.take(NEWEST_WINDOW)
        if (pool.size < 2) return ranked
        val weights = pool.map { r -> minOf(r.day.size, SIZE_CAP) * r.unseenShare * (weigher?.invoke(r.day.anyPhoto) ?: 1.0) }
        var pick = draw.random.nextDouble() * weights.sum()
        var chosen = pool.last()
        for (i in pool.indices) { pick -= weights[i]; if (pick <= 0.0) { chosen = pool[i]; break } }
        return listOf(chosen) + ranked.filter { it !== chosen }
    }

    /**
     * The photos taken within [memoryDays] of today's date in an earlier year, "on this day", the window the single-photo
     * memories use, so a moving holiday is caught. Only millisecond arithmetic per photo: a calendar conversion for each
     * of 15,000 photos costs about a third of a second on the frame.
     */
    fun onThisDay(photos: List<FramePhoto>, today: LocalDate, memoryDays: Int): List<FramePhoto> =
        DayIndex(photos).onThisDay(today, memoryDays).values.flatten()

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
