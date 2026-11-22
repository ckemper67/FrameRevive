package com.framerevive.app

import java.time.LocalDate

/**
 * Orders photos so that each one is as far in time from those before it as possible: a spread through a day or an
 * outing instead of neighbours of one instant. Time is known for every photo, unlike a hash or a sharpness score.
 */
object TimeSpread {
    /**
     * Up to [limit] photos from [photos], the first being the best-ranked (the first in the list) unless [anchors]
     * are given, in which case the first is the one farthest from them. Ties go to the earlier in the list.
     */
    fun order(photos: List<FramePhoto>, anchors: List<Long> = emptyList(), limit: Int = photos.size): List<FramePhoto> {
        val remaining = ArrayList(photos)
        val out = ArrayList<FramePhoto>(minOf(limit, photos.size))
        val reference = ArrayList(anchors)
        while (out.size < limit && remaining.isNotEmpty()) {
            val next = if (reference.isEmpty()) remaining.first()
                else remaining.maxByOrNull { p -> reference.minOf { kotlin.math.abs(p.takenMs - it) } }!!
            remaining.remove(next)
            out += next
            reference += next.takenMs
        }
        return out
    }

    /**
     * [photos] in order with any photo within [minGapMs] of an earlier kept one left out, so no two are frames of one
     * instant. Falls back to [photos] unchanged when fewer than [needed] would remain: a collage of near neighbours is
     * better than none.
     */
    fun separated(photos: List<FramePhoto>, needed: Int, minGapMs: Long = Bursts.MIN_SEPARATION_MS): List<FramePhoto> {
        val kept = ArrayList<FramePhoto>()
        for (p in photos) if (kept.none { kotlin.math.abs(p.takenMs - it.takenMs) < minGapMs }) kept += p
        return if (kept.size >= needed) kept else photos
    }
}

/** A way of choosing the photos of a collage. The shape fitting then picks the combination that crops least. */
interface CollageTheme {
    val id: String
    /** What a person reads on the controls page. */
    val label: String
    /**
     * 8 to 12 candidate photos for a collage of [count], best first (the first few are the ones downloaded ahead), or
     * empty when the theme has nothing to offer now. Must not consume the queue. [lastDay] is the previous collage's
     * day, which a theme avoids when it can.
     */
    fun candidates(ctx: SlideContext, queue: SlideQueue, count: Int, avoid: Set<String>, lastDay: LocalDate?): List<FramePhoto>
}

object CollageThemes {
    /** Photos of an outing lie within this long of its seed photo, either side. */
    const val OUTING_WINDOW_MS = 60L * 60L * 1000L
    private const val OUTING_DAYS = 40
    private const val OUTING_SEEDS = 20

    /** Not yet shown first, then spread through time, so a collage shows different parts of the day. */
    private fun ordered(photos: List<FramePhoto>, seen: Map<String, PlayRecord>, limit: Int): List<FramePhoto> {
        val (shown, unseen) = photos.partition { (seen[it.id]?.plays ?: 0) > 0 }
        val first = TimeSpread.order(unseen, limit = limit)
        return first + TimeSpread.order(shown, anchors = first.map { it.takenMs }, limit = limit - first.size)
    }

    /** Different parts of one day. */
    val DAY = object : CollageTheme {
        override val id = "day"
        override val label = "A day"
        override fun candidates(ctx: SlideContext, queue: SlideQueue, count: Int, avoid: Set<String>, lastDay: LocalDate?): List<FramePhoto> {
            val day = CollageThemeSelection.rankedDays(ctx.photos, count, ctx.cachedIds, queue.ineligible(avoid), setOfNotNull(lastDay), ctx.seen,
                minCount = CollageCadence.MIN_PHOTOS).firstOrNull() ?: return emptyList()
            return TimeSpread.separated(ordered(day.photos, ctx.seen, CollageThemeSelection.MAX_CANDIDATES * 3), CollageCadence.MIN_PHOTOS)
                .take(CollageThemeSelection.MAX_CANDIDATES)
        }
    }

    /**
     * One stretch of a day, like a walk or a party: a seed photo and the photos within [OUTING_WINDOW_MS] of it, chosen
     * far apart in time and never within two minutes of each other, so they are not frames of one instant.
     */
    val OUTING = object : CollageTheme {
        override val id = "outing"
        override val label = "One outing"
        override fun candidates(ctx: SlideContext, queue: SlideQueue, count: Int, avoid: Set<String>, lastDay: LocalDate?): List<FramePhoto> {
            val days = CollageThemeSelection.rankedDays(ctx.photos, count, ctx.cachedIds, queue.ineligible(avoid), setOfNotNull(lastDay), ctx.seen,
                minCount = CollageCadence.MIN_PHOTOS)
            // The first outing with enough photos for [count] wins; failing that, the richest one found.
            var richest: List<FramePhoto> = emptyList(); var richestApart = 0
            for (day in days.take(OUTING_DAYS)) {
                // Seeds: not yet shown first, then already downloaded, then shown longest ago.
                val seeds = day.photos.sortedWith(compareBy<FramePhoto> { if ((ctx.seen[it.id]?.plays ?: 0) > 0) 1 else 0 }
                    .thenBy { if (it.id in ctx.cachedIds) 0 else 1 }.thenBy { ctx.seen[it.id]?.lastShownMs ?: 0L })
                for (seed in seeds.take(OUTING_SEEDS)) {
                    val near = day.photos.filter { it !== seed && kotlin.math.abs(it.takenMs - seed.takenMs) <= OUTING_WINDOW_MS }
                    val spread = TimeSpread.order(near, anchors = listOf(seed.takenMs), limit = CollageThemeSelection.MAX_CANDIDATES - 1)
                    val apart = apart(seed, spread)
                    if (apart >= count - 1) return TimeSpread.separated(listOf(seed) + spread, count).take(CollageThemeSelection.MAX_CANDIDATES)
                    if (apart > richestApart) { richestApart = apart; richest = TimeSpread.separated(listOf(seed) + spread, apart + 1).take(CollageThemeSelection.MAX_CANDIDATES) }
                }
            }
            return if (richestApart >= CollageCadence.MIN_PHOTOS - 1) richest else emptyList()
        }

        /** How many of [spread], taken in order, are at least [Bursts.MIN_SEPARATION_MS] from the seed and from each other. */
        private fun apart(seed: FramePhoto, spread: List<FramePhoto>): Int {
            val chosen = arrayListOf(seed.takenMs)
            for (p in spread) if (chosen.all { kotlin.math.abs(p.takenMs - it) >= Bursts.MIN_SEPARATION_MS }) chosen += p.takenMs
            return chosen.size - 1
        }
    }

    val all: List<CollageTheme> = listOf(DAY, OUTING)
    fun byId(id: String): CollageTheme? = all.firstOrNull { it.id == id }
}
