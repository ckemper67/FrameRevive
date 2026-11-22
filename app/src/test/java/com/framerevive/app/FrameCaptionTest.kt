package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameCaptionTest {
    private val day = 86_400_000L
    private fun photo(id: String, epochDay: Long) = FramePhoto(id, "u", epochDay * day + 3_600_000L)

    @Test fun eachDistinctDateGetsItsOwnRowInTheOrderOfThePhotos() {
        val photos = listOf(photo("a", 20_400), photo("b", 20_100), photo("c", 20_400), photo("d", 20_250))
        val lines = FrameCaption.groupedDates(photos).split("\n")
        assertEquals("a date shows once, where its first picture is", photos.map { it.date() }.distinct().map { it.format(FrameCaption.DATE) }, lines)
        assertEquals(3, lines.size)
    }

    @Test fun theRowsFollowTheOrderTheyAreGiven() {
        val a = photo("a", 20_400); val b = photo("b", 20_100)
        assertEquals(listOf(a.date(), b.date()), FrameCaption.groupedDates(listOf(a, b)).split("\n").map { java.time.LocalDate.parse(it, FrameCaption.DATE) })
        assertEquals(listOf(b.date(), a.date()), FrameCaption.groupedDates(listOf(b, a)).split("\n").map { java.time.LocalDate.parse(it, FrameCaption.DATE) })
    }

    @Test fun aSameDayGroupIsOneRow() {
        assertEquals(1, FrameCaption.groupedDates(listOf(photo("a", 20_400), photo("b", 20_400))).split("\n").size)
    }

    @Test fun undatedPhotosAreLeftOutAndNoneMeansEmpty() {
        assertEquals("", FrameCaption.groupedDates(listOf(FramePhoto("x", "u", 0L))))
        assertEquals(1, FrameCaption.groupedDates(listOf(FramePhoto("x", "u", 0L), photo("a", 20_400))).split("\n").size)
    }

    @Test fun noDotSeparatorRemains() {
        assertEquals(false, FrameCaption.groupedDates(listOf(photo("a", 20_400), photo("b", 20_401))).contains("·"))
    }
}
