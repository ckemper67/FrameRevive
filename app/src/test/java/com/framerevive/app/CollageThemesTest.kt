package com.framerevive.app

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageThemesTest {
    private val dayMs = 86_400_000L
    private val minute = 60_000L
    private val landscape = CollageLayout.Size(1200, 800)
    private val portrait = CollageLayout.Size(800, 1200)
    /** Every other photo is a portrait: a landscape frame needs a mix of shapes, and a day's photos have one. */
    private val mixedShapes: (FramePhoto) -> CollageLayout.Size = { if (it.id.last().digitToInt() % 2 == 0) landscape else portrait }

    private fun at(id: String, day: Int, minuteOfDay: Int) = FramePhoto(id, "u$id", (20_000L + day) * dayMs + minuteOfDay * minute)

    /** [count] photos on [day] starting at [from], [step] minutes apart. */
    private fun run(prefix: String, day: Int, from: Int, step: Int, count: Int) = (0 until count).map { at("$prefix$it", day, from + it * step) }

    private fun context(photos: List<FramePhoto>, themes: Set<String>, cached: Set<String> = photos.map { it.id }.toSet(),
                        seen: Map<String, PlayRecord> = emptyMap(), size: (FramePhoto) -> CollageLayout.Size = { landscape },
                        pairs: Boolean = false) =
        SlideContext(photos, cached, { it.id in cached }, size, { true }, emptyMap(), 1920, 1080, true, 5, themes, pairs, seen)

    private fun chooser(playlist: List<FramePhoto>) = SlideChooser(Random(1)).apply { load(playlist, playlist.map { it.id }.toSet()) }
    private fun SlideChooser.showSingles(n: Int) = repeat(n) { cadence.displayed(false) }
    private fun queue(playlist: List<FramePhoto>): SlideQueue = chooser(playlist)

    // ---- TimeSpread ----

    @Test fun spreadPicksTheFirstThenEachFarthestFromThosePicked() {
        val photos = run("p", 0, 0, 10, 7)          // minutes 0, 10, ..., 60
        assertEquals(listOf("p0", "p6", "p3"), TimeSpread.order(photos, limit = 3).map { it.id })
    }

    @Test fun anchorsPushThePicksAwayFromWhatWasAlreadyShown() {
        val photos = run("p", 0, 0, 10, 7)
        val first = TimeSpread.order(photos, anchors = listOf(0L), limit = 1).single()
        assertEquals("p6", first.id)
    }

    @Test fun spreadOfNothingIsNothingAndTheLimitIsRespected() {
        assertTrue(TimeSpread.order(emptyList()).isEmpty())
        assertEquals(2, TimeSpread.order(run("p", 0, 0, 10, 7), limit = 2).size)
        assertEquals(7, TimeSpread.order(run("p", 0, 0, 10, 7)).size)
    }

    @Test fun equalDistancesKeepTheListOrder() {
        val photos = listOf(at("a", 0, 0), at("b", 0, 20), at("c", 0, 40), at("d", 0, 60))
        // After a, d is farthest; then b and c are equally far from both, so the earlier one wins.
        assertEquals(listOf("a", "d", "b"), TimeSpread.order(photos, limit = 3).map { it.id })
    }

    @Test fun separatedDropsPhotosWithinTwoMinutesOfAKeptOne() {
        val photos = listOf(at("a", 0, 0), at("b", 0, 1), at("c", 0, 10), at("d", 0, 11), at("e", 0, 30))
        assertEquals(listOf("a", "c", "e"), TimeSpread.separated(photos, needed = 3).map { it.id })
    }

    @Test fun separatedKeepsEverythingWhenTooFewWouldRemain() {
        val photos = listOf(at("a", 0, 0), at("b", 0, 1), at("c", 0, 2))
        assertEquals(3, TimeSpread.separated(photos, needed = 3).size)
    }

    // ---- A day ----

    @Test fun aDayOffersPhotosSpreadThroughItNotTheFirstFew() {
        val day = run("d", 1, 8 * 60, 5, 24)         // two hours of photos, 5 minutes apart
        val candidates = CollageThemes.DAY.candidates(context(day, setOf("day")), queue(day), 4, emptySet(), null)
        assertEquals(CollageThemeSelection.MAX_CANDIDATES, candidates.size)
        val firstFour = candidates.take(4).map { it.takenMs / minute }.sorted()
        assertTrue("spread across the day: $firstFour", firstFour.last() - firstFour.first() >= 100)
    }

    @Test fun aDayNeverOffersTwoFramesOfOneInstantWhenItHasOthers() {
        val day = run("burst", 1, 9 * 60, 0, 1).let { run("x", 1, 9 * 60, 1, 20) + run("y", 1, 15 * 60, 30, 4) }   // a one-minute-apart run and a few spaced photos
        val candidates = CollageThemes.DAY.candidates(context(day, setOf("day")), queue(day), 4, emptySet(), null)
        for (a in candidates) for (b in candidates) if (a !== b) assertTrue("${a.id} and ${b.id}", kotlin.math.abs(a.takenMs - b.takenMs) >= Bursts.MIN_SEPARATION_MS)
    }

    @Test fun aDayPrefersPhotosNotYetShown() {
        val day = run("d", 1, 8 * 60, 30, 8)
        val seen = day.take(4).associate { it.id to PlayRecord(5L, 1) }
        val candidates = CollageThemes.DAY.candidates(context(day, setOf("day"), seen = seen), queue(day), 4, emptySet(), null)
        assertTrue(candidates.take(4).none { it.id in seen })
    }

    @Test fun aDayOffersWhatItHasDownToTwoPhotosAndNothingBelow() {
        val three = run("t", 1, 8 * 60, 30, 3)
        assertEquals(3, CollageThemes.DAY.candidates(context(three, setOf("day")), queue(three), 4, emptySet(), null).size)
        val one = run("u", 1, 8 * 60, 30, 1)
        assertTrue(CollageThemes.DAY.candidates(context(one, setOf("day")), queue(one), 4, emptySet(), null).isEmpty())
    }

    // ---- One outing ----

    @Test fun anOutingStaysWithinAnHourOfItsSeedEvenOnABusyDay() {
        // Morning at home, then an evening out: a day collage may mix them, an outing must not.
        val morning = run("m", 1, 9 * 60, 15, 5)
        val evening = run("e", 1, 19 * 60, 15, 5)
        val day = morning + evening
        repeat(5) { round ->
            val seen = (0 until round).associate { day[it].id to PlayRecord(1L, 1) }
            val candidates = CollageThemes.OUTING.candidates(context(day, setOf("outing"), seen = seen), queue(day), 4, emptySet(), null)
            assertTrue(candidates.size >= 4)
            val span = candidates.map { it.takenMs / minute }.let { it.max() - it.min() }
            assertTrue("one stretch of the day: span $span minutes", span <= 120)
        }
    }

    @Test fun anOutingsPhotosAreAtLeastTwoMinutesApart() {
        val day = run("d", 1, 9 * 60, 1, 40)         // a photo a minute for 40 minutes
        val candidates = CollageThemes.OUTING.candidates(context(day, setOf("outing")), queue(day), 4, emptySet(), null)
        assertTrue(candidates.size >= 4)
        for (a in candidates) for (b in candidates) if (a !== b) assertTrue(kotlin.math.abs(a.takenMs - b.takenMs) >= Bursts.MIN_SEPARATION_MS)
    }

    @Test fun anOutingNeverUsesFramesOfOneInstant() {
        val rapid = (0 until 8).map { FramePhoto("r$it", "u", 20_001L * dayMs + 9 * 60 * minute + it * 10_000L) }
        val candidates = CollageThemes.OUTING.candidates(context(rapid, setOf("outing")), queue(rapid), 3, emptySet(), null)
        assertTrue("eight frames in 70 seconds are one picture, not a collage", candidates.isEmpty())
    }

    @Test fun anOutingStartsFromAPhotoNotShownYet() {
        val day = run("d", 1, 9 * 60, 15, 8)
        val seen = day.take(3).associate { it.id to PlayRecord(1L, 1) }
        val candidates = CollageThemes.OUTING.candidates(context(day, setOf("outing"), seen = seen), queue(day), 3, emptySet(), null)
        assertTrue("the seed is unseen", candidates.first().id !in seen)
    }

    @Test fun anOutingFallsThroughToTheNextDayWhenTheBestHasNoSeed() {
        val scattered = run("s", 1, 0, 200, 6)        // six photos, 200 minutes apart: nobody has neighbours within an hour
        val cluster = run("c", 2, 12 * 60, 20, 5)
        val album = scattered + cluster
        val candidates = CollageThemes.OUTING.candidates(context(album, setOf("outing")), queue(album), 3, emptySet(), null)
        assertTrue(candidates.isNotEmpty() && candidates.all { it.id.startsWith("c") })
    }

    // ---- Taking turns ----

    private fun threeDays() = run("a", 1, 9 * 60, 12, 6) + run("b", 2, 9 * 60, 12, 6) + run("c", 3, 9 * 60, 12, 6)

    @Test fun enabledThemesTakeTurns() {
        // Four collages of five photos need more than three days of six.
        val album = run("a", 1, 9 * 60, 12, 12) + run("b", 2, 9 * 60, 12, 12) + run("c", 3, 9 * 60, 12, 12)
        val chooser = chooser(album + (0 until 40).map { at("q$it", 50 + it, 600) })
        val seen = ArrayList<String>()
        repeat(4) {
            chooser.showSingles(5)
            val slide = chooser.next(context(album, setOf("day", "outing"), size = mixedShapes), null)!!
            assertEquals("round $it after $seen: ${slide.photos.map { p -> p.id }}", SlideMode.COLLAGE, slide.mode)
            seen += chooser.lastCollageTheme!!
            chooser.cadence.displayed(true)
            chooser.claim(slide.photos.map { it.id }, HashMap(), 1L)
        }
        assertEquals(listOf("day", "outing", "day", "outing"), seen)
    }

    @Test fun repeatedPreviewsMoveThroughDifferentDaysInsteadOfTwoTakingTurns() {
        val album = run("a", 1, 9 * 60, 12, 12) + run("b", 2, 9 * 60, 12, 12) + run("c", 3, 9 * 60, 12, 12) + run("d", 4, 9 * 60, 12, 12)
        val chooser = chooser(album)
        val days = (0 until 4).map {
            chooser.previewCollage(context(album, setOf("day"), size = mixedShapes), 5).photos.first().id.first()
        }
        // The preview records no plays, so without a memory of its own the best two days would alternate.
        assertEquals("each preview shows a day not seen in the last few: $days", days.size, days.distinct().size)
    }

    @Test fun aPreviewWidensToTheDaysAroundWhenTheDayLacksTheShapes() {
        // One day of landscapes only; four days later a few portraits. A five needs both, so the preview has to look wider.
        val album = run("a", 1, 9 * 60, 12, 8) + run("b", 5, 9 * 60, 12, 4)
        val shapes: (FramePhoto) -> CollageLayout.Size = { if (it.id.startsWith("b")) portrait else landscape }
        val preview = chooser(album).previewCollage(context(album, setOf("day"), size = shapes, pairs = true), 5)
        assertEquals("notes: ${preview.notes}", 5, preview.photos.size)
        assertTrue("photos ${preview.photos.map { it.id }}", preview.photos.any { it.id.startsWith("b") })
        assertTrue("noted the widening: ${preview.notes}", preview.notes.any { it.contains("within 7 days") })
    }

    @Test fun playbackDoesNotWidenToTheDaysAround() {
        val album = run("a", 1, 9 * 60, 12, 8) + run("b", 5, 9 * 60, 12, 4)
        val shapes: (FramePhoto) -> CollageLayout.Size = { if (it.id.startsWith("b")) portrait else landscape }
        val chooser = chooser(album + (0 until 40).map { at("q$it", 50 + it, 600) })
        chooser.showSingles(5)
        val slide = chooser.next(context(album, setOf("day"), size = shapes, pairs = true), null)!!
        // With the screen to be filled, the day's landscapes alone make no collage on this frame, and playback does not look
        // at the days around it: the collage is skipped and a single photo is shown.
        assertEquals(SlideMode.SINGLE, slide.mode)
    }

    @Test fun aSingleEnabledThemeIsUsedEveryTime() {
        val album = threeDays()
        val chooser = chooser(album)
        repeat(2) {
            chooser.showSingles(5)
            val slide = chooser.next(context(album, setOf("outing")), null)!!
            assertEquals("outing", chooser.lastCollageTheme)
            chooser.cadence.displayed(true)
            chooser.claim(slide.photos.map { it.id }, HashMap(), 1L)
        }
    }

    @Test fun noThemeEnabledMeansADay() {
        val album = threeDays()
        val chooser = chooser(album)
        chooser.showSingles(5)
        assertEquals(SlideMode.COLLAGE, chooser.next(context(album, emptySet()), null)!!.mode)
        assertEquals("day", chooser.lastCollageTheme)
    }

    @Test fun aThemeWithNothingToOfferHandsOverToTheNext() {
        // Every day is a burst of one instant: an outing is impossible, a day collage is possible.
        val rapid = (0 until 3).flatMap { d -> (0 until 6).map { FramePhoto("r${d}_$it", "u", (20_001L + d) * dayMs + 9 * 60 * minute + it * 10_000L) } }
        val chooser = chooser(rapid)
        val ctx = context(rapid, setOf("outing", "day"))
        chooser.showSingles(5)
        val first = chooser.next(ctx, null)!!                      // the day is first in turn and serves
        assertEquals("day", chooser.lastCollageTheme)
        assertEquals(0, chooser.themeFallbacks)
        chooser.cadence.displayed(true)
        chooser.claim(first.photos.map { it.id }, HashMap(), 1L)
        chooser.showSingles(5)
        val second = chooser.next(ctx, first)!!                    // now the outing is first in turn, finds nothing, and the day serves
        assertEquals(SlideMode.COLLAGE, second.mode)
        assertEquals("day", chooser.lastCollageTheme)
        assertEquals(1, chooser.themeFallbacks)
    }

    @Test fun whenNoEnabledThemeCanMakeACollageASingleIsShownAndItIsCounted() {
        val rapid = (0 until 6).map { FramePhoto("r$it", "u", 20_001L * dayMs + 9 * 60 * minute + it * 10_000L) }
        val chooser = chooser(rapid)
        chooser.showSingles(5)
        assertEquals(SlideMode.SINGLE, chooser.next(context(rapid, setOf("outing")), null)!!.mode)
        assertEquals(1, chooser.collageSkips)
        assertNull(chooser.lastCollageTheme)
    }

    @Test fun thePlanMadeAheadComesFromTheThemeWhoseTurnItIs() {
        val album = threeDays()
        val chooser = chooser(album)
        chooser.showSingles(4)
        val plan = chooser.preloadPlan(context(album, setOf("outing"), cached = emptySet()), depth = 3)
        assertTrue(chooser.collagePlan.isNotEmpty())
        assertEquals(chooser.collagePlan.take(5).map { it.id }, plan.take(5).map { it.id })
        assertEquals("the seed leads the plan", chooser.collagePlan.first().id, plan.first().id)
    }

    @Test fun aPreviewTakesTheNextThemeEachTime() {
        val album = threeDays()
        val chooser = chooser(album)
        val ctx = context(album, setOf("day", "outing"))
        val first = chooser.previewCollage(ctx, 3)
        val second = chooser.previewCollage(ctx, 3)
        assertEquals(listOf("day", "outing"), listOf(first.themeId, second.themeId))
    }

    @Test fun everyThemeHasAUniqueIdAndAPlainLabel() {
        assertEquals(CollageThemes.all.size, CollageThemes.all.map { it.id }.toSet().size)
        assertTrue(CollageThemes.all.all { it.label.isNotBlank() })
        assertEquals(CollageThemes.OUTING, CollageThemes.byId("outing"))
        assertNull(CollageThemes.byId("mixed"))
    }
}
