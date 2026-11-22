package com.framerevive.app

import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** The text under a photo or a group of photos. */
object FrameCaption {
    /**
     * The date as the frame's language writes it: "March 4, 2021" in English, "2021年3月4日" in Japanese. Read each time, so
     * changing the language takes effect without a restart.
     */
    val DATE: DateTimeFormatter get() = dateFormat(Locale.getDefault(), FormatStyle.LONG)

    /** The shorter form drawn on each picture of a collage, where room is tight: "Mar 4, 2021". */
    val SHORT_DATE: DateTimeFormatter get() = dateFormat(Locale.getDefault(), FormatStyle.MEDIUM)

    fun dateFormat(locale: Locale, style: FormatStyle): DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(style).withLocale(locale)

    /**
     * One row per distinct capture date, in the order of [photos]: pass them in the order the pictures are laid out (left
     * to right, top to bottom) and the rows follow the pictures, so a group from several days reads as a short list in
     * the order it is seen.
     */
    fun groupedDates(photos: List<FramePhoto>): String =
        photos.filter { it.dated }.map { it.date() }.distinct().joinToString("\n") { it.format(DATE) }
}
