package com.framerevive.app

import java.util.Locale

/** The "Your album" card for the controls page. Every value is a number or one of our own words, never album text. */
object AlbumCard {
    /** Below this many days between returns a memory photo comes round often enough to be worth a hint. */
    private const val OFTEN_DAYS = 3.0

    private fun n(value: Int) = String.format(Locale.US, "%,d", value)
    private fun pct(share: Double) = String.format(Locale.US, "%.0f%%", 100.0 * share)
    private fun days(value: Double) = when {
        value < 10 -> String.format(Locale.US, "%.1f", value)
        else -> String.format(Locale.US, "%.0f", value)
    }
    private fun plural(count: Int, one: String, many: String) = if (count == 1) one else many
    private fun photos(count: Int) = "${n(count)} ${plural(count, "photo", "photos")}"
    private fun dayCount(count: Int) = "${n(count)} ${plural(count, "day", "days")}"

    fun html(s: AlbumStats.Stats): String {
        if (s.days == 0) return """<article class="card" id="albumStats"><h2>Your album</h2><p class="muted">No dated photos yet.</p></article>"""
        val sb = StringBuilder()
        sb.append("""<article class="card" id="albumStats"><h2>Your album</h2>""")
        sb.append("""<p class="muted">${photos(s.photos)} on ${dayCount(s.days)}, ${s.firstYear} to ${s.lastYear}.</p>""")
        sb.append("<p>A typical day has ${photos(s.perDayMedian)}. One day in ten has ${n(s.perDayP90)} or more, ")
        sb.append("and the busiest has ${n(s.perDayMax)}. ${dayCount(s.bigDays)} (${pct(s.bigDays.toDouble() / s.days)} of days) ${plural(s.bigDays, "holds", "hold")} ${pct(s.bigDayPhotoShare)} of all photos, ")
        sb.append("while ${dayCount(s.singlePhotoDays)} ${plural(s.singlePhotoDays, "has", "have")} a single photo.</p>")

        sb.append("<h3>Recent</h3>")
        for (w in s.recent) sb.append("""<span class="fine-print">Last ${w.months} ${plural(w.months, "month", "months")}: ${photos(w.photos)} on ${dayCount(w.days)}</span>""")

        sb.append("<h3>By year</h3>")
        val most = s.byYear.maxOfOrNull { it.photos }?.coerceAtLeast(1) ?: 1
        for (y in s.byYear) {
            val width = (100.0 * y.photos / most).coerceIn(1.0, 100.0)
            sb.append("""<div style="display:flex;align-items:center;gap:10px;margin:3px 0"><span class="fine-print" style="width:36px">${y.year}</span>""")
            sb.append("""<span style="display:block;height:8px;border-radius:4px;background:var(--accent);width:${String.format(Locale.US, "%.0f", width)}%;max-width:60%"></span>""")
            sb.append("""<span class="fine-print">${photos(y.photos)}, ${dayCount(y.days)}</span></div>""")
        }

        s.memories?.let { m ->
            sb.append("<h3>This time of year</h3>")
            sb.append("<p>${photos(m.photos)} on ${dayCount(m.days)} ${plural(m.photos, "falls", "fall")} within ${m.windowDays} days of today in earlier years.</p>")
        }

        val p = s.progress
        sb.append("<h3>What the frame has shown</h3>")
        sb.append("<p>${n(p.photosShown)} of ${photos(s.photos)} (${pct(p.photosShown.toDouble() / s.photos.coerceAtLeast(1))}) and ")
        sb.append("${n(p.daysReached)} of ${dayCount(s.days)} (${pct(p.daysReached.toDouble() / s.days)}). ")
        sb.append("${dayCount(p.daysNotShownLately)} ${plural(p.daysNotShownLately, "has", "have")} not been on screen in the last ${AlbumStats.LATELY_DAYS} days.</p>")

        val pace = s.pace
        if (pace.slotsPerDay > 0) {
            sb.append("<h3>At your settings</h3>")
            sb.append("<p>The frame shows about ${n(pace.slotsPerDay.toInt())} photos a day. One pass through the whole album takes about ${days(pace.passDays)} days.")
            if (pace.memoryShare > 0 && pace.memoryReturnDays != null) {
                sb.append(" Memories are ${pct(pace.memoryShare)} of what you see, so each memory photo comes back about every ${days(pace.memoryReturnDays)} days")
                sb.append(if (pace.otherReturnDays != null) " and any other photo about every ${days(pace.otherReturnDays)} days.</p>" else ".</p>")
                if (pace.memoryReturnDays < OFTEN_DAYS && pace.memoryShare < 1.0)
                    sb.append("""<p class="fine-print">That is often. Move the mix slider toward Recent to see memories less.</p>""")
            } else if (pace.otherReturnDays != null) {
                sb.append(" Each photo comes back about every ${days(pace.otherReturnDays)} days.</p>")
            } else sb.append("</p>")
        }
        sb.append("</article>")
        return sb.toString()
    }
}
