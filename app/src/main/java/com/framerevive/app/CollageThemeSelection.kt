package com.framerevive.app

import java.time.LocalDate

/** Which photos a curated collage may draw from. */
enum class CollageTheme { MIXED, RECENT, DAY }

/**
 * Collage themes. Mixed keeps the ordinary slideshow window, Recent stays inside the newest photos that
 * the photo mix already treats as recent, and Same day groups every photo taken on one date so a collage
 * is one afternoon or one birthday instead of unrelated pictures.
 */
object CollageThemeSelection {
    const val MAX_MEMBERS = 4

    fun theme(value: String?): CollageTheme = when (value) {
        "recent" -> CollageTheme.RECENT
        "day" -> CollageTheme.DAY
        else -> CollageTheme.MIXED
    }

    fun value(theme: CollageTheme): String = theme.name.lowercase()

    /**
     * Members of the best single capture date: a fully cached date first, then the date with the most
     * photos, then the most recent one. [avoidDays] keeps the same date out of two collages in a row and
     * is dropped when no other date qualifies.
     */
    fun bestDay(album: List<FramePhoto>, count: Int, ready: (FramePhoto) -> Boolean, avoid: Set<String> = emptySet(),
                avoidDays: Set<LocalDate> = emptySet(), limit: Int = MAX_MEMBERS): List<FramePhoto> {
        require(count in 2..4 && limit in 2..4)
        val candidates = album.filter { it.id !in avoid }
        fun pick(excluded: Set<LocalDate>): List<FramePhoto>? {
            val day = candidates.groupBy { it.date() }.filter { (date, photos) -> date !in excluded && photos.size >= count }
                .entries.sortedWith(compareByDescending<Map.Entry<LocalDate, List<FramePhoto>>> { entry -> entry.value.count { ready(it) } >= count }
                    .thenByDescending { it.value.size }
                    .thenByDescending { it.key })
                .firstOrNull() ?: return null
            val (cached, uncached) = day.value.partition { ready(it) }
            return (cached + uncached).take(limit)
        }
        return pick(avoidDays) ?: pick(emptySet()) ?: emptyList()
    }
}
