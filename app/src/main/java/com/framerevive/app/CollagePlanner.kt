package com.framerevive.app

import kotlin.math.abs

/**
 * Chooses the photos of a collage from the album's sizes and capture times alone, before anything is downloaded, so
 * that only the chosen photos (and two spares) are fetched. The size of the pool matters: from ten photos of one day
 * a five-photo collage is possible about 62 percent of the time, from the whole day 75 percent and from a week's
 * photos up to 97 percent, and a bigger pool no longer costs downloads. See docs/collage-picking-plan.md.
 *
 * A photo of aspect `a` fits a slot of aspect `s` when it loses at most [CollageLayout.MAX_CROP] to centre cropping,
 * that is when `(1 - max) * s <= a <= s / (1 - max)`. Both ends rise with `s`, which is why the best assignment of a
 * set of photos to a layout's slots is the sorted one (no photo order can do better), and why the photos that can
 * fill a layout form a structure in which walking the candidates in priority order and keeping each one that leaves
 * the set fillable gives the best-ranked set.
 */
object CollagePlanner {
    /** How close to the occasion the photos lie. A closer window is preferred: a moment over a day over a week. */
    enum class Window(val penalty: Float) { MOMENT(0f), DAY(0.03f), WEEK(0.08f) }

    /** The candidates of one window, best first. */
    class Pool(val window: Window, val photos: List<FramePhoto>)

    /**
     * [collage] is the chosen photos, best first; [spares] stand in for a photo that fails the blur or duplicate check
     * once it is downloaded; [downloads] is the order to fetch them in: a pair that makes a collage on its own first,
     * so a late photo still leaves something to show, then the rest of the collage, then the spares.
     */
    class Choice(val collage: List<FramePhoto>, val spares: List<FramePhoto>, val downloads: List<FramePhoto>,
                 val window: Window, val crop: Float, val utility: Float)

    /** What a collage of each size is worth against its worst crop: a good pair can beat a poor five. */
    private val BONUS = mapOf(5 to 0.5f, 3 to 0.35f, 2 to 0.3f)
    private const val DAY_MS = 86_400_000L
    /** The most photos looked at in one window. */
    const val POOL_LIMIT = 60
    /** The days either side of the occasion that make up a week. */
    const val WEEK_DAYS = 7
    private const val SPARES = 2

    private class Shaped(val photo: FramePhoto, val aspect: Float, val size: CollageLayout.Size, val rank: Int)

    private fun cropOf(slot: Float, aspect: Float): Float {
        val ratio = slot / aspect
        return if (ratio >= 1f) 1f - 1f / ratio else 1f - ratio
    }

    /** The best collage over [pools], or null when no window makes one. */
    fun plan(width: Int, height: Int, dims: (FramePhoto) -> CollageLayout.Size?, pools: List<Pool>, maxCrop: Float = CollageLayout.MAX_CROP): Choice? {
        var best: Choice? = null
        for (pool in pools) {
            val shaped = pool.photos.mapIndexedNotNull { rank, p -> dims(p)?.let { Shaped(p, it.width.toFloat() / it.height, it, rank) } }
            for (n in CollageCadence.layoutSizes(CollageCadence.MAX_PHOTOS)) {
                if (shaped.size < n) continue
                val found = (if (n == 2) bestPair(width, height, shaped, maxCrop) else bestSet(width, height, n, shaped, maxCrop)) ?: continue
                val utility = BONUS.getValue(n) - found.second - pool.window.penalty
                if (best == null || utility > best.utility + 0.0001f) {
                    val chosen = found.first
                    best = Choice(chosen.map { it.photo }, spares(chosen, shaped), emptyList(), pool.window, found.second, utility)
                        .withDownloads(width, height, chosen, maxCrop)
                }
            }
        }
        return best
    }

    /** The best-ranked photos that fill a layout of [n] slots with the least worst crop, and that crop. */
    private fun bestSet(width: Int, height: Int, n: Int, photos: List<Shaped>, maxCrop: Float): Pair<List<Shaped>, Float>? {
        var best: List<Shaped>? = null; var bestCrop = Float.MAX_VALUE; var bestRank = Int.MAX_VALUE
        for (template in CollageLayout.templates(width, height, n)) {
            val slots = template.map { it.width / it.height }.sorted()
            val bands = slots.map { (1f - maxCrop) * it to it / (1f - maxCrop) }
            val chosen = ArrayList<Shaped>(n)
            for (p in photos) {
                if (chosen.size == n) break
                chosen += p
                if (!fillable(chosen, bands)) chosen.removeAt(chosen.size - 1)
            }
            if (chosen.size < n) continue
            val sorted = chosen.sortedBy { it.aspect }
            val worst = sorted.indices.maxOf { cropOf(slots[it], sorted[it].aspect) }
            val rank = chosen.sumOf { it.rank }
            // The least crop wins; crops within a thousandth are a tie, which goes to the better-ranked photos.
            if (worst < bestCrop - 0.001f || (worst < bestCrop + 0.001f && rank < bestRank)) { best = chosen; bestCrop = worst; bestRank = rank }
        }
        return best?.let { it to bestCrop }
    }

