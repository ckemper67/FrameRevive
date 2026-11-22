package com.framerevive.app

import java.io.File
import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Writes the controls page, with the "Your album" card in it, for a real album, so the card can be iterated on in a
 * browser on the dev machine without touching the frame. The page lands next to the CSV (local/controls-preview.html,
 * which git ignores). Skips itself when there is no CSV. See docs/tuning-the-strategy.md.
 *
 * FRAMEREVIVE_PREVIEW_ROUNDS sets how many rounds of play the history holds (default 20); 0 shows a frame that has shown
 * nothing yet. FRAMEREVIVE_SECONDS and FRAMEREVIVE_AWAKE_HOURS set the frame's pace (see AlbumFixture).
 */
class AlbumCardPreview {
    @Test fun writesTheControlsPageForTheRealAlbum() {
        val source = AlbumFixture.source()
        assumeTrue("no album dates file; run tools/album_dates.py (see docs/tuning-the-strategy.md)", source != null)
        val photos = AlbumFixture.load()!!
        val rounds = (System.getenv("FRAMEREVIVE_PREVIEW_ROUNDS") ?: "20").toInt()
        val today = LocalDate.now()
        val nowMs = System.currentTimeMillis()
        val cycle = CycleSettings()
        val byRecency = photos.sortedByDescending { it.takenMs }
        val seasonal = PhotoSelection.seasonal(photos, today, cycle.memoryDays)
        val history = HashMap<String, PlayRecord>()
        val random = Random(7)
        // Play the rounds with the real pipeline at the frame's pace, ending now.
        val plays = ArrayList<FramePhoto>()
        repeat(rounds) {
            val round = PlaybackEngine.buildCycle(photos, today, cycle, history, byRecency, seasonal, emptyMap(), { null }, emptySet(), random).playlist
            for (p in round) { history[p.id] = PlayRecord(0L, 1); plays += p }
        }
        val gapMs = (86_400_000L / AlbumFixture.slotsPerDay()).toLong()
        plays.forEachIndexed { i, p -> history[p.id] = PlayRecord(nowMs - (plays.size - i) * gapMs, 1) }
        val settings = AlbumStats.Settings(cycle.roundSize, cycle.recentPercent, cycle.memoryDays, AlbumFixture.secondsPerPhoto(), AlbumFixture.awakeHours())
        val card = AlbumCard.html(AlbumStats.compute(photos, today, history, nowMs, settings, seasonal))
        val page = FrameWeb.render(FrameWeb.State(
            mapOf("roundSize" to "${cycle.roundSize}", "memoryDays" to "${cycle.memoryDays}", "recentPercent" to "${cycle.recentPercent}",
                "memoryPercent" to "${100 - cycle.recentPercent}", "recencyBias" to "somewhat", "skipBlurry" to "medium", "collageEvery" to "20",
                "collageSource" to "day", "deckSummary" to "Preview of a real album after $rounds rounds of play."),
            setOf("shared"), albumHtml = card))
        val out = File(source!!.parentFile, "controls-preview.html")
        out.writeText(page)
        // The card on its own, with the page's own styles, for a quick look and for screenshots.
        val style = Regex("<style>.*?</style>", RegexOption.DOT_MATCHES_ALL).find(page)?.value.orEmpty()
        File(source.parentFile, "album-card.html").writeText(
            "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">$style</head>" +
                "<body><main class=\"shell\" style=\"max-width:760px;margin:0 auto;padding:24px 16px\">$card</main></body></html>")
        println("preview: wrote ${out.path} ($rounds rounds of play)")
    }
}
