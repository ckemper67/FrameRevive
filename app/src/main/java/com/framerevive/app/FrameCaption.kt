package com.framerevive.app

import java.time.format.DateTimeFormatter

/** The text under a photo or a group of photos. */
object FrameCaption {
    val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    /**
     * One row per distinct capture date, in the order of [photos]: pass them in the order the pictures are laid out (left
     * to right, top to bottom) and the rows follow the pictures, so a group from several days reads as a short list in
     * the order it is seen.
     */
    fun groupedDates(photos: List<FramePhoto>): String =
        photos.filter { it.dated }.map { it.date() }.distinct().joinToString("\n") { it.format(DATE) }
}
