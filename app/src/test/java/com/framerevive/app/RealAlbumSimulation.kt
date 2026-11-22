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

    /** A mix of shapes like a phone album: mostly landscape, a third portrait, a few squares. */
    private fun shapeOf(p: FramePhoto) = when (p.id.hashCode().mod(20)) {
        in 0..11 -> CollageLayout.Size(1200, 800)
        in 12..18 -> CollageLayout.Size(800, 1200)
        else -> CollageLayout.Size(1000, 1000)
    }

    /**
     * Plays [draws] collages from one source of candidates, as the frame would: each aims for four photos and settles for
     * fewer when four do not tile, each draw avoids the previous collage's day and counts as shown, and the shape
     * fitting picks the combination. Returns one line.
     */
    private fun collageReport(label: String, photos: List<FramePhoto>, draws: Int, lookahead: Int = CollageSelection.LOOKAHEAD,
                              candidates: (SlideContext, SlideQueue, Set<java.time.LocalDate>) -> List<FramePhoto>): String {
        val history = HashMap<String, PlayRecord>()
        val queue = SlideChooser().apply { load(emptyList(), emptySet()) }
        val all = photos.map { it.id }.toSet()
        var lastDay: java.time.LocalDate? = null
        val spans = ArrayList<Double>(); var chooseNanos = 0L
        val sizes = IntArray(5); var made = 0; var twins = 0; var tiledCount = 0
        for (draw in 0 until draws) {
            val ctx = SlideContext(photos, all, { true }, ::shapeOf, { true }, emptyMap(), 1920, 1080, true, 20, setOf("day"), false, history)
            val pool = candidates(ctx, queue, setOfNotNull(lastDay))
            val started = System.nanoTime()
            val tiled = CollageSelection.choose(4, 1920, 1080, pool, ::shapeOf, { true }, emptySet(), lookahead = lookahead)
            chooseNanos += System.nanoTime() - started
            // As the frame does: when nothing tiles within the crop limit, the first candidates are shown whole.
            val chosen = if (tiled.size >= 2) tiled else (4 downTo 2).firstNotNullOfOrNull { n -> pool.take(n).takeIf { it.size == n } } ?: continue
            made++; sizes[chosen.size]++
            if (tiled.size >= 2) tiledCount++
            val times = chosen.map { it.takenMs }.sorted()
            spans += (times.last() - times.first()) / 60_000.0
            if (times.zipWithNext().any { (a, b) -> b - a < Bursts.MIN_SEPARATION_MS }) twins++
            lastDay = chosen.first().date()
            for (p in chosen) history[p.id] = PlayRecord(draw.toLong() + 1, 1)
        }
        if (made == 0) return "collage theme $label: none could be made"
        return ("collage theme %s (%d draws): made %d%%, tiled within the crop limit %d%%, photos 4/3/2: %d%%/%d%%/%d%%, median span %.0f min, " +
            "twins %.0f%%, fitting %.2f ms a collage").format(label, draws, 100 * made / draws, 100 * tiledCount / made,
            100 * sizes[4] / made, 100 * sizes[3] / made, 100 * sizes[2] / made, spans.sorted()[spans.size / 2], 100.0 * twins / made, chooseNanos / 1e6 / draws)
    }

    @Test fun collageThemeReport() {
        val photos = AlbumFixture.load()
        assumeTrue("no album dates file; run tools/album_dates.py (see docs/tuning-the-strategy.md)", photos != null)
        println(collageReport("old same-day pool, 6 candidates", photos!!, 300, lookahead = 6) { ctx, queue, avoidDays ->
            CollageThemeSelection.bestDay(ctx.photos, 4, ctx.cachedIds, queue.ineligible(emptySet()), avoidDays, seen = ctx.seen) })
        for (lookahead in listOf(6, 8, 10, 12)) for (theme in CollageThemes.all)
            println(collageReport("${theme.label}, ${lookahead} candidates", photos, 300, lookahead) { ctx, queue, avoidDays ->
                theme.candidates(ctx, queue, 4, emptySet(), avoidDays.firstOrNull()) })
    }

    @Test fun realAlbumReport() {
        val photos = AlbumFixture.load()
        assumeTrue("no album dates file; run tools/album_dates.py (see docs/tuning-the-strategy.md)", photos != null)
        val today = LocalDate.now()
        println(albumLine(photos!!))
        for (recency in Recency.entries) {
            for (line in report(photos, today, AlbumFixture.cycleSettings(recency), "favor-newer=${recency.id}", listOf(1, 5, 10, 20))) println(line)
        }
    }
}
