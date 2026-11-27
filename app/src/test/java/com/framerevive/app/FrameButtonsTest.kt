package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameButtonsTest {
    @Test fun shortPressesAndRepeatsProduceOneAction() {
        val b = FrameButtons()
        assertEquals(FrameButtons.Action.NEXT,b.down(24,100))
        assertEquals(FrameButtons.Action.NONE,b.down(24,200))
        assertEquals(FrameButtons.Action.NONE,b.up(24,900))
        assertEquals(FrameButtons.Action.NONE,b.up(24,1000))
        assertEquals(FrameButtons.Action.PREVIOUS,b.down(25,1100))
        assertEquals(FrameButtons.Action.NONE,b.up(25,1200))
        b.down(295,2000)
        assertEquals(FrameButtons.Action.TOGGLE_PAUSE,b.up(295,3600))
    }
    @Test fun holdOpensControlsWithoutTogglingOnRelease() {
        val b = FrameButtons(); b.down(295,100)
        assertEquals(FrameButtons.Action.NONE,b.hold(2599))
        assertEquals(FrameButtons.Action.CONTROLS,b.hold(2600))
        assertEquals(FrameButtons.Action.NONE,b.hold(4000))
        assertEquals(FrameButtons.Action.NONE,b.up(295,5100))
    }
    @Test fun delayedHoldAndCanceledLifecycleAreHandled() {
        val b = FrameButtons(); b.down(295,0)
        assertEquals(FrameButtons.Action.CONTROLS,b.up(295,5000))
        b.down(295,6000); b.cancel()
        assertEquals(FrameButtons.Action.NONE,b.up(295,6500))
        b.down(24,7000)
        assertEquals(FrameButtons.Action.NONE,b.up(24,7100,true))
    }
    @Test fun theStarButtonCanStarThePhotoInsteadOfPausing() {
        val b = FrameButtons(); b.shortPress = FrameButtons.Action.TOGGLE_FAVORITE
        b.down(295,100)
        assertEquals(FrameButtons.Action.TOGGLE_FAVORITE,b.up(295,600))
        // Holding still opens the controls, and releasing afterwards does nothing, in either mode.
        b.down(295,1000)
        assertEquals(FrameButtons.Action.CONTROLS,b.hold(3600))
        assertEquals(FrameButtons.Action.NONE,b.up(295,3700))
        // Plus and minus are unaffected.
        assertEquals(FrameButtons.Action.NEXT,b.down(24,4000))
    }
    @Test fun aShortStarPressClosesTheControlsOnlyWhileTheyAreOpen() {
        assertTrue(FrameButtons.closesControls(FrameButtons.Action.TOGGLE_PAUSE,true))
        assertTrue(FrameButtons.closesControls(FrameButtons.Action.TOGGLE_FAVORITE,true))
        assertFalse("with the controls closed a short press still pauses or stars",FrameButtons.closesControls(FrameButtons.Action.TOGGLE_PAUSE,false))
        // The release after the hold that opened them gives no action, so it does not close them again.
        val b=FrameButtons();b.down(295,0);assertEquals(FrameButtons.Action.CONTROLS,b.hold(2600))
        assertFalse(FrameButtons.closesControls(b.up(295,2700),true))
        // Plus and Minus keep their meaning.
        val c=FrameButtons();assertEquals(FrameButtons.Action.NEXT,c.down(24,0));assertFalse(FrameButtons.closesControls(c.up(24,100),true))
    }
    @Test fun theButtonsStayAwakeForTheWindowAfterATouchAndEachTouchRestartsIt() {
        val w=ButtonWake(8000)
        assertFalse("asleep until touched",w.awake(100))
        w.touch(1000)
        assertTrue(w.awake(1000));assertTrue(w.awake(8999))
        assertFalse(w.awake(9000))
        w.touch(5000)
        assertTrue("a later touch restarts the window",w.awake(12999));assertFalse(w.awake(13000))
    }
}
