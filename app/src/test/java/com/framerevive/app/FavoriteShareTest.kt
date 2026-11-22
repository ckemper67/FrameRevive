package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteShareTest {
    private val today = LocalDate.of(2026, 11, 21)
    // One photo a day for 2,000 days, so no two photos are close in time and thinning leaves the plan alone.
    private val photos = (1..2000).map { FramePhoto("p$it", "u", (10_000L + it) * 86_400_000L) }
    private val byRecency = photos.sortedByDescending { it.takenMs }

    private fun build(favorites: Set<String>, percent: Int, keep: (FramePhoto) -> Boolean = { true }, seed: Int = 1, seen: Map<String, PlayRecord> = emptyMap(),
                      recentPercent: Int = 100) =
        PlaybackEngine.buildCycle(photos, today, CycleSettings(roundSize = 300, recentPercent = recentPercent, favoritePercent = percent), seen, byRecency, emptyList(),
            emptyMap(), { null }, emptySet(), Random(seed), keep, favorites).playlist

    private fun starred(n: Int) = (1..n).map { "p${it * 37 % 2000 + 1}" }.toSet()

    @Test fun theShareOfFavoritesIsTheSameWhateverTheirNumber() {
        // The same number for a short list and a long one alike, and twice as many at twice the share.
        val five = PlaybackEngine.favoriteSlots(300, 5); val ten = PlaybackEngine.favoriteSlots(300, 10)
        assertEquals(listOf(five, five, five), listOf(50, 200, 500).map { n -> val fav = starred(n); build(fav, 5).count { it.id in fav } })
        assertEquals(listOf(ten, ten, ten), listOf(50, 200, 500).map { n -> val fav = starred(n); build(fav, 10).count { it.id in fav } })
        assertEquals(2.0, ten.toDouble() / five, 0.1)
    }

    @Test fun thePlanKeepsItsLengthAndNoPhotoPlaysTwice() {
        val fav = starred(200)
        val plan = build(fav, 10)
        assertEquals(300, plan.size)
        assertEquals(plan.size, plan.map { it.id }.toSet().size)
    }

    @Test fun offOrNoFavoritesChangesNothing() {
        val fav = starred(100)
        assertEquals(build(emptySet(), 5).map { it.id }, build(fav, 0).map { it.id })
        assertEquals(300, build(emptySet(), 5).size)
    }

    @Test fun favoritesAreSpreadThroughThePlanAndNeverBackToBack() {
        val fav = starred(200)
        val plan = build(fav, 10)
        val at = plan.indices.filter { plan[it].id in fav }
        assertTrue("no two favorites in a row", at.zipWithNext().all { (a, b) -> b - a > 1 })
        assertTrue("the first third has some, so do the last", at.any { it < 100 } && at.any { it >= 200 })
        assertTrue("evenly spread: the largest gap is a small multiple of the average", (at.zipWithNext { a, b -> b - a }.max()) <= 2 * 300 / at.size)
    }

    @Test fun otherPoolsNeitherDrawFavoritesNorSpendSlotsOnThem() {
        val fav = starred(500)
        val plan = build(fav, 5)
        // Every favorite in the plan came from the favorites deck, and the rest of the plan is what is left.
        val slots = PlaybackEngine.favoriteSlots(300, 5)
        assertEquals(slots, plan.count { it.id in fav })
        assertEquals(300 - slots, plan.count { it.id !in fav })
    }

    @Test fun aStarredPhotoIsShownEvenWhenTheGateRefusesIt() {
        val fav = starred(50)
        val soft = photos.filter { it.id in fav || it.takenMs % 3 == 0L }.map { it.id }.toSet()   // all favorites and a third of the rest are too soft
        val plan = build(fav, 10, keep = { it.id !in soft })
        assertEquals("every favorite slot is filled although the gate refuses them", PlaybackEngine.favoriteSlots(300, 10), plan.count { it.id in fav })
        assertTrue("the gate still holds for everything else", plan.filter { it.id !in fav }.none { it.id in soft })
    }

    @Test fun favoritesComeBackLeastRecentlyShownFirst() {
        val fav = starred(100).toList()
        val seen = fav.mapIndexed { i, id -> id to PlayRecord(1_000L + i, 1) }.toMap()
        val plan = build(fav.toSet(), 10, seen = seen)
        val shown = plan.filter { it.id in fav }.map { it.id }.toSet()
        assertEquals("the ones shown longest ago", fav.take(PlaybackEngine.favoriteSlots(300, 10)).toSet(), shown)
    }

    @Test fun aListShorterThanTheShareShowsEachOnceAndGivesTheRestBack() {
        val fav = starred(10)
        val plan = build(fav, 10)
        assertEquals(10, plan.count { it.id in fav })
        assertEquals("the unused favorite slots went back to the other pools", 300, plan.size)
    }

    @Test fun aFavoriteInABurstIsKeptAndItsNeighboursKeepAway() {
        val day = (30_000L) * 86_400_000L
        val burst = (0 until 8).map { FramePhoto("b$it", "u", day + it * 20_000L) }   // 8 frames in under 3 minutes
        val pinned = burst[3]
        val result = Bursts.thin(burst, emptyMap(), sharpness = { null }, pinned = setOf(pinned.id))
        assertTrue(pinned.id in result.kept.map { it.id })
        val times = result.kept.map { it.takenMs }.sorted()
        assertTrue("no two kept frames within two minutes", times.zipWithNext().all { (a, b) -> b - a >= Bursts.MIN_SEPARATION_MS })
        assertFalse(result.kept.size > Bursts.KEEP)
    }

    @Test fun favoritesInOneBurstAreNotAllKept() {
        val day = 30_000L * 86_400_000L
        val burst = (0 until 8).map { FramePhoto("b$it", "u", day + it * 20_000L) }   // all within three minutes, all starred
        val result = Bursts.thin(burst, emptyMap(), pinned = burst.map { it.id }.toSet(), sharpness = { null })
        val times = result.kept.map { it.takenMs }.sorted()
        assertTrue("some are kept", result.kept.isNotEmpty())
        assertTrue("but never two of the same moment", times.zipWithNext().all { (a, b) -> b - a >= Bursts.MIN_SEPARATION_MS })
    }

    @Test fun aFavoriteSavedTwiceIsNotTheCopyThatIsDropped() {
        val a = FramePhoto("a", "u", 40_000L * 86_400_000L)
        val b = FramePhoto("b", "u", 41_000L * 86_400_000L)
        val hashes = mapOf("a" to 7L, "b" to 7L)
        val result = Bursts.thin(listOf(a, b), emptyMap(), hashes = hashes, sharpness = { null }, pinned = setOf("b"))
        assertTrue("b is the favorite and stays", "b" in result.kept.map { it.id })
    }

    @Test fun theSummaryIsInPlainWords() {
        assertEquals("About 12 a day; each one comes back about every 8 days.", FavoriteReport.sentence(100, 5, 240.0))
        assertEquals("About 12 a day; each one comes back about every 4 weeks.", FavoriteReport.sentence(300, 5, 240.0))
        assertEquals("About 24 a day; each one comes back about every 2 days.", FavoriteReport.sentence(50, 10, 240.0))
        assertEquals("a short list is shown once a day at most", "About 5 a day; each one comes back about every day.", FavoriteReport.sentence(5, 10, 240.0))
        assertEquals("Favorites are not shown more often.", FavoriteReport.sentence(100, 0, 240.0))
    }

    @Test fun theSettingMapsToAShareAndDefaultsToNowAndThen() {
        assertEquals(0, FavoriteShare.of("off").percent)
        assertEquals(5, FavoriteShare.of("some").percent)
        assertEquals(10, FavoriteShare.of("often").percent)
        assertEquals(FavoriteShare.SOMETIMES, FavoriteShare.of(null))
        assertEquals(FavoriteShare.SOMETIMES, FavoriteShare.of("junk"))
    }
}
