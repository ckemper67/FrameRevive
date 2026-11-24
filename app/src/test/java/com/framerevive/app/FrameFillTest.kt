package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameFillTest {
    @Test fun aPortraitPhotoOnAPortraitFrameFillsItWithALittleCropped() {
        // 2:3 on a 9:16 frame: about 16% of its width is cut.
        assertEquals(0.156f, FrameFill.crop(2000, 3000, 1080, 1920), 0.005f)
        assertTrue(FrameFill.fills(2000, 3000, 1080, 1920))
        assertTrue("a photo of the frame's own shape needs no crop", FrameFill.crop(1080, 1920, 1080, 1920) < 0.0001f)
    }

    @Test fun aLandscapePhotoOnAPortraitFrameIsNeverCroppedToFillIt() {
        // 3:2 on 9:16 would lose 62 percent of its width.
        assertEquals(0.625f, FrameFill.crop(3000, 2000, 1080, 1920), 0.005f)
        assertFalse(FrameFill.fills(3000, 2000, 1080, 1920))
    }

    @Test fun theLimitIsTheSameAsForAPairOrACollageTile() {
        assertEquals(CollageLayout.MAX_CROP, FrameFill.MAX_CROP)
        assertTrue("a square photo loses 44 percent, which is over the limit", !FrameFill.fills(1000, 1000, 1080, 1920))
        assertTrue("4:5 loses 29 percent, which is within it", FrameFill.fills(1600, 2000, 1080, 1920))
    }

    @Test fun anUnknownSizeIsNeverCropped() {
        assertFalse(FrameFill.fills(0, 0, 1080, 1920)); assertFalse(FrameFill.fills(1000, 1000, 0, 0))
    }
}
