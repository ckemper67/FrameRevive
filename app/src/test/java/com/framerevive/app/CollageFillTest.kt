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
    @Test fun threePicturesAreOneWideAndTwoTallOnAPortraitFrame() {
        val fill=CollageLayout.fill(1080,1920,listOf(landscape,portrait,portrait))
        assertNotNull(fill)
        val panels=fill!!.panels
        assertEquals(3,panels.size)
        // One picture across the whole width, the other two side by side in the other half.
        val wide=panels.single { it.box.width>1000f }
        assertEquals(wide.photo,0)
        val tall=panels.filter { it !== wide }
        assertEquals(1,tall.map { it.box.top }.distinct().size)
        assertEquals(2,tall.map { it.box.left }.distinct().size)
        assertTrue(fill.crop<=CollageLayout.MAX_CROP)
    }
    @Test fun threeLandscapesNoLongerStackIntoRows() {
        // The frame's original app has no stack of three: its three-picture layout is one and two, and the two
        // beside each other are tall, so three landscapes do not tile and are paired instead.
        assertNull(CollageLayout.fill(1080,1920,List(3) { landscape }))
    }
    @Test fun fivePicturesInterlockOnAPortraitFrame() {
        val fill=CollageLayout.fill(1080,1920,listOf(landscape,landscape,landscape,portrait,portrait))
        assertNotNull(fill)
        val panels=fill!!.panels
        assertEquals(5,panels.size)
        assertEquals((0 until 5).toSet(),panels.map { it.photo }.toSet())
        // Three wide slots (the strip and the two small cells) take the landscapes, two tall slots the portraits.
        val tall=panels.filter { it.box.height>it.box.width }
        assertEquals(2,tall.size)
        assertTrue(tall.all { it.photo>=3 })
        // The strip spans the screen and nothing lines up in a grid: the two columns' rows are offset.
        assertEquals(1,panels.count { it.box.width>1000f })
        assertTrue(panels.map { it.box.top }.distinct().size>=4)
        assertTrue(fill.crop<=CollageLayout.MAX_CROP)
    }
    @Test fun fivePicturesTurnOnTheirSideOnALandscapeFrame() {
        val fill=CollageLayout.fill(1920,1080,listOf(portrait,portrait,portrait,landscape,landscape))
        assertNotNull(fill)
        val panels=fill!!.panels
        assertEquals(5,panels.size)
        // The strip is now a column that spans the full height.
        assertEquals(1,panels.count { it.box.height>1000f })
        assertTrue(fill.crop<=CollageLayout.MAX_CROP)
    }
    @Test fun fiveFromOneCameraFitBecauseTheSmallCellsCanBeTaller() {
        // 4:3 landscapes in the original app's small cells lose 25.3 percent, a hair over the limit; a taller small
        // cell keeps them within it. A day's photos mostly come from one camera, so this is the common case.
        val fourByThreeTall=CollageLayout.Size(900,1200)
        val fill=CollageLayout.fill(1080,1920,listOf(fourByThree,fourByThree,fourByThree,fourByThreeTall,fourByThreeTall))
        assertNotNull(fill)
        assertTrue(fill!!.crop<=CollageLayout.MAX_CROP)
    }
    @Test fun fillTimeStaysSmallForTheLargestSearch() {
        // Five different shapes try every layout in every order. This is the most work one fit does; the frame is far
        // slower than the machine running the tests, so the budget is tight.
        val shapes=listOf(CollageLayout.Size(1200,900),CollageLayout.Size(1500,1000),CollageLayout.Size(1920,1080),CollageLayout.Size(900,1200),CollageLayout.Size(1000,1500))
        CollageLayout.fill(1080,1920,shapes)   // warm up the JIT
        val start=System.nanoTime()
        repeat(20) { CollageLayout.fill(1080,1920,shapes) }
        val millis=(System.nanoTime()-start)/20/1_000_000.0
        println("one five-photo fit: %.2f ms".format(millis))
        assertTrue("one fit took $millis ms",millis<1.0)
    }
    @Test fun fivePicturesOfOneShapeDoNotFit() {
        assertNull(CollageLayout.fill(1080,1920,List(5) { landscape }))
        assertNull(CollageLayout.fill(1080,1920,List(5) { portrait }))
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
        // Three portraits used to run as columns on a landscape frame; one and two leaves the pair tall in wide slots.
        assertNull(CollageLayout.fill(1920,1080,List(3) { portrait }))
    }
    @Test fun twoPhotosReuseThePairSplitSoBothLoseTheSameShare() {
        val fill=CollageLayout.fill(1080,1920,listOf(fourByThree,landscape))
        assertNotNull(fill)
        assertEquals(CollageLayout.pair(1080,1920,fourByThree,landscape,true).crop,fill!!.crop,0.0001f)
        assertTrue(fill.crop<=CollageLayout.MAX_CROP)
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
        // Landscapes alone make no collage of three or five on a portrait frame, but they pair up.
        assertEquals(2,CollageSelection.choose(5,1080,1920,ready,{ sizes[it.id] },{ true },emptySet()).size)
        // Three landscapes and two portraits among the candidates are the five the interlocking layout wants.
        val mixed=(1..6).map { photo(it) }
        val mixedSizes=mixed.associate { it.id to if (it.id.toInt() <= 3) landscape else portrait }
        assertEquals(5,CollageSelection.choose(5,1080,1920,mixed,{ mixedSizes[it.id] },{ true },emptySet()).size)
        // Portraits already fill a portrait frame, so they make no collage there; on a landscape frame they pair.
        val portraits=(1..6).map { photo(it) }
        val portraitSizes=portraits.associate { it.id to portrait }
        assertTrue(CollageSelection.choose(5,1080,1920,portraits,{ portraitSizes[it.id] },{ true },emptySet()).isEmpty())
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
