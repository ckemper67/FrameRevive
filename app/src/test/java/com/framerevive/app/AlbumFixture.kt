package com.framerevive.app

import java.io.File

/**
 * An album's capture times as written by tools/album_dates.py: a header, then `takenMs,offsetMs` per photo. No ids and
 * no addresses, so the photos get made-up ids. Used by the tests that run on a real album; see
 * docs/tuning-the-strategy.md.
 */
object AlbumFixture {
    fun parse(lines: List<String>): List<FramePhoto> {
        require(lines.firstOrNull()?.trim() == "takenMs,offsetMs") { "Not an album dates file (expected the header takenMs,offsetMs)." }
        return lines.drop(1).filter { it.isNotBlank() }.mapIndexed { i, line ->
            val (taken, offset) = line.trim().split(',')
            FramePhoto("r$i", "u", taken.toLong(), offset.toLong())
        }
    }

    /** The CSV named by FRAMEREVIVE_ALBUM_DATES, or local/album-dates.csv (Gradle runs tests from the app folder). */
    fun source(): File? {
        val named = System.getenv("FRAMEREVIVE_ALBUM_DATES")
        return (if (named != null) listOf(named) else listOf("../local/album-dates.csv", "local/album-dates.csv"))
            .map(::File).firstOrNull { it.isFile }
    }

    fun load(): List<FramePhoto>? = source()?.let { parse(it.readLines()) }
}
