package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameCacheTest {
    @Test fun namesAreStableSafeAndDistinct() {
        val suffix = FrameCache.suffix(1920, 1080)
        val id = "AF1QipPeZLsnNjtG9Mf0OfECG3JuoyKYv2oF3CIz6O0h"
        val a = FrameCache.name(id, suffix)
        assertEquals("the same photo must reuse its cache file", a, FrameCache.name(id, suffix))
        assertNotEquals("a different photo must not share a file", a, FrameCache.name(id.dropLast(1) + "i", suffix))
        assertTrue("plain file name: $a", a.matches(Regex("[0-9a-f]{32}\\.jpg")))
        // The requested box is part of the name, so a larger request is a different file.
        assertNotEquals(a, FrameCache.name(id, FrameCache.suffix(2560, 2560)))
    }
}
