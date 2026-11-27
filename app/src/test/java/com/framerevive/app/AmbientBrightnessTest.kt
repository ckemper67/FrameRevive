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

    @Test fun aLampSwitchedOnIsBelievedGraduallyNotAtOnce() {
        val smoother = AmbientBrightness.Smoother()
        smoother.add(2f, 0L)
        val after1s = smoother.add(300f, 1_000L)
        assertTrue(after1s < 100f)
        val after60s = smoother.add(300f, 61_000L)
        assertEquals(300f, after60s, 5f)
    }

    @Test fun theGlideMovesTowardTheTargetWithoutOvershooting() {
        var shown = 0.10f
        var last = shown
        repeat(100) {
            shown = AmbientBrightness.glide(shown, 0.40f, 40L)
            assertTrue(shown >= last && shown <= 0.40f)
            last = shown
        }
        assertEquals(0.40f, shown, 0f)
    }

    @Test fun theGlideTakesAboutASecondForMostOfAMove() {
        var shown = 0.10f
        repeat(25) { shown = AmbientBrightness.glide(shown, 0.40f, 40L) } // one second
        assertTrue("was $shown", shown > 0.35f && shown < 0.40f)
    }

    @Test fun theGlideSnapsWhenWithinOneBacklightStep() {
        assertEquals(0.40f, AmbientBrightness.glide(0.399f, 0.40f, 40L), 0f)
    }

    @Test fun noTimePassedMeansNoMovement() {
        assertEquals(0.10f, AmbientBrightness.glide(0.10f, 0.40f, 0L), 0.0001f)
    }

    @Test fun theDiagnosticsLineShowsTheTableTheRoomTheScreenAndTheDaytimeSetting() {
        assertEquals(
            "Ambient brightness: on; table device table, 5 points; room 78 lux now, 75 lux smoothed; screen 43% of a 55% daytime setting",
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
        val held = 10f / 255f
        assertEquals(held, AmbientBrightness.settle(held, 11f / 255f), 0f)
        assertEquals(held, AmbientBrightness.settle(held, 9f / 255f), 0f)
        assertEquals(13f / 255f, AmbientBrightness.settle(held, 13f / 255f), 0f)
        assertEquals(7f / 255f, AmbientBrightness.settle(held, 7f / 255f), 0f)
    }

    @Test fun theBandGrowsWithTheLevelSoABrightRoomIsNotStuckOnTinySteps() {
        val held = 100f / 255f
        assertEquals(held, AmbientBrightness.settle(held, 110f / 255f), 0f)
        assertEquals(120f / 255f, AmbientBrightness.settle(held, 120f / 255f), 0f)
    }

    @Test fun nothingHeldTakesTheWantedLevel() {
        assertEquals(0.3f, AmbientBrightness.settle(null, 0.3f), 0f)
    }

    @Test fun theNoisySensorOfADimRoomMovesTheScreenOnlyAFewTimes() {
        // Whole-lux readings from a real sensor in a dim room (5 per second): 4 to 7 lux with a few outliers.
        val readings = listOf(5, 6, 4, 5, 6, 5, 4, 6, 4, 6, 4, 6, 4, 5, 1, 7, 5, 6, 5, 6, 5, 4, 5, 4, 5, 6, 5, 7, 4, 7, 5,
            2, 4, 6, 8, 5, 4, 7, 6, 7, 4, 6, 4, 5, 4, 5, 4, 6, 5, 6)
        val smoother = AmbientBrightness.Smoother()
        var held: Float? = null
        var changesHeld = 0
        var changesRaw = 0
        var lastRaw: Float? = null
        readings.forEachIndexed { i, lux ->
            smoother.add(lux.toFloat(), i * 200L)
            val wanted = AmbientBrightness.level(55, smoother.lux, true, table)
            if (lastRaw != null && lastRaw != wanted) changesRaw++
            lastRaw = wanted
            val next = AmbientBrightness.settle(held, wanted)
            if (held != null && held != next) changesHeld++
            held = next
        }
        assertTrue("held $changesHeld, raw $changesRaw", changesHeld <= 3 && changesRaw > 3 * changesHeld.coerceAtLeast(1))
    }
}
