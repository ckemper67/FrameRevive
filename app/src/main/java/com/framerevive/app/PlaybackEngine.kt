package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random

/** The settings that shape one cycle of the playlist. */
data class CycleSettings(val roundSize: Int = 300, val memoryDays: Int = 14, val recentPercent: Int = 50)

/** One built cycle, with what each stage held back so the controls page and the simulation can report it. */
data class CycleBuild(val playlist: List<FramePhoto>, val similarSkipped: Int, val burstHeld: Int, val deckNanos: Long,
                      /** Photos the cycle's thinning passes left out; no other presentation may take them either. */
                      val excluded: Set<String> = emptySet())

/**
 * Decides what comes next, with no Android types so it can run in a unit test and in the simulation. The
 * activity supplies the album, the play history and the cached analysis; this builds the cycle from them.
 */
object PlaybackEngine {
    /**
     * Playlist, then one event pass (burst limit and near-duplicates), then the priority queue. Frames the
     * event pass leaves out are not recorded as shown, so they return in a later cycle.
     */
    fun buildCycle(photos: List<FramePhoto>, today: LocalDate, settings: CycleSettings, seen: Map<String, PlayRecord>,
                   byRecency: List<FramePhoto>, seasonal: List<FramePhoto>?, hashes: Map<String, Long>,
                   quality: (FramePhoto) -> Float?, priorityIds: Set<String>, random: Random = Random.Default): CycleBuild {
        val started = System.nanoTime()
        val normal = PhotoSelection.playlist(photos, today, random, settings.roundSize, settings.memoryDays,
            settings.recentPercent, seen, byRecency, seasonal)
        val deckNanos = System.nanoTime() - started
        val bursts = Bursts.thin(normal, seen, hashes = hashes, sharpness = quality)
        val excluded = normal.map { it.id }.toHashSet().apply { removeAll(bursts.kept.map { it.id }.toSet()) }
        return CycleBuild(PhotoSelection.prioritize(bursts.kept, photos, priorityIds), bursts.similar, bursts.heldBack, deckNanos, excluded)
    }
}
