package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEngineTest {
    private val today = LocalDate.of(2026, 11, 21)
    private val photos = (1..400).map { FramePhoto("p$it", "u", (20_000L + it) * 86_400_000L) }
    private val byRecency = photos.sortedByDescending { it.takenMs }

    private fun build(keep: (FramePhoto) -> Boolean) =
        PlaybackEngine.buildCycle(photos, today, CycleSettings(roundSize = 100, recentPercent = 100), emptyMap(), byRecency, emptyList(),
            emptyMap(), { null }, emptySet(), Random(1), keep).playlist

    @Test fun photosTheGateRefusesAreNotCandidatesForTheCycle() {
        val soft = (1..400 step 3).map { "p$it" }.toSet()
        val cycle = build { it.id !in soft }
        assertTrue(cycle.none { it.id in soft })
        assertEquals("the round is still full, drawn from what is left", 100, cycle.size)
    }

    @Test fun withNothingRefusedTheCycleIsUnchanged() {
        assertEquals(build { true }.map { it.id }, build { true }.map { it.id })
        assertEquals(100, build { true }.size)
    }
}
