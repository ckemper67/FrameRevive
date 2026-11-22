package com.framerevive.app

import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds the same picture under two ids, as happens when a photo is in both the main album and the favorites album
 * (the two albums give a photo different ids and different addresses).
 *
 * The md5 of a photo's downloaded bytes is kept for every photo the frame has fetched. A photo is a repeat when another
 * id with the same md5 was shown within [WINDOW_MS]. Two renditions of one original at the same size are normally
 * byte-identical; when they are not there is simply no match, and both copies keep playing.
 */
class PictureDuplicates {
    private val md5ById = ConcurrentHashMap<String, String>()
    private val idsByMd5 = ConcurrentHashMap<String, Set<String>>()
    @Volatile var dirty = false
        private set

    val known: Int get() = md5ById.size

    fun md5Of(id: String): String? = md5ById[id]

    fun know(id: String, md5: String) {
        if (md5ById.put(id, md5) == md5) return
        idsByMd5.merge(md5, setOf(id)) { old, add -> old + add }
        dirty = true
    }

    /** Another photo with the same bytes that was shown within the window, or null. */
    fun recentTwin(id: String, lastShownMs: (String) -> Long?, nowMs: Long, windowMs: Long = WINDOW_MS): String? {
        val md5 = md5ById[id] ?: return null
        return idsByMd5[md5]?.firstOrNull { other -> other != id && lastShownMs(other)?.let { nowMs - it in 0..windowMs } == true }
    }

    fun export(): String {
        dirty = false
        val text = StringBuilder(md5ById.size * 56)
        md5ById.forEach { (id, md5) -> text.append(id).append('\t').append(md5).append('\n') }
        return text.toString()
    }

    fun load(file: File) {
        runCatching {
            if (file.exists()) file.forEachLine { line ->
                val at = line.indexOf('\t')
                if (at > 0 && line.length - at - 1 == 32) know(line.substring(0, at), line.substring(at + 1))
            }
        }
        dirty = false
    }

    companion object {
        /** A twin counts as a repeat if it was on the panel this recently. */
        const val WINDOW_MS = 30L * 24 * 3600 * 1000

        private val SHIFT = intArrayOf(7, 12, 17, 22, 5, 9, 14, 20, 4, 11, 16, 23, 6, 10, 15, 21)
        private val SINE = IntArray(64) { (Math.abs(Math.sin(it + 1.0)) * 4294967296.0).toLong().toInt() }

        /**
         * MD5 written out here rather than asked of MessageDigest: starting a security provider on this frame can block
         * behind the TLS threads (see [FrameCache]), and a photo is hashed on the threads that keep the slideshow moving.
         */
        fun md5(bytes: ByteArray): String {
            val padded = ((bytes.size + 8) / 64 + 1) * 64
            val data = bytes.copyOf(padded)
            data[bytes.size] = 0x80.toByte()
            val bits = bytes.size.toLong() * 8
            for (i in 0 until 8) data[padded - 8 + i] = (bits ushr (8 * i)).toByte()
            var a0 = 0x67452301; var b0 = 0xefcdab89.toInt(); var c0 = 0x98badcfe.toInt(); var d0 = 0x10325476
            val m = IntArray(16)
            for (block in 0 until padded / 64) {
                for (i in 0 until 16) {
                    val o = block * 64 + i * 4
                    m[i] = (data[o].toInt() and 0xff) or ((data[o + 1].toInt() and 0xff) shl 8) or ((data[o + 2].toInt() and 0xff) shl 16) or ((data[o + 3].toInt() and 0xff) shl 24)
                }
                var a = a0; var b = b0; var c = c0; var d = d0
                for (i in 0 until 64) {
                    val f: Int; val g: Int
                    when (i / 16) {
                        0 -> { f = (b and c) or (b.inv() and d); g = i }
                        1 -> { f = (d and b) or (d.inv() and c); g = (5 * i + 1) % 16 }
                        2 -> { f = b xor c xor d; g = (3 * i + 5) % 16 }
                        else -> { f = c xor (b or d.inv()); g = (7 * i) % 16 }
                    }
                    val rotated = a + f + SINE[i] + m[g]
                    a = d; d = c; c = b
                    b += Integer.rotateLeft(rotated, SHIFT[(i / 16) * 4 + i % 4])
                }
                a0 += a; b0 += b; c0 += c; d0 += d
            }
            val out = StringBuilder(32)
            for (word in intArrayOf(a0, b0, c0, d0)) for (i in 0 until 4) out.append("%02x".format((word ushr (8 * i)) and 0xff))
            return out.toString()
        }
    }
}
