package com.framerevive.app

import java.io.File
import kotlin.random.Random

/** How often a photo has been shown, and when it was last on the panel. */
data class PlayRecord(val lastShownMs: Long, val plays: Int)

data class DeckStats(val shown: Int, val unseen: Int, val total: Int) {
    val percentShown: Int get() = if (total == 0) 0 else (shown * 100.0 / total).roundToInt()
}

/**
 * The album as a deck of cards rather than a random draw. Shuffling the newest N photos on every rebuild
 * brings the same pictures back within minutes and leaves the rest of a large album unseen for good: with
 * 15,479 photos and a 300-photo cycle, most of them never reached the panel at all.
 *
 * Here a photo the frame has never shown comes first, then the one shown longest ago, so a cycle walks
 * through the album instead of circling its newest slice. Records survive restarts, which a shuffled list
 * cannot do.
 */
object PhotoDeck {
    /** Candidates considered for one cycle; the cycle itself is drawn from these. */
    const val WINDOW_MULTIPLIER = 4

    fun load(file: File): MutableMap<String, PlayRecord> {
        if (!file.exists()) return mutableMapOf()
        val out = HashMap<String, PlayRecord>(4096)
        return runCatching {
            file.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size == 3) out[parts[0]] = PlayRecord(parts[1].toLongOrNull() ?: 0L, parts[2].toIntOrNull() ?: 0)
            }
            out
        }.getOrElse { mutableMapOf() }
    }

    fun save(file: File, seen: Map<String, PlayRecord>) {
        // One tab-separated line per photo: smaller than JSON and parseable without Android's org.json.
        val text = StringBuilder(seen.size * 48)
        for ((id, record) in seen) text.append(id).append('\t').append(record.lastShownMs).append('\t').append(record.plays).append('\n')
        val bytes = text.toString().toByteArray()
        // Written beside the target and renamed, so a power cut cannot leave a half-written history.
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(file)) { file.writeBytes(bytes); temp.delete() }
    }

    fun recorded(seen: MutableMap<String, PlayRecord>, ids: Collection<String>, now: Long) {
        for (id in ids) {
            val previous = seen[id]
            seen[id] = PlayRecord(now, (previous?.plays ?: 0) + 1)
        }
    }

    /**
     * One cycle: the least recently shown photos, drawn from a window several times the cycle size so the
     * order still varies. Never-shown photos keep the album's recency order, so a fresh album opens with what
     * is in living memory and then works backwards.
     *
     * [byRecency] must already be sorted newest capture first. Building that order here cost 650 ms on the
     * frame's UI thread for a 15,479-photo album, so it is prepared once when the album is read.
     *
     * With a [weight], the photos not yet shown are drawn by that weight from the whole pool instead of from
     * the newest [window]; see [Recency]. Shown photos still return least recently shown first.
     */
    fun deck(byRecency: List<FramePhoto>, seen: Map<String, PlayRecord>, count: Int, random: Random = Random.Default,
             window: Int = count * WINDOW_MULTIPLIER, weight: ((FramePhoto) -> Double)? = null): List<FramePhoto> {
        if (byRecency.isEmpty() || count <= 0) return emptyList()
        // Nothing shown yet: the album's own order is the deck, with no need to partition 15,000 photos.
        if (seen.isEmpty()) return if (weight != null) draw(byRecency, count, weight, random)
            else byRecency.take(minOf(maxOf(window, count), byRecency.size)).shuffled(random).take(count)
        val unseen = byRecency.filter { it.id !in seen }
        val shown = byRecency.filter { it.id in seen }.sortedBy { seen[it.id]?.lastShownMs ?: 0L }
        // Unseen photos are taken first: shuffling a window that still contains shown photos would let them
        // push out photos that have never appeared, which is the case this deck exists to fix.
        if (unseen.size >= count) return if (weight != null) draw(unseen, count, weight, random)
            else unseen.take(window.coerceIn(count, unseen.size)).shuffled(random).take(count)
        return unseen.shuffled(random) + shown.take(count - unseen.size)
    }

    /**
     * [count] photos drawn without replacement, each with a chance in proportion to its weight (Efraimidis and
     * Spirakis: give each photo the key ln(u)/weight and keep the largest). The result is shuffled, so the
     * order carries no trace of the weights.
     */
    private fun draw(pool: List<FramePhoto>, count: Int, weight: (FramePhoto) -> Double, random: Random): List<FramePhoto> {
        if (pool.size <= count) return pool.shuffled(random)
        val keys = DoubleArray(pool.size) { i -> kotlin.math.ln(random.nextDouble().coerceAtLeast(1e-12)) / weight(pool[i]).coerceAtLeast(1e-9) }
        return pool.indices.sortedByDescending { keys[it] }.take(count).map { pool[it] }.shuffled(random)
    }

    fun stats(photos: List<FramePhoto>, seen: Map<String, PlayRecord>): DeckStats {
        var shown = 0
        for (photo in photos) if ((seen[photo.id]?.plays ?: 0) > 0) shown++
        return DeckStats(shown, photos.size - shown, photos.size)
    }

    /** Drops records for photos that have left the album, so the file cannot grow without bound. */
    fun pruned(seen: Map<String, PlayRecord>, albumIds: Set<String>): MutableMap<String, PlayRecord> {
        val out = HashMap<String, PlayRecord>(albumIds.size)
        for (id in albumIds) seen[id]?.let { out[id] = it }
        return out
    }
}

private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
