package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class DayIndexTest {
    private val dayMs = 86_400_000L
    private fun on(id: String, date: LocalDate, minute: Int = 600) = FramePhoto(id, "u$id", date.toEpochDay() * dayMs + minute * 60_000L)

    @Test fun photosAreGroupedByTheirOwnDay() {
        val a = LocalDate.of(2024, 5, 1); val b = LocalDate.of(2024, 5, 3)
        val index = DayIndex(listOf(on("a1", a), on("a2", a, 700), on("b1", b)))
        assertEquals(2, index.days.size)
        assertEquals(listOf("a1", "a2"), index.days.getValue(a.toEpochDay()).map { it.id })
    }

    @Test fun undatedPhotosAreLeftOut() {
        val index = DayIndex(listOf(FramePhoto("x", "ux", 0L), on("a", LocalDate.of(2024, 5, 1))))
        assertEquals(1, index.days.size)
    }

    @Test fun aWindowTakesTheDaysEitherSideAndNothingFurther() {
        val centre = LocalDate.of(2024, 5, 10)
        val index = DayIndex((-9..9 step 3).map { on("d$it", centre.plusDays(it.toLong())) })
        assertEquals(setOf("d-6", "d-3", "d0", "d3", "d6"), index.window(centre.toEpochDay(), 6).map { it.id }.toSet())
        assertTrue(index.window(centre.toEpochDay() + 100, 6).isEmpty())
    }

    @Test fun onThisDayReachesBackIntoDecemberAndIncludesTheAlbumsFirstYear() {
        // Today is 5 Jan 2026 with a 14 day window. 28 Dec 2025 is last December, an earlier year, 8 days before today.
        val lastDecember = on("dec", LocalDate.of(2025, 12, 28))
        // 2 Jan 2000, the very first photo of the album: its window is centred on 5 Jan 2000, or on 5 Jan 2001 for a December one.
        val firstYear = on("first", LocalDate.of(2000, 1, 2))
        val firstDecember = on("firstdec", LocalDate.of(2000, 12, 30))
        val kept = DayIndex(listOf(lastDecember, firstYear, firstDecember)).onThisDay(LocalDate.of(2026, 1, 5), 14).values.flatten().map { it.id }.toSet()
        assertEquals(setOf("dec", "first", "firstdec"), kept)
    }

    @Test fun onThisDayLeavesOutThisYearAndPhotosOutsideTheWindow() {
        val today = LocalDate.of(2026, 6, 15)
        val index = DayIndex(listOf(on("in", LocalDate.of(2024, 6, 20)), on("out", LocalDate.of(2024, 7, 20)), on("now", LocalDate.of(2026, 6, 14))))
        assertEquals(setOf("in"), index.onThisDay(today, 7).values.flatten().map { it.id }.toSet())
    }

    @Test fun onThisDayAgreesWithTheSinglesMemoriesExceptForADayAtTheEndOfFebruary() {
        // The singles use a circular day-of-year distance on a leap-year basis; the index uses a window per year. They agree
        // everywhere except by one day at the edge of a window that spans 29 February, which is documented and accepted.
        val random = Random(42)
        var differences = 0
        repeat(40_000) {
            val today = LocalDate.of(2020 + random.nextInt(11), 1, 1).plusDays(random.nextLong(365))
            val photo = LocalDate.of(2000 + random.nextInt(today.year - 2000), 1, 1).plusDays(random.nextLong(365))
            val n = random.nextInt(61)
            val p = on("p", photo)
            // What the singles used before the index: an earlier year, within n days by the circular day-of-year distance.
            val old = photo.year < today.year && PhotoSelection.distance(photo, today) <= n
            val new = DayIndex(listOf(p)).onThisDay(today, n).isNotEmpty()
            if (old != new) {
                differences++
                assertTrue("$photo vs $today with $n days differ by more than the leap-day edge", kotlin.math.abs(PhotoSelection.distance(photo, today) - n) <= 1)
            }
        }
        assertTrue("only a handful of edge cases differ, not $differences", differences < 40_000 / 100)
    }

    @Test fun theSinglesMemoriesKeepAlbumOrderAndLeaveOutUndatedPhotos() {
        val today = LocalDate.of(2026, 1, 2)
        val photos = listOf(on("b", LocalDate.of(2023, 1, 3)), FramePhoto("undated", "uu", 0L), on("c", LocalDate.of(2025, 12, 30)),
            on("a", LocalDate.of(2024, 1, 1)), on("far", LocalDate.of(2024, 7, 1)), on("now", LocalDate.of(2026, 1, 1)))
        // An undated photo is stored as 1970, which is within a window around New Year: it must not count as a memory.
        assertEquals(listOf("b", "c", "a"), PhotoSelection.seasonal(photos, today, 7).map { it.id })
        assertEquals(PhotoSelection.seasonal(photos, today, 7), PhotoSelection.seasonal(photos, today, 7, DayIndex(photos)))
    }

    @Test fun theSinglesMemoriesOverAWholeAlbumAreFastWithoutACalendarConversionPerPhoto() {
        val today = LocalDate.of(2026, 11, 27)
        val photos = (0 until 15_000).map { i -> on("z$i", today.minusDays(((i * 37L) % 7000) + 1)) }
        PhotoSelection.seasonal(photos, today, 14)   // warm up
        var oldMillis = Double.MAX_VALUE; var newMillis = Double.MAX_VALUE; var found = 0
        repeat(4) {
            var t = System.nanoTime()
            found = PhotoSelection.seasonal(photos, today, 14).size
            newMillis = minOf(newMillis, (System.nanoTime() - t) / 1_000_000.0)
            // The old way, for comparison: a calendar conversion for every photo.
            t = System.nanoTime()
            photos.count { val d = LocalDate.ofEpochDay(Math.floorDiv(it.takenMs + it.offsetMs, dayMs)); d.year < today.year && PhotoSelection.distance(d, today) <= 14 }
            oldMillis = minOf(oldMillis, (System.nanoTime() - t) / 1_000_000.0)
        }
        println("singles memories over 15,000 photos: new %.2f ms, old way %.2f ms (%d found)".format(newMillis, oldMillis, found))
        assertTrue("new way took $newMillis ms", newMillis < 10.0)
    }
}
