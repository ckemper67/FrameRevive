package com.framerevive.app

import java.time.format.DateTimeFormatter

/** The text under a photo or a group of photos. */
object FrameCaption {
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    /** One row per distinct capture date, oldest first, so a group from several days reads as a short list. */
    fun groupedDates(photos: List<FramePhoto>): String =
        photos.filter { it.dated }.map { it.date() }.distinct().sorted().joinToString("\n") { it.format(DATE) }
}
