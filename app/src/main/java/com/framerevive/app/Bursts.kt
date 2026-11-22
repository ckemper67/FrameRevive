package com.framerevive.app

/** Frames captured close together: one party, one walk, one wave breaking. */
data class Event(val photos: List<FramePhoto>) {
    val startMs: Long get() = photos.first().takenMs
    val isBurst: Boolean get() = photos.size >= Bursts.MIN_EVENT
}

data class BurstResult(val events: List<Event>, val kept: List<FramePhoto>, val heldBack: Int)

/**
 * Groups the album by capture time and lets only a few frames of a burst into a cycle.
 *
 * Upstream tried replaying bursts as a flipbook and dropped it: near-identical frames in sequence are
 * unwatchable, and timestamps alone cannot tell a burst from a scene change. Grouping is still useful for the
 * opposite job - keeping a single afternoon from filling the queue with forty shots of the same moment.
 *
 * Which frames survive is decided by coverage first: frames that have never been shown come before frames
 * that have, so a burst is revealed a few frames per cycle instead of being reduced to its sharpest shot and
 * forgotten. Sharpness only breaks ties inside each of those groups.
 */
object Bursts {
    /** Silence longer than this starts a new event. */
    const val MAX_GAP_MS = 60L * 60L * 1000L
    /** Fewer frames than this is an ordinary sequence, not a burst. */
    const val MIN_EVENT = 3
    /** Frames of one burst allowed into a single cycle. */
    const val KEEP = 3

    /** Events in capture order. Input need not be sorted; photos without a timestamp are kept as singletons. */
    fun events(photos: List<FramePhoto>, maxGapMs: Long = MAX_GAP_MS): List<Event> {
        if (photos.isEmpty()) return emptyList()
        val dated = photos.filter { it.dated }.sortedBy { it.takenMs + it.offsetMs }
        val undated = photos.filterNot { it.dated }
        val out = ArrayList<Event>()
        var start = 0
        for (i in 1..dated.size) {
            val gap = if (i == dated.size) Long.MAX_VALUE else
                (dated[i].takenMs + dated[i].offsetMs) - (dated[i - 1].takenMs + dated[i - 1].offsetMs)
            if (gap > maxGapMs) {
                if (i > start) out += Event(dated.subList(start, i).toList())
                start = i
            }
        }
        for (photo in undated) out += Event(listOf(photo))
        return out
    }

    /**
     * A cycle with the over-represented frames held back. Order is preserved, so the deck's decisions survive
     * this pass; held-back frames are simply left out of this cycle and stay eligible for the next one.
     */
    fun thin(cycle: List<FramePhoto>, seen: Map<String, PlayRecord>, keep: Int = KEEP, maxGapMs: Long = MAX_GAP_MS,
             sharpness: (FramePhoto) -> Float?): BurstResult {
        if (cycle.size < MIN_EVENT) return BurstResult(events(cycle, maxGapMs), cycle, 0)
        val events = events(cycle, maxGapMs)
        val allowed = HashSet<String>(cycle.size)
        var held = 0
        for (event in events) {
            if (!event.isBurst || event.photos.size <= keep) {
                event.photos.forEach { allowed += it.id }
                continue
            }
            val ranked = event.photos.sortedWith(
                compareBy<FramePhoto> { if ((seen[it.id]?.plays ?: 0) > 0) 1 else 0 }
                    .thenByDescending { sharpness(it) ?: -1f })
            ranked.take(keep).forEach { allowed += it.id }
            held += event.photos.size - keep
        }
        if (held == 0) return BurstResult(events, cycle, 0)
        return BurstResult(events, cycle.filter { it.id in allowed }, held)
    }
}
