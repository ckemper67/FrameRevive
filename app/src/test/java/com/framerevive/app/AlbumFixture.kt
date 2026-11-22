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

    /**
     * The frame's pace, which turns screen time into days. Set FRAMEREVIVE_SECONDS (seconds per photo) and
     * FRAMEREVIVE_AWAKE_HOURS to match your frame: the controls page's "Your album" card shows the photos a day it
     * works out. The defaults are the app's own, 30 seconds and 15 awake hours, 1,800 photos a day.
     */
    fun secondsPerPhoto(): Int = System.getenv("FRAMEREVIVE_SECONDS")?.toIntOrNull()?.takeIf { it > 0 } ?: 30
    fun awakeHours(): Double = System.getenv("FRAMEREVIVE_AWAKE_HOURS")?.toDoubleOrNull()?.takeIf { it > 0 } ?: 15.0
    fun slotsPerDay(): Double = 3600.0 / secondsPerPhoto() * awakeHours()
}
