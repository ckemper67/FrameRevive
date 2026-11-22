package com.framerevive.app

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class CollageThemeTest {
    private fun photo(id:Int,day:String)=FramePhoto(id.toString(),"old",
        LocalDate.parse(day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),0,0)

    @Test fun bestDayGroupsOneCaptureDateAndSkipsThinDays() {
        val album=listOf(photo(1,"2019-07-04"),photo(2,"2019-07-04"),photo(3,"2019-07-04"),
            photo(4,"2021-05-01"),photo(5,"2021-05-01"),photo(6,"2023-12-24"))
        val members=CollageThemeSelection.bestDay(album,3,album.map { it.id }.toSet())
        assertEquals(setOf("2019-07-04"),members.map { it.date().toString() }.toSet())
        assertEquals(3,members.size)
        assertTrue(CollageThemeSelection.bestDay(album,4,album.map { it.id }.toSet()).isEmpty())
    }
    @Test fun bestDayPrefersAFullyCachedDateOverABiggerOne() {
        val album=(1..5).map { photo(it,"2018-08-15") }+listOf(photo(6,"2020-02-02"),photo(7,"2020-02-02"))
        val cached=setOf("6","7")
        val members=CollageThemeSelection.bestDay(album,2,cached)
        assertEquals(setOf("2020-02-02"),members.map { it.date().toString() }.toSet())
        assertTrue(members.all { it.id in cached })
    }
    @Test fun bestDayAvoidsThePhotoOnScreenAndThePreviousDate() {
        val album=listOf(photo(1,"2019-07-04"),photo(2,"2019-07-04"),photo(3,"2019-07-04"),
            photo(4,"2021-05-01"),photo(5,"2021-05-01"),photo(6,"2021-05-01"))
        val all=album.map { it.id }.toSet()
        val next=CollageThemeSelection.bestDay(album,3,all,avoidDays=setOf(LocalDate.parse("2021-05-01")))
        assertEquals(setOf("2019-07-04"),next.map { it.date().toString() }.toSet())
        val withoutScreenPhoto=CollageThemeSelection.bestDay(album,3,all,avoid=setOf("1","2","3"))
        assertEquals(setOf("2021-05-01"),withoutScreenPhoto.map { it.date().toString() }.toSet())
    }
    @Test fun bestDayOffersMoreCandidatesThanACollageUses() {
        // Shapes can only compose if the date offers a choice, so the pool is wider than four.
        val album=(1..6).map { photo(it,"2017-06-10") }
        val members=CollageThemeSelection.bestDay(album,2,album.map { it.id }.toSet())
        assertEquals(6,members.size)
        assertEquals(members.size,members.map { it.id }.distinct().size)
        assertTrue(members.size > CollageThemeSelection.MAX_MEMBERS)
        val many=(1..30).map { photo(it,"2017-06-10") }
        assertEquals(CollageThemeSelection.MAX_CANDIDATES,CollageThemeSelection.bestDay(many,4,many.map { it.id }.toSet()).size)
    }
    @Test fun bestDayPrefersADateWhosePhotosHaveNotBeenShown() {
        // The bigger date has been shown; the smaller one has not.
        val album=(1..5).map { photo(it,"2018-08-15") }+listOf(photo(6,"2020-02-02"),photo(7,"2020-02-02"))
        val all=album.map { it.id }.toSet()
        val seen=(1..5).associate { it.toString() to PlayRecord(1_000L,1) }
        assertEquals(setOf("2020-02-02"),CollageThemeSelection.bestDay(album,2,all,seen=seen).map { it.date().toString() }.toSet())
        // With no history the bigger date wins, as before.
        assertEquals(setOf("2018-08-15"),CollageThemeSelection.bestDay(album,2,all).map { it.date().toString() }.toSet())
    }
    @Test fun whenEveryDateWasShownTheOneShownLongestAgoWins() {
        val album=listOf(photo(1,"2019-07-04"),photo(2,"2019-07-04"),photo(3,"2021-05-01"),photo(4,"2021-05-01"))
        val all=album.map { it.id }.toSet()
        val seen=mapOf("1" to PlayRecord(500L,1),"2" to PlayRecord(500L,1),"3" to PlayRecord(100L,1),"4" to PlayRecord(100L,1))
        assertEquals(setOf("2021-05-01"),CollageThemeSelection.bestDay(album,2,all,seen=seen).map { it.date().toString() }.toSet())
    }
    @Test fun withinADateUnseenPhotosComeBeforeShownOnes() {
        val album=(1..6).map { photo(it,"2019-07-04") }
        val seen=mapOf("1" to PlayRecord(900L,1),"2" to PlayRecord(800L,1),"3" to PlayRecord(100L,1))
        val members=CollageThemeSelection.bestDay(album,2,album.map { it.id }.toSet(),seen=seen)
        assertEquals(listOf("4","5","6","3","2","1"),members.map { it.id })
    }
    @Test fun undatedPhotosNeverFormADay() {
        val undated=(1..6).map { FramePhoto("u$it","old",0L) }
        assertTrue(CollageThemeSelection.rankedDays(undated,2,undated.map { it.id }.toSet()).isEmpty())
        assertTrue(CollageThemeSelection.bestDay(undated,2,undated.map { it.id }.toSet()).isEmpty())
    }
    @Test fun daysAreRankedBestFirstWithEachDaysPhotosInCaptureOrder() {
        val album=listOf(photo(1,"2019-07-04"),photo(2,"2019-07-04"),photo(3,"2021-05-01"),photo(4,"2021-05-01"),photo(5,"2021-05-01"))
        val days=CollageThemeSelection.rankedDays(album,2,album.map { it.id }.toSet())
        assertEquals(listOf("2021-05-01","2019-07-04"),days.map { it.date.toString() })
        assertEquals(listOf("3","4","5"),days.first().photos.map { it.id })
    }
    @Test fun aPhotosOwnOffsetDecidesItsDay() {
        // 23:30 UTC with a +2 hour offset is 01:30 the next local day, so these two share a day and the third does not.
        val base=LocalDate.parse("2024-06-01").toEpochDay()*86_400_000L
        val album=listOf(FramePhoto("a","u",base+23*3_600_000L+1_800_000L,7_200_000L),FramePhoto("b","u",base+30*3_600_000L,0L),FramePhoto("c","u",base+10*3_600_000L,0L))
        val days=CollageThemeSelection.rankedDays(album,2,album.map { it.id }.toSet())
        assertEquals(1,days.size)
        assertEquals(setOf("a","b"),days.single().photos.map { it.id }.toSet())
    }
    @Test fun theLastDayIsAvoidedUnlessNothingElseQualifies() {
        val album=listOf(photo(1,"2019-07-04"),photo(2,"2019-07-04"),photo(3,"2021-05-01"),photo(4,"2021-05-01"))
        val all=album.map { it.id }.toSet()
        assertEquals("2019-07-04",CollageThemeSelection.rankedDays(album,2,all,avoidDays=setOf(LocalDate.parse("2021-05-01"))).first().date.toString())
        val only=listOf(photo(1,"2019-07-04"),photo(2,"2019-07-04"))
        assertEquals(1,CollageThemeSelection.rankedDays(only,2,all,avoidDays=setOf(LocalDate.parse("2019-07-04"))).size)
    }
}
