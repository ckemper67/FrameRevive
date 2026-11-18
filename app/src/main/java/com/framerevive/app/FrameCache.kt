package com.framerevive.app

import java.security.MessageDigest

/**
 * Request and cache geometry. Google scales a photo to fit the requested box, so a square box of the
 * panel's long edge keeps full resolution whichever way the frame hangs — a portrait screen cropping a
 * landscape photo to half the height needs more than one panel width of pixels.
 */
object FrameCache {
    const val MIN_SIDE = 640
    const val MAX_SIDE = 2560

    fun side(width: Int, height: Int): Int = maxOf(width, height).coerceIn(MIN_SIDE, MAX_SIDE)
    fun suffix(width: Int, height: Int): String = "=w${side(width, height)}-h${side(width, height)}"
    /** The requested box is part of the name so a higher-resolution request replaces the old file. */
    fun name(id: String, suffix: String): String =
        MessageDigest.getInstance("SHA-256").digest((suffix + id).toByteArray()).joinToString("") { "%02x".format(it) } + ".jpg"
}
