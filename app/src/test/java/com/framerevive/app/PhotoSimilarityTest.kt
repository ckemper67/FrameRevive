package com.framerevive.app

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSimilarityTest {
    /** A grey ramp: brightness rises left to right, so every row hashes to the same pattern. */
    private fun ramp(width: Int = 90, height: Int = 80, shift: Int = 0): IntArray =
        IntArray(width * height) { i -> ((i % width) * 2 + shift).coerceAtMost(255) }

    private fun noisy(width: Int = 90, height: Int = 80, seed: Int = 1): IntArray {
        val random = Random(seed)
        return IntArray(width * height) { random.nextInt(256) }
    }

    private fun photo(id: String) = FramePhoto(id, "u$id", 0L)

    @Test fun theSamePictureHashesTheSameAndASlightlyBrighterCopyStaysClose() {
        val base = PhotoSimilarity.dhash(ramp(), 90, 80)
        assertEquals(base, PhotoSimilarity.dhash(ramp(), 90, 80))
        val brighter = PhotoSimilarity.dhash(ramp(shift = 20), 90, 80)
        assertTrue("exposure change must not read as a different picture (${PhotoSimilarity.distance(base, brighter)} bits)",
            PhotoSimilarity.near(base, brighter))
    }

    @Test fun differentPicturesAreNotNearDuplicates() {
        val a = PhotoSimilarity.dhash(ramp(), 90, 80)
        val b = PhotoSimilarity.dhash(noisy(seed = 7), 90, 80)
        val c = PhotoSimilarity.dhash(ramp(shift = 0).reversedArray(), 90, 80)
        assertFalse(PhotoSimilarity.near(a, b))
        assertFalse("a mirrored ramp is a different picture", PhotoSimilarity.near(a, c))
    }

    @Test fun aBurstIsThinnedButDistinctShotsSurvive() {
        // Three near-identical frames of one moment, then an unrelated photo.
        val same = PhotoSimilarity.dhash(ramp(shift = 200), 90, 80)
        val photos = listOf(photo("a"), photo("b"), photo("c"), photo("d"))
        val hashes = mapOf("a" to same, "b" to same, "c" to same, "d" to PhotoSimilarity.dhash(noisy(seed = 3), 90, 80))
        val kept = PhotoSimilarity.thin(photos, hashes)
        assertEquals(listOf("a", "d"), kept.map { it.id })
    }

    @Test fun unhashedPhotosAreNeverDropped() {
        val photos = listOf(photo("a"), photo("b"), photo("c"))
        assertEquals(photos, PhotoSimilarity.thin(photos, emptyMap()))
        val partly = PhotoSimilarity.thin(photos, mapOf("a" to 0L))
        assertEquals(3, partly.size)
    }

    @Test fun duplicatesAreRecognisedInsideACandidateGroup() {
        val one = PhotoSimilarity.dhash(ramp(), 90, 80)
        val other = PhotoSimilarity.dhash(noisy(seed = 9), 90, 80)
        assertTrue(PhotoSimilarity.hasDuplicates(listOf(photo("a"), photo("b")), mapOf("a" to one, "b" to one)))
        assertFalse(PhotoSimilarity.hasDuplicates(listOf(photo("a"), photo("b")), mapOf("a" to one, "b" to other)))
    }

    @Test fun anIdenticalRunCollapsesToItsFirstFrame() {
        // A burst of the same moment: one frame is enough. The rest stay eligible for a later cycle, since
        // thinning drops them from this playlist without recording them as shown.
        val same = PhotoSimilarity.dhash(ramp(), 90, 80)
        val photos = (1..12).map { photo("p$it") }
        assertEquals(listOf("p1"), PhotoSimilarity.thin(photos, photos.associate { it.id to same }).map { it.id })
    }

    @Test fun aRepeatIsAllowedAgainOnceTheWindowHasPassed() {
        val same = PhotoSimilarity.dhash(ramp(), 90, 80)
        val photos = (1..10).map { photo("p$it") }
        val hashes = photos.associate { it.id to PhotoSimilarity.dhash(noisy(seed = it.id.length + it.id.last().code), 90, 80) }.toMutableMap()
        hashes["p1"] = same
        hashes["p10"] = same
        val kept = PhotoSimilarity.thin(photos, hashes, window = 3)
        assertTrue("both copies survive once they are far enough apart: ${kept.map { it.id }}", kept.map { it.id }.containsAll(listOf("p1", "p10")))
    }
}