    /** True when every photo of [set] can be given a different slot whose band it lies in. */
    private fun fillable(set: List<Shaped>, bands: List<Pair<Float, Float>>): Boolean {
        val used = BooleanArray(bands.size)
        fun place(i: Int): Boolean {
            if (i == set.size) return true
            for (s in bands.indices) if (!used[s] && set[i].aspect >= bands[s].first && set[i].aspect <= bands[s].second) {
                used[s] = true
                if (place(i + 1)) return true
                used[s] = false
            }
            return false
        }
        return place(0)
    }

    /** The best-ranked pair that tiles the screen (the pair layout splits it so both lose the same share). */
    private fun bestPair(width: Int, height: Int, photos: List<Shaped>, maxCrop: Float): Pair<List<Shaped>, Float>? {
        var best: List<Shaped>? = null; var bestCrop = 0f; var bestRank = Int.MAX_VALUE
        for (i in photos.indices) {
            if (i * 2 + 1 >= bestRank) break
            for (j in i + 1 until photos.size) {
                val rank = photos[i].rank + photos[j].rank
                if (rank >= bestRank) break
                val crop = CollageLayout.pair(width, height, photos[i].size, photos[j].size, height > width).crop
                if (crop <= maxCrop) { best = listOf(photos[i], photos[j]); bestCrop = crop; bestRank = rank }
            }
        }
        return best?.let { it to bestCrop }
    }

    /**
     * Up to [SPARES] photos, best ranked first, that could take the place of a chosen one: not chosen, and within a quarter
     * of a chosen photo's shape. One wide and one tall where there are both, since a photo that fails is most often
     * replaced by one of its own shape.
     */
    private fun spares(chosen: List<Shaped>, photos: List<Shaped>): List<FramePhoto> {
        val taken = chosen.map { it.photo.id }.toSet()
        val fits = photos.filter { it.photo.id !in taken && chosen.any { c -> (c.aspect > 1f) == (it.aspect > 1f) && abs(c.aspect - it.aspect) / c.aspect < 0.25f } }
        val out = ArrayList<Shaped>()
        for (wide in listOf(true, false)) fits.firstOrNull { (it.aspect > 1f) == wide }?.let { out += it }
        for (p in fits) { if (out.size >= SPARES) break; if (p !in out) out += p }
        return out.sortedBy { it.rank }.take(SPARES).map { it.photo }
    }

    private fun Choice.withDownloads(width: Int, height: Int, chosen: List<Shaped>, maxCrop: Float): Choice {
        var pair: Pair<Shaped, Shaped>? = null; var pairCrop = Float.MAX_VALUE
        for (i in chosen.indices) for (j in i + 1 until chosen.size) {
            val crop = CollageLayout.pair(width, height, chosen[i].size, chosen[j].size, height > width).crop
            if (crop <= maxCrop && crop < pairCrop) { pair = chosen[i] to chosen[j]; pairCrop = crop }
        }
        val first = pair?.let { listOf(it.first.photo, it.second.photo) }.orEmpty()
        val order = (first + collage + spares).distinctBy { it.id }
        return Choice(collage, spares, order, window, crop, utility)
    }

    // ---- The candidate pools ----

    private fun dayOf(p: FramePhoto) = Math.floorDiv(p.takenMs + p.offsetMs, DAY_MS)

    /**
     * The pools to plan from, closest occasion first: the theme's own candidates when it is an outing (the moment), the
     * photos of its day, and the photos within a week of it. Photos already shown this cycle or too soft are left out;
     * photos not yet shown come first, then the nearer days. Only sizes and times are read, so the pools can include
     * photos that are not downloaded. The scan is millisecond arithmetic over the album, which the frame can afford.
     */
    fun pools(ctx: SlideContext, queue: SlideQueue, theme: CollageTheme, themePool: List<FramePhoto>): List<Pool> {
        val anchor = themePool.firstOrNull { it.dated }?.let(::dayOf) ?: return emptyList()
        val blocked = queue.ineligible(emptySet())
        fun shown(p: FramePhoto) = (ctx.seen[p.id]?.plays ?: 0) > 0
        val week = ctx.dayIndex.window(anchor, WEEK_DAYS).filter { it.id !in blocked && ctx.sharp(it) }
        val onDay = week.filter { dayOf(it) == anchor }
        // Not yet shown first and spread through the day, so the walk does not take neighbours of one instant.
        val day = CollageThemes.ordered(onDay, ctx.seen, POOL_LIMIT)
        // The other days: not yet shown first, then the nearer days, then in time order.
        val others = week.filter { dayOf(it) != anchor }
            .sortedWith(compareBy<FramePhoto>({ if (shown(it)) 1 else 0 }, { abs(dayOf(it) - anchor) }, { it.takenMs }))
        val out = ArrayList<Pool>()
        if (theme.id == "outing") out += Pool(Window.MOMENT, themePool.filter { it.id !in blocked && ctx.sharp(it) })
        out += Pool(Window.DAY, day)
        out += Pool(Window.WEEK, day.take(POOL_LIMIT / 2) + others.take(POOL_LIMIT / 2))
        return out
    }
}
