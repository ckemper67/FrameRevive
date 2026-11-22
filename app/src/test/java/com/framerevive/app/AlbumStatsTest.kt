package com.framerevive.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumStatsTest {
    private val dayMs = 86_400_000L
    private val today = LocalDate.of(2026, 11, 22)
    private val now = today.toEpochDay() * dayMs + 12 * 3_600_000L
    private val settings = AlbumStats.Settings(roundSize = 300, recentPercent = 50, memoryDays = 14, secondsPerPhoto = 30, awakeHours = 15.0)

    private fun photo(id: String, date: LocalDate, hour: Int = 12) = FramePhoto(id, "u", date.toEpochDay() * dayMs + hour * 3_600_000L)

    /** 5 photos on one day, 1 on another, 3 on a third, and two undated. */
    private val album = buildList {
        repeat(5) { add(photo("a$it", LocalDate.of(2026, 11, 1), 8 + it)) }
        add(photo("b0", LocalDate.of(2026, 10, 20)))
        repeat(3) { add(photo("c$it", LocalDate.of(2025, 3, 5), 9 + it)) }
        repeat(2) { add(FramePhoto("u$it", "u", 0L)) }
    }

    private fun stats(history: Map<String, PlayRecord> = emptyMap(), seasonal: List<FramePhoto>? = emptyList(), s: AlbumStats.Settings = settings) =
        AlbumStats.compute(album, today, history, now, s, seasonal)

    @Test fun theShapeOfTheAlbumIsCounted() {
        val s = stats()
        assertEquals(11, s.photos); assertEquals(2, s.undated); assertEquals(3, s.days)
        assertEquals(2025, s.firstYear); assertEquals(2026, s.lastYear)
        assertEquals(3, s.perDayMedian); assertEquals(5, s.perDayMax); assertEquals(1, s.singlePhotoDays)
        assertEquals(0, s.bigDays); assertEquals(0.0, s.bigDayPhotoShare, 0.0)
    }

    @Test fun bigDaysAndTheirShareOfThePhotosAreFound() {
        val busy = (0 until 30).map { photo("x$it", LocalDate.of(2024, 6, 1), it % 24) } + (0 until 10).map { photo("y$it", LocalDate.of(2024, 6, 2).plusDays(it.toLong())) }
        val s = AlbumStats.compute(busy, today, emptyMap(), now, settings, emptyList())
        assertEquals(1, s.bigDays)
        assertEquals(0.75, s.bigDayPhotoShare, 1e-9)
    }

    @Test fun recentWindowsCountPhotosAndDays() {
        val byMonths = stats().recent.associateBy { it.months }
        assertEquals(AlbumStats.Window(1, 5, 1), byMonths.getValue(1))
        assertEquals(AlbumStats.Window(3, 6, 2), byMonths.getValue(3))
        assertEquals(AlbumStats.Window(24, 9, 3), byMonths.getValue(24))
    }

    @Test fun yearsAreListedOldestFirst() {
        assertEquals(listOf(AlbumStats.Year(2025, 3, 1), AlbumStats.Year(2026, 6, 2)), stats().byYear)
    }

    @Test fun theMemoryPoolIsCountedFromTheCachedSeasonalSet() {
        val pool = listOf(photo("m1", LocalDate.of(2024, 11, 20)), photo("m2", LocalDate.of(2024, 11, 20), 15), photo("m3", LocalDate.of(2023, 11, 25)))
        assertEquals(AlbumStats.Memories(14, 3, 2), stats(seasonal = pool).memories)
        assertNull("not calculated yet", stats(seasonal = null).memories)
    }

    @Test fun progressCountsPhotosDaysAndDaysNotShownLately() {
        val history = mapOf(
            "a0" to PlayRecord(now - 2 * dayMs, 1),          // day A shown two days ago
            "c0" to PlayRecord(now - 90 * dayMs, 1),         // day C shown long ago
            "u0" to PlayRecord(now - dayMs, 1))              // an undated photo counts as a photo shown
        val p = stats(history).progress
        assertEquals(3, p.photosShown)
        assertEquals(2, p.daysReached)
        assertEquals("day B never, day C long ago", 2, p.daysNotShownLately)
    }

    @Test fun nothingShownMeansEveryDayIsNotShownLately() {
        val p = stats().progress
        assertEquals(0, p.photosShown); assertEquals(0, p.daysReached); assertEquals(3, p.daysNotShownLately)
    }

    @Test fun paceFollowsThePhotosADayTheAlbumAndTheMemoryShare() {
        // 30 s a photo for 15 hours = 1,800 a day. 15,481 photos, 1,155 in the memory pool, a 50/50 mix: memories
        // are half the screen time, so 1,155 / 900 = 1.28 days between returns; the rest 14,326 / 900 = 15.9.
        val pace = AlbumStats.pace(15_481, 1_155, settings)
        assertEquals(1800.0, pace.slotsPerDay, 1e-9)
        assertEquals(15_481 / 1800.0, pace.passDays, 1e-9)
        assertEquals(0.5, pace.memoryShare, 1e-9)
        assertEquals(1155 / 900.0, pace.memoryReturnDays!!, 1e-9)
        assertEquals(14_326 / 900.0, pace.otherReturnDays!!, 1e-9)
    }

    @Test fun aSmallMemoryPoolTakesOnlyWhatItHas() {
        // The quota is 300 memories next to 300 recent, but a pool of 40 gives 40 of 600 slots.
        val pace = AlbumStats.pace(15_000, 40, settings)
        assertEquals(40.0 / 600.0, pace.memoryShare, 1e-9)
    }

    @Test fun theSliderEndsPickOnePoolOutright() {
        assertEquals(0.0, AlbumStats.pace(1000, 100, settings.copy(recentPercent = 100)).memoryShare, 0.0)
        val only = AlbumStats.pace(1000, 100, settings.copy(recentPercent = 0))
        assertEquals(1.0, only.memoryShare, 0.0)
        assertNull("no other photos are shown at all", only.otherReturnDays)
        assertEquals(0.0, AlbumStats.pace(1000, 0, settings).memoryShare, 0.0)
    }

    @Test fun anAlbumWithoutPhotosOrAScheduleIsSafe() {
        val empty = AlbumStats.compute(emptyList(), today, emptyMap(), now, settings, emptyList())
        assertEquals(0, empty.days); assertEquals(0, empty.perDayMax); assertEquals(null, empty.firstYear)
        val noPace = AlbumStats.pace(100, 10, settings.copy(secondsPerPhoto = 0))
        assertEquals(0.0, noPace.slotsPerDay, 0.0); assertNull(noPace.memoryReturnDays)
        assertNotNull(AlbumStats.pace(100, 10, settings).memoryReturnDays)
    }

    @Test fun theLocalDayUsesTheStoredOffset() {
        val late = FramePhoto("x", "u", LocalDate.of(2026, 5, 1).toEpochDay() * dayMs + 23 * 3_600_000L + 1_800_000L, 7_200_000L)
        assertEquals(LocalDate.of(2026, 5, 2).year, AlbumStats.compute(listOf(late), today, emptyMap(), now, settings, emptyList()).firstYear)
        assertEquals(1, AlbumStats.compute(listOf(late, late.copy(id = "y")), today, emptyMap(), now, settings, emptyList()).days)
    }
}
