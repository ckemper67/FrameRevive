package com.framerevive.app

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PhotoQualityTest {
    private val width=341
    private val height=256

    /** Sharp rectangles: plenty of hard edges, like a normal photo of objects. */
    private fun rectangles(seed:Int):IntArray {
        val random=Random(seed); val luma=IntArray(width*height) { 200 }
        repeat(40) {
            val x0=random.nextInt(width-40); val y0=random.nextInt(height-40)
            val w=20+random.nextInt(100); val h=20+random.nextInt(100); val value=20+random.nextInt(215)
            for (y in y0 until minOf(height,y0+h)) for (x in x0 until minOf(width,x0+w)) luma[y*width+x]=value
        }
        return luma
    }
    private fun blur(luma:IntArray,radius:Int):IntArray {
        val out=IntArray(luma.size); 
        for (y in 0 until height) for (x in 0 until width) {
            var sum=0; var count=0
            for (yy in maxOf(0,y-radius)..minOf(height-1,y+radius)) for (xx in maxOf(0,x-radius)..minOf(width-1,x+radius)) { sum+=luma[yy*width+xx]; count++ }
            out[y*width+x]=sum/count
        }
        return out
    }
    private fun score(luma:IntArray)=PhotoQuality.measure(luma,width,height)

    @Test fun blurLowersTheFocusMeasureByAnOrderOfMagnitude() {
        val sharp=score(rectangles(1)); val soft=score(blur(rectangles(1),3))
        assertTrue(sharp.visible); assertTrue(soft.visible)
        assertTrue(sharp.sharpness > soft.sharpness*8f)
        assertTrue(soft.sharpness < 0.004f)
    }
    @Test fun smoothRampsAndFlatFieldsAreNotSharp() {
        val ramp=IntArray(width*height) { i -> (i % width) * 255 / (width-1) }
        assertEquals(0f,score(ramp).sharpness,0.0001f)
        assertEquals(0f,score(IntArray(width*height) { 128 }).contrast,0.0001f)
        assertFalse(score(IntArray(width*height) { 128 }).visible)
    }
    @Test fun exposureAndClippingRejectUnusableFrames() {
        assertFalse(score(IntArray(width*height) { 5 }).visible)
        assertFalse(score(IntArray(width*height) { 252 }).visible)
        // A dark night shot still has structure to measure: dark base, brighter sky, light sources.
        val night=IntArray(width*height) { i -> (10+(i%width)/4+Random(i).nextInt(6)).coerceAtMost(255) }
        assertTrue(score(night).visible)
        // Mostly blown highlights leave nothing to show.
        val blown=IntArray(width*height) { if(it%10<7) 255 else 200 }
        assertFalse(score(blown).visible)
    }
    @Test fun storedScoresRejectOnlyPlainlyUnusablePhotos() {
        val flat=score(IntArray(width*height) { 128 })
        assertEquals(PhotoQuality.REJECTED,PhotoQuality.stored(flat),0f)
        val sharp=score(rectangles(2))
        assertEquals(sharp.sharpness,PhotoQuality.stored(sharp),0.0001f)
    }
    @Test fun aSoftPhotoIsDroppedAgainstTheAlbumsOwnMedian() {
        val sharpScores=(1..40).map { score(rectangles(it)).sharpness }
        val median=PhotoQuality.median(sharpScores)!!
        val soft=PhotoQuality.stored(score(blur(rectangles(99),3)))
        assertFalse(PhotoQuality.acceptable(soft,median))
        assertTrue(PhotoQuality.acceptable(sharpScores[0],median))
        assertTrue(soft<median*PhotoQuality.SOFT_FRACTION)
    }
    @Test fun withoutEnoughSamplesOrAFilterableAlbumNothingIsDropped() {
        assertNull(PhotoQuality.median(listOf(0.1f,0.2f)))
        assertNull(PhotoQuality.median(emptyList()))
        assertTrue(PhotoQuality.acceptable(0.0001f,null))
        assertTrue(PhotoQuality.acceptable(0f,0.0001f))
        assertTrue(PhotoQuality.acceptable(null,0.5f))
        assertFalse(PhotoQuality.acceptable(PhotoQuality.REJECTED,0.5f))
    }
    @Test fun theMedianIgnoresRejectionsAndSortsScores() {
        val scores=List(30) { 0.01f*(it+1) }+List(5) { PhotoQuality.REJECTED }
        assertEquals(0.16f,PhotoQuality.median(scores)!!,0.0001f)
        assertEquals(0.01f,PhotoQuality.median(List(30) { 0.01f })!!,0.0001f)
    }
}
