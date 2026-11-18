package com.framerevive.app

import java.time.LocalDate

/** Which photos a curated collage may draw from. */
enum class CollageTheme { RECENT, DAY }

/**
 * A collage is one afternoon or one birthday: it draws from a single capture date, which is the only thing a
 * collage adds over the ordinary stream, where unrelated photos already alternate. Mixed keeps the option of
 * an occasional collage from the newest photos instead. Either way the other source is tried when the chosen
 * one has nothing ready, so a collage is never skipped for want of material.
 */
object CollageThemeSelection {
    const val MAX_MEMBERS = 4
    /** A date offers more than the four members used, so the layout can pick the shapes that compose. */
    const val MAX_CANDIDATES = 12
    const val SOURCE_DAY = "day"
    const val SOURCE_MIXED = "mixed"

    /** Unset or unknown means same-day, so an older install gets the behaviour the setting exists for. */
    fun chosen(source: String, random: kotlin.random.Random = kotlin.random.Random.Default): CollageTheme =
        if (source == SOURCE_MIXED) pick(random) else CollageTheme.DAY

    fun pick(random: kotlin.random.Random = kotlin.random.Random.Default): CollageTheme =
        if (random.nextBoolean()) CollageTheme.RECENT else CollageTheme.DAY

    fun other(theme: CollageTheme): CollageTheme = if (theme == CollageTheme.RECENT) CollageTheme.DAY else CollageTheme.RECENT

    /**
     * Candidates from the best single capture date: a fully cached date first, then the date with the most
     * photos, then the most recent one. More candidates than the collage needs are returned so the layout can
     * choose shapes that compose instead of being handed whatever four photos the date happened to have.
     * [avoidDays] keeps the same date out of two collages in a row and is dropped when no other date qualifies.
     */
    fun bestDay(album: List<FramePhoto>, count: Int, ready: (FramePhoto) -> Boolean, avoid: Set<String> = emptySet(),
                avoidDays: Set<LocalDate> = emptySet(), limit: Int = MAX_CANDIDATES): List<FramePhoto> {
        require(count in 2..4 && limit in count..MAX_CANDIDATES)
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
