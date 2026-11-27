package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class StarShapeTest {
    @Test fun theStarHasTenCornersAndTheFirstIsTheTopTip() {
        val c = StarShape.corners(100f, 100f, 50f)
        assertEquals(20, c.size)
        assertEquals(100f, c[0], 0.001f)
        assertEquals(50f, c[1], 0.001f)
    }

    @Test fun tipsAreAtTheRadiusAndInnerCornersAtTheInnerShare() {
        val c = StarShape.corners(0f, 0f, 40f)
        for (i in 0 until 10) {
            val distance = hypot(c[2 * i], c[2 * i + 1])
            val expected = if (i % 2 == 0) 40f else 40f * StarShape.INNER_SHARE
            assertEquals("corner $i", expected, distance, 0.001f)
        }
    }

    @Test fun theStarIsSymmetricLeftToRight() {
        val c = StarShape.corners(0f, 0f, 40f)
        // Corner i and corner 10 - i mirror each other across the vertical axis.
        for (i in 1 until 5) {
            assertEquals(-c[2 * i], c[2 * (10 - i)], 0.001f)
            assertEquals(c[2 * i + 1], c[2 * (10 - i) + 1], 0.001f)
        }
    }

    @Test fun theStarFillsAboutThreeQuartersOfItsBoxAndSitsInTheMiddle() {
        val size = 160f
        val c = StarShape.corners(size / 2, size / 2, size * StarShape.RADIUS_SHARE)
        val xs = (0 until 10).map { c[2 * it] }
        val ys = (0 until 10).map { c[2 * it + 1] }
        val width = xs.max() - xs.min()
        val height = ys.max() - ys.min()
        assertTrue("width $width", abs(width / size - 0.735f) < 0.01f)
        assertTrue("height $height", abs(height / size - 0.70f) < 0.01f)
        // Left and right margins are equal; the tip leaves a little more room above than the base does below.
        assertEquals(xs.min(), size - xs.max(), 0.01f)
        assertTrue(ys.min() > 0f && ys.max() < size)
    }
}
