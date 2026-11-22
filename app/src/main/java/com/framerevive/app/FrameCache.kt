package com.framerevive.app

/**
 * Request and cache geometry. Google scales a photo to fit the requested box, so a square box of the
 * panel's long edge keeps full resolution whichever way the frame hangs — a portrait screen cropping a
 * landscape photo to half the height needs more than one panel width of pixels.
 *
 * File names use a local 128-bit hash rather than MessageDigest. Requesting a digest initialises a security
 * provider, and doing that from the render path blocks behind the TLS threads that share the provider: it
 * froze the UI thread for minutes, and the stall stack pointed into SHA-256 initialisation.
 */
object FrameCache {
    const val MIN_SIDE = 640
    const val MAX_SIDE = 2560
    /** Bumped when the naming scheme changes, so stale cache files can be dropped once. */
    const val NAMING = "fnv1"

    fun side(width: Int, height: Int): Int = maxOf(width, height).coerceIn(MIN_SIDE, MAX_SIDE)
    fun suffix(width: Int, height: Int): String = "=w${side(width, height)}-h${side(width, height)}"

    /** The requested box is part of the name so a higher-resolution request replaces the old file. */
    fun name(id: String, suffix: String): String {
        val key = suffix + id
        var a = -0x340d631b7bdddcdbL
        var b = 0x6a09e667f3bcc909L
        // 0x9e3779b97f4a7c15 as a signed long: the literal itself does not fit.
        val golden = -7046029254386353131L
        for (ch in key) {
            val c = ch.code.toLong()
            a = (a xor c) * 0x100000001b3L
            b = (b + c * golden) xor (b ushr 29)
        }
        return "%016x%016x.jpg".format(a, b)
    }
}
