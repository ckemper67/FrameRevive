package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteStarsTest {
    private fun photo(id: String) = FramePhoto(id, "u", 1000)
    private val photos = listOf(photo("a"), photo("b"), photo("c"))
    // Panels deliberately out of order against the photos, as a layout may place them.
    private val panels = listOf(
        CollageLayout.Panel(2, CollageLayout.Box(0f, 0f, 100f, 200f)),
        CollageLayout.Panel(0, CollageLayout.Box(100f, 0f, 300f, 100f)),
        CollageLayout.Panel(1, CollageLayout.Box(100f, 100f, 300f, 200f)))
    private fun tile(side: Float) = listOf(CollageLayout.Panel(0, CollageLayout.Box(0f, 0f, side, side)))

    @Test fun theStarGoesOnTheFavoritesOwnTileTopRightSizedToThatTile() {
        val spots = FavoriteStars.spots(panels, photos, { it == "a" }, 2f, min = 1f, max = 1000f)
        // The tile is 200 by 100, so its shorter side is 100, which is 200 on the screen at scale 2.
        val size = 200f * FavoriteStars.SHARE
        assertEquals(listOf(StarSpot(600f - size * 0.25f, size * 0.25f, size)), spots)
    }

    @Test fun aSmallerTileGetsAProportionallySmallerStarWithinTheLimits() {
        val big = FavoriteStars.spots(tile(400f), photos, { true }, 1f, 10f, 1000f).single().size
        val small = FavoriteStars.spots(tile(100f), photos, { true }, 1f, 10f, 1000f).single().size
        assertEquals(4.0, (big / small).toDouble(), 0.001)
        assertEquals("never below the minimum", 30f, FavoriteStars.spots(tile(10f), photos, { true }, 1f, 30f, 60f).single().size)
        assertEquals("never above the maximum", 60f, FavoriteStars.spots(tile(4000f), photos, { true }, 1f, 30f, 60f).single().size)
    }

    @Test fun everyFavoriteGetsOneAndNoFavoriteGetsNone() {
        assertEquals(3, FavoriteStars.spots(panels, photos, { true }, 1f, 1f, 100f).size)
        assertTrue(FavoriteStars.spots(panels, photos, { false }, 1f, 1f, 100f).isEmpty())
    }
}
