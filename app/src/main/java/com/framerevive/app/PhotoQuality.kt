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

/** How much of the album's blur the frame tolerates, as a share of the album's typical sharpness. */
enum class Sharpness(val id: String, val fraction: Float?) {
    OFF("off", null),
    /** Only photos far softer than the album's typical one; the original collage-only rule. */
    LIGHT("light", PhotoQuality.SOFT_FRACTION),
    /** Also photos that are plainly unsharp without being very blurry; the default since the owner disliked those too. */
    MEDIUM("medium", 0.25f),
    STRONG("strong", 0.40f);

    companion object {
        val DEFAULT = MEDIUM
        fun of(id: String?): Sharpness = entries.firstOrNull { it.id == id } ?: DEFAULT
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

    /**
     * Whether a remembered score may be shown; unknown photos are kept until scored. A [fraction] of null
     * switches the blur test off, but black, blown-out and flat photos are still refused.
     */
    fun acceptable(stored: Float?, median: Float?, fraction: Float? = SOFT_FRACTION): Boolean {
        if (stored == null) return true
        if (stored == REJECTED) return false
        if (fraction == null || median == null || median < MIN_MEDIAN) return true
        return stored >= median * fraction
    }

    /** A photo's sharpness as a share of the album's typical one, or null when either is unknown or rejected. */
    fun relative(stored: Float?, median: Float?): Float? =
        if (stored == null || stored < 0f || median == null || median <= 0f) null else stored / median

    /** How many scored photos fall below each cut, as counts for the bands under, between and over [cuts]. */
    fun bands(scores: Collection<Float>, median: Float?, cuts: List<Float> = listOf(0.12f, 0.25f, 0.40f, 0.60f)): List<Int>? {
        if (median == null || median <= 0f) return null
        val out = IntArray(cuts.size + 1)
        for (score in scores) {
            if (score < 0f) continue
            val share = score / median
            out[cuts.indexOfFirst { share < it }.let { if (it < 0) cuts.size else it }]++
        }
        return out.toList()
    }

    /** Median of the remembered scores, ignoring rejections; null until enough photos are scored. */
    fun median(scores: Collection<Float>): Float? {
        val values = scores.filter { it >= 0f }.sorted()
        if (values.size < MIN_SAMPLES) return null
        return values[values.size / 2]
    }
}
