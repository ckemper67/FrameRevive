package com.framerevive.app

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A synthetic album shaped like the frame's through the real playlist pipeline, cycle after cycle with a fake
 * clock. The report is printed so a change in behaviour shows up as a change in numbers; the assertions only
 * pin what must hold whatever the tuning.
 */
class PlaybackSimulation {
    private val today = LocalDate.of(2026, 11, 21)
    private val day = 86_400_000L

    private fun epoch(d: LocalDate, minuteOfDay: Int = 12 * 60) = d.toEpochDay() * day + minuteOfDay * 60_000L

    /** 15k photos over eight years, a few dense burst days, and a 40-photo pocket of earlier Novembers. */
    private fun album(random: Random): List<FramePhoto> {
        val out = ArrayList<FramePhoto>()
        val first = LocalDate.of(2018, 11, 1).toEpochDay(); val last = today.toEpochDay()
        var n = 0
        while (out.size < 14_700) {
            val d = LocalDate.ofEpochDay(random.nextLong(first, last))
            // Keep the calendar window around today empty except for the pocket below, as in a real album
            // where one season is thin.
            if (d.year < today.year && PhotoSelection.distance(d, today) <= 14) continue
            out += FramePhoto("p${n++}", "u", epoch(d, random.nextInt(24 * 60)))
        }
        repeat(6) { burstDay ->
            val d = LocalDate.of(2022 + burstDay % 4, 3 + burstDay, 10)
            repeat(50) { out += FramePhoto("b${burstDay}_$it", "u", epoch(d, 600 + it / 5)) }
        }
        repeat(40) { out += FramePhoto("s$it", "u", epoch(LocalDate.of(2022 + it % 4, 11, 10 + it % 20), random.nextInt(24 * 60))) }
        return out
    }

    /** Burst frames hash within two bits of their burst's base, as real near-identical shots do; the rest are unrelated. */
    private fun hashes(photos: List<FramePhoto>, random: Random): Map<String, Long> {
        val bases = HashMap<String, Long>()
        return photos.associate { p ->
            val burst = p.id.takeIf { it.startsWith("b") }?.substringBefore('_')
            p.id to if (burst == null) random.nextLong() else bases.getOrPut(burst) { random.nextLong() } xor (1L shl random.nextInt(2))
        }
    }

    private class Report(val cycleLength: Int, val maxPlays: Int, val repeatedSlots: Int, val seasonalSlots: Int,
                         val burstShare: Double, val coverage: Double, val years: Map<Int, Int>,
                         val similar: Int = 0, val held: Int = 0, val nearDuplicates: Int = 0)

    private fun run(settings: CycleSettings, cycles: Int, seed: Int): List<Report> {
        val random = Random(seed)
        val photos = album(random)
        val byRecency = photos.sortedByDescending { it.takenMs }
        val seasonal = PhotoSelection.seasonal(photos, today, settings.memoryDays)
        val seasonalIds = seasonal.map { it.id }.toSet()
        val burstIds = Bursts.events(photos).filter { it.isBurst }.flatMap { e -> e.photos.map { it.id } }.toSet()
        val history = HashMap<String, PlayRecord>()
        val hashes = hashes(photos, random)
        var clock = 0L
        return (1..cycles).map {
            val built = PlaybackEngine.buildCycle(photos, today, settings, history, byRecency, seasonal, hashes,
                { null }, emptySet(), random)
            val cycle = built.playlist
            val plays = HashMap<String, Int>()
            for (photo in cycle) {
                plays.merge(photo.id, 1, Int::plus)
                PhotoDeck.recorded(history, listOf(photo.id), clock); clock += 30_000
            }
            Report(cycle.size, plays.values.max(), plays.values.sumOf { it - 1 }, cycle.count { it.id in seasonalIds },
                cycle.count { it.id in burstIds }.toDouble() / cycle.size, PhotoDeck.stats(photos, history).percentShown / 100.0,
                cycle.groupingBy { it.date().year }.eachCount().toSortedMap(), built.similarSkipped, built.burstHeld,
                cycle.withIndex().count { (i, p) -> hashes[p.id]?.let { h -> cycle.take(i).any { q -> hashes[q.id]?.let { PhotoSimilarity.near(h, it) } == true } } == true })
        }
    }

