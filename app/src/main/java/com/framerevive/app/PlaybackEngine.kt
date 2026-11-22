package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random

/** The settings that shape one cycle of the playlist. [roundSize] is internal, not a setting a person chooses. */
data class CycleSettings(val roundSize: Int = PlaybackEngine.ROUND_SIZE, val memoryDays: Int = 14, val recentPercent: Int = 50, val recency: Recency = Recency.DEFAULT)

/** One built cycle, with what each stage held back so the controls page and the simulation can report it. */
data class CycleBuild(val playlist: List<FramePhoto>, val similarSkipped: Int, val burstHeld: Int, val deckNanos: Long,
                      /** Photos the cycle's thinning passes left out; no other presentation may take them either. */
                      val excluded: Set<String> = emptySet())

/**
 * Decides what comes next, with no Android types so it can run in a unit test and in the simulation. The
 * activity supplies the album, the play history and the cached analysis; this builds the cycle from them.
 */
object PlaybackEngine {
    /** Round size when no pace is known (tests and the synthetic simulation). The frame sizes its plan from its pace. */
    const val ROUND_SIZE = 300
    /** How many of the newest photos the "newest photos" collage source draws from. */
    const val RECENT_COLLAGE_POOL = 300

    /** The plan covers a day's photos with this much to spare, because skipping near-identical frames shortens a round by about a third. */
    const val PLAN_MARGIN = 1.6
    const val MIN_PLAN = 300
    const val MAX_PLAN = 2000

    /** Photos the frame shows in an awake day at its pace. */
    fun slotsPerDay(secondsPerPhoto: Int, awakeHours: Double): Double =
        if (secondsPerPhoto > 0 && awakeHours > 0) 3600.0 / secondsPerPhoto * awakeHours else 0.0

    /**
     * How many photos one plan lines up in total: an awake day's worth with a margin, kept between [MIN_PLAN] and
     * [MAX_PLAN]. The plan is rebuilt when the date changes (memories depend on it) or when it runs out, so a slower
     * frame does not carry a long plan it never finishes and a faster one is not rebuilt every few minutes.
     */
    fun plannedSlots(secondsPerPhoto: Int, awakeHours: Double): Int =
        Math.round(slotsPerDay(secondsPerPhoto, awakeHours) * PLAN_MARGIN).toInt().coerceIn(MIN_PLAN, MAX_PLAN)

    /** The recent-deck share of a plan of [planned] photos when the slider is at [recentPercent]; memories fill the rest. */
    fun roundSizeFor(planned: Int, recentPercent: Int): Int =
        (if (recentPercent <= 0 || recentPercent >= 100) planned else planned * recentPercent / 100).coerceIn(1, 2000)

    /**
     * Playlist, then one event pass (burst limit and near-duplicates), then the priority queue. Frames the
     * event pass leaves out are not recorded as shown, so they return in a later cycle.
     */
    fun buildCycle(photos: List<FramePhoto>, today: LocalDate, settings: CycleSettings, seen: Map<String, PlayRecord>,
                   byRecency: List<FramePhoto>, seasonal: List<FramePhoto>?, hashes: Map<String, Long>,
                   quality: (FramePhoto) -> Float?, priorityIds: Set<String>, random: Random = Random.Default,
                   /** Called for every photo in the album on the UI thread: resolve settings first, never read preferences or files in here. */
                   keep: (FramePhoto) -> Boolean = { true }): CycleBuild {
        val started = System.nanoTime()
        // Photos known to be too soft are not candidates, so they cannot fill slots that would then be skipped.
        val normal = PhotoSelection.playlist(photos, today, random, settings.roundSize, settings.memoryDays,
            settings.recentPercent, seen, byRecency.filter(keep), seasonal?.filter(keep), settings.recency)
        val deckNanos = System.nanoTime() - started
        val bursts = Bursts.thin(normal, seen, hashes = hashes, sharpness = quality)
        val excluded = normal.map { it.id }.toHashSet().apply { removeAll(bursts.kept.map { it.id }.toSet()) }
        return CycleBuild(PhotoSelection.prioritize(bursts.kept, photos, priorityIds), bursts.similar, bursts.heldBack, deckNanos, excluded)
    }
}
