package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurstsTest {
    private val minute = 60_000L

    private fun photo(id: String, at: Long) = FramePhoto(id, "u$id", at)
    private fun photo(id: String, day: Int, minuteOfDay: Int) =
        photo(id, (day * 1440L + minuteOfDay) * minute)

    @Test fun eventsSplitOnAnHourOfSilence() {
        val photos = listOf(
            photo("a", 0, 9 * 60), photo("b", 0, 9 * 60 + 20), photo("c", 0, 9 * 60 + 50),
            // 90 minutes later: a different outing
            photo("d", 0, 11 * 60 + 20), photo("e", 0, 11 * 60 + 30))
        val events = Bursts.events(photos)
        assertEquals(2, events.size)
        assertEquals(listOf("a", "b", "c"), events[0].photos.map { it.id })
        assertEquals(listOf("d", "e"), events[1].photos.map { it.id })
        assertTrue("three frames is a burst", events[0].isBurst)
        assertTrue("two frames is a sequence, not a burst", !events[1].isBurst)
    }

    @Test fun inputOrderDoesNotMatterAndUndatedPhotosStaySingletons() {
        val photos = listOf(photo("late", 0, 9 * 60 + 30), photo("early", 0, 9 * 60), FramePhoto("none", "u", 0L))
        val events = Bursts.events(photos)
        assertEquals(listOf("early", "late"), events.first().photos.map { it.id })
        assertEquals(listOf("none"), events.last().photos.map { it.id })
    }

    @Test fun aBurstKeepsThreeFramesAndHoldsTheRestBack() {
        val cycle = (1..10).map { photo("p$it", 0, 9 * 60 + it) }
        val result = Bursts.thin(cycle, emptyMap()) { null }
        assertEquals(3, result.kept.size)
        assertEquals(7, result.heldBack)
        assertEquals("order must be preserved", result.kept.map { it.id }, listOf("p1", "p2", "p3"))
    }

    @Test fun unseenFramesComeBeforeAlreadyShownOnesThenSharpnessBreaksTies() {
        val cycle = (1..6).map { photo("p$it", 0, 9 * 60 + it) }
        // p1 and p2 were already shown; p3..p6 are new, with p5 the sharpest.
        val seen = mapOf("p1" to PlayRecord(1L, 1), "p2" to PlayRecord(1L, 1))
        val sharp = mapOf("p1" to 0.9f, "p2" to 0.9f, "p3" to 0.1f, "p4" to 0.2f, "p5" to 0.8f, "p6" to 0.05f)
        val kept = Bursts.thin(cycle, seen) { sharp[it.id] }.kept.map { it.id }
        assertEquals("the sharpest of the unseen frames must win", listOf("p3", "p4", "p5"), kept)
    }

    @Test fun shortEventsAndSmallCyclesAreLeftAlone() {
        val cycle = listOf(photo("a", 0, 60), photo("b", 0, 120))
        val result = Bursts.thin(cycle, emptyMap()) { null }
        assertEquals(cycle, result.kept)
        assertEquals(0, result.heldBack)
    }

    @Test fun separateOutingsAllSurviveInOneCycle() {
        val first = (1..6).map { photo("a$it", 0, 9 * 60 + it) }
        val second = (1..6).map { photo("b$it", 0, 14 * 60 + it) }
        val result = Bursts.thin(first + second, emptyMap()) { null }
        assertEquals(6, result.kept.size)
        assertEquals(6, result.heldBack)
        assertTrue(result.kept.any { it.id.startsWith("a") } && result.kept.any { it.id.startsWith("b") })
    }

    /** Hashes far enough apart (more than the threshold) that no two count as the same moment. */
    private fun distinct(i: Int) = 0xFFFL shl (i * 5)

    private fun hashed(photos: List<FramePhoto>, hash: (Int) -> Long) = photos.withIndex().associate { (i, p) -> p.id to hash(i) }

    @Test fun anIdenticalBurstGivesOneFrameNotThreeNearIdenticalOnes() {
        val cycle = (1..10).map { photo("p$it", 0, 9 * 60 + it) }
        val same = PhotoSimilarity.dhash(IntArray(90 * 80) { (it % 90) * 2 }, 90, 80)
        val result = Bursts.thin(cycle, emptyMap(), hashes = cycle.associate { it.id to same }) { null }
        assertEquals(listOf("p1"), result.kept.map { it.id })
        assertEquals(9, result.similar)
        assertEquals(0, result.heldBack)
    }

    @Test fun aBurstOfDistinctShotsStillKeepsItsLimit() {
        val cycle = (1..10).map { photo("p$it", 0, 9 * 60 + it) }
        val result = Bursts.thin(cycle, emptyMap(), hashes = hashed(cycle, ::distinct)) { null }
        assertEquals(3, result.kept.size)
        assertEquals(7, result.heldBack)
        assertEquals(0, result.similar)
    }

    @Test fun nearDuplicatesAreLeftOutBeforeTheLimitIsFilled() {
        // p1..p4 are one scene, p5..p8 another: three frames survive and none is a near-duplicate of another.
        val cycle = (1..8).map { photo("p$it", 0, 9 * 60 + it) }
        val hashes = hashed(cycle) { i -> distinct(if (i < 4) 0 else 1) xor (1L shl (40 + i % 2)) }
        val result = Bursts.thin(cycle, emptyMap(), hashes = hashes) { null }
        assertEquals(listOf("p1", "p5"), result.kept.map { it.id })
        assertEquals(6, result.similar)
    }

    @Test fun aShortSequenceOfTheSameMomentIsReducedToOne() {
        val cycle = listOf(photo("a", 0, 9 * 60), photo("b", 0, 9 * 60 + 1))
        val result = Bursts.thin(cycle, emptyMap(), hashes = mapOf("a" to 5L, "b" to 5L)) { null }
        assertEquals(listOf("a"), result.kept.map { it.id })
    }

    @Test fun unhashedFramesAreNeverLeftOut() {
        val cycle = (1..10).map { photo("p$it", 0, 9 * 60 + it) }
        val result = Bursts.thin(cycle, emptyMap(), hashes = mapOf("p1" to 0L)) { null }
        assertEquals("only the keep limit applies to unhashed frames", 3, result.kept.size)
        assertEquals(0, result.similar)
    }

    @Test fun theSamePictureSavedTwiceInTwoEventsPlaysOnceAtMost() {
        val cycle = listOf(photo("first", 0, 9 * 60), photo("copy", 3, 15 * 60), photo("other", 6, 12 * 60))
        val hashes = mapOf("first" to 77L, "copy" to 77L, "other" to distinct(3))
        val result = Bursts.thin(cycle, emptyMap(), hashes = hashes) { null }
        assertEquals(listOf("first", "other"), result.kept.map { it.id })
        assertEquals(1, result.similar)
    }

    @Test fun withoutHashesTheEventPassIsUnchanged() {
        val cycle = (1..10).map { photo("p$it", 0, 9 * 60 + it) }
        assertEquals(Bursts.thin(cycle, emptyMap()) { null }.kept, Bursts.thin(cycle, emptyMap(), hashes = emptyMap()) { null }.kept)
    }
}
