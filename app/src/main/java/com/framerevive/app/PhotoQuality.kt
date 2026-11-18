package com.framerevive.app

/**
 * Cheap still-image quality gate, in the spirit of the "ignoring blurry or grainy shots" claim on Lenovo's
 * frame. One grey-scale pass over a 256-pixel analysis copy gives the focus, exposure and contrast measures,
 * so the cost is the small decode rather than the maths, and each photo is scored once and remembered.
 *
 * Absolute blur thresholds misfire across scenes: a foggy landscape is legitimately soft, and a photo of a
 * plain wall has almost no detail. The gate is therefore comparative — a photo is only dropped from collages
 * when it is far softer than the album's own median — plus unambiguous exposure and contrast rejections.
 */
data class PhotoScore(val sharpness: Float, val contrast: Float, val mean: Float, val clipped: Float) {
    /** Anything plainly unusable: black, blown out, or flat. Blur is judged separately, against the album. */
    val visible: Boolean
        get() = contrast >= MIN_CONTRAST && mean in MIN_MEAN..MAX_MEAN && clipped <= MAX_CLIPPED

    companion object {
        const val MIN_CONTRAST = 3f
        const val MIN_MEAN = 8f
        const val MAX_MEAN = 247f
        const val MAX_CLIPPED = 0.6f
    }
}

object PhotoQuality {
    const val ANALYSIS_EDGE = 256
    /** Fewer scored photos than this leave the album without a usable median. */
    const val MIN_SAMPLES = 30
    /** Share of the album's median sharpness below which a photo counts as a soft shot. */
    const val SOFT_FRACTION = 0.12f
    /** Below this median the album itself is soft, so nothing is filtered on sharpness. */
    const val MIN_MEDIAN = 0.004f
    /** Stored instead of a score for photos that should never reach a collage. */
    const val REJECTED = -1f

    /**
     * [luma] holds one 0-255 grey value per pixel, row major. Sharpness is the 4-neighbour Laplacian variance
     * normalised by the photo's own pixel variance, so it measures structure rather than brightness.
     */
    fun measure(luma: IntArray, width: Int, height: Int): PhotoScore {
        require(width > 2 && height > 2 && luma.size >= width * height)
        val pixels = width * height
        var sum = 0.0; var squares = 0.0; var clipped = 0
        for (i in 0 until pixels) {
            val v = luma[i]; sum += v; squares += v.toDouble() * v
            if (v <= 4 || v >= 251) clipped++
        }
        val mean = (sum / pixels).toFloat()
        val variance = (squares / pixels - mean.toDouble() * mean).toFloat().coerceAtLeast(0f)
        val contrast = kotlin.math.sqrt(variance)
        var lapSum = 0.0; var lapSquares = 0.0; var count = 0
        for (y in 1 until height - 1) for (x in 1 until width - 1) {
            val i = y * width + x
            val lap = 4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - width] - luma[i + width]
            lapSum += lap; lapSquares += lap.toDouble() * lap; count++
        }
        val lapMean = lapSum / count
        val lapVariance = (lapSquares / count - lapMean * lapMean).toFloat().coerceAtLeast(0f)
        // The Laplacian's coefficients sum to 20 in squared terms, so 20x the pixel variance is the value an
        // image of pure noise would reach: sharpness stays near 1 for noise and near 0 for a smooth ramp.
        return PhotoScore(lapVariance / (20f * variance + 1f), contrast, mean, clipped.toFloat() / pixels)
    }

    /** Stores a rejected photo without inventing a score, so the album median is not dragged down. */
    fun stored(score: PhotoScore): Float = if (score.visible) score.sharpness else REJECTED

    /** Whether a remembered score may appear in a collage; unknown photos are kept until scored. */
    fun acceptable(stored: Float?, median: Float?): Boolean {
        if (stored == null) return true
        if (stored == REJECTED) return false
        if (median == null || median < MIN_MEDIAN) return true
        return stored >= median * SOFT_FRACTION
    }

    /** Median of the remembered scores, ignoring rejections; null until enough photos are scored. */
    fun median(scores: Collection<Float>): Float? {
        val values = scores.filter { it >= 0f }.sorted()
        if (values.size < MIN_SAMPLES) return null
        return values[values.size / 2]
    }
}
