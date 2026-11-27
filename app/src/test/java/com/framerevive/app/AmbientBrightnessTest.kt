package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientBrightnessTest {
    @Test fun theTableIsFollowedAtItsOwnPoints() {
        assertEquals(9f, AmbientBrightness.panelBacklight(0f), 0.01f)
        assertEquals(9f, AmbientBrightness.panelBacklight(5f), 0.01f)
        assertEquals(19f, AmbientBrightness.panelBacklight(8f), 0.01f)
        assertEquals(112f, AmbientBrightness.panelBacklight(80f), 0.01f)
        assertEquals(235f, AmbientBrightness.panelBacklight(290f), 0.01f)
        assertEquals(235f, AmbientBrightness.panelBacklight(5000f), 0.01f)
    }

    @Test fun valuesBetweenThePointsAreInterpolated() {
        assertEquals(14f, AmbientBrightness.panelBacklight(6.5f), 0.01f)
        assertEquals(108f, AmbientBrightness.panelBacklight(72.5f), 0.01f)
    }

    @Test fun aBrightRoomNeverPassesTheDaytimeSetting() {
        assertEquals(0.55f, AmbientBrightness.level(55, 5000f, true), 0.0001f)
    }

    @Test fun theOwnersLitEveningRoomIsWellBelowTheDaytimeSetting() {
        val level = AmbientBrightness.level(55, 78f, true)
        assertEquals(110f / 255f, level, 0.01f)
    }

    @Test fun aDarkRoomFallsToThePanelMinimum() {
        assertEquals(9f / 255f, AmbientBrightness.level(55, 0f, true), 0.0001f)
    }

    @Test fun dimmerRoomsNeverGetBrighterScreens() {
        var last = 0f
        for (lux in listOf(0f, 1f, 2f, 5f, 10f, 25f, 50f, 80f, 150f, 300f, 900f)) {
            val now = AmbientBrightness.level(100, lux, true)
            assertTrue("level fell at $lux lux", now >= last)
            last = now
        }
    }

    @Test fun aLowDaytimeSettingCapsTheLevelInABrightRoomAndTheDarkRoomStaysAtThePanelMinimum() {
        assertEquals(0.20f, AmbientBrightness.level(20, 400f, true), 0.0001f)
        assertEquals(9f / 255f, AmbientBrightness.level(5, 0f, true), 0.0001f)
    }

    @Test fun switchedOffOrWithoutAReadingTheFixedLevelIsUsed() {
        assertEquals(0.55f, AmbientBrightness.level(55, 0f, false), 0.0001f)
        assertEquals(0.55f, AmbientBrightness.level(55, null, true), 0.0001f)
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
}
