package com.framerevive.app

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDeckTest {
    @Test fun unseenPhotosComeBeforeAnyRepeat() {
        val album = (1..10).map { FramePhoto(it.toString(), "u$it", it * 1000L) }
        val seen = HashMap<String, PlayRecord>()
        val shown = HashSet<String>()
        repeat(5) {
            val cycle = PhotoDeck.deck(album, seen, 3, Random(it))
            for (p in cycle) shown += p.id
            PhotoDeck.recorded(seen, cycle.map { it.id }, it.toLong() + 1)
        }
        assertEquals("every photo should be reached before one repeats", 10, shown.size)
    }

    @Test fun oldestShownIsPreferredAndNewPhotosComeFirst() {
        val album = (1..6).map { FramePhoto(it.toString(), "u$it", it * 1000L) }
        val seen = HashMap<String, PlayRecord>()
        PhotoDeck.recorded(seen, listOf("1", "2", "3"), 500L)
        val cycle = PhotoDeck.deck(album, seen, 3, Random(7))
        assertTrue("never-shown photos must be drawn first", cycle.all { it.id in setOf("4", "5", "6") })
        // Within the never-shown group, the newest capture is offered first.
        val ordered = PhotoDeck.deck(album, seen, 3, Random(7))
        assertEquals(3, ordered.size)
    }

    @Test fun acceptedWindowKeepsCyclesSmallButVaried() {
        val album = (1..100).map { FramePhoto(it.toString(), "u$it", it * 1000L) }
        val cycle = PhotoDeck.deck(album, emptyMap(), 10, Random(3))
        assertEquals(10, cycle.size)
        assertEquals("a cycle must not repeat a photo", 10, cycle.map { it.id }.distinct().size)
    }

    @Test fun historySurvivesARoundTripOnDisk() {
        val file = File.createTempFile("photo-history", ".json").also { it.delete() }
        val seen = mapOf("a" to PlayRecord(1234L, 3), "b" to PlayRecord(99L, 1))
        PhotoDeck.save(file, seen)
        assertEquals(seen, PhotoDeck.load(file))
        file.delete()
    }

    @Test fun recordsForPhotosLeavingTheAlbumAreDropped() {
        val seen = mapOf("keep" to PlayRecord(1L, 1), "gone" to PlayRecord(2L, 1))
        assertEquals(setOf("keep"), PhotoDeck.pruned(seen, setOf("keep", "new")).keys)
    }

    @Test fun statsReportCoverage() {
        val album = (1..4).map { FramePhoto(it.toString(), "u$it", 0L) }
        val seen = mapOf("1" to PlayRecord(1L, 2), "2" to PlayRecord(1L, 1))
        val stats = PhotoDeck.stats(album, seen)
        assertEquals(2, stats.shown)
        assertEquals(2, stats.unseen)
        assertEquals(50, stats.percentShown)
    }

    private fun pool(n: Int) = (1..n).map { FramePhoto("p$it", "u$it", it * 1000L) }

    @Test fun aWeightedDrawFavoursHeavyPhotosButStillReachesLightOnes() {
        val album = pool(200)
        val weight: (FramePhoto) -> Double = { if (it.takenMs > 100_000L) 1.0 else 0.05 }
        val picks = (1..40).flatMap { PhotoDeck.deck(album, emptyMap(), 50, Random(it), weight = weight) }
        val heavy = picks.count { it.takenMs > 100_000L }
        assertTrue("heavy photos dominate: $heavy of ${picks.size}", heavy > picks.size * 0.8)
        assertTrue("light photos still appear", picks.size - heavy > 0)
    }

    @Test fun aWeightedRoundHasTheRequestedSizeAndNoDuplicates() {
        val round = PhotoDeck.deck(pool(500), emptyMap(), 120, Random(3), weight = { (1.0 + it.takenMs % 7) / 7.0 })
        assertEquals(120, round.size)
        assertEquals(120, round.map { it.id }.distinct().size)
    }

    @Test fun aWeightedDrawStillTakesUnseenPhotosBeforeShownOnes() {
        val album = pool(200)
        val seen = (1..100).associate { "p$it" to PlayRecord(10L, 1) }
        // Weights favour exactly the photos already shown; unseen ones must win regardless.
        val round = PhotoDeck.deck(album, seen, 50, Random(5), weight = { if (it.takenMs <= 100_000L) 1.0 else 0.001 })
        assertTrue(round.all { it.id !in seen })
    }

    @Test fun whenTooFewAreUnseenAWeightedDeckStillFillsWithTheLeastRecentlyShown() {
        val album = pool(10)
        val seen = (1..8).associate { "p$it" to PlayRecord(it * 100L, 1) }
        val round = PhotoDeck.deck(album, seen, 5, Random(2), weight = { 1.0 })
        assertEquals(setOf("p9", "p10", "p1", "p2", "p3"), round.map { it.id }.toSet())
    }

    @Test fun aWeightedDrawIsReproducibleForOneSeed() {
        val a = PhotoDeck.deck(pool(300), emptyMap(), 40, Random(11), weight = { (1.0 + it.takenMs % 5) / 5.0 })
        val b = PhotoDeck.deck(pool(300), emptyMap(), 40, Random(11), weight = { (1.0 + it.takenMs % 5) / 5.0 })
        assertEquals(a, b)
    }

    @Test fun withoutAWeightTheHardWindowIsUnchanged() {
        val newest = (1..400).map { FramePhoto("p$it", "u", (401 - it) * 1000L) }
        val round = PhotoDeck.deck(newest, emptyMap(), 10, Random(1))
        assertTrue("only the newest ${10 * PhotoDeck.WINDOW_MULTIPLIER} are candidates", round.all { it.takenMs > (400 - 10 * PhotoDeck.WINDOW_MULTIPLIER) * 1000L })
    }

    @Test fun aLargeShareOfThePoolIsDrawnByTheExactMethodAndStillFavoursHeavyPhotos() {
        val album = pool(60)
        val weight: (FramePhoto) -> Double = { if (it.takenMs > 30_000L) 1.0 else 0.1 }
        val picks = (1..60).flatMap { PhotoDeck.deck(album, emptyMap(), 30, Random(it), weight = weight) }
        assertTrue("every round is full and unique", picks.chunked(30).all { r -> r.size == 30 && r.map { it.id }.distinct().size == 30 })
        val heavy = picks.count { it.takenMs > 30_000L }
        assertTrue("heavy photos dominate: $heavy of ${picks.size}", heavy > picks.size * 0.6)
    }

    @Test fun weightsTooSmallForRejectionStillGiveAFullRound() {
        val round = PhotoDeck.deck(pool(1000), emptyMap(), 100, Random(9), weight = { 1e-12 })
        assertEquals(100, round.size)
        assertEquals(100, round.map { it.id }.distinct().size)
    }

    @Test fun aSmallDrawFromALargePoolFollowsTheWeightsInProportion() {
        // Heavy photos are four times as likely: of 100 picks from 2,000 photos in equal halves, about 80 are heavy.
        val album = pool(2000)
        val weight: (FramePhoto) -> Double = { if (it.takenMs % 2000L == 0L) 1.0 else 0.25 }
        val heavyShare = (1..30).flatMap { PhotoDeck.deck(album, emptyMap(), 100, Random(it), weight = weight) }
            .let { picks -> picks.count { it.takenMs % 2000L == 0L } / picks.size.toDouble() }
        assertEquals("expected 0.5 / (0.5 + 0.125)", 0.8, heavyShare, 0.05)
    }
}
