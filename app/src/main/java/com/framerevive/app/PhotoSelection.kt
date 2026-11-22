package com.framerevive.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

data class FramePhoto(val id: String, val url: String, val takenMs: Long, val offsetMs: Long = 0, val discoveredMs: Long = 0) {
    fun date(): LocalDate = Instant.ofEpochMilli(takenMs + offsetMs).atZone(ZoneOffset.UTC).toLocalDate()
    /** Album entries do not always carry a capture timestamp; a zero would render as January 1970. */
    val dated: Boolean get() = takenMs > 0
}

object PhotoSelection {
    // Newly discovered IDs get one turn before the usual mix, including old captures.
    fun prioritize(normal: List<FramePhoto>, photos: List<FramePhoto>, pending: Set<String>): List<FramePhoto> {
        val first = photos.filter { it.id in pending }.distinctBy { it.id }.sortedByDescending { it.discoveredMs }
        return first + normal.filterNot { it.id in pending }
    }
    /** Day of year on a leap-year basis, so 29 February and 1 March stay one day apart across years. */
    private fun basis(d: LocalDate): Int = if (d.isLeapYear || d.monthValue <= 2) d.dayOfYear else d.dayOfYear + 1

    /** Circular distance between two days of the year, so a window wraps across New Year and leap day. */
    fun distance(a: LocalDate, b: LocalDate): Int {
        val days = abs(basis(a) - basis(b))
        return min(days, 366 - days)
    }

    /**
     * Photos captured on and around today's date in earlier years. Building this costs about a third of a
     * second on a 15,000-photo album because every photo needs a calendar conversion, so the frame computes
     * it on a worker and hands the result to [playlist] rather than paying for it while drawing.
     */
    fun seasonal(photos: List<FramePhoto>, today: LocalDate, memoryDays: Int): List<FramePhoto> {
        val todayYear = today.year
        return photos.filter {
            val day = LocalDate.ofEpochDay(Math.floorDiv(it.takenMs + it.offsetMs, 86_400_000L))
            day.year < todayYear && distance(day, today) <= memoryDays
        }
    }

    // Equal turns for the deck and a +/-14-day seasonal pool.
    // Each pool is shuffled without replacement; dates are recalculated each day.
    fun playlist(photos: List<FramePhoto>, today: LocalDate, random: Random = Random.Default, recentCount: Int = 300, memoryDays: Int = 14, recentPercent: Int = 50,
                 seen: Map<String, PlayRecord> = emptyMap(), byRecency: List<FramePhoto> = photos.sortedByDescending { it.takenMs },
                 seasonalCandidates: List<FramePhoto>? = null): List<FramePhoto> {
        require(recentCount in 1..2000 && memoryDays in 0..60 && recentPercent in 0..100)
        if (photos.isEmpty()) return emptyList()
        // Seasonal candidates are the same photos as before; the pools are then built so a photo belongs to
        // one of them, which keeps the ratio exact.
        val seasonal = seasonalCandidates ?: seasonal(photos, today, memoryDays)
        // Built whenever seasonal photos exist; the 0% and 100% endpoints then choose one pool wholesale.
        val memories = seasonal.shuffled(random)
        // Seasonal photos belong to the memories slot alone: at 100% the owner has asked for no memories, and
        // letting them leak into the deck would make the slider mean nothing.
        val memoryIds = seasonal.map { it.id }.toHashSet()
        // The recent pool is a deck, not the newest slice: photos never shown, then the ones shown longest
        // ago, so a large album is covered instead of circling its newest few hundred.
        val latest = PhotoDeck.deck(if (memoryIds.isEmpty()) byRecency else byRecency.filter { it.id !in memoryIds }, seen, recentCount, random)
        if (memories.isEmpty() || recentPercent == 100) return latest
        if (recentPercent == 0) return memories
        // Ensure each pool has a full turn at ordinary ratios; cap very large cycles.
        val turns = maxOf(kotlin.math.ceil(latest.size * 100.0 / recentPercent).toInt(),
            kotlin.math.ceil(memories.size * 100.0 / (100-recentPercent)).toInt()).coerceAtMost(20000)
        val result = ArrayList<FramePhoto>(turns)
        var balance = 100-recentPercent
        var recentIndex = 0; var memoryIndex = 0
        repeat(turns) {
            balance += recentPercent
            if (balance >= 100) { balance -= 100; result.add(latest[recentIndex++ % latest.size]) }
            else result.add(memories[memoryIndex++ % memories.size])
        }
        return result
    }
}
