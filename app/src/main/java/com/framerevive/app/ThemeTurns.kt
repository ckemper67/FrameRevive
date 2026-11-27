package com.framerevive.app

/**
 * Which collage theme's turn it is. The themes still take turns, so consecutive collages differ, but "On this day" takes
 * its turns in proportion to the memories share of the owner's recent-versus-memories slider: the other themes have a
 * stride of 1 and "On this day" a stride of `1 / memoryShare`, where `memoryShare = (100 - recentPercent) / 100`. Each theme
 * has an accumulator, the lowest goes first, and a theme that fires advances by its stride. With the slider at all
 * memories every theme has the same share and the turns go round as they always did; at all recent "On this day" never
 * comes up, as the single photos then show no memories, unless it is the only theme enabled. See docs/collage-mix-plan.md.
 */
class ThemeTurns {
    private val accumulators = HashMap<String, Double>()
    private var key = ""
    private var memoryShare = 1.0

    private fun stride(theme: CollageTheme): Double =
        if (theme.id == SEASONAL_ID) (if (memoryShare > 0.0) 1.0 / memoryShare else Double.MAX_VALUE) else 1.0

    /** The themes to try now, the one whose turn it is first, then the rest in turn order as fallbacks. */
    fun order(themes: List<CollageTheme>, recentPercent: Int): List<CollageTheme> {
        memoryShare = (100 - recentPercent.coerceIn(0, 100)) / 100.0
        val usable = if (memoryShare == 0.0 && themes.any { it.id != SEASONAL_ID }) themes.filter { it.id != SEASONAL_ID } else themes
        val newKey = usable.joinToString(",") { it.id } + "|" + recentPercent
        if (newKey != key) {
            // A changed setting starts the turns afresh, staggered so the themes do not all fall due together.
            key = newKey; accumulators.clear()
            for (theme in usable) accumulators[theme.id] = stride(theme) / 2.0
        }
        return usable.sortedBy { accumulators.getValue(it.id) }
    }

    /** [theme] has just been used: its next turn is a stride later. */
    fun fired(theme: CollageTheme) {
        val current = accumulators[theme.id] ?: return
        accumulators[theme.id] = current + stride(theme)
    }

    fun reset() { key = ""; accumulators.clear() }

    private companion object { const val SEASONAL_ID = "anniversary" }
}
