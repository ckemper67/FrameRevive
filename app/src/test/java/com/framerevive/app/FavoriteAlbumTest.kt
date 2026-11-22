package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteAlbumTest {
    private fun photo(id: String, taken: Long) = FramePhoto(id, "u-$id", taken)

    private val main = listOf(photo("m1", 1000), photo("m2", 2000), photo("burst-a", 3000), photo("burst-b", 3000), photo("undated", 0))

    @Test fun withoutAFavoritesAlbumNothingChanges() {
        val merged = FavoriteAlbum.merge(main, emptyList(), emptySet())
        assertSame(main, merged.photos); assertEquals(0, merged.live); assertEquals(0, merged.hidden)
    }

    @Test fun aFavoriteIsAddedAsItIsAndHidesItsOneTwinInTheMainAlbum() {
        val merged = FavoriteAlbum.merge(main, listOf(photo("f1", 2000)), emptySet())
        assertEquals(listOf("m1", "burst-a", "burst-b", "undated", "f1"), merged.photos.map { it.id })
        assertEquals(1, merged.live); assertEquals(1, merged.hidden)
    }

    @Test fun aTimeSeveralMainPhotosShareHidesNothing() {
        val merged = FavoriteAlbum.merge(main, listOf(photo("f1", 3000)), emptySet())
        assertTrue(merged.photos.map { it.id }.containsAll(listOf("burst-a", "burst-b", "f1")))
        assertEquals(0, merged.hidden)
    }

    @Test fun aFavoriteWithNoTwinOrNoDateHidesNothing() {
        val merged = FavoriteAlbum.merge(main, listOf(photo("far", 9999), photo("nodate", 0)), emptySet())
        assertEquals(main.size + 2, merged.photos.size); assertEquals(0, merged.hidden)
    }

    @Test fun anUnstarredFavoriteIsLeftOutAndDoesNotHideItsTwin() {
        val merged = FavoriteAlbum.merge(main, listOf(photo("f1", 2000), photo("f2", 1000)), setOf("f1"))
        assertFalse("f1" in merged.photos.map { it.id })
        assertTrue("m2" in merged.photos.map { it.id })
        assertEquals(listOf("f2"), merged.photos.filter { it.id.startsWith("f") }.map { it.id })
        assertEquals(1, merged.hidden)
    }

    @Test fun theSameAlbumConnectedTwiceAddsNoCopies() {
        val merged = FavoriteAlbum.merge(main, listOf(photo("m1", 1000)), emptySet())
        assertSame(main, merged.photos)
    }

    @Test fun anUnstarredAlbumPhotoComesBackOnlyWhenStarredAgain() {
        val f = Favorites()
        f.seed(listOf("f1"), 1000)
        f.toggle("f1", 2000)
        assertEquals(setOf("f1"), f.vetoed); assertFalse(f.isFavorite("f1"))
        assertEquals("seeding again does not bring it back", 0, f.seed(listOf("f1"), 3000))
        assertEquals(setOf("f1"), f.vetoed)
        f.toggle("f1", 4000)
        assertTrue(f.isFavorite("f1")); assertTrue(f.vetoed.isEmpty())
    }
}
