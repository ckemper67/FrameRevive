package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameCaptionTest {
    private val day = 86_400_000L
    private fun photo(id: String, epochDay: Long) = FramePhoto(id, "u", epochDay * day + 3_600_000L)

    @Test fun eachDistinctDateGetsItsOwnRowOldestFirst() {
        val photos = listOf(photo("a", 20_400), photo("b", 20_100), photo("c", 20_400), photo("d", 20_250))
        val lines = FrameCaption.groupedDates(photos).split("\n")
        assertEquals(3, lines.size)
        assertEquals(lines, photos.map { it.date() }.distinct().sorted().map { it.format(FrameCaption.DATE) })
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
