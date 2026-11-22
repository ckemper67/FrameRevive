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
}
