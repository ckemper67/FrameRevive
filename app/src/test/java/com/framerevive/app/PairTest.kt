package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class PairTest {
    private fun photo(id:Int,url:String="old")=FramePhoto(id.toString(),url,id.toLong(),0,0)
    private val landscape=CollageLayout.Size(1200,800)
    private val portrait=CollageLayout.Size(800,1200)
    private val fourByThree=CollageLayout.Size(1200,900)
    private val wide=CollageLayout.Size(1920,1080)

    @Test fun portraitFrameStacksTwoLandscapesAndFillsTheScreen() {
        val layout=CollageLayout.pair(1080,1920,landscape,landscape,portrait=true)
        val boxes=layout.boxes
        assertEquals(8f,boxes[0].left,0.01f);assertEquals(1072f,boxes[0].right,0.01f)
        assertEquals(boxes[0].left,boxes[1].left,0.01f)
        assertTrue(boxes[0].bottom<boxes[1].top)
        assertEquals(1920f,boxes[0].height+boxes[1].height+24f,0.05f)
        assertTrue(boxes[1].bottom<=1920f)
        assertEquals(0.2517f,layout.crop,0.002f)
    }
    @Test fun landscapeFrameSplitsTwoPortraitsSideBySide() {
        val layout=CollageLayout.pair(1920,1080,portrait,portrait,portrait=false)
        val boxes=layout.boxes
        assertEquals(boxes[0].top,boxes[1].top,0.01f)
        assertTrue(boxes[0].right<boxes[1].left)
        assertEquals(1920f,boxes[0].width+boxes[1].width+24f,0.05f)
        assertTrue(boxes[1].right<=1920f)
        assertEquals(0.2517f,layout.crop,0.002f)
    }
    @Test fun mixedAspectsShareOneCropAndOneZoom() {
        val layout=CollageLayout.pair(1080,1920,fourByThree,wide,portrait=true)
        val first=layout.boxes[0];val second=layout.boxes[1]
        assertEquals(first.width,second.width,0.01f)
        assertEquals(0.75f,second.height/first.height,0.001f)
        assertTrue(layout.crop>0f && layout.crop<=PairSelection.MAX_CROP)
    }
    @Test fun cropGrowsWithTheAspectMismatch() {
        val tight=CollageLayout.pair(1080,1920,fourByThree,fourByThree,portrait=true).crop
        val looser=CollageLayout.pair(1080,1920,landscape,landscape,portrait=true).crop
        val worst=CollageLayout.pair(1080,1920,wide,wide,portrait=true).crop
        assertTrue(tight<looser);assertTrue(looser<worst)
        assertEquals(0.1582f,tight,0.002f);assertEquals(0.3687f,worst,0.002f)
    }
    @Test fun nearlySquarePairsCropHeightInstead() {
        val square=CollageLayout.Size(1000,1050)
        val layout=CollageLayout.pair(1080,1920,square,square,portrait=true)
        assertTrue(layout.zoom<1f)
        assertTrue(layout.crop in 0f..PairSelection.MAX_CROP)
        assertEquals(1896f,layout.boxes[0].height+layout.boxes[1].height,0.05f)
    }
    @Test fun orientationAndPairabilityFollowTheFrame() {
        assertEquals(FrameOrientation.PORTRAIT,PairSelection.orientation(1080,1920))
        assertEquals(FrameOrientation.LANDSCAPE,PairSelection.orientation(1920,1080))
        assertTrue(PairSelection.pairable(FrameOrientation.PORTRAIT,landscape))
        assertFalse(PairSelection.pairable(FrameOrientation.PORTRAIT,portrait))
        assertTrue(PairSelection.pairable(FrameOrientation.LANDSCAPE,portrait))
        assertFalse(PairSelection.pairable(FrameOrientation.LANDSCAPE,landscape))
    }
    @Test fun plannerPairsTwoReadyLandscapesOnAPortraitFrame() {
        val upcoming=(1..4).map { photo(it) }
        val chosen=PairSelection.choose(1080,1920,upcoming,{ landscape },{ true },emptySet())
        assertEquals(listOf("1","2"),chosen.map { it.id })
        assertEquals(SlideMode.PAIR,FrameSlide(chosen,SlideMode.PAIR).mode)
    }
    @Test fun plannerLeavesMatchingPhotosAndLonelyOrUnreadyPartnersAlone() {
        assertTrue(PairSelection.choose(1080,1920,(1..3).map { photo(it) },{ portrait },{ true },emptySet()).isEmpty())
        assertTrue(PairSelection.choose(1080,1920,(1..3).map { photo(it) },{ landscape },{ it.id == "1" },emptySet()).isEmpty())
        assertTrue(PairSelection.choose(1080,1920,listOf(photo(1)),{ landscape },{ true },emptySet()).isEmpty())
        assertTrue(PairSelection.choose(1080,1920,(1..3).map { photo(it) },{ null },{ true },emptySet()).isEmpty())
    }
    @Test fun plannerAvoidsThePhotoOnScreenAndSkipsPastUnpairablePartners() {
        val upcoming=(1..4).map { photo(it) }
        val sizes=mapOf("1" to landscape,"2" to landscape,"3" to portrait,"4" to landscape)
        val chosen=PairSelection.choose(1080,1920,upcoming,{ sizes[it.id] },{ true },setOf("1"))
        assertEquals(listOf("2","4"),chosen.map { it.id })
        val noAvoid=PairSelection.choose(1080,1920,upcoming,{ sizes[it.id] },{ true },emptySet())
        assertEquals(listOf("1","2"),noAvoid.map { it.id })
    }
    @Test fun plannerRefusesPairsThatWouldCropTooMuch() {
        val panorama=listOf(photo(1),photo(2))
        assertTrue(PairSelection.choose(1080,1920,panorama,{ wide },{ true },emptySet()).isEmpty())
        assertTrue(PairSelection.choose(1080,1920,panorama,{ wide },{ true },emptySet(),maxCrop=0.40f).size==2)
        assertTrue(PairSelection.choose(1080,1920,listOf(photo(1),photo(2)),{ fourByThree },{ true },emptySet()).size==2)
    }
    @Test fun cacheRequestsASquareBoxAndNamesTheFileAfterIt() {
        assertEquals("=w1920-h1920",FrameCache.suffix(1080,1920))
        assertEquals("=w1920-h1920",FrameCache.suffix(1920,1080))
        assertEquals("=w640-h640",FrameCache.suffix(320,240))
        assertEquals("=w2560-h2560",FrameCache.suffix(4000,3000))
        assertNotEquals(FrameCache.name("abc",FrameCache.suffix(1080,1920)),FrameCache.name("abc",FrameCache.suffix(2560,1440)))
        assertEquals(FrameCache.name("abc","=w1920-h1920"),FrameCache.name("abc","=w1920-h1920"))
    }
    @Test fun pairsCountAsSinglesForTheCollageCadence() {
        val cadence=CollageCadence()
        repeat(19) { cadence.displayed(false) }
        assertFalse(cadence.due(20))
        val pair=FrameSlide(listOf(photo(1),photo(2)),SlideMode.PAIR)
        assertTrue(pair.pair);assertFalse(pair.collage)
        cadence.displayed(pair.collage)
        assertEquals(20,cadence.singles);assertTrue(cadence.due(20))
        assertEquals(2,cadence.nextCount)
        val collage=FrameSlide(listOf(photo(3),photo(4)),SlideMode.COLLAGE)
        assertTrue(collage.collage);assertFalse(collage.pair)
    }
}
