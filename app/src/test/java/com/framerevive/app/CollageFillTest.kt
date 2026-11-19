package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class CollageFillTest {
    private fun photo(id:Int)=FramePhoto(id.toString(),"old",id.toLong(),0,0)
    private val landscape=CollageLayout.Size(1200,800)
    private val portrait=CollageLayout.Size(800,1200)
    private val fourByThree=CollageLayout.Size(1200,900)
    private val wide=CollageLayout.Size(1920,1080)

    @Test fun cropMeasuresWhatACoveringCellTakes() {
        val tallCell=CollageLayout.Box(0f,0f,1064f,948f)
        assertEquals(0.2517f,CollageLayout.crop(tallCell,landscape),0.001f)
        assertEquals(0.2517f,CollageLayout.crop(CollageLayout.Box(0f,0f,948f,1064f),portrait),0.001f)
        assertEquals(0f,CollageLayout.crop(CollageLayout.Box(0f,0f,1200f,800f),landscape),0.0001f)
        assertTrue(CollageLayout.crop(CollageLayout.Box(0f,0f,528f,948f),landscape)>0.6f)
    }
    @Test fun threeLandscapesStackIntoRowsOnAPortraitFrame() {
        val fill=CollageLayout.fill(1080,1920,List(3) { landscape })
        assertNotNull(fill)
        val panels=fill!!.panels
        assertEquals(3,panels.size)
        assertEquals(1,panels.map { it.box.left }.distinct().size)
        assertEquals(3,panels.map { it.box.top }.distinct().size)
        assertEquals(1080f-panels[0].box.width,panels[0].box.left*2,0.01f)
        for(panel in panels) assertEquals(CollageLayout.crop(panel.box,landscape),fill.crop,0.0001f)
        assertTrue(fill.crop in 0.05f..0.20f)
    }
    @Test fun fourPortraitsUseTheGridOnAPortraitFrame() {
        val fill=CollageLayout.fill(1080,1920,List(4) { portrait })
        assertNotNull(fill)
        val panels=fill!!.panels
        assertEquals(2,panels.map { it.box.top }.distinct().size)
        assertEquals(2,panels.map { it.box.left }.distinct().size)
        assertEquals((0 until 4).toSet(),panels.map { it.photo }.toSet())
        assertTrue(fill.crop in 0.10f..0.20f)
    }
    @Test fun fourLandscapesCannotCoverAPortraitFrame() {
        assertNull(CollageLayout.fill(1080,1920,List(4) { landscape }))
        assertNull(CollageLayout.fill(1080,1920,List(3) { portrait }))
        assertNotNull(CollageLayout.fill(1920,1080,List(4) { landscape }))
        assertNotNull(CollageLayout.fill(1920,1080,List(3) { portrait }))
    }
    @Test fun twoPhotosReuseThePairSplitSoBothLoseTheSameShare() {
        val fill=CollageLayout.fill(1080,1920,listOf(fourByThree,wide))
        assertNotNull(fill)
        assertEquals(CollageLayout.pair(1080,1920,fourByThree,wide,true).crop,fill!!.crop,0.0001f)
        assertTrue(fill.crop<0.30f)
        assertNull(CollageLayout.fill(1080,1920,listOf(wide,wide)))
    }
    @Test fun decodeEdgeCoversTheCroppedCellButNotTheWholePanel() {
        val cell=CollageLayout.Box(0f,0f,1064f,948f)
        assertEquals(1422,CollageLayout.neededEdge(cell,landscape,cover=true))
        assertEquals(1064,CollageLayout.neededEdge(cell,landscape,cover=false))
        assertEquals(4096,CollageLayout.neededEdge(CollageLayout.Box(0f,0f,9000f,9000f),landscape,cover=false))
    }
    @Test fun selectionPicksTheCountThatFits() {
        val ready=(1..6).map { photo(it) }
        val sizes=ready.associate { it.id to landscape }
        val four=CollageSelection.choose(4,1080,1920,ready,{ sizes[it.id] },{ true },emptySet())
        assertEquals(3,four.size)
        val portraits=(1..6).map { photo(it) }
        val portraitSizes=portraits.associate { it.id to portrait }
        assertEquals(4,CollageSelection.choose(4,1080,1920,portraits,{ portraitSizes[it.id] },{ true },emptySet()).size)
        assertEquals(2,CollageSelection.choose(2,1920,1080,portraits,{ portraitSizes[it.id] },{ true },emptySet()).size)
    }
    @Test fun selectionSkipsPhotosOnScreenUnreadyOrOffFrameAndGivesUpWhenNothingFits() {
        val pool=(1..4).map { photo(it) }
        val sizes=pool.associate { it.id to landscape }
        val chosen=CollageSelection.choose(2,1080,1920,pool,{ sizes[it.id] },{ it.id!="1" },setOf("1"))
        assertEquals(listOf("2","3"),chosen.map { it.id })
        assertTrue(CollageSelection.choose(2,1080,1920,pool,{ sizes[it.id] },{ it.id=="1" },emptySet()).isEmpty())
        assertTrue(CollageSelection.choose(2,1080,1920,pool,{ null },{ true },emptySet()).isEmpty())
        val panoramas=(1..4).map { photo(it) }
        val wideSizes=panoramas.associate { it.id to wide }
        assertTrue(CollageSelection.choose(2,1080,1920,panoramas,{ wideSizes[it.id] },{ true },emptySet()).isEmpty())
        val frames=(1..4).map { photo(it) }
        val portraitSizes=frames.associate { it.id to portrait }
        assertTrue(CollageSelection.choose(2,1080,1920,frames,{ portraitSizes[it.id] },{ true },emptySet()).isEmpty())
    }
}
