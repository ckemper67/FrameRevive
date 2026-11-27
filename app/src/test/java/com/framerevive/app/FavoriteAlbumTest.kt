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

    private fun keyed(id: String, taken: Long, key: String) = FramePhoto(id, "u-$id", taken, key = key)

    @Test fun aContentKeyFindsTheTwinEvenInABurstAndEvenWhenTheTimesDiffer() {
        val burst = listOf(keyed("b1", 3000, "K1"), keyed("b2", 3000, "K2"), keyed("b3", 3000, "K3"), keyed("edited", 9000, "K4"))
        val merged = FavoriteAlbum.merge(burst, listOf(keyed("f1", 3000, "K2"), keyed("f2", 5000, "K4")), emptySet())
        assertEquals("exactly the frame with the same key goes, not its neighbours", listOf("b1", "b3", "f1", "f2"), merged.photos.map { it.id })
        assertEquals(2, merged.hidden)
    }

    @Test fun aSameTimeWithADifferentKeyIsADifferentPicture() {
        val merged = FavoriteAlbum.merge(listOf(keyed("m", 2000, "K1")), listOf(keyed("f", 2000, "K9")), emptySet())
        assertEquals(listOf("m", "f"), merged.photos.map { it.id }); assertEquals(0, merged.hidden)
    }

    @Test fun aFavoriteWithoutAKeyStillUsesTheCaptureTime() {
        val merged = FavoriteAlbum.merge(listOf(keyed("m", 2000, "K1"), keyed("n", 4000, "")), listOf(keyed("f", 2000, ""), keyed("g", 4000, "")), emptySet())
        assertEquals(listOf("f", "g"), merged.photos.map { it.id })
    }

    @Test fun anUnstarredFavoriteHidesNoTwinByKeyEither() {
        val merged = FavoriteAlbum.merge(listOf(keyed("m", 2000, "K1")), listOf(keyed("f", 2000, "K1")), setOf("f"))
        assertEquals(listOf("m"), merged.photos.map { it.id })
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

    private fun video(id: String, taken: Long) = FramePhoto(id, "u-$id", taken, video = true)

    @Test fun videosInTheMainAlbumAreNeverPlayedEvenWithoutAFavoritesAlbum() {
        val merged = FavoriteAlbum.merge(listOf(photo("p1", 1000), video("v1", 2000), photo("p2", 3000)), emptyList(), emptySet())
        assertEquals(listOf("p1", "p2"), merged.photos.map { it.id })
    }

    @Test fun videosInTheFavoritesAlbumAreLeftOutAndHideNoTwin() {
        val merged = FavoriteAlbum.merge(listOf(photo("m1", 1000), photo("m2", 2000)), listOf(video("fv", 2000), photo("f1", 5000)), emptySet())
        assertEquals(listOf("m1", "m2", "f1"), merged.photos.map { it.id })
        assertEquals(1, merged.live)
        assertEquals(0, merged.hidden)
    }
}
