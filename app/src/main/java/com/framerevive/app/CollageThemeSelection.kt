package com.framerevive.app

import java.time.LocalDate

/** Which photos a curated collage may draw from. */
enum class CollageTheme { RECENT, DAY }

/**
 * Collages are decided per collage and need no setting: each one draws either from the newest photos that
 * the photo mix already treats as recent, or from a single capture date, so a collage is one afternoon or
 * one birthday instead of unrelated pictures. The other source is tried when the chosen one has nothing
 * ready.
 */
object CollageThemeSelection {
    const val MAX_MEMBERS = 4

    fun pick(random: kotlin.random.Random = kotlin.random.Random.Default): CollageTheme =
        if (random.nextBoolean()) CollageTheme.RECENT else CollageTheme.DAY

    fun other(theme: CollageTheme): CollageTheme = if (theme == CollageTheme.RECENT) CollageTheme.DAY else CollageTheme.RECENT

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
