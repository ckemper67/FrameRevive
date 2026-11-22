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

    @Test fun duplicatesAreRecognisedInsideACandidateGroup() {
        val one = PhotoSimilarity.dhash(ramp(), 90, 80)
        val other = PhotoSimilarity.dhash(noisy(seed = 9), 90, 80)
        assertTrue(PhotoSimilarity.hasDuplicates(listOf(photo("a"), photo("b")), mapOf("a" to one, "b" to one)))
        assertFalse(PhotoSimilarity.hasDuplicates(listOf(photo("a"), photo("b")), mapOf("a" to one, "b" to other)))
    }

}