    private class SlideReport(val slides: Int, val singles: Int, val pairs: Int, val collages: Int, val collagesDue: Int,
                              val recentFallbacks: Int, val skippedCollages: Int, val outsideQueue: Int, val repeatedSlots: Int,
                              val coverage: Double, val collageReplays: Int)

    /** A mix of shapes like a phone album: mostly landscape, a third portrait, a few squares. */
    private fun shape(p: FramePhoto) = when (p.id.hashCode().mod(20)) {
        in 0..11 -> CollageLayout.Size(1200, 800)
        in 12..18 -> CollageLayout.Size(800, 1200)
        else -> CollageLayout.Size(1000, 1000)
    }

    /** Drives the real chooser the way the activity does: rebuild an exhausted queue, ask for a slide, record it. */
    private fun runSlides(settings: CycleSettings, slides: Int, seed: Int, themes: Set<String> = setOf("day"),
                          pairs: Boolean = true, collageEvery: Int = 20): SlideReport {
        val random = Random(seed)
        val photos = album(random)
        val byRecency = photos.sortedByDescending { it.takenMs }
        val seasonal = PhotoSelection.seasonal(photos, today, settings.memoryDays)
        val allIds = photos.map { it.id }.toSet()
        val history = HashMap<String, PlayRecord>()
        val ctx = SlideContext(photos, allIds, { true }, ::shape, { true }, emptyMap(), 1920, 1080, true, collageEvery, themes, pairs, history)
        val chooser = SlideChooser(random)
        var last: FrameSlide? = null
        var clock = 0L
        var singles = 0; var pairCount = 0; var collages = 0; var due = 0; var fallbacks = 0; var skipped = 0
        var outside = 0; var repeated = 0; var replays = 0
        val cycleIds = HashSet<String>(); val cyclePlays = HashMap<String, Int>()
        fun closeCycle() { repeated += cyclePlays.values.sumOf { it - 1 }; cyclePlays.clear(); cycleIds.clear() }
        repeat(slides) {
            if (chooser.index >= chooser.playlist.size) {
                closeCycle()
                val cycle = PlaybackEngine.buildCycle(photos, today, settings, history, byRecency, seasonal, emptyMap(),
                    { null }, emptySet(), random)
                chooser.load(cycle.playlist, byRecency.take(settings.roundSize).map { it.id }.toSet(), cycle.excluded)
                cycleIds += cycle.playlist.map { it.id }
            }
            val wasDue = chooser.cadence.due(collageEvery)
            val slide = chooser.next(ctx, last)!!
            if (wasDue) { due++; if (!slide.collage) skipped++ }
            when (slide.mode) {
                SlideMode.SINGLE -> singles++
                SlideMode.PAIR -> pairCount++
                SlideMode.COLLAGE -> {
                    collages++
                    outside += slide.photos.count { it.id !in cycleIds }
                    replays += slide.photos.count { it.id in history }
                }
            }
            chooser.cadence.displayed(slide.collage)
            for (p in slide.photos) cyclePlays.merge(p.id, 1, Int::plus)
            chooser.claim(slide.photos.map { it.id }, history, clock)
            clock += 30_000; last = slide
        }
        closeCycle()
        return SlideReport(slides, singles, pairCount, collages, due, chooser.themeFallbacks, skipped, outside, repeated, PhotoDeck.stats(photos, history).percentShown / 100.0, replays)
    }

