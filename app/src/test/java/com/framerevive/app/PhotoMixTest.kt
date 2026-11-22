package com.framerevive.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.random.Random

class PhotoMixTest {
    private fun p(id:String,date:String)=FramePhoto(id,"",LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    @Test fun sliderEndpointsSelectOnlyTheRequestedPool() {
        val photos=listOf(p("recent","2026-09-01"),p("memory","2010-10-20"))
        // At 100% only the deck is used, and the seasonal photo is deliberately not part of it.
        val deckOnly=PhotoSelection.playlist(photos,LocalDate.parse("2026-10-20"),Random(1),1,14,100)
        assertEquals(listOf("recent"),deckOnly.map { it.id })
        assertEquals(listOf("memory"),PhotoSelection.playlist(photos,LocalDate.parse("2026-10-20"),Random(1),1,14,0).map { it.id })
    }
    @Test fun exactDateWindowAndEmptyMemoryFallbackWork() {
        val photos=listOf(p("recent","2026-09-01"),p("sameDay","2010-10-20"),p("otherDay","2010-10-21"))
        assertEquals(listOf("sameDay"),PhotoSelection.playlist(photos,LocalDate.parse("2026-10-20"),Random(1),1,0,0).map { it.id })
        // No seasonal match means the deck fills the gap, which is any album photo - not necessarily the newest.
        val fallback=PhotoSelection.playlist(photos,LocalDate.parse("2026-03-01"),Random(1),1,0,0)
        assertEquals(1,fallback.size)
        assertTrue(fallback.first().id in photos.map { it.id })
    }
    private val today=LocalDate.parse("2026-10-20")
    @Test fun aFullMemoryPoolKeepsTheRequestedBalance() {
        val recent=(1..30).map { p("r$it","2026-09-01") }
        val memories=(1..12).map { p("m$it","2010-10-20") }
        val result=PhotoSelection.playlist(recent+memories,today,Random(1),10,14,50)
        assertEquals(20,result.size)
        assertEquals(10,result.count { it.id.startsWith("r") })
        assertEquals(10,result.count { it.id.startsWith("m") })
        assertEquals("a cycle never plays a photo twice",20,result.map { it.id }.distinct().size)
    }
    @Test fun aSmallMemoryPoolStopsContributingAndTheShortfallGoesToRecent() {
        val recent=(1..30).map { p("r$it","2026-09-01") }
        val memories=(1..3).map { p("m$it","2010-10-20") }
        // 50% of a 10-photo recent deck wants 10 memory slots; only 3 memories exist, so 7 slots move to recent.
        val result=PhotoSelection.playlist(recent+memories,today,Random(1),10,14,50)
        assertEquals(20,result.size)
        assertEquals(3,result.count { it.id.startsWith("m") })
        assertEquals(17,result.count { it.id.startsWith("r") })
        assertEquals(20,result.map { it.id }.distinct().size)
    }
    @Test fun noMemoryPhotoPlaysTwiceInACycleAtAnySlider() {
        val recent=(1..400).map { p("r$it","2026-09-01") }
        val memories=(1..40).map { p("m$it","2010-10-${10+it%10}") }
        for(percent in listOf(1,10,25,50,75,90,99)) {
            val ids=PhotoSelection.playlist(recent+memories,today,Random(percent),50,14,percent).map { it.id }
            assertEquals("slider $percent",ids.size,ids.distinct().size)
            assertEquals("slider $percent keeps every memory",setOf<String>(),ids.filter { it.startsWith("m") }.toSet()-memories.map { it.id }.toSet())
        }
    }
    @Test fun memoriesAreSpreadThroughTheCycleNotBunched() {
        val recent=(1..100).map { p("r$it","2026-09-01") }
        val memories=(1..20).map { p("m$it","2010-10-20") }
        val result=PhotoSelection.playlist(recent+memories,today,Random(2),100,14,83)
        val positions=result.withIndex().filter { it.value.id.startsWith("m") }.map { it.index }
        assertEquals(20,positions.size)
        assertTrue("first and last memory sit far apart",positions.last()-positions.first() > result.size/2)
    }
    @Test fun aShownMemoryWaitsForTheOnesNotYetShown() {
        val recent=(1..30).map { p("r$it","2026-09-01") }
        val memories=(1..6).map { p("m$it","2010-10-20") }
        val seen=mapOf("m1" to PlayRecord(10,1),"m2" to PlayRecord(20,1),"m3" to PlayRecord(30,1))
        // Quota is 3 memory slots (50% of a 3-photo recent deck): the three unseen memories take them.
        val result=PhotoSelection.playlist(recent+memories,today,Random(1),3,14,50,seen)
        assertEquals(setOf("m4","m5","m6"),result.filter { it.id.startsWith("m") }.map { it.id }.toSet())
    }
    @Test fun theRecencySettingDecidesWhetherOldYearsReachTheRound() {
        val newer=(1..500).map { p("n$it","2026-09-01") }
        val older=(1..500).map { p("o$it","2015-01-15") }
        val album=newer+older
        fun oldShare(recency:Recency):Double {
            val round=PhotoSelection.playlist(album,today,Random(4),100,14,100,emptyMap(),album.sortedByDescending { it.takenMs },null,recency)
            return round.count { it.id.startsWith("o") }/round.size.toDouble()
        }
        assertEquals("the hard window never reaches past the newest 400",0.0,oldShare(Recency.NEWEST),0.0)
        assertTrue("somewhat reaches them: ${oldShare(Recency.SOMEWHAT)}",oldShare(Recency.SOMEWHAT) > 0.0)
        assertTrue("even reaches them about half the time",oldShare(Recency.EVEN) in 0.3..0.7)
        assertTrue(oldShare(Recency.SOMEWHAT) < oldShare(Recency.EVEN))
    }
    @Test fun theMemoryQuotaFollowsTheSliderAndIsBounded() {
        assertEquals(300,PhotoSelection.memorySlots(300,50))
        assertEquals(100,PhotoSelection.memorySlots(300,75))
        assertEquals(900,PhotoSelection.memorySlots(300,25))
        assertEquals("the ends pick a pool outright",0,PhotoSelection.memorySlots(300,100))
        assertEquals(0,PhotoSelection.memorySlots(300,0))
        assertEquals("never past the longest cycle",PhotoSelection.MAX_CYCLE-300,PhotoSelection.memorySlots(300,1))
    }
}
