package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class CollageTest {
    private fun photo(id:Int,url:String="old")=FramePhoto(id.toString(),url,id.toLong(),0,0)
    @Test fun cadenceRequiresTwentyCompletedSinglesThenResets() {
        val c=CollageCadence()
        repeat(19) { c.displayed(false) };assertFalse(c.due(20))
        c.displayed(false);assertTrue(c.due(20))
        repeat(8) { c.displayed(false) };assertTrue(c.due(20))
        c.displayed(true);assertFalse(c.due(20));assertEquals(0,c.singles)
    }
    @Test fun collageWaitsForAllFiveAndAvoidsRepeatedCurrentPhoto() {
        val list=(1..6).map { photo(it) }
        assertTrue(CollageCadence.candidates(list,listOf(photo(1))) { it.id != "4" }.isEmpty())
        assertEquals(listOf("2","3","4","5","6"),CollageCadence.candidates(list,listOf(photo(1))) { true }.map { it.id })
        assertTrue(CollageCadence.candidates(listOf(photo(1),photo(1),photo(2),photo(3)),emptyList()) { true }.isEmpty())
    }
    @Test fun cellsHaveGuttersAndFitKeepsEveryEdgeVisible() {
        val cells=CollageLayout.cells(1920,1080)
        assertEquals(4,cells.size);assertTrue(cells[0].right < cells[1].left);assertTrue(cells[0].bottom < cells[2].top)
        for(cell in cells) {
            for((w,h) in listOf(400 to 1200,1800 to 500)) {
                val fit=CollageLayout.fit(w,h,cell)
                assertTrue(fit.left>=cell.left-0.01 && fit.top>=cell.top-0.01 && fit.right<=cell.right+0.01 && fit.bottom<=cell.bottom+0.01)
                assertEquals(w.toFloat()/h,fit.width/fit.height,0.001f)
            }
        }
    }
    @Test fun collageHistoryRetainsMembershipAndRefreshesUrls() {
        val h=PhotoHistory<FrameSlide> { it.id }
        val single=FrameSlide(listOf(photo(1)));val collage=FrameSlide((2..5).map { photo(it) })
        h.record(single);h.record(collage);h.record(FrameSlide(listOf(photo(6))))
        val fresh=(1..6).map { photo(it,"fresh") }.associateBy { it.id }
        h.transform { old -> FrameSlide(old.photos.map { fresh.getValue(it.id) }) }
        val back=h.previous()!!;assertEquals(collage.id,back.id);assertTrue(back.photos.all { it.url == "fresh" })
        h.record(back,true);assertEquals(single.id,h.previous()!!.id)
        h.transform { if(it.collage) null else it };assertNull(h.previous());assertEquals("6",h.next()!!.photos.first().id)
    }
    @Test fun collageSizesCycleOnlyAfterSuccessfulAutomaticCollages() {
        val c=CollageCadence()
        assertEquals("a collage aims for the most photos every time",5,CollageCadence.MAX_PHOTOS)
        assertEquals(2,CollageCadence.MIN_PHOTOS)
        repeat(20) { c.displayed(false) };assertEquals(20,c.singles)
        c.displayed(true);assertEquals(0,c.singles)
        repeat(5) { c.displayed(false) };c.reset();assertEquals(0,c.singles)
    }
    @Test fun candidatesRequireExactlyTheRequestedNumberReady() {
        for(count in 2..5) {
            assertEquals(count,CollageCadence.candidates((1..8).map { photo(it) },emptyList(),count) { true }.size)
            assertTrue(CollageCadence.candidates((1..8).map { photo(it) },emptyList(),count) { it.id != count.toString() }.isEmpty())
            assertTrue(FrameSlide((1..count).map { photo(it) }).collage)
        }
        assertFalse(FrameSlide(listOf(photo(1))).collage)
    }
    @Test fun twoPortraitsUseTallSideBySidePanels() {
        val panels=CollageLayout.arrange(1920,1080,List(2) { CollageLayout.Size(600,1000) })
        assertEquals(panels[0].box.top,panels[1].box.top,0.01f)
        assertTrue(panels[0].box.right<panels[1].box.left)
        assertTrue(panels.all { it.box.height>it.box.width })
    }
    @Test fun twoWideLandscapesUseStackedPanels() {
        val panels=CollageLayout.arrange(1920,1080,List(2) { CollageLayout.Size(1800,500) })
        assertEquals(panels[0].box.left,panels[1].box.left,0.01f)
        assertTrue(panels[0].box.bottom<panels[1].box.top)
    }
    @Test fun fourLandscapePhotosPreferTwoByTwoGrid() {
        val panels=CollageLayout.arrange(1920,1080,List(4) { CollageLayout.Size(1600,900) })
        assertEquals(2,panels.map { it.box.top }.distinct().size)
        assertEquals(2,panels.map { it.box.left }.distinct().size)
    }
    @Test fun everyAdaptiveLayoutHasUniquePhotosAndNonOverlappingCompleteCells() {
        for((width,height) in listOf(1920 to 1080,1080 to 1920)) for(count in 2..4) {
            val sizes=(0 until count).map { if(it%2==0) CollageLayout.Size(600,1000) else CollageLayout.Size(1600,900) }
            val panels=CollageLayout.arrange(width,height,sizes)
            assertEquals((0 until count).toSet(),panels.map { it.photo }.toSet())
            for((i,panel) in panels.withIndex()) {
                val b=panel.box
                assertTrue(b.left>=0 && b.top>=0 && b.right<=width && b.bottom<=height)
                val size=sizes[panel.photo];val fitted=CollageLayout.fit(size.width,size.height,b)
                assertTrue(fitted.left>=b.left-.01 && fitted.top>=b.top-.01 && fitted.right<=b.right+.01 && fitted.bottom<=b.bottom+.01)
                for(other in panels.drop(i+1)) {
                    val c=other.box;assertTrue(b.right<=c.left || c.right<=b.left || b.bottom<=c.top || c.bottom<=b.top)
                }
            }
        }
    }

    @Test fun everyTemplateRunsToTheScreenEdgeAndKeepsOneMatBetweenPhotos() {
        for(count in 2..4) for(template in CollageLayout.templates(1920,1080,count)) {
            assertEquals("count $count left",CollageLayout.EDGE.toFloat(),template.minOf { it.left },0.01f)
            assertEquals("count $count top",CollageLayout.EDGE.toFloat(),template.minOf { it.top },0.01f)
            assertEquals("count $count right",1920f-CollageLayout.EDGE,template.maxOf { it.right },0.01f)
            assertEquals("count $count bottom",1080f-CollageLayout.EDGE,template.maxOf { it.bottom },0.01f)
            // A box's nearest neighbour to its right, and the nearest below it, is exactly one mat away.
            for(a in template) {
                val right=template.filter { b -> b !== a && a.top < b.bottom && b.top < a.bottom && a.right <= b.left }.minOfOrNull { it.left-a.right }
                val below=template.filter { b -> b !== a && a.left < b.right && b.left < a.right && a.bottom <= b.top }.minOfOrNull { it.top-a.bottom }
                if(right != null) assertEquals("count $count neighbours",CollageLayout.MAT.toFloat(),right,0.01f)
                if(below != null) assertEquals("count $count stacked",CollageLayout.MAT.toFloat(),below,0.01f)
            }
        }
    }
    @Test fun theOuterMarginIsNone() { assertEquals(0,CollageLayout.EDGE) }
}