    @Test fun presentationMixReport() {
        for ((name, pairs) in listOf("pairs on" to true, "pairs off" to false)) {
            val r = runSlides(CycleSettings(), slides = 2400, seed = 7, pairs = pairs)
            println("sim mix ($name): slides=${r.slides} singles=${r.singles} pairs=${r.pairs} collages=${r.collages} " +
                "due=${r.collagesDue} skipped=${r.skippedCollages} recentFallback=${r.recentFallbacks} " +
                "collagePhotosOutsideQueue=${r.outsideQueue} repeatedSlots=${r.repeatedSlots} coverage=${"%.3f".format(r.coverage)} collagePhotosAlreadyShown=${r.collageReplays}")
            assertEquals(r.slides, r.singles + r.pairs + r.collages)
            assertEquals("collages obey the same no-repeat rule as singles", 0, r.repeatedSlots)
            assertEquals("a collage prefers photos the frame has not shown while the album has them", 0, r.collageReplays)
            assertTrue("collages fall due about every twenty slides", r.collages > 0)
            if (!pairs) assertEquals(0, r.pairs)
        }
    }

    @Test fun recencyReport() {
        val results = Recency.entries.associateWith { r -> run(CycleSettings(recency = r), cycles = 5, seed = 7) }
        fun share(reports: List<Report>, older: Boolean): Double {
            val all = reports.flatMap { it.years.entries }
            val total = all.sumOf { it.value }.toDouble()
            return all.filter { (it.key <= today.year - 3) == older }.sumOf { it.value } / total
        }
        for ((r, reports) in results) {
            val years = reports.flatMap { it.years.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }.toSortedMap()
            println("sim recency ${r.id}: years=$years olderThanThreeYears=${"%.3f".format(share(reports, true))} " +
                "coverage=${"%.3f".format(reports.last().coverage)} repeated=${reports.sumOf { it.repeatedSlots }}")
        }
        val older = results.mapValues { share(it.value, true) }
        assertTrue("somewhat favours newer photos more than even does", older.getValue(Recency.SOMEWHAT) < older.getValue(Recency.EVEN))
        assertTrue("even reaches older years more than the hard window does", older.getValue(Recency.EVEN) > older.getValue(Recency.NEWEST))
        for ((r, reports) in results) {
            assertEquals("no repeats with ${r.id}", 0, reports.sumOf { it.repeatedSlots })
            assertTrue("coverage with ${r.id} is not worse than the hard window",
                reports.last().coverage >= results.getValue(Recency.NEWEST).last().coverage - 0.011)
        }
    }

    @Test fun defaultsReport() {
        val reports = run(CycleSettings(), cycles = 5, seed = 7)
        reports.forEachIndexed { i, r ->
            println("sim cycle ${i + 1}: slots=${r.cycleLength} maxPlays=${r.maxPlays} repeatedSlots=${r.repeatedSlots} " +
                "seasonalSlots=${r.seasonalSlots} burstShare=${"%.3f".format(r.burstShare)} similarSkipped=${r.similar} burstHeld=${r.held} nearDuplicates=${r.nearDuplicates} coverage=${"%.3f".format(r.coverage)} years=${r.years}")
        }
        reports.forEach { assertTrue("every cycle plays something", it.cycleLength > 0) }
        reports.forEach { assertEquals("no photo plays twice within a cycle", 0, it.repeatedSlots) }
        reports.forEach { assertEquals("no two frames of one moment share a cycle", 0, it.nearDuplicates) }
        assertTrue("coverage never goes backwards", reports.zipWithNext().all { (a, b) -> b.coverage >= a.coverage })
    }

    @Test fun recentOnlyNeverRepeatsWithinACycle() {
        // 100% recent has no seasonal half, so the deck alone must give each photo one turn per cycle.
        val reports = run(CycleSettings(recentPercent = 100), cycles = 3, seed = 7)
        reports.forEach { assertEquals(0, it.repeatedSlots) }
    }

    @Test fun theSameSeedGivesTheSameCycles() {
        assertEquals(run(CycleSettings(), 2, 3).map { it.cycleLength to it.seasonalSlots },
            run(CycleSettings(), 2, 3).map { it.cycleLength to it.seasonalSlots })
    }
}
