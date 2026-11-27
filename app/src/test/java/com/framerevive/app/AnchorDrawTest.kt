package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AnchorDrawTest {
    private val dayMs = 86_400_000L
    private val today = LocalDate.of(2026, 11, 27)
    private val todayMs = today.toEpochDay() * dayMs

    private fun on(id: String, date: LocalDate, minute: Int = 600) = FramePhoto(id, "u$id", date.toEpochDay() * dayMs + minute * 60_000L)
    private fun day(prefix: String, date: LocalDate, count: Int) = (0 until count).map { on("$prefix$it", date, 540 + it * 12) }
    /** One day of [count] photos, [yearsAgo] years before today, as a map the ranking takes. */
    private fun groups(vararg days: List<FramePhoto>) = days.associate { d -> DayIndex.dayOf(d.first()) to d }
    private val ages = listOf(0, 1, 2, 3, 5, 8, 12, 16)
    private fun yearsOfDays(count: Int = 8) = ages.map { y -> day("y$y-", today.minusYears(y.toLong()).minusDays(30), count) }

    private fun firstDays(groups: Map<Long, List<FramePhoto>>, draw: (Random) -> AnchorDraw, draws: Int = 800, seen: Map<String, PlayRecord> = emptyMap()): List<Long> {
        val random = Random(7)
        return (1..draws).map { CollageThemeSelection.rankedDays(groups, 5, emptySet(), seen = seen, draw = draw(random)).first().epochDay }
    }
    private fun yearOf(epochDay: Long) = LocalDate.ofEpochDay(epochDay).year

    @Test fun aRecentDrawUnderSomewhatLeansTowardsNewerDays() {
        val g = groups(*yearsOfDays().toTypedArray())
        val picked = firstDays(g, { AnchorDraw(true, Recency.SOMEWHAT, todayMs, it) }).map { yearOf(it) }
        val newer = picked.count { it >= 2024 }; val older = picked.count { it <= 2014 }
        assertTrue("newer $newer should far exceed older $older", newer > 4 * older)
    }

    @Test fun aPlainDrawIsSpreadAcrossAllTheYears() {
        val g = groups(*yearsOfDays().toTypedArray())
        val picked = firstDays(g, { AnchorDraw(false, Recency.SOMEWHAT, todayMs, it) }).groupingBy { yearOf(it) }.eachCount()
        assertEquals("every year comes up", 8, picked.size)
        assertTrue("none dominates: $picked", picked.values.max() < 0.25 * 800 && picked.values.min() > 0.05 * 800)
    }

    @Test fun anEvenRecencyDrawsRecentLikePlain() {
        val g = groups(*yearsOfDays().toTypedArray())
        val picked = firstDays(g, { AnchorDraw(true, Recency.EVEN, todayMs, it) }).groupingBy { yearOf(it) }.eachCount()
        assertEquals(8, picked.size)
        assertTrue(picked.values.max() < 0.25 * 800)
    }

    @Test fun aRecentDrawUnderNewestTakesOnlyTheNewestDays() {
        // 30 days, one a week apart going back; the newest window is 20 of them.
        val days = (0 until 30).map { day("w$it-", today.minusDays(7L * it + 3), 8) }
        val newest = days.take(20).map { DayIndex.dayOf(it.first()) }.toSet()
        val picked = firstDays(groups(*days.toTypedArray()), { AnchorDraw(true, Recency.NEWEST, todayMs, it) })
        assertTrue(picked.all { it in newest })
        assertTrue("and it varies", picked.toSet().size > 10)
    }

    @Test fun aDayWithFewerUnseenPhotosIsNeverPreferredEvenWhenItIsTheNewest() {
        // The newest day has only two photos, so it cannot fill five: it is outside the best class however recent it is.
        val thin = day("thin", today.minusDays(2), 2)
        val others = (1..6).map { day("o$it-", today.minusDays(40L * it), 8) }
        val picked = firstDays(groups(thin, *others.toTypedArray()), { AnchorDraw(true, Recency.SOMEWHAT, todayMs, it) })
        assertTrue(picked.none { it == DayIndex.dayOf(thin.first()) })
    }

    @Test fun aDayAlreadyShownFromIsNotDrawnWhileAFullyUnseenDayExists() {
        val fresh = day("fresh", today.minusDays(100), 10); val used = day("used", today.minusDays(200), 10)
        val seen = used.take(7).associate { it.id to PlayRecord(1L, 1) }
        val picked = firstDays(groups(fresh, used), { AnchorDraw(false, Recency.SOMEWHAT, todayMs, it) }, seen = seen)
        val freshDay = DayIndex.dayOf(fresh.first())
        assertTrue("only the unseen day", picked.all { it == freshDay })
    }

    @Test fun theSameSeedGivesTheSameDraws() {
        val g = groups(*yearsOfDays().toTypedArray())
        val a = firstDays(g, { AnchorDraw(true, Recency.SOMEWHAT, todayMs, it) }, 50)
        val b = firstDays(g, { AnchorDraw(true, Recency.SOMEWHAT, todayMs, it) }, 50)
        assertEquals(a, b)
    }

    @Test fun theSliderSetsTheChanceOfARecentDrawAtTheExtremes() {
        val random = Random(3)
        assertTrue((1..200).all { AnchorDraw.flip(100, Recency.SOMEWHAT, todayMs, random).recent })
        assertTrue((1..200).none { AnchorDraw.flip(0, Recency.SOMEWHAT, todayMs, random).recent })
        val half = (1..2000).count { AnchorDraw.flip(50, Recency.SOMEWHAT, todayMs, random).recent }
        assertTrue("about half: $half", half in 900..1100)
    }

    @Test fun withoutADrawTheRankingIsUnchanged() {
        val g = groups(*yearsOfDays().toTypedArray())
        val plain = CollageThemeSelection.rankedDays(g, 5, emptySet())
        // Today's ranking: the biggest day, then the more recent one; all days here are equal, so the newest.
        assertEquals(DayIndex.dayOf(yearsOfDays().first().first()), plain.first().epochDay)
    }
}
