package com.framerevive.app

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideChooserTest {
    private val day = 86_400_000L
    private val landscape = CollageLayout.Size(1200, 800)
    private val portrait = CollageLayout.Size(800, 1200)

    /** One photo per day, plus six on one date: a same-day collage has something to draw from. */
    private val singles = (0 until 20).map { FramePhoto("s$it", "u", (20_000L + it) * day + 3_600_000L) }
    private val group = (0 until 6).map { FramePhoto("d$it", "u", 21_000L * day + it * 3_600_000L) }

    private fun context(photos: List<FramePhoto> = singles + group, cached: Set<String> = photos.map { it.id }.toSet(),
                        collages: Boolean = true, pairs: Boolean = false, usable: Boolean = true,
                        size: (FramePhoto) -> CollageLayout.Size? = { landscape }) =
        SlideContext(photos, cached, { it.id in cached }, size, { usable }, emptyMap(), 1920, 1080,
            collages, 5, CollageThemeSelection.SOURCE_DAY, pairs)

    private fun chooser(playlist: List<FramePhoto>) = SlideChooser(Random(1)).apply { load(playlist, playlist.map { it.id }.toSet()) }

    private fun SlideChooser.showSingles(n: Int) = repeat(n) { cadence.displayed(false) }

    @Test fun emptyQueueGivesNoSlide() {
        assertEquals(null, chooser(emptyList()).next(context(), null))
    }

    @Test fun singlesPlayInQueueOrderWhenNothingElseIsOn() {
        val chooser = chooser(singles)
        val shown = (1..3).map { chooser.next(context(collages = false), null)!! }
        assertEquals(listOf("s0", "s1", "s2"), shown.map { it.photos.single().id })
        assertEquals(3, chooser.index)
    }

    @Test fun aCollageFallsDueAfterTheCadenceAndTakesOneDate() {
        val chooser = chooser(singles + group)
        chooser.showSingles(5)
        val slide = chooser.next(context(), null)!!
        assertEquals(SlideMode.COLLAGE, slide.mode)
        assertEquals("a same-day collage has one capture date", 1, slide.photos.map { it.date() }.distinct().size)
        assertTrue(slide.photos.all { it.id.startsWith("d") })
        assertTrue("its photos leave the queue so they do not replay", chooser.playlist.none { p -> slide.photos.any { it.id == p.id } })
        assertEquals(CollageTheme.DAY, chooser.lastCollageTheme)
        assertEquals(slide.photos.first().date(), chooser.lastCollageDay)
        assertEquals(null, chooser.collageTheme)
    }

    @Test fun noCollageBeforeItFallsDue() {
        val chooser = chooser(singles + group)
        chooser.showSingles(4)
        assertEquals(SlideMode.SINGLE, chooser.next(context(), null)!!.mode)
    }

    @Test fun aCollageWithNothingUsableLeavesASingleAndStaysDue() {
        val chooser = chooser(singles + group)
        chooser.showSingles(5)
        val slide = chooser.next(context(usable = false), null)!!
        assertEquals(SlideMode.SINGLE, slide.mode)
        assertTrue("it is tried again on the next slide", chooser.cadence.due(5))
    }

    @Test fun twoOppositeOrientationPhotosMakeAPairAndLeaveTheQueue() {
        val queue = (0 until 6).map { FramePhoto("q$it", "u", (22_000L + it) * day) }
        val chooser = chooser(queue)
        val slide = chooser.next(context(photos = queue, collages = false, pairs = true, size = { portrait }), null)!!
        assertEquals(SlideMode.PAIR, slide.mode)
        assertEquals(listOf("q0", "q1"), slide.photos.map { it.id })
        assertEquals(listOf("q2", "q3", "q4", "q5"), chooser.playlist.drop(chooser.index).map { it.id })
    }

    @Test fun matchingOrientationStaysSingle() {
        val queue = (0 until 6).map { FramePhoto("q$it", "u", (22_000L + it) * day) }
        val slide = chooser(queue).next(context(photos = queue, collages = false, pairs = true, size = { landscape }), null)!!
        assertEquals(SlideMode.SINGLE, slide.mode)
    }

    @Test fun thePreviousSlidesPhotosAreNotRepeatedInAPair() {
        val queue = (0 until 6).map { FramePhoto("q$it", "u", (22_000L + it) * day) }
        val last = FrameSlide(listOf(queue[0]))
        val slide = chooser(queue).next(context(photos = queue, collages = false, pairs = true, size = { portrait }), last)!!
        assertEquals(listOf("q1", "q2"), slide.photos.map { it.id })
    }

    @Test fun theCollageDateIsChosenBeforeTheCollageFallsDue() {
        // The collage's photos are not in the queue, so they must be named for download in advance.
        val chooser = chooser(singles)
        chooser.showSingles(4)
        val plan = chooser.preloadPlan(context(photos = singles + group, cached = emptySet()), depth = 3)
        assertEquals(5, plan.size)
        assertTrue(plan.take(plan.count { it.id.startsWith("d") }).all { it.id.startsWith("d") } && plan.any { it.id.startsWith("d") })
        assertTrue(chooser.collageDay.isNotEmpty())
    }

    @Test fun planningFarFromACollageLooksOnlyAtTheQueue() {
        val chooser = chooser(singles)
        chooser.showSingles(1)
        val plan = chooser.preloadPlan(context(cached = emptySet()), depth = 3)
        assertEquals(listOf("s0", "s1", "s2"), plan.map { it.id })
        assertNotNull(chooser.collageDay)
        assertTrue(chooser.collageDay.isEmpty())
    }

    @Test fun anAlbumChangeForgetsTheChosenDate() {
        val chooser = chooser(singles)
        chooser.showSingles(4)
        chooser.preloadPlan(context(cached = emptySet()), depth = 3)
        chooser.resetForAlbum()
        assertTrue(chooser.collageDay.isEmpty())
        assertEquals(0, chooser.cadence.singles)
    }

    @Test fun claimRecordsPlaysAndKeepsThosePhotosOutOfAnyCollageThisCycle() {
        val chooser = chooser(singles + group)
        val history = HashMap<String, PlayRecord>()
        chooser.claim(listOf("d0", "d1"), history, 1_000L)
        assertEquals(PlayRecord(1_000L, 1), history["d0"])
        chooser.showSingles(5)
        val slide = chooser.next(context(), null)!!
        assertEquals(SlideMode.COLLAGE, slide.mode)
        assertTrue("a photo already shown this cycle is not shown again", slide.photos.none { it.id == "d0" || it.id == "d1" })
    }

    @Test fun aNewCycleMakesClaimedPhotosEligibleAgain() {
        val chooser = chooser(singles + group)
        chooser.claim(group.map { it.id }, HashMap(), 1L)
        chooser.load(singles + group, emptySet())
        chooser.showSingles(5)
        assertTrue(chooser.next(context(), null)!!.photos.all { it.id.startsWith("d") })
    }

    @Test fun aCollageNeverTakesWhatTheCycleThinnedOut() {
        val chooser = SlideChooser(Random(1)).apply { load(singles, singles.map { it.id }.toSet(), group.map { it.id }.toSet()) }
        chooser.showSingles(5)
        val slide = chooser.next(context(), null)!!
        assertTrue(slide.photos.none { it.id.startsWith("d") })
        assertEquals("the chosen source had nothing eligible, so the other one served", 1, chooser.themeFallbacks)
    }

    @Test fun aCollageWithNothingUsableIsCountedAsSkipped() {
        val chooser = chooser(singles + group)
        chooser.showSingles(5)
        chooser.next(context(usable = false), null)
        assertEquals(1, chooser.collageSkips)
        assertEquals(0, chooser.collagesChosen)
    }

    @Test fun theReportCountsShownSkippedAndFallbackCollages() {
        val chooser = chooser(singles + group)
        assertEquals("0 shown, 0 skipped for want of eligible ready photos, 0 from the other source", chooser.collageReport())
        chooser.showSingles(5)
        chooser.next(context(usable = false), null)
        chooser.next(context(), null)
        assertEquals("1 shown, 1 skipped for want of eligible ready photos, 0 from the other source", chooser.collageReport())
    }
}
