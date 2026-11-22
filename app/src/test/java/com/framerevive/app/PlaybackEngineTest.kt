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

    @Test fun thePlanCoversADaysPhotosWithAMargin() {
        // 240 seconds a photo for 16 hours is 240 a day; with the margin 384.
        assertEquals(240.0, PlaybackEngine.slotsPerDay(240, 16.0), 1e-9)
        assertEquals(384, PlaybackEngine.plannedSlots(240, 16.0))
    }

    @Test fun thePlanIsKeptBetweenTheFloorAndTheCeiling() {
        assertEquals("a fast frame is capped", PlaybackEngine.MAX_PLAN, PlaybackEngine.plannedSlots(10, 16.0))
        assertEquals("the app default is capped too", PlaybackEngine.MAX_PLAN, PlaybackEngine.plannedSlots(30, 15.0))
        assertEquals("a slow frame still plans a useful round", PlaybackEngine.MIN_PLAN, PlaybackEngine.plannedSlots(600, 16.0))
        assertEquals("no pace known", PlaybackEngine.MIN_PLAN, PlaybackEngine.plannedSlots(0, 16.0))
        assertEquals(PlaybackEngine.MIN_PLAN, PlaybackEngine.plannedSlots(240, 0.0))
    }

    @Test fun aSlowerPaceNeverPlansMoreThanAFasterOne() {
        val plans = listOf(10, 30, 60, 120, 240, 600).map { PlaybackEngine.plannedSlots(it, 16.0) }
        assertEquals(plans, plans.sortedDescending())
    }

    @Test fun theRecentShareAndTheMemoryQuotaTogetherMakeThePlan() {
        for (planned in listOf(300, 384, 1000, 2000)) for (percent in listOf(5, 25, 50, 75, 95)) {
            val recent = PlaybackEngine.roundSizeFor(planned, percent)
            val total = recent + PhotoSelection.memorySlots(recent, percent)
            // Whole photos make the plan approximate: within about 2 percent.
            assertTrue("planned $planned at $percent%: $total", kotlin.math.abs(total - planned) <= planned / 50 + 1)
        }
    }

    @Test fun theSliderEndsPlanOnePoolAndTheSizeStaysInRange() {
        assertEquals(384, PlaybackEngine.roundSizeFor(384, 100))
        assertEquals(384, PlaybackEngine.roundSizeFor(384, 0))
        assertTrue(PlaybackEngine.roundSizeFor(300, 1) in 1..2000)
        assertEquals(2000, PlaybackEngine.roundSizeFor(5000, 100))
    }

    @Test fun theRecentCollagePoolKeepsItsOwnSize() { assertEquals(300, PlaybackEngine.RECENT_COLLAGE_POOL) }
}
