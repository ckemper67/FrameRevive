package com.framerevive.app

import java.io.File

/**
 * An album's capture times as written by tools/album_metadata.py: a header, then `takenMs,offsetMs,id,width,height,key,video,addedMs`
 * per photo (older files stop earlier). The ids are not used, so the photos get made-up ones. Used by the tests that run on a real album; see
 * docs/tuning-the-strategy.md.
 */
object AlbumFixture {
    fun parse(lines: List<String>): List<FramePhoto> {
        require(lines.firstOrNull()?.trim()?.startsWith("takenMs,offsetMs") == true) { "Not an album metadata file (expected the header takenMs,offsetMs,id)." }
        return lines.drop(1).filter { it.isNotBlank() }.mapIndexed { i, line ->
            val f = line.trim().split(',')
            // Older files stop after the offset; newer ones carry id, width, height, content key, video flag and added time.
            FramePhoto("r$i", "u", f[0].toLong(), f[1].toLong(), width = f.getOrNull(3)?.toIntOrNull() ?: 0, height = f.getOrNull(4)?.toIntOrNull() ?: 0,
                key = f.getOrNull(5).orEmpty(), video = f.getOrNull(6) == "1")
        }
    }

    /** The CSV named by FRAMEREVIVE_ALBUM_DATES, or local/album-metadata.csv, or the older local/album-dates.csv (Gradle runs tests from the app folder). */
    fun source(): File? {
        val named = System.getenv("FRAMEREVIVE_ALBUM_DATES")
        return (if (named != null) listOf(named) else listOf("../local/album-metadata.csv", "local/album-metadata.csv", "../local/album-dates.csv", "local/album-dates.csv"))
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
    fun slotsPerDay(): Double = PlaybackEngine.slotsPerDay(secondsPerPhoto(), awakeHours())

    /** Cycle settings as the frame would build them at this pace, so reports match what the frame plans. */
    fun cycleSettings(recency: Recency = Recency.DEFAULT, recentPercent: Int = 50, memoryDays: Int = 14): CycleSettings =
        CycleSettings(PlaybackEngine.roundSizeFor(PlaybackEngine.plannedSlots(secondsPerPhoto(), awakeHours()), recentPercent), memoryDays, recentPercent, recency)
}
