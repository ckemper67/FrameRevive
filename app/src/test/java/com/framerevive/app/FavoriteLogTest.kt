package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteLogTest {
    @Test fun countsPerDayAndOverAWindowAndInTotal() {
        val log = FavoriteLog()
        log.record(100); log.record(100, 2); log.record(98); log.record(90, 5)
        assertEquals(3, log.on(100)); assertEquals(0, log.on(99))
        assertEquals("today and the six days before it", 4, log.lastDays(100, 7))
        assertEquals(9, log.total()); assertEquals(90L, log.firstDay())
        log.record(100, 0); assertEquals("nothing to add", 3, log.on(100))
    }

    @Test fun survivesARestartAndIgnoresDamagedLines() {
        val log = FavoriteLog(); log.record(100, 3); log.record(101)
        val file = java.io.File.createTempFile("shown", ".tsv").apply { deleteOnExit() }
        file.writeText(log.export() + "junk\n5\tx\n7\n")
        val back = FavoriteLog().apply { load(file) }
        assertEquals(4, back.total()); assertEquals(3, back.on(100)); assertEquals(1, back.on(101))
    }

    @Test fun theSentenceSaysWhatWasShown() {
        assertTrue(FavoriteLog().sentence(100, 0).startsWith("No favorite has been shown"))
        val log = FavoriteLog(); log.record(100, 2); log.record(90, 4)
        val text = log.sentence(100, 5)
        assertTrue("a day ten days back is outside the week: $text", text.contains("2 today, 2 in the last 7 days, 6 since") && text.endsWith("(5 different ones)."))
    }
}
