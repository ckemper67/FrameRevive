package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientBrightnessTest {
    // A table in Android's format (config_autoBrightnessLevels and config_autoBrightnessLcdBacklightValues): the
    // backlight is 10 in the dark, 40 from 10 lux, 90 from 30, 160 from 100 and 235 from 300 lux.
    private val levels = intArrayOf(10, 30, 100, 300)
    private val values = intArrayOf(10, 40, 90, 160, 235)
    private val table = AmbientBrightness.Curve.fromDevice(levels, values)!!

    @Test fun theTableIsFollowedAtItsOwnPoints() {
        assertEquals(10f, table.backlight(0f), 0.01f)
        assertEquals(40f, table.backlight(10f), 0.01f)
        assertEquals(90f, table.backlight(30f), 0.01f)
        assertEquals(160f, table.backlight(100f), 0.01f)
        assertEquals(235f, table.backlight(300f), 0.01f)
        assertEquals(235f, table.backlight(5000f), 0.01f)
    }

    @Test fun valuesBetweenThePointsAreInterpolated() {
        assertEquals(65f, table.backlight(20f), 0.01f)
        assertEquals(125f, table.backlight(65f), 0.01f)
    }

    @Test fun aTableInAndroidsFormatIsReadWithItsSource() {
        assertEquals("device table", table.source)
        assertEquals(5, table.size)
    }

    @Test fun aBrightRoomNeverPassesTheDaytimeSetting() {
        assertEquals(0.55f, AmbientBrightness.level(55, 5000f, true, table), 0.0001f)
    }

    @Test fun aLitRoomGetsTheLevelOfTheTable() {
        assertEquals(125f / 255f, AmbientBrightness.level(55, 65f, true, table), 0.0001f)
    }

    @Test fun aRoomWithTheLightsOutStaysAtTheTablesLowestLevelAndTheScreenIsNeverOff() {
        assertEquals(10f / 255f, AmbientBrightness.level(55, 0f, true, table), 0.0001f)
        assertTrue(AmbientBrightness.level(55, 0f, true, table) > 0f)
    }

    @Test fun dimmerRoomsNeverGetBrighterScreens() {
        var last = 0f
        for (lux in listOf(0f, 1f, 2f, 5f, 10f, 25f, 50f, 80f, 150f, 300f, 900f)) {
            val now = AmbientBrightness.level(100, lux, true, table)
            assertTrue("level fell at $lux lux", now >= last)
            last = now
        }
    }

    @Test fun aLowDaytimeSettingCapsTheLevelInABrightRoomAndADarkRoomStaysAtTheTablesLowestLevel() {
        assertEquals(0.20f, AmbientBrightness.level(20, 400f, true, table), 0.0001f)
        assertEquals(10f / 255f, AmbientBrightness.level(5, 0f, true, table), 0.0001f)
    }

    @Test fun switchedOffOrWithoutAReadingTheFixedLevelIsUsed() {
        assertEquals(0.55f, AmbientBrightness.level(55, 0f, false, table), 0.0001f)
        assertEquals(0.55f, AmbientBrightness.level(55, null, true, table), 0.0001f)
    }

    @Test fun withoutATableTheFixedDaytimeLevelIsUsed() {
        assertEquals(0.55f, AmbientBrightness.level(55, 5f, true, null), 0.0001f)
    }

    @Test fun aTableThatIsEmptyTheWrongLengthOrNotARisingCurveIsRefused() {
        assertNull(AmbientBrightness.Curve.fromDevice(intArrayOf(), intArrayOf(0)))
        assertNull(AmbientBrightness.Curve.fromDevice(intArrayOf(10, 20), intArrayOf(10, 20)))
        assertNull(AmbientBrightness.Curve.fromDevice(intArrayOf(20, 10), intArrayOf(10, 20, 30)))
        assertNull(AmbientBrightness.Curve.fromDevice(intArrayOf(10, 10), intArrayOf(10, 20, 30)))
        assertNull(AmbientBrightness.Curve.fromDevice(intArrayOf(10, 20), intArrayOf(30, 20, 40)))
        assertNull(AmbientBrightness.Curve.fromDevice(intArrayOf(10, 20), intArrayOf(10, 300, 400)))
        assertNull(AmbientBrightness.Curve.fromDevice(intArrayOf(10, 20), intArrayOf(50, 50, 50)))
    }

    // The sensor reads about 5 times a second.
    private fun feed(estimator: AmbientBrightness.Estimator, from: Long, seconds: Int, lux: (Int) -> Float): Long {
        var t = from
        repeat(seconds * 5) { estimator.add(lux(it), t); t += 200 }
        return t
    }

    @Test fun theFirstReadingIsTakenAsItIs() {
        val estimator = AmbientBrightness.Estimator()
        assertEquals(null, estimator.lux)
        assertTrue(estimator.add(42f, 0L))
        assertEquals(42f, estimator.lux!!, 0.01f)
    }

    @Test fun aBrighterRoomIsBelievedOnlyAfterThreeSecondsOfIt() {
        val estimator = AmbientBrightness.Estimator()
        val t0 = feed(estimator, 0L, 12) { 10f }
        assertEquals(10f, estimator.lux!!, 0.01f)
        var t = t0
        while (t < t0 + 2_800) { estimator.add(100f, t); t += 200 }
        assertEquals("believed too soon", 10f, estimator.lux!!, 0.01f)
        while (t < t0 + 3_400) { estimator.add(100f, t); t += 200 }
        assertTrue("not believed after 3 seconds, ${estimator.lux}", estimator.lux!! > 20f)
    }

    @Test fun aSingleStrayReadingChangesNothing() {
        val estimator = AmbientBrightness.Estimator()
        var t = feed(estimator, 0L, 12) { 10f }
        estimator.add(1000f, t); t += 200
        feed(estimator, t, 10) { 10f }
        assertEquals(10f, estimator.lux!!, 0.01f)
    }

    @Test fun aRiseOfTenPercentIsBelievedButAFallOfFifteenPercentIsNot() {
        val up = AmbientBrightness.Estimator()
        feed(up, feed(up, 0L, 12) { 100f }, 12) { 115f }
        assertTrue("a rise past the brightening share is believed, ${up.lux}", up.lux!! > 100f)
        val down = AmbientBrightness.Estimator()
        feed(down, feed(down, 0L, 12) { 100f }, 12) { 85f }
        assertEquals("a fall short of the darkening share is not", 100f, down.lux!!, 0.01f)
    }

    @Test fun aFallPastTwentyPercentIsBelievedAfterThreeSeconds() {
        val estimator = AmbientBrightness.Estimator()
        feed(estimator, feed(estimator, 0L, 12) { 100f }, 12) { 60f }
        assertTrue("was ${estimator.lux}", estimator.lux!! < 80f)
    }

    @Test fun readingsThatStopComingAreStillBelievedWhenTheEstimateIsUpdated() {
        val estimator = AmbientBrightness.Estimator()
        val t0 = feed(estimator, 0L, 12) { 10f }
        estimator.add(100f, t0)
        var t = t0
        while (t < t0 + 4_000) { t += 250; estimator.update(t) }
        assertTrue("was ${estimator.lux}", estimator.lux!! > 20f)
    }

    @Test fun theNoisySensorOfADimRoomOrALampChangesTheEstimateNotAtAll() {
        // Whole-lux readings from a real sensor, 5 per second: a dim room of 4 to 7 lux, and a small lamp of about 3.
        val dim = listOf(5, 6, 4, 5, 6, 5, 4, 6, 4, 6, 4, 6, 4, 5, 1, 7, 5, 6, 5, 6, 5, 4, 5, 4, 5, 6, 5, 7, 4, 7, 5,
            2, 4, 6, 8, 5, 4, 7, 6, 7, 4, 6, 4, 5, 4, 5, 4, 6, 5, 6)
        val lamp = listOf(3, 4, 3, 4, 3, 2, 4, 2, 4, 3, 2, 3, 1, 3, 0, 2, 4, 3, 2, 3, 4, 3, 5, 4, 2, 4, 3, 2, 4, 3, 4, 2, 3, 2,
            3, 4, 5, 4, 3, 2, 3, 0, 3, 4, 5, 2, 3, 5, 3, 5)
        for (readings in listOf(dim, lamp)) {
            val estimator = AmbientBrightness.Estimator()
            var changes = 0
            var t = 0L
            repeat(6) { round -> readings.forEach { if (estimator.add(it.toFloat(), t) && !(round == 0 && t == 0L)) changes++; t += 200 } }
            assertEquals("changes for $readings", 0, changes)
        }
    }

    @Test fun aSmallLampAboutThreeLuxBringsTheEstimateUpAfterTheDarkAndKeepsItThere() {
        val lamp = listOf(3, 4, 3, 4, 3, 2, 4, 2, 4, 3, 2, 3, 1, 3, 0, 2, 4, 3, 2, 3, 4, 3, 5, 4, 2, 4, 3, 2, 4, 3, 4, 2, 3, 2,
            3, 4, 5, 4, 3, 2, 3, 0, 3, 4, 5, 2, 3, 5, 3, 5)
        val estimator = AmbientBrightness.Estimator()
        var t = feed(estimator, 0L, 20) { listOf(0f, 1f, 0f, 2f)[it % 4] }
        val dark = estimator.lux!!
        var litAt = -1L
        for (i in 0 until 600) {
            estimator.add(lamp[i % lamp.size].toFloat(), t)
            if (litAt < 0 && estimator.lux!! >= 2f) litAt = t
            if (litAt >= 0) assertTrue("fell back to ${estimator.lux} at $t", estimator.lux!! >= 1.5f)
            t += 200
        }
        assertTrue("dark room estimate $dark", dark < 1.5f)
        assertTrue("never came up", litAt >= 0)
    }

    @Test fun theScreenGetsMostOfTheWayWithinASecondAndEquallyFastBothWays() {
        var up = 0.10f
        repeat(25) { up = AmbientBrightness.glide(up, 0.90f, 40L) } // one second
        var down = 0.90f
        repeat(25) { down = AmbientBrightness.glide(down, 0.10f, 40L) }
        // One time constant is 0.3 s, so a second covers about 96% of the distance.
        assertTrue("up $up", up > 0.10f + 0.80f * 0.95f)
        assertTrue("down $down", down < 0.90f - 0.80f * 0.95f)
        assertEquals(up - 0.10f, 0.90f - down, 0.001f)
    }

    @Test fun theGlideNeverOvershootsAndArrivesAtTheTarget() {
        var shown = 0.10f
        repeat(100) {
            shown = AmbientBrightness.glide(shown, 0.40f, 40L)
            assertTrue(shown <= 0.40f)
        }
        assertEquals(0.40f, shown, 0f)
        repeat(300) {
            shown = AmbientBrightness.glide(shown, 0.20f, 40L)
            assertTrue(shown >= 0.20f)
        }
        assertEquals(0.20f, shown, 0f)
    }

    @Test fun theGlideSnapsWhenWithinOneBacklightStep() {
        assertEquals(0.40f, AmbientBrightness.glide(0.399f, 0.40f, 40L), 0f)
    }

    @Test fun noTimePassedMeansNoMovement() {
        assertEquals(0.10f, AmbientBrightness.glide(0.10f, 0.40f, 0L), 0.0001f)
    }

    @Test fun theDiagnosticsLineShowsTheTableTheRoomTheScreenAndTheDaytimeSetting() {
        assertEquals(
            "Ambient brightness: on; table device table, 5 points; room 78 lux now, 75 lux estimated; screen 43% of a 55% daytime setting",
            AmbientBrightness.report(true, table, 78.4f, 75.2f, 0.43f, 55),
        )
    }

    @Test fun theDiagnosticsLineSaysSoWhenThereIsNoTableNoReadingOrItIsOff() {
        assertEquals(
            "Ambient brightness: off; table none, fixed level; room no reading, -; screen - of a 20% daytime setting",
            AmbientBrightness.report(false, null, null, null, null, 20),
        )
    }

    @Test fun aSmallWobbleInTheWantedLevelIsHeldAndARealChangeIsFollowed() {
        val held = 100f / 255f
        assertEquals(held, AmbientBrightness.settle(held, 105f / 255f), 0f)
        assertEquals(held, AmbientBrightness.settle(held, 85f / 255f), 0f)
        assertEquals(111f / 255f, AmbientBrightness.settle(held, 111f / 255f), 0f)
        assertEquals(79f / 255f, AmbientBrightness.settle(held, 79f / 255f), 0f)
    }

    @Test fun theScreenBrightensOnTenPercentAndDarkensOnTwentyPercent() {
        val held = 100f / 255f
        assertEquals(held, AmbientBrightness.settle(held, 109f / 255f), 0f)
        assertEquals(110.5f / 255f, AmbientBrightness.settle(held, 110.5f / 255f), 0f)
        assertEquals(held, AmbientBrightness.settle(held, 81f / 255f), 0f)
        assertEquals(79.5f / 255f, AmbientBrightness.settle(held, 79.5f / 255f), 0f)
    }

    @Test fun nothingHeldTakesTheWantedLevel() {
        assertEquals(0.3f, AmbientBrightness.settle(null, 0.3f), 0f)
    }
}
