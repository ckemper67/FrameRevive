package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random

/** What the chooser needs from its surroundings, so it needs no Android types. */
class SlideContext(
    val photos: List<FramePhoto>, val cachedIds: Set<String>, val isCached: (FramePhoto) -> Boolean,
    val size: (FramePhoto) -> CollageLayout.Size?, val usableForCollage: (FramePhoto) -> Boolean,
    val hashes: Map<String, Long>, val width: Int, val height: Int,
    val collages: Boolean, val collageEvery: Int, val collageSource: String, val pairs: Boolean)

/**
 * Owns the queue and decides the presentation of each slide: a collage when one falls due, else a pair when
 * one fits, else a single. It also names the photos worth downloading next, because a same-day collage pulls
 * photos that are not in the ordinary queue and its date has to be chosen before it falls due.
 */
class SlideChooser(private val random: Random = Random.Default) {
    val cadence = CollageCadence()
    var playlist: List<FramePhoto> = emptyList()
    var index = 0
    var recentIds: Set<String> = emptySet()
    var collageDay: List<FramePhoto> = emptyList()
    var collageTheme: CollageTheme? = null
    var lastCollageTheme: CollageTheme? = null
    var lastCollageDay: LocalDate? = null

    /** Starts a cycle from a freshly built playlist. */
    fun load(cycle: List<FramePhoto>, recent: Set<String>) { playlist = cycle; recentIds = recent; index = 0 }

    /** A new album invalidates everything chosen from the old one. */
    fun resetForAlbum() { cadence.reset(); collageDay = emptyList(); lastCollageDay = null; collageTheme = null }

    /** The photos to have ready, nearest first. */
    fun preloadPlan(ctx: SlideContext, depth: Int): List<FramePhoto> {
        val collageSoon = ctx.collages && cadence.singles >= ctx.collageEvery - 1
        val count = if (collageSoon) maxOf(depth, 5) else depth
        // The date has to be chosen here, while there is still time to download it: choosing it only when
        // the collage falls due leaves nothing cached, the layout finds no usable shapes and playback
        // silently falls back to the recent source.
        val planned = if (collageSoon) {
            val source = CollageThemeSelection.chosen(ctx.collageSource, random)
            if (source == CollageTheme.DAY && collageDay.size < cadence.nextCount)
                collageDay = CollageThemeSelection.bestDay(ctx.photos, cadence.nextCount, ctx.cachedIds, emptySet(), setOfNotNull(lastCollageDay))
            collageDay + playlist.drop(index)
        } else playlist.drop(index)
        return planned.distinctBy { it.id }.take(count)
    }

    /** The next slide, or null when the queue is empty. The caller rebuilds an exhausted queue first. */
    fun next(ctx: SlideContext, last: FrameSlide?): FrameSlide? {
        if (playlist.isEmpty()) return null
        val avoid = last?.photos?.map { it.id }?.toSet().orEmpty()
        if (ctx.collages && cadence.due(ctx.collageEvery)) collage(ctx, avoid, last)?.let { return it }
        if (ctx.pairs) pair(ctx, avoid)?.let { return it }
        return FrameSlide(listOf(playlist[index++]))
    }

    private fun take(chosen: List<FramePhoto>) {
        val ids = chosen.map { it.id }.toSet()
        playlist = playlist.take(index) + playlist.drop(index).filterNot { it.id in ids }
    }

    private fun collage(ctx: SlideContext, avoid: Set<String>, last: FrameSlide?): FrameSlide? {
        var theme = collageTheme ?: CollageThemeSelection.chosen(ctx.collageSource, random)
            .also { collageTheme = it; collageDay = emptyList() }
        fun poolFor(source: CollageTheme): List<FramePhoto> = when (source) {
            CollageTheme.DAY -> {
                if (collageDay.size < cadence.nextCount)
                    collageDay = CollageThemeSelection.bestDay(ctx.photos, cadence.nextCount, ctx.cachedIds, avoid, setOfNotNull(lastCollageDay))
                collageDay
            }
            CollageTheme.RECENT -> playlist.drop(index).filter { it.id in recentIds }
        }
        // Shape and quality selection run inside the theme; the letterboxed layout is the last resort.
        fun collageFor(source: CollageTheme): List<FramePhoto> {
            val pool = poolFor(source)
            val fitting = CollageSelection.choose(cadence.nextCount, ctx.width, ctx.height, pool, ctx.size, ctx.usableForCollage, avoid, hashes = ctx.hashes)
            return if (fitting.size in 2..4) fitting
                else CollageCadence.candidates(pool, last?.photos.orEmpty(), cadence.nextCount, ctx.usableForCollage)
        }
        var selected = collageFor(theme)
        if (selected.size !in 2..4) {
            val alternative = CollageThemeSelection.other(theme)
            collageFor(alternative).takeIf { it.size in 2..4 }?.let { selected = it; theme = alternative }
        }
        if (selected.size !in 2..4) return null
        take(selected)
        lastCollageTheme = theme; collageTheme = null; collageDay = emptyList()
        if (theme == CollageTheme.DAY) lastCollageDay = selected.first().date()
        return FrameSlide(selected, SlideMode.COLLAGE)
    }

    private fun pair(ctx: SlideContext, avoid: Set<String>): FrameSlide? {
        // Show Pair: two photos of the orientation opposite the frame fill the screen.
        val pair = PairSelection.choose(ctx.width, ctx.height, playlist.drop(index), ctx.size, ctx.isCached, avoid)
        if (pair.size != 2) return null
        take(pair)
        return FrameSlide(pair, SlideMode.PAIR)
    }
}
