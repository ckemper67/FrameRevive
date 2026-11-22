package com.framerevive.app

/**
 * Catches near-identical frames: a burst of ten shots of the same moment, or the same picture saved twice.
 *
 * A 64-bit difference hash is built from the grey analysis copy the quality gate already decodes, so this
 * costs one pass over pixels that are in memory anyway. Each bit compares a cell with the one to its right,
 * which survives small changes in exposure and focus but changes as soon as the composition does.
 */
object PhotoSimilarity {
    const val GRID_W = 9
    const val GRID_H = 8
    /** Bits of difference below which two photos count as the same moment. Out of 64. */
    const val THRESHOLD = 8

    fun dhash(luma: IntArray, width: Int, height: Int): Long {
        require(width > 1 && height > 1 && luma.size >= width * height)
        val cells = IntArray(GRID_W * GRID_H)
        for (gy in 0 until GRID_H) {
            val y0 = gy * height / GRID_H
            val y1 = maxOf(y0 + 1, (gy + 1) * height / GRID_H)
            for (gx in 0 until GRID_W) {
                val x0 = gx * width / GRID_W
                val x1 = maxOf(x0 + 1, (gx + 1) * width / GRID_W)
                var sum = 0L
                var count = 0
                for (y in y0 until y1) for (x in x0 until x1) { sum += luma[y * width + x]; count++ }
                cells[gy * GRID_W + gx] = (sum / maxOf(count, 1)).toInt()
            }
        }
        var bits = 0L
        var bit = 0
        for (gy in 0 until GRID_H) for (gx in 0 until GRID_W - 1) {
            if (cells[gy * GRID_W + gx] > cells[gy * GRID_W + gx + 1]) bits = bits or (1L shl bit)
            bit++
        }
        return bits
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    fun near(a: Long, b: Long, threshold: Int = THRESHOLD): Boolean = distance(a, b) <= threshold

    /** True when any pair in [photos] is the same moment; used to keep a collage's members distinct. */
    fun hasDuplicates(photos: List<FramePhoto>, hashes: Map<String, Long>, threshold: Int = THRESHOLD): Boolean {
        val values = photos.mapNotNull { hashes[it.id] }
        for (i in values.indices) for (j in i + 1 until values.size) if (near(values[i], values[j], threshold)) return true
        return false
    }
}
