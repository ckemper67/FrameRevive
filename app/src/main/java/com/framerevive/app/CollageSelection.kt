package com.framerevive.app

/**
 * Picks the photos for a curated collage so their shapes tile the frame, the way Show Pair does for two.
 * The saved cadence still sets the wanted count; if that many photos cannot cover the frame within the
 * crop limit the next smaller count is tried, and only then does playback fall back to the letterboxed
 * layout.
 */
object CollageSelection {
    const val LOOKAHEAD = 6

    fun choose(count: Int, width: Int, height: Int, upcoming: List<FramePhoto>, size: (FramePhoto) -> CollageLayout.Size?,
               usable: (FramePhoto) -> Boolean, avoid: Set<String>, maxCrop: Float = CollageLayout.MAX_CROP,
               hashes: Map<String, Long> = emptyMap()): List<FramePhoto> {
        val pool = upcoming.asSequence().filter { it.id !in avoid }.take(LOOKAHEAD).filter { usable(it) }.toList()
        for (n in count.coerceIn(2, 4) downTo 2) {
            if (pool.size < n) continue
            best(n, width, height, pool, size, maxCrop, hashes)?.let { return it }
            // Every arrangement wanted the same shot twice: allow the repeats rather than show nothing.
            if (n == 2) best(n, width, height, pool, size, maxCrop, emptyMap())?.let { return it }
        }
        return emptyList()
    }

    private fun best(n: Int, width: Int, height: Int, pool: List<FramePhoto>, size: (FramePhoto) -> CollageLayout.Size?, maxCrop: Float,
                     hashes: Map<String, Long>): List<FramePhoto>? {
        var chosen: List<FramePhoto>? = null; var leastCrop = Float.MAX_VALUE
        for (indices in combinations((0 until pool.size).toList(), n)) {
            val photos = indices.map { pool[it] }
            val sizes = photos.map { size(it) ?: return@map null }
            if (sizes.any { it == null }) continue
            // A collage of the same moment twice is worse than a smaller collage.
            if (hashes.isNotEmpty() && PhotoSimilarity.hasDuplicates(photos, hashes)) continue
            val layout = CollageLayout.fill(width, height, sizes.filterNotNull(), maxCrop = maxCrop) ?: continue
            if (layout.crop < leastCrop - 0.001f) { leastCrop = layout.crop; chosen = photos }
        }
        return chosen
    }

    private fun combinations(items: List<Int>, n: Int): List<List<Int>> =
        if (n == 0) listOf(emptyList())
        else items.indices.flatMap { i -> combinations(items.drop(i + 1), n - 1).map { listOf(items[i]) + it } }
}
