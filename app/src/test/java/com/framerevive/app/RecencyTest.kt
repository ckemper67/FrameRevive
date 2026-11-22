package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecencyTest {
    private val day = 86_400_000L
    private val now = 20_000L * day
    private fun photo(daysOld: Long, offsetMs: Long = 0L) = FramePhoto("p$daysOld", "u", now - daysOld * day - offsetMs, offsetMs)

    @Test fun theHardWindowHasNoWeights() { assertNull(Recency.NEWEST.weigher(now)) }

    @Test fun evenWeighsEverythingTheSame() {
        val weigh = Recency.EVEN.weigher(now)!!
        assertEquals(weigh(photo(0)), weigh(photo(4000)), 0.0)
    }

    @Test fun aNewPhotoWeighsOneAndAnOldOneNeverDropsBelowTheFloor() {
        val weigh = Recency.SOMEWHAT.weigher(now)!!
        assertEquals(1.0, weigh(photo(0)), 1e-9)
        assertEquals("one time constant old", 0.05 + 0.95 * Math.exp(-1.0), weigh(photo(180)), 1e-9)
        assertEquals(0.05, weigh(photo(20_000)), 1e-3)
        assertTrue(weigh(photo(30)) > weigh(photo(300)))
    }

    @Test fun aPhotoDatedInTheFutureCountsAsNew() {
        assertEquals(1.0, Recency.SOMEWHAT.weigher(now)!!(photo(-10)), 1e-9)
    }

    @Test fun theCaptureOffsetIsPartOfTheAge() {
        val weigh = Recency.SOMEWHAT.weigher(now)!!
        // The same instant, 90 days apart once the local offset is added: the older reading must weigh less.
        assertTrue(weigh(FramePhoto("a", "u", now - 100 * day, 0L)) > weigh(FramePhoto("b", "u", now - 100 * day, -90 * day)))
    }

    @Test fun settingsComeFromStoredIdsAndFallBackToTheDefault() {
        assertEquals(Recency.EVEN, Recency.of("even"))
        assertEquals(Recency.NEWEST, Recency.of("newest"))
        assertEquals(Recency.DEFAULT, Recency.of(null))
        assertEquals(Recency.DEFAULT, Recency.of("unheard-of"))
        assertEquals(Recency.entries.size, Recency.entries.map { it.id }.toSet().size)
        assertNotNull(Recency.DEFAULT.weigher(now))
    }
}
