package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageOrderTest {
    private fun panel(photo: Int, left: Float, top: Float) = CollageLayout.Panel(photo, CollageLayout.Box(left, top, left + 100f, top + 100f))

    @Test fun aGridReadsAcrossThenDown() {
        // Photos 0..3 placed in scrambled boxes: bottom-right, top-left, bottom-left, top-right.
        val panels = listOf(panel(0, 500f, 400f), panel(1, 0f, 0f), panel(2, 0f, 400f), panel(3, 500f, 0f))
        assertEquals(listOf(1, 3, 2, 0), CollageLayout.readingOrder(panels))
    }

    @Test fun aTallPictureBesideTwoStackedReadsLeftThenTopRightThenBottomRight() {
        val panels = listOf(panel(0, 500f, 400f), panel(1, 500f, 0f), panel(2, 0f, 0f))
        assertEquals(listOf(2, 1, 0), CollageLayout.readingOrder(panels))
    }

    @Test fun aRowReadsLeftToRightAndAColumnTopToBottom() {
        assertEquals(listOf(2, 0, 1), CollageLayout.readingOrder(listOf(panel(0, 300f, 0f), panel(1, 600f, 0f), panel(2, 0f, 0f))))
        assertEquals(listOf(1, 2, 0), CollageLayout.readingOrder(listOf(panel(0, 0f, 600f), panel(1, 0f, 0f), panel(2, 0f, 300f))))
    }

    @Test fun boxesWithTopsAPixelApartShareARow() {
        val panels = listOf(panel(0, 500f, 0f), panel(1, 0f, 1f), panel(2, 0f, 300f))
        assertEquals(listOf(1, 0, 2), CollageLayout.readingOrder(panels))
    }

    @Test fun everyPhotoAppearsOnceInTheOrder() {
        val sizes = listOf(CollageLayout.Size(1200, 800), CollageLayout.Size(800, 1200), CollageLayout.Size(1000, 1000), CollageLayout.Size(1600, 900))
        for (n in 2..4) {
            val order = CollageLayout.readingOrder(CollageLayout.place(1920, 1080, sizes.take(n), fill = true).panels)
            assertEquals((0 until n).toList(), order.sorted())
        }
    }

    @Test fun theOrderIsTheOneTheRendererDrawsWhenThePhotosTile() {
        val sizes = List(4) { CollageLayout.Size(1200, 800) }
        val placement = CollageLayout.place(1920, 1080, sizes, fill = true)
        val (w, h) = CollageLayout.outputSize(1920, 1080)
        assertEquals(CollageLayout.fill(w, h, sizes)!!.panels.map { it.photo to it.box }, placement.panels.map { it.photo to it.box })
        assertTrue(placement.covers)
    }

    @Test fun theOrderIsTheOneTheRendererDrawsWhenTheyDoNotTile() {
        val sizes = listOf(CollageLayout.Size(1200, 800), CollageLayout.Size(800, 1200), CollageLayout.Size(800, 1200))
        val placement = CollageLayout.place(1920, 1080, sizes, fill = true)
        val (w, h) = CollageLayout.outputSize(1920, 1080)
        if (CollageLayout.fill(w, h, sizes) == null) {
            assertEquals(CollageLayout.arrange(w, h, sizes).map { it.photo to it.box }, placement.panels.map { it.photo to it.box })
            assertTrue("the letterboxed layout does not cover the frame", !placement.covers)
        }
        assertEquals((0 until 3).toList(), CollageLayout.readingOrder(placement.panels).sorted())
    }

    @Test fun theDatesAreInTheOrderOfThePicturesOnScreen() {
        val day = 86_400_000L
        val photos = (0 until 4).map { FramePhoto("p$it", "u", (20_000L + it * 100) * day + 12 * 3_600_000L) }   // four different days, oldest first
        val sizes = listOf(CollageLayout.Size(800, 1200), CollageLayout.Size(1200, 800), CollageLayout.Size(800, 1200), CollageLayout.Size(1200, 800))
        val panels = CollageLayout.place(1920, 1080, sizes, fill = true).panels
        val ordered = CollageLayout.readingOrder(panels).map { photos[it] }
        val rows = FrameCaption.groupedDates(ordered).split("\n")
        assertEquals(ordered.map { it.date().format(FrameCaption.DATE) }, rows)
        // Reading the boxes by hand gives the same photos.
        val byHand = panels.sortedWith(compareBy({ Math.round(it.box.top) }, { it.box.left })).map { photos[it.photo] }
        assertEquals(byHand.map { it.id }, ordered.map { it.id })
    }

    @Test fun aRightToLeftLanguageReadsEachRowFromTheRight() {
        val panels = listOf(panel(0, 500f, 400f), panel(1, 0f, 0f), panel(2, 0f, 400f), panel(3, 500f, 0f))
        assertEquals("top right, top left, bottom right, bottom left", listOf(3, 1, 0, 2), CollageLayout.readingOrder(panels, rightToLeft = true))
        assertEquals("left to right is still the default", listOf(1, 3, 2, 0), CollageLayout.readingOrder(panels))
    }

    @Test fun aColumnReadsTopToBottomInEitherDirection() {
        val column = listOf(panel(0, 0f, 600f), panel(1, 0f, 0f), panel(2, 0f, 300f))
        assertEquals(listOf(1, 2, 0), CollageLayout.readingOrder(column, rightToLeft = true))
        assertEquals(listOf(1, 2, 0), CollageLayout.readingOrder(column, rightToLeft = false))
    }

    @Test fun aTallPictureBesideTwoStackedReadsFromTheRightWhenReadingRightToLeft() {
        val panels = listOf(panel(0, 500f, 400f), panel(1, 500f, 0f), panel(2, 0f, 0f))
        assertEquals(listOf(1, 2, 0), CollageLayout.readingOrder(panels, rightToLeft = true))
    }
}
