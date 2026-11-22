package com.framerevive.app

/**
 * Picks the photos for a curated collage so their shapes tile the frame, the way Show Pair does for two.
 * The saved cadence still sets the wanted count; if that many photos cannot cover the frame within the
 * crop limit the next smaller count is tried, and only then does playback fall back to the letterboxed
 * layout.
 */
object CollageSelection {
    /** Candidates looked at. More shapes to choose from tile the frame more often; each distinct set of shapes is fitted once. */
    const val LOOKAHEAD = 10

    fun choose(count: Int, width: Int, height: Int, upcoming: List<FramePhoto>, size: (FramePhoto) -> CollageLayout.Size?,
               usable: (FramePhoto) -> Boolean, avoid: Set<String>, maxCrop: Float = CollageLayout.MAX_CROP,
               hashes: Map<String, Long> = emptyMap(), lookahead: Int = LOOKAHEAD): List<FramePhoto> {
        val pool = upcoming.asSequence().filter { it.id !in avoid }.take(lookahead).filter { usable(it) }.toList()
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
        // The layout depends only on the aspect ratios, and photos from one camera share theirs, so photos are grouped by
        // aspect and each distinct way of taking n of them is fitted once, however many combinations of photos it covers.
        val sizes = pool.map(size)
        val classes = LinkedHashMap<Int, ArrayList<Int>>()
        for (i in pool.indices) { val s = sizes[i] ?: continue; classes.getOrPut(Math.round(s.width.toFloat() / s.height * 50f)) { ArrayList() } += i }
        val keys = classes.keys.toList()
        val shapeOf = keys.map { sizes[classes.getValue(it).first()]!! }
        class Option(val counts: IntArray, val crop: Float, val indexSum: Int)
        val options = ArrayList<Option>()
        fun walk(k: Int, left: Int, counts: IntArray) {
            if (k == keys.size) {
                if (left != 0) return
                val shapes = keys.indices.flatMap { c -> List(counts[c]) { shapeOf[c] } }
                val layout = CollageLayout.fill(width, height, shapes, maxCrop = maxCrop) ?: return
                // The earliest photos of each class are the ones used, so the sum of their positions breaks ties.
                options += Option(counts.copyOf(), layout.crop, keys.indices.sumOf { c -> (0 until counts[c]).sumOf { classes.getValue(keys[c])[it] } })
                return
            }
            for (c in 0..minOf(left, classes.getValue(keys[k]).size)) { counts[k] = c; walk(k + 1, left - c, counts) }
            counts[k] = 0
        }
        walk(0, n, IntArray(keys.size))
        // Least crop first; crops within a thousandth are a tie, which goes to the earlier photos.
        for (option in options.sortedWith(compareBy({ Math.round(it.crop * 1000f) }, { it.indexSum }))) {
            val photos = keys.indices.flatMap { c -> classes.getValue(keys[c]).take(option.counts[c]).map { pool[it] } }
                .sortedBy { pool.indexOf(it) }
            // A collage of the same moment twice is worse than a smaller collage.
            if (hashes.isNotEmpty() && PhotoSimilarity.hasDuplicates(photos, hashes)) continue
            return photos
        }
        return null
    }

    private fun combinations(items: List<Int>, n: Int): List<List<Int>> =
        if (n == 0) listOf(emptyList())
        else items.indices.flatMap { i -> combinations(items.drop(i + 1), n - 1).map { listOf(items[i]) + it } }
}
