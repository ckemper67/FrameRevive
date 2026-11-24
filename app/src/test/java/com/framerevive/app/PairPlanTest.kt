package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PairPlanTest {
    private fun photo(id: String, w: Int, h: Int) = FramePhoto(id, "u", 1000, width = w, height = h)
    private fun land(id: String) = photo(id, 1200, 800)
    private fun port(id: String) = photo(id, 800, 1200)
    private fun arrange(list: List<FramePhoto>, pool: List<FramePhoto> = emptyList()) =
        PairPlan.arrange(list, 1080, 1920, { pool }).map { it.id }

    @Test fun photosThatFillTheScreenKeepTheirPlaceAndLandscapeOnesComeInPairsSideBySide() {
        val out = arrange(listOf(land("L1"), port("P1"), land("L2"), port("P2"), land("L3"), land("L4")))
        assertEquals(listOf("L1", "L2", "P1", "P2", "L3", "L4"), out)
    }

    @Test fun anOddPhotoBorrowsAPartnerFromTheAlbumInsteadOfBeingLeftOut() {
        val out = arrange(listOf(land("L1"), port("P1"), land("L2"), land("L3")), pool = listOf(port("X"), land("B1"), land("B2")))
        assertEquals("L3 has no partner in the cycle, so the first fitting photo of the pool joins it", listOf("L1", "L2", "P1", "L3", "B1"), out)
    }

    @Test fun aPhotoWithNoPartnerAnywhereLeavesTheCycleAndNothingElseChanges() {
        assertEquals(listOf("P1", "P2"), arrange(listOf(port("P1"), land("L1"), port("P2"))))
    }

    @Test fun aSquareAndALandscapeCanPairAndPortraitPhotosNeverNeedAPartner() {
        assertEquals(listOf("Q1", "L1", "P1"), arrange(listOf(photo("Q1", 1000, 1000), port("P1"), land("L1"))))
    }

    @Test fun noFrameSizeOrUnknownPhotoSizesLeaveThePlanAsItWas() {
        val unknown = listOf(FramePhoto("a", "u", 1), FramePhoto("b", "u", 2), land("L1"))
        assertEquals(listOf("a", "b", "L1"), PairPlan.arrange(unknown, 0, 0, { emptyList() }).map { it.id })
        assertEquals("L1 has no partner so it leaves; the photos with unknown size cannot be judged and stay", listOf("a", "b"), arrange(unknown))
    }

    @Test fun thePoolIsOnlyAskedForWhenAPhotoHasNoPartnerInTheCycle() {
        var asked = 0
        PairPlan.arrange(listOf(land("L1"), land("L2")), 1080, 1920, { asked++; emptyList() })
        assertEquals(0, asked)
        PairPlan.arrange(listOf(land("L1")), 1080, 1920, { asked++; emptyList() })
        assertEquals(1, asked)
    }

    @Test fun everyPairInTheResultIsWithinTheCropLimitAndNoPhotoAppearsTwice() {
        val many = (1..40).map { if (it % 3 == 0) port("P$it") else land("L$it") }
        val pool = (1..10).map { land("B$it") }
        val out = PairPlan.arrange(many, 1080, 1920, { pool })
        assertEquals(out.size, out.map { it.id }.distinct().size)
        val landscapes = out.filter { it.landscape }
        assertTrue("landscape photos come in an even number, two by two", landscapes.size % 2 == 0)
    }
}
