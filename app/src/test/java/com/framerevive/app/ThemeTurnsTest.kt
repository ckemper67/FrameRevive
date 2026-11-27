package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class ThemeTurnsTest {
    private val all = listOf(CollageThemes.DAY, CollageThemes.OUTING, CollageThemes.ANNIVERSARY)
    private fun sequence(themes: List<CollageTheme>, recentPercent: Int, turns: Int): List<String> {
        val t = ThemeTurns()
        return (1..turns).map { val next = t.order(themes, recentPercent).first(); t.fired(next); next.id }
    }

    @Test fun allMemoriesGivesEveryThemeTheSameShareAndTheTurnsGoRound() {
        assertEquals(listOf("day", "outing", "anniversary", "day", "outing", "anniversary"), sequence(all, 0, 6))
    }

    @Test fun halfMemoriesGivesOnThisDayHalfAsManyTurns() {
        val seq = sequence(all, 50, 100)
        // Weights 1, 1 and 0.5 give 20 percent of the turns to On this day.
        assertEquals(20.0, 100.0 * seq.count { it == "anniversary" } / seq.size, 3.0)
        assertTrue(seq.count { it == "day" } in 38..42 && seq.count { it == "outing" } in 38..42)
    }

    @Test fun allRecentNeverPicksOnThisDayWhileOtherThemesAreOn() {
        val t = ThemeTurns()
        assertEquals(setOf("day", "outing"), t.order(all, 100).map { it.id }.toSet())
        assertTrue(sequence(all, 100, 20).none { it == "anniversary" })
        assertTrue("not even as a fallback", ThemeTurns().order(all, 100).none { it.id == "anniversary" })
    }

    @Test fun onThisDayAloneStillFiresAtAllRecent() {
        assertEquals(listOf("anniversary"), ThemeTurns().order(listOf(CollageThemes.ANNIVERSARY), 100).map { it.id })
    }

    @Test fun theOtherThemesStillTakeTurnsAtAnySlider() {
        for (recent in listOf(0, 25, 50, 75, 100)) {
            val seq = sequence(listOf(CollageThemes.DAY, CollageThemes.OUTING), recent, 10)
            assertEquals("day and outing alternate at $recent", listOf("day", "outing"), seq.take(2))
            assertEquals(5, seq.count { it == "day" })
        }
    }

    @Test fun theSequenceIsDeterministicAndASettingChangeStartsItAfresh() {
        assertEquals(sequence(all, 50, 30), sequence(all, 50, 30))
        val t = ThemeTurns()
        repeat(5) { t.fired(t.order(all, 50).first()) }
        val afterChange = t.order(all, 75).first().id
        assertEquals(ThemeTurns().order(all, 75).first().id, afterChange)
    }

    @Test fun firstInTheOrderIsTheOneWhoseTurnItIsAndTheRestAreFallbacks() {
        val t = ThemeTurns()
        val order = t.order(all, 0)
        assertEquals(3, order.size)
        t.fired(order.first())
        assertNotEquals(order.first().id, t.order(all, 0).first().id)
    }
}
