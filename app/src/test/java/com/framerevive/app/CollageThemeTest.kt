package com.framerevive.app

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class CollageThemeTest {
    private fun photo(id:Int,day:String)=FramePhoto(id.toString(),"old",
        LocalDate.parse(day).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),0,0)

    @Test fun eachCollagePicksOneOfTheTwoSourcesAndCanFallBackToTheOther() {
        val draws=(1..40).map { CollageThemeSelection.pick(kotlin.random.Random(it)) }
        assertEquals(setOf(CollageTheme.RECENT,CollageTheme.DAY),draws.toSet())
        assertEquals(CollageTheme.DAY,CollageThemeSelection.other(CollageTheme.RECENT))
        assertEquals(CollageTheme.RECENT,CollageThemeSelection.other(CollageTheme.DAY))
    }
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
    @Test fun sameDayIsTheDefaultAndMixedStillAlternates() {
        // An install that predates the setting, or a page that omits it, means same day.
        repeat(20) { assertEquals(CollageTheme.DAY,CollageThemeSelection.chosen("")) }
        repeat(20) { assertEquals(CollageTheme.DAY,CollageThemeSelection.chosen("nonsense")) }
        repeat(20) { assertEquals(CollageTheme.DAY,CollageThemeSelection.chosen(CollageThemeSelection.SOURCE_DAY)) }
        val mixed=(1..60).map { CollageThemeSelection.chosen(CollageThemeSelection.SOURCE_MIXED) }.toSet()
        assertEquals(setOf(CollageTheme.RECENT,CollageTheme.DAY),mixed)
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
}
