package com.framerevive.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.min
import kotlin.random.Random

/**
 * One album entry. [width] and [height] are the original's pixels (0 when the album did not say), so orientation is known
 * without a download. [key] is Google's content key: two entries with the same key are the same original file, even in
 * two different albums. [video] marks an entry that is a video. [addedMs] is when Google says the entry was added (0 if unknown).
 */
data class FramePhoto(val id: String, val url: String, val takenMs: Long, val offsetMs: Long = 0, val discoveredMs: Long = 0,
                      val width: Int = 0, val height: Int = 0, val key: String = "", val video: Boolean = false, val addedMs: Long = 0) {
    val sized: Boolean get() = width > 0 && height > 0
    val landscape: Boolean get() = sized && width > height
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
     * Photos captured on and around today's date in earlier years, in album order. The days come from the shared [DayIndex]
     * (millisecond arithmetic, the same "on this day" the "On this day" collage theme uses), so there is no calendar
     * conversion per photo; pass the index the frame already built for this catalog to save grouping the album again.
     * It differs from the circular day-of-year distance this used (kept as [distance]) only by a day at the edge of a
     * window around the end of February, and it no longer counts an undated photo (stored as 1970) as a memory.
     */
    fun seasonal(photos: List<FramePhoto>, today: LocalDate, memoryDays: Int, index: DayIndex = DayIndex(photos)): List<FramePhoto> {
        val days = index.onThisDay(today, memoryDays).keys
        if (days.isEmpty()) return emptyList()
        return photos.filter { it.dated && DayIndex.dayOf(it) in days }
    }

    /** Longest cycle a playlist may reach, however extreme the slider. */
    const val MAX_CYCLE = 20000

    /**
     * Memory slots a round asks for next to [roundSize] recent ones when the slider is at [recentPercent]. A pool
     * too small to fill them contributes only what it has. The ends (0 and 100 percent) pick one pool outright and
     * ask for none here.
     */
    fun memorySlots(roundSize: Int, recentPercent: Int): Int =
        if (recentPercent <= 0 || recentPercent >= 100) 0
        else Math.round(roundSize * (100.0 - recentPercent) / recentPercent).toInt().coerceIn(0, MAX_CYCLE - roundSize)

    /**
     * One cycle: a deck of recent photos and a deck of seasonal memories, spread evenly through each other.
     * Both pools are decks, so within a cycle no photo plays twice. The slider sets how many memory slots a
     * cycle would like; a pool too small to fill them contributes only what it has, and the shortfall goes
     * back to the recent deck instead of replaying the same few memories to hold the ratio.
     */
    fun playlist(photos: List<FramePhoto>, today: LocalDate, random: Random = Random.Default, roundSize: Int = 300, memoryDays: Int = 14, recentPercent: Int = 50,
                 seen: Map<String, PlayRecord> = emptyMap(), byRecency: List<FramePhoto> = photos.sortedByDescending { it.takenMs },
                 seasonalCandidates: List<FramePhoto>? = null, recency: Recency = Recency.DEFAULT): List<FramePhoto> {
        require(roundSize in 1..2000 && memoryDays in 0..60 && recentPercent in 0..100)
        if (photos.isEmpty()) return emptyList()
        val seasonal = seasonalCandidates ?: seasonal(photos, today, memoryDays)
        // Seasonal photos belong to the memories slot alone: at 100% the owner has asked for no memories, and
        // letting them leak into the deck would make the slider mean nothing.
        val memoryIds = seasonal.map { it.id }.toHashSet()
        // The recent pool is a deck, not the newest slice: photos never shown, then the ones shown longest
        // ago, so a large album is covered instead of circling its newest few hundred.
        val pool = if (memoryIds.isEmpty()) byRecency else byRecency.filter { it.id !in memoryIds }
        val weigher = recency.weigher(today.toEpochDay() * 86_400_000L)
        if (seasonal.isEmpty() || recentPercent == 100) return PhotoDeck.deck(pool, seen, roundSize, random, weight = weigher)
        val memoryOrder = seasonal.sortedByDescending { it.takenMs }
        if (recentPercent == 0) return PhotoDeck.deck(memoryOrder, seen, memoryOrder.size, random)
        val wanted = memorySlots(roundSize, recentPercent)
        val memories = PhotoDeck.deck(memoryOrder, seen, wanted, random)
        val latest = PhotoDeck.deck(pool, seen, (roundSize + wanted - memories.size).coerceAtMost(MAX_CYCLE - memories.size), random, weight = weigher)
        return interleave(latest, memories)
    }

    /** Spreads [memories] evenly through [latest], each in its own order and each used once. */
    fun interleave(latest: List<FramePhoto>, memories: List<FramePhoto>): List<FramePhoto> {
        val total = latest.size + memories.size
        val out = ArrayList<FramePhoto>(total)
        var balance = 0; var recent = 0; var memory = 0
        repeat(total) {
            balance += memories.size
            if (balance >= total && memory < memories.size) { balance -= total; out.add(memories[memory++]) }
            else if (recent < latest.size) out.add(latest[recent++])
            else out.add(memories[memory++])
        }
        return out
    }
}
