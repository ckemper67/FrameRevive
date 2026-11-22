package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the real selection pipeline over an album's capture times, so a maintainer can see how a strategy treats
 * days (how many it reaches, how much a big day takes) on a real album's shape. The times come from
 * `tools/album_dates.py`: one `takenMs,offsetMs` line per photo, no ids and no addresses. See
 * docs/tuning-the-strategy.md. Without that file the report is skipped.
 */
class RealAlbumSimulation {
    private val dayMs = 86_400_000L
    /** Photos a day at the frame's pace (FRAMEREVIVE_SECONDS and FRAMEREVIVE_AWAKE_HOURS; default 1,800 a day). */
    private val slotsPerDay get() = AlbumFixture.slotsPerDay()

    private fun dayOf(p: FramePhoto) = Math.floorDiv(p.takenMs + p.offsetMs, dayMs)

    /** One strategy over [cycles] rounds; returns a line per checkpoint. */
    private fun report(photos: List<FramePhoto>, today: LocalDate, settings: CycleSettings, label: String,
                       checkpoints: List<Int>, bigDay: Int = 20, recentMonths: Long = 6, seed: Int = 7): List<String> {
        val perDay = photos.filter { it.dated }.groupingBy(::dayOf).eachCount()
        val recentFrom = today.minusMonths(recentMonths).toEpochDay()
        val byRecency = photos.sortedByDescending { it.takenMs }
        val seasonal = PhotoSelection.seasonal(photos, today, settings.memoryDays)
        val history = HashMap<String, PlayRecord>()
        val random = Random(seed)
        val shownPhotos = HashSet<String>(); val shownDays = HashSet<Long>()
        var slots = 0; var bigSlots = 0; var recentSlots = 0; var clock = 0L; var heldTotal = 0
        val shown = ArrayList<FramePhoto>()
        val lines = ArrayList<String>()
        for (cycle in 1..checkpoints.max()) {
            val built = PlaybackEngine.buildCycle(photos, today, settings, history, byRecency, seasonal, emptyMap(), { null }, emptySet(), random)
            val round = built.playlist
            heldTotal += built.burstHeld
            assertEquals("a round never repeats a photo", round.size, round.map { it.id }.distinct().size)
            for (p in round) {
                shownPhotos += p.id; shownDays += dayOf(p); shown += p
                if ((perDay[dayOf(p)] ?: 0) >= bigDay) bigSlots++
                if (p.dated && dayOf(p) >= recentFrom) recentSlots++
                PhotoDeck.recorded(history, listOf(p.id), clock); clock += 30_000; slots++
            }
            if (cycle in checkpoints) lines += ("real album $label after $cycle rounds (%d slots, about %.1f days of display): photos shown %.0f%%, days reached %.0f%%, slots on days with %d+ photos %.0f%%, " +
                "slots from the last %d months %.0f%%, shown photos with a twin (another shown the same display day within 2 minutes of capture) %.1f%%, burst frames held back per round %.1f").format(
                slots, slots / slotsPerDay, 100.0 * shownPhotos.size / photos.size,
                100.0 * shownDays.size / perDay.size.coerceAtLeast(1), bigDay, 100.0 * bigSlots / slots, recentMonths, 100.0 * recentSlots / slots,
                100.0 * twinShare(shown), heldTotal.toDouble() / cycle)
        }
        return lines
    }

    /** Share of shown photos that have another shown photo, in the same display day, captured within two minutes of it. */
    private fun twinShare(shown: List<FramePhoto>): Double {
        if (shown.isEmpty()) return 0.0
        val perDay = slotsPerDay.toInt().coerceAtLeast(1)
        var twins = 0
        for (chunk in shown.chunked(perDay)) {
            val times = chunk.map { it.takenMs }.sorted()
            val twin = BooleanArray(times.size)
            for (i in 1 until times.size) if (times[i] - times[i - 1] <= 120_000L) { twin[i] = true; twin[i - 1] = true }
            twins += twin.count { it }
        }
        return twins.toDouble() / shown.size
    }

    private fun albumLine(photos: List<FramePhoto>, bigDay: Int = 20): String {
        val perDay = photos.filter { it.dated }.groupingBy(::dayOf).eachCount()
        val big = perDay.values.filter { it >= bigDay }
        return "real album: %d photos on %d days; days with %d+ photos: %d (%.0f%% of days) hold %.0f%% of the photos".format(
            photos.size, perDay.size, bigDay, big.size, 100.0 * big.size / perDay.size.coerceAtLeast(1), 100.0 * big.sum() / photos.size.coerceAtLeast(1))
    }

    @Test fun theScriptsFileFormatIsParsed() {
        val photos = AlbumFixture.parse(listOf("takenMs,offsetMs", "1000,0", "", "2000,3600000"))
        assertEquals(listOf(1000L, 2000L), photos.map { it.takenMs })
        assertEquals(3_600_000L, photos[1].offsetMs)
        assertEquals(2, photos.map { it.id }.distinct().size)
    }

    @Test fun aFileInAnotherFormatIsRefused() {
        val refused = runCatching { AlbumFixture.parse(listOf("id,url", "1,2")) }
        assertTrue(refused.isFailure)
    }

    @Test fun theReportRunsOnAnyAlbumAndFindsWhatItShouldOnASmallOne() {
        // A made-up album: 1,500 photos over 400 days, one 120-photo day. Exercises the same code the real report uses.
        val today = LocalDate.of(2026, 11, 22)
        val random = Random(1)
        val photos = (0 until 1380).map { FramePhoto("s$it", "u", (today.toEpochDay() - random.nextLong(0, 400)) * dayMs + 12 * 3_600_000L) } +
            (0 until 120).map { FramePhoto("b$it", "u", (today.toEpochDay() - 200) * dayMs + it * 60_000L) }
        val lines = report(photos, today, CycleSettings(roundSize = 100, recentPercent = 100), "small", listOf(2, 5))
        assertEquals(2, lines.size)
        assertTrue(lines.first(), lines.first().contains("days reached"))
        assertTrue(albumLine(photos).contains("1500 photos"))
    }

    @Test fun realAlbumReport() {
        val photos = AlbumFixture.load()
        assumeTrue("no album dates file; run tools/album_dates.py (see docs/tuning-the-strategy.md)", photos != null)
        val today = LocalDate.now()
        println(albumLine(photos!!))
        for (recency in Recency.entries) {
            for (line in report(photos, today, CycleSettings(recency = recency), "favor-newer=${recency.id}", listOf(1, 5, 10, 20))) println(line)
        }
    }
}
