package com.framerevive.app

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumCardTest {
    private val dayMs = 86_400_000L
    private val today = LocalDate.of(2026, 11, 22)
    private val now = today.toEpochDay() * dayMs
    private fun photo(id: String, date: LocalDate) = FramePhoto(id, "u", date.toEpochDay() * dayMs + 12 * 3_600_000L)
    private val album = (0 until 40).map { photo("p$it", LocalDate.of(2025, 1, 1).plusDays((it % 8).toLong())) } + photo("q", LocalDate.of(2026, 11, 20))
    private val memories = (0 until 10).map { photo("m$it", LocalDate.of(2024, 11, 20)) }

    private fun card(settings: AlbumStats.Settings = AlbumStats.Settings(300, 50, 14, 30, 15.0), seasonal: List<FramePhoto>? = memories,
                     history: Map<String, PlayRecord> = emptyMap()) =
        AlbumCard.html(AlbumStats.compute(album, today, history, now, settings, seasonal))

    @Test fun theCardNamesTheNumbersAPersonActsOn() {
        val html = card(history = mapOf("p0" to PlayRecord(now - dayMs, 1)))
        for (needle in listOf("Your album", "41 photos on 9 days", "2025 to 2026", "Last 6 months", "By year", "2025", "This time of year",
            "10 photos on 1 day fall within 14 days", "What the frame has shown", "1 of 41 photos", "At your settings", "1,800 photos a day"))
            assertTrue(needle + " in: " + html, html.contains(needle))
    }

    @Test fun singularsAreUsedForOne() {
        val one = AlbumCard.html(AlbumStats.compute(listOf(photo("only", LocalDate.of(2020, 1, 1))), today, emptyMap(), now,
            AlbumStats.Settings(300, 50, 14, 30, 15.0), listOf(photo("m", LocalDate.of(2024, 11, 22)))))
        for (needle in listOf("1 photo on 1 day,", "A typical day has 1 photo.", "1 day has a single photo", "1 photo, 1 day", "1 photo on 1 day falls within"))
            assertTrue(needle + " in " + one, one.contains(needle))
        assertFalse("1 photos in $one", Regex("(^|[^0-9,])1 photos").containsMatchIn(one))
        assertFalse("1 days in $one", Regex("(^|[^0-9,])1 days").containsMatchIn(one))
    }

    @Test fun numbersGetThousandsSeparatorsInOneLocale() {
        val many = (0 until 12_000).map { photo("n$it", LocalDate.of(2020, 1, 1).plusDays((it % 400).toLong())) }
        val html = AlbumCard.html(AlbumStats.compute(many, today, emptyMap(), now, AlbumStats.Settings(300, 50, 14, 30, 15.0), emptyList()))
        assertTrue(html.contains("12,000 photos"))
    }

    @Test fun theOftenHintAppearsOnlyWhenMemoriesComeRoundQuickly() {
        // One photo every five minutes is 180 a day, so a 10-photo pool taking 10/600 of the time returns about every 3.3 days.
        assertFalse(card(AlbumStats.Settings(300, 50, 14, 300, 15.0), seasonal = memories).contains("That is often"))
        val bigPool = (0 until 1155).map { photo("m$it", LocalDate.of(2024, 11, 20)) }
        val oftenCard = AlbumCard.html(AlbumStats.compute(List(15_000) { photo("z$it", LocalDate.of(2020, 1, 1).plusDays((it % 900).toLong())) }, today, emptyMap(), now,
            AlbumStats.Settings(300, 50, 14, 30, 15.0), bigPool))
        assertTrue(oftenCard.contains("That is often"))
        assertTrue(oftenCard.contains("Move the mix slider toward Recent"))
    }

    @Test fun withNoMemoryPoolTheCardOmitsMemoriesAndStillSaysHowOftenPhotosReturn() {
        val html = card(seasonal = emptyList())
        assertFalse(html.contains("Memories are"))
        assertTrue(html.contains("Each photo comes back about every"))
        assertFalse("not calculated yet means no memories section", card(seasonal = null).contains("This time of year"))
    }

    @Test fun anEmptyAlbumGivesAShortCardAndNoBrokenNumbers() {
        val html = AlbumCard.html(AlbumStats.compute(emptyList(), today, emptyMap(), now, AlbumStats.Settings(300, 50, 14, 30, 15.0), emptyList()))
        assertTrue(html.contains("No dated photos yet"))
        for (bad in listOf("NaN", "Infinity", "null")) assertFalse(bad, html.contains(bad))
    }

    @Test fun noNumberIsEverNaNOrInfinite() {
        for (settings in listOf(AlbumStats.Settings(300, 0, 14, 30, 15.0), AlbumStats.Settings(300, 100, 14, 30, 15.0), AlbumStats.Settings(300, 50, 14, 0, 15.0),
            AlbumStats.Settings(300, 50, 14, 30, 0.0))) {
            val html = card(settings)
            for (bad in listOf("NaN", "Infinity", "null")) assertFalse("$bad with $settings", html.contains(bad))
        }
    }

    @Test fun theCardIsOneArticleAndCarriesNoMarkupFromData() {
        val html = card()
        assertEquals(1, Regex("<article").findAll(html).count())
        assertEquals(1, Regex("</article>").findAll(html).count())
        assertFalse(html.contains("<script"))
    }
}
