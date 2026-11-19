package com.framerevive.app

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class CollageThemeTest {
    private fun photo(id:Int,day:String)=FramePhoto(id.toString(),"old",
        LocalDate.parse(day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),0,0)

    @Test fun themeParsingFallsBackToMixed() {
        assertEquals(CollageTheme.MIXED,CollageThemeSelection.theme(null))
        assertEquals(CollageTheme.MIXED,CollageThemeSelection.theme("nonsense"))
        assertEquals(CollageTheme.RECENT,CollageThemeSelection.theme("recent"))
        assertEquals(CollageTheme.DAY,CollageThemeSelection.theme("day"))
        assertEquals("day",CollageThemeSelection.value(CollageTheme.DAY))
        assertEquals("mixed",CollageThemeSelection.value(CollageTheme.MIXED))
    }
    @Test fun bestDayGroupsOneCaptureDateAndSkipsThinDays() {
        val album=listOf(photo(1,"2019-07-04"),photo(2,"2019-07-04"),photo(3,"2019-07-04"),
            photo(4,"2021-05-01"),photo(5,"2021-05-01"),photo(6,"2023-12-24"))
        val members=CollageThemeSelection.bestDay(album,3,{ true })
        assertEquals(setOf("2019-07-04"),members.map { it.date().toString() }.toSet())
        assertEquals(3,members.size)
        assertTrue(CollageThemeSelection.bestDay(album,4,{ true }).isEmpty())
    }
    @Test fun bestDayPrefersAFullyCachedDateOverABiggerOne() {
        val album=(1..5).map { photo(it,"2018-08-15") }+listOf(photo(6,"2020-02-02"),photo(7,"2020-02-02"))
        val cached=setOf("6","7")
        val members=CollageThemeSelection.bestDay(album,2,{ it.id in cached })
        assertEquals(setOf("2020-02-02"),members.map { it.date().toString() }.toSet())
        assertTrue(members.all { it.id in cached })
    }
    @Test fun bestDayAvoidsThePhotoOnScreenAndThePreviousDate() {
        val album=listOf(photo(1,"2019-07-04"),photo(2,"2019-07-04"),photo(3,"2019-07-04"),
            photo(4,"2021-05-01"),photo(5,"2021-05-01"),photo(6,"2021-05-01"))
        val next=CollageThemeSelection.bestDay(album,3,{ true },avoidDays=setOf(LocalDate.parse("2021-05-01")))
        assertEquals(setOf("2019-07-04"),next.map { it.date().toString() }.toSet())
        val withoutScreenPhoto=CollageThemeSelection.bestDay(album,3,{ true },avoid=setOf("1","2","3"))
        assertEquals(setOf("2021-05-01"),withoutScreenPhoto.map { it.date().toString() }.toSet())
    }
    @Test fun bestDayLimitsMembersAndNeverRepeatsAPhoto() {
        val album=(1..6).map { photo(it,"2017-06-10") }
        val members=CollageThemeSelection.bestDay(album,2,{ true })
        assertEquals(CollageThemeSelection.MAX_MEMBERS,members.size)
        assertEquals(members.size,members.map { it.id }.distinct().size)
    }
}
