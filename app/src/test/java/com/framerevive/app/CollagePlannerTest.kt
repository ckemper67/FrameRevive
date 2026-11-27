package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test

class CollagePlannerTest {
    private val landscape = CollageLayout.Size(1200, 900)   // 4:3
    private val portrait = CollageLayout.Size(900, 1200)    // 3:4
    private val wide = CollageLayout.Size(1920, 1080)
    private val shapes = HashMap<String, CollageLayout.Size>()

    private fun photo(id: String, size: CollageLayout.Size, t: Long = 0L): FramePhoto { shapes[id] = size; return FramePhoto(id, "u$id", 1_000_000L + t) }
    private fun photos(prefix: String, count: Int, size: CollageLayout.Size) = (0 until count).map { photo("$prefix$it", size, it * 600_000L) }
    private fun plan(vararg pools: CollagePlanner.Pool, width: Int = 1080, height: Int = 1920) =
        CollagePlanner.plan(width, height, { shapes[it.id] }, pools.toList())
    private fun pool(window: CollagePlanner.Window, list: List<FramePhoto>) = CollagePlanner.Pool(window, list)

    @Test fun aDayOfOneCameraMakesAFiveWhenItHasBothOrientations() {
        val day = photos("l", 6, landscape) + photos("p", 3, portrait)
        val choice = plan(pool(CollagePlanner.Window.DAY, day))!!
        assertEquals(5, choice.collage.size)
        assertEquals(CollagePlanner.Window.DAY, choice.window)
        assertEquals(3, choice.collage.count { shapes.getValue(it.id) == landscape })
        assertEquals(2, choice.collage.count { shapes.getValue(it.id) == portrait })
        assertTrue(choice.crop <= CollageLayout.MAX_CROP)
    }

    @Test fun theCloserWindowWinsWhenBothMakeTheSameCollage() {
        val day = photos("l", 6, landscape) + photos("p", 3, portrait)
        val week = day + photos("w", 5, landscape)
        val choice = plan(pool(CollagePlanner.Window.WEEK, week), pool(CollagePlanner.Window.DAY, day))!!
        assertEquals(CollagePlanner.Window.DAY, choice.window)
    }

    @Test fun aWeekIsUsedWhenTheDayLacksTheShapes() {
        val day = photos("l", 8, landscape)
        // Landscapes alone make only a pair on this frame; the days around add the portraits a five needs.
        val week = day + photos("p", 3, portrait)
        val choice = plan(pool(CollagePlanner.Window.DAY, day), pool(CollagePlanner.Window.WEEK, week))!!
        assertEquals(CollagePlanner.Window.WEEK, choice.window)
        assertEquals(5, choice.collage.size)
        assertTrue(choice.collage.any { it.id.startsWith("p") })
    }

    @Test fun aGoodPairIsChosenWhenNoFiveFitsAnywhere() {
        val day = photos("l", 8, landscape)
        val choice = plan(pool(CollagePlanner.Window.DAY, day), pool(CollagePlanner.Window.WEEK, day))!!
        assertEquals(2, choice.collage.size)
        assertEquals(CollagePlanner.Window.DAY, choice.window)
    }

    @Test fun nothingIsChosenWhenNothingTiles() {
        // Portraits already fill a portrait frame; two of them cannot be stacked within the crop limit.
        assertNull(plan(pool(CollagePlanner.Window.DAY, photos("p", 8, portrait))))
    }

    @Test fun photosWithoutASizeAreLeftOut() {
        val day = photos("l", 6, landscape) + photos("p", 3, portrait)
        val unsized = day.take(2).map { it.id }.toSet()
        val choice = CollagePlanner.plan(1080, 1920, { if (it.id in unsized) null else shapes[it.id] }, listOf(pool(CollagePlanner.Window.DAY, day)))!!
        assertTrue(choice.collage.none { it.id in unsized })
    }

    @Test fun theBestRankedPhotosAreTaken() {
        val day = photos("l", 8, landscape) + photos("p", 4, portrait)
        val choice = plan(pool(CollagePlanner.Window.DAY, day))!!
        // The first three landscapes and the first two portraits of the ranked pool.
        assertEquals(setOf("l0", "l1", "l2", "p0", "p1"), choice.collage.map { it.id }.toSet())
    }

    @Test fun sparesAreNotInTheCollageAndDownloadsStartWithAPairThatTiles() {
        val day = photos("l", 8, landscape) + photos("p", 4, portrait)
        val choice = plan(pool(CollagePlanner.Window.DAY, day))!!
        assertTrue(choice.spares.size in 1..2)
        assertTrue(choice.spares.none { s -> choice.collage.any { it.id == s.id } })
        assertEquals(choice.downloads.map { it.id }.distinct(), choice.downloads.map { it.id })
        assertTrue(choice.downloads.containsAll(choice.collage + choice.spares))
        val firstTwo = choice.downloads.take(2)
        assertTrue("the first two downloads make a pair on their own",
            CollageLayout.pair(1080, 1920, shapes.getValue(firstTwo[0].id), shapes.getValue(firstTwo[1].id), true).crop <= CollageLayout.MAX_CROP)
        // At most five plus two spares: never more downloads than the ten queued before.
        assertTrue(choice.downloads.size <= 7)
    }

    @Test fun aLandscapeFrameNeedsTheOtherMixOfShapes() {
        val day = photos("p", 6, portrait) + photos("l", 3, landscape)
        val choice = plan(pool(CollagePlanner.Window.DAY, day), width = 1920, height = 1080)!!
        assertEquals(5, choice.collage.size)
        assertEquals(3, choice.collage.count { shapes.getValue(it.id) == portrait })
    }

    @Test fun aDifferentShapeStillWorksWithinTheLimit() {
        // 16:9 photos suit the wide strip; the two tall slots still need portraits.
        val day = photos("w", 4, wide) + photos("p", 3, portrait)
        val choice = plan(pool(CollagePlanner.Window.DAY, day))
        assertNotNull(choice)
        assertTrue(choice!!.crop <= CollageLayout.MAX_CROP)
    }

    @Test fun planningFromTheLargestPoolIsFastEnoughForTheFrame() {
        val mixed = (0 until 60).map { i -> photo("m$i", if (i % 3 == 0) portrait else if (i % 3 == 1) landscape else wide, i * 60_000L) }
        val pools = listOf(pool(CollagePlanner.Window.DAY, mixed.take(30)), pool(CollagePlanner.Window.WEEK, mixed))
        plan(*pools.toTypedArray())   // warm up
        val start = System.nanoTime()
        repeat(20) { plan(*pools.toTypedArray()) }
        val millis = (System.nanoTime() - start) / 20 / 1_000_000.0
        println("one plan over two pools of up to 60: %.2f ms".format(millis))
        assertTrue("one plan took $millis ms", millis < 5.0)
    }
}
