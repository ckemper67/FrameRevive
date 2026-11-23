package com.framerevive.app

import java.io.File
import java.util.TreeMap

/** How many favorites were put on the panel each day, so the owner can see the real rate next to the share setting. */
class FavoriteLog {
    private val days = TreeMap<Long, Int>()

    /** Counts [n] favorites shown on [epochDay]. */
    @Synchronized fun record(epochDay: Long, n: Int = 1) { if (n > 0) days.merge(epochDay, n, Int::plus) }
    @Synchronized fun on(epochDay: Long): Int = days[epochDay] ?: 0
    /** The count for [epochDay] and the days before it, [count] days in all. */
    @Synchronized fun lastDays(epochDay: Long, count: Int): Int = days.subMap(epochDay - count + 1, epochDay + 1).values.sum()
    @Synchronized fun total(): Int = days.values.sum()
    @Synchronized fun firstDay(): Long? = if (days.isEmpty()) null else days.firstKey()

    @Synchronized fun export(): String = days.entries.joinToString("") { "${it.key}\t${it.value}\n" }

    fun load(file: File) {
        runCatching {
            if (file.exists()) file.forEachLine { line ->
                val parts = line.split('\t')
                val day = parts.getOrNull(0)?.toLongOrNull(); val n = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size == 2 && day != null && n != null) record(day, n)
            }
        }
    }

    /** The sentence for the controls page. [distinct] is how many different favorites have been shown at all. */
    fun sentence(today: Long, distinct: Int): String {
        val first = firstDay() ?: return "No favorite has been shown since counting began."
        val since = java.time.LocalDate.ofEpochDay(first).format(FrameCaption.SHORT_DATE)
        return "Favorites shown: ${on(today)} today, ${lastDays(today, 7)} in the last 7 days, ${total()} since $since ($distinct different ones)."
    }
}
