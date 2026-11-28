package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class FavoritesInGoogleTest {
    private fun photo(id: String, takenMs: Long, key: String = "") = FramePhoto(id, "u$id", takenMs, key = key)
    private fun byId(vararg photos: FramePhoto) = photos.associateBy { it.id }

    @Test fun aStarWhoseKeyIsInTheFavoritesAlbumIsInGoogle() {
        val main = photo("m1", 1_000L, key = "K1")
        val twin = photo("f1", 1_000L, key = "K1")   // another id, same original file
        assertTrue(FavoritesInGoogle.pending(listOf("m1" to 50L), byId(main), listOf(twin)).isEmpty())
    }

    @Test fun aStarWithNoMatchIsPending() {
        val main = photo("m1", 1_000L, key = "K1")
        val other = photo("f1", 2_000L, key = "K2")
        val pending = FavoritesInGoogle.pending(listOf("m1" to 50L), byId(main), listOf(other))
        assertEquals(listOf("m1"), pending.map { it.photo.id })
        assertEquals(50L, pending.single().starredMs)
    }

    @Test fun aPhotoWithoutAKeyFallsBackToTheCaptureTime() {
        val keyless = photo("m1", 5_000L)
        assertTrue(FavoritesInGoogle.pending(listOf("m1" to 1L), byId(keyless), listOf(photo("f1", 5_000L))).isEmpty())
        assertEquals(1, FavoritesInGoogle.pending(listOf("m1" to 1L), byId(keyless), listOf(photo("f1", 6_000L))).size)
    }

    @Test fun aKeyThatDiffersIsNotRescuedByTheSameTime() {
        // Frames of a burst share a time but not a key: a keyed photo is matched by its key only.
        val main = photo("m1", 5_000L, key = "K1")
        val burstNeighbour = photo("f1", 5_000L, key = "K2")
        assertEquals(1, FavoritesInGoogle.pending(listOf("m1" to 1L), byId(main), listOf(burstNeighbour)).size)
    }

    @Test fun aStarWhosePhotoIsNotInTheAlbumNowIsLeftOutAndTheOrderIsKept() {
        val a = photo("a", 1_000L, key = "A"); val b = photo("b", 2_000L, key = "B")
        val pending = FavoritesInGoogle.pending(listOf("b" to 30L, "gone" to 20L, "a" to 10L), byId(a, b), emptyList())
        assertEquals(listOf("b", "a"), pending.map { it.photo.id })
    }

    @Test fun noStarsMeansNothingPending() {
        assertTrue(FavoritesInGoogle.pending(emptyList(), emptyMap(), listOf(photo("f", 1L))).isEmpty())
    }

    @Test fun theStoreListsOnlyStarsSetOnTheFrameNewestFirst() {
        val f = Favorites()
        f.toggle("old", 100L); f.toggle("new", 300L); f.toggle("mid", 200L)
        f.seed(listOf("fromGoogle"), 50L)
        f.toggle("gone", 400L); f.toggle("gone", 500L)           // starred, then un-starred
        assertEquals(listOf("new" to 300L, "mid" to 200L, "old" to 100L), f.frameStars())
        // A seeded star that is un-starred is a veto, never a frame star.
        f.toggle("fromGoogle", 600L)
        assertEquals(3, f.frameStars().size)
    }
}
