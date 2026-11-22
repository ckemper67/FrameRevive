package com.framerevive.app

/** Frames captured close together: one party, one walk, one wave breaking. */
data class Event(val photos: List<FramePhoto>) {
    val startMs: Long get() = photos.first().takenMs
    val isBurst: Boolean get() = photos.size >= Bursts.MIN_EVENT
}

/** [heldBack] counts frames over an event's limit; [similar] counts frames left out as repeats of one kept. */
data class BurstResult(val events: List<Event>, val kept: List<FramePhoto>, val heldBack: Int, val similar: Int = 0)

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
 *
 * With image hashes an event is also refined by what the frames look like: a frame that repeats one already
 * kept from its event is left out, so ten shots of one moment give one frame, not three near-identical ones.
 * Across events only an exact repeat (the same picture saved twice) is dropped, which keeps the pass linear.
 */
object Bursts {
    /** Silence longer than this starts a new event. */
    const val MAX_GAP_MS = 60L * 60L * 1000L
    /** Fewer frames than this is an ordinary sequence, not a burst. */
    const val MIN_EVENT = 3
    /** Frames of one burst allowed into a single cycle. */
    const val KEEP = 3
    /** Frames captured this close together count as the same picture, whether or not a hash says so. */
    const val MIN_SEPARATION_MS = 2L * 60L * 1000L

    /** Events in capture order. Input need not be sorted; photos without a timestamp are kept as singletons. */
    fun events(photos: List<FramePhoto>, maxGapMs: Long = MAX_GAP_MS): List<Event> {
        if (photos.isEmpty()) return emptyList()
        // Absolute capture time, not the local clock: when the phone changes timezone between two photos taken minutes
        // apart, the local gap jumps by hours and would split one event in two.
        val dated = photos.filter { it.dated }.sortedBy { it.takenMs }
        val undated = photos.filterNot { it.dated }
        val out = ArrayList<Event>()
        var start = 0
        for (i in 1..dated.size) {
            val gap = if (i == dated.size) Long.MAX_VALUE else dated[i].takenMs - dated[i - 1].takenMs
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
    /** Capture times of the frames of [event] already allowed into this cycle. */
    private fun allowedTimes(event: Event, allowed: Set<String>): List<Long> = event.photos.filter { it.id in allowed }.map { it.takenMs }

    /** The pinned photos worth showing: none within [MIN_SEPARATION_MS] of an earlier one, and no near-identical pair. */
    private fun apartFrom(pins: List<FramePhoto>, hashes: Map<String, Long>, threshold: Int): List<FramePhoto> {
        val kept = ArrayList<FramePhoto>(pins.size)
        for (p in pins.sortedBy { it.takenMs }) {
            val hash = hashes[p.id]
            val same = kept.any { k ->
                (p.dated && k.dated && kotlin.math.abs(p.takenMs - k.takenMs) < MIN_SEPARATION_MS) ||
                    (hash != null && hashes[k.id]?.let { PhotoSimilarity.near(hash, it, threshold) } == true)
            }
            if (!same) kept += p
        }
        return kept
    }

    fun thin(cycle: List<FramePhoto>, seen: Map<String, PlayRecord>, keep: Int = KEEP, maxGapMs: Long = MAX_GAP_MS,
             hashes: Map<String, Long> = emptyMap(), threshold: Int = PhotoSimilarity.THRESHOLD, pinned: Set<String> = emptySet(),
             sharpness: (FramePhoto) -> Float?): BurstResult {
        if (cycle.size < MIN_EVENT && hashes.isEmpty()) return BurstResult(events(cycle, maxGapMs), cycle, 0)
        val events = events(cycle, maxGapMs)
        val allowed = HashSet<String>(cycle.size)
        var held = 0; var similar = 0
        for (full in events) {
            // A pinned photo (a favorite) always plays; the others in its event keep away from it like from any shown frame.
            val pins = if (pinned.isEmpty()) emptyList() else apartFrom(full.photos.filter { it.id in pinned }, hashes, threshold)
            pins.forEach { allowed += it.id }
            val pinIds = pins.map { it.id }.toHashSet()
            // Pinned photos that are the same picture as another pinned one go back to the event and are thinned like any frame.
            val event = if (pins.isEmpty()) full else Event(full.photos.filter { it.id !in pinIds })
            val limited = full.isBurst && event.photos.size + pins.size > keep
            if (!limited && pins.isEmpty() && event.photos.size < 2) {
                event.photos.forEach { allowed += it.id }
                continue
            }
            val chosenHashes = ArrayList<Long>(keep)
            pins.forEach { p -> hashes[p.id]?.let { chosenHashes += it } }
            val pinnedTimes = pins.filter { it.dated }.map { it.takenMs }
            val budget = (keep - pins.size).coerceAtLeast(0)
            if (limited) {
                // Rank by "not yet shown", then sharpness, which decides the first pick and every tie. After that each
                // pick is the frame farthest in time from those already chosen and from frames of this event already
                // shown, so a burst gives frames from different stretches of it, not neighbours of one instant. Sharpness
                // is known for few photos, so the rank alone would pick the earliest frames in time order.
                val remaining = ArrayList(event.photos.sortedWith(
                    compareBy<FramePhoto> { if ((seen[it.id]?.plays ?: 0) > 0) 1 else 0 }
                        .thenByDescending { sharpness(it) ?: -1f }))
                val anchors = remaining.filter { (seen[it.id]?.plays ?: 0) > 0 }.map { it.takenMs } + pinnedTimes
                var chosen = 0
                while (chosen < budget && remaining.isNotEmpty()) {
                    val unseen = remaining.filter { (seen[it.id]?.plays ?: 0) == 0 }
                    val reference = anchors + allowedTimes(event, allowed)
                    // Frames within MIN_SEPARATION_MS of one already chosen or shown are the same picture; leave them for a later cycle.
                    val apart = { p: FramePhoto -> reference.none { kotlin.math.abs(p.takenMs - it) < MIN_SEPARATION_MS } }
                    val pool = unseen.filter(apart).ifEmpty { if (unseen.isEmpty()) remaining.filter(apart) else emptyList() }
                    if (pool.isEmpty()) break
                    val next = if (reference.isEmpty()) pool.first() else pool.maxByOrNull { p -> reference.minOf { kotlin.math.abs(p.takenMs - it) } }!!
                    remaining.remove(next)
                    val hash = hashes[next.id]
                    if (hash != null && chosenHashes.any { PhotoSimilarity.near(hash, it, threshold) }) { similar++; continue }
                    allowed += next.id; chosen++
                    if (hash != null) chosenHashes += hash
                }
                // What is left was either the same picture as a chosen or shown frame (by hash or by time) or simply over the limit.
                val settled = anchors + allowedTimes(event, allowed)
                for (photo in remaining) {
                    val hash = hashes[photo.id]
                    val same = (hash != null && chosenHashes.any { PhotoSimilarity.near(hash, it, threshold) }) ||
                        settled.any { kotlin.math.abs(photo.takenMs - it) < MIN_SEPARATION_MS }
                    if (same) similar++ else held++
                }
                continue
            }
            var lastKept: Long? = null
            for (photo in event.photos) {
                if (photo.dated && pinnedTimes.any { kotlin.math.abs(photo.takenMs - it) < MIN_SEPARATION_MS }) { similar++; continue }
                val hash = hashes[photo.id]
                if (hash != null && chosenHashes.any { PhotoSimilarity.near(hash, it, threshold) }) { similar++; continue }
                if (photo.dated && lastKept?.let { photo.takenMs - it < MIN_SEPARATION_MS } == true) { similar++; continue }
                allowed += photo.id
                if (photo.dated) lastKept = photo.takenMs
                if (hash != null) chosenHashes += hash
            }
        }
        // The same picture saved twice lands in two events; only its first copy plays this cycle.
        if (hashes.isNotEmpty()) {
            val exact = HashSet<Long>()
            // Pinned photos claim their picture first, so the copy that is dropped is never the favorite.
            for (photo in cycle) if (photo.id in pinned && photo.id in allowed) hashes[photo.id]?.let { exact.add(it) }
            for (photo in cycle) {
                if (photo.id !in allowed || photo.id in pinned) continue
                val hash = hashes[photo.id] ?: continue
                if (!exact.add(hash)) { allowed -= photo.id; similar++ }
            }
        }
        if (held == 0 && similar == 0) return BurstResult(events, cycle, 0)
        return BurstResult(events, cycle.filter { it.id in allowed }, held, similar)
    }
}
