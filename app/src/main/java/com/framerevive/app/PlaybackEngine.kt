package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random

/** The settings that shape one cycle of the playlist. [roundSize] is internal, not a setting a person chooses. */
data class CycleSettings(val roundSize: Int = PlaybackEngine.ROUND_SIZE, val memoryDays: Int = 14, val recentPercent: Int = 50, val recency: Recency = Recency.DEFAULT,
                         /** Percent of the plan given to favorites, whatever their number; see [Favorites]. */
                         val favoritePercent: Int = 0,
                         /** The screen's size when Show Pair is on, so the plan can put photos that would not fill it into pairs; 0 leaves the plan as chosen. */
                         val frameWidth: Int = 0, val frameHeight: Int = 0)

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

    /** The most the owner can give favorites: a fifth of the plan keeps the rest of the mix recognisable. */
    const val MAX_FAVORITE_PERCENT = 20

    /**
     * The favorites for one plan: a deck, so none plays twice within the plan and the ones shown longest ago come first,
     * as many as [share] percent of the plan. The count does not depend on how many favorites there are, except that a
     * list shorter than that share shows each once per plan rather than repeating a photo within hours.
     */
    fun favoriteDeck(pool: List<FramePhoto>, seen: Map<String, PlayRecord>, settings: CycleSettings, share: Int, random: Random): List<FramePhoto> {
        val total = settings.roundSize + PhotoSelection.memorySlots(settings.roundSize, settings.recentPercent)
        val wanted = favoriteSlots(total, share)
        // The deck's order over the whole list, then each favorite taken unless it is the same moment as one already taken,
        // so a party of starred photos gives its slots to different moments instead of losing them to thinning later.
        val order = PhotoDeck.deck(pool.sortedByDescending { it.takenMs }, seen, pool.size, random)
        val chosen = ArrayList<FramePhoto>(wanted)
        for (p in order) {
            if (chosen.size >= wanted) break
            if (p.dated && chosen.any { it.dated && kotlin.math.abs(it.takenMs - p.takenMs) < Bursts.MIN_SEPARATION_MS }) continue
            chosen += p
        }
        return chosen
    }

    /**
     * Thinning leaves out about a sixth of the other photos (measured on a real album) and never a favorite, so the
     * share is taken of what will actually be shown, not of the plan, or 5 percent would show as about 6.
     */
    const val THINNING_SURVIVAL = 0.85

    /** Slots a plan of [planned] photos gives to favorites at [percent]. */
    fun favoriteSlots(planned: Int, percent: Int): Int = Math.round(planned * THINNING_SURVIVAL * percent.coerceIn(0, MAX_FAVORITE_PERCENT) / 100.0).toInt()

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
                   keep: (FramePhoto) -> Boolean = { true },
                   /** Photo ids the owner starred. They are drawn as their own share of the plan and are exempt from [keep]. */
                   favorites: Set<String> = emptySet()): CycleBuild {
        val started = System.nanoTime()
        val share = settings.favoritePercent.coerceIn(0, MAX_FAVORITE_PERCENT)
        val favoritePool = if (share == 0 || favorites.isEmpty()) emptyList() else photos.filter { it.id in favorites }.distinctBy { it.id }
        // Favorites have their own slots, so the other pools neither draw them nor spend their own slots on them.
        val taken = favoritePool.map { it.id }.toHashSet()
        val favoriteCards = if (taken.isEmpty()) emptyList() else favoriteDeck(favoritePool, seen, settings, share, random)
        // Slots a short list cannot fill go back to the other pools, so the plan keeps its length.
        val used = if (favoriteCards.isEmpty()) 0.0 else (favoriteCards.size * 100.0 / (settings.roundSize + PhotoSelection.memorySlots(settings.roundSize, settings.recentPercent))).coerceAtMost(share.toDouble())
        val rest = if (taken.isEmpty()) settings else settings.copy(roundSize = Math.round(settings.roundSize * (1 - used / 100)).toInt().coerceAtLeast(1))
        val recentPool = byRecency.filter(keep).let { if (taken.isEmpty()) it else it.filter { p -> p.id !in taken } }
        val memoryPool = seasonal?.filter(keep).let { if (taken.isEmpty() || it == null) it else it.filter { p -> p.id !in taken } }
        // Photos known to be too soft are not candidates, so they cannot fill slots that would then be skipped.
        val plain = PhotoSelection.playlist(photos, today, random, rest.roundSize, rest.memoryDays,
            rest.recentPercent, seen, recentPool, memoryPool, rest.recency)
        val normal = if (favoritePool.isEmpty()) plain else PhotoSelection.interleave(plain, favoriteCards)
        val deckNanos = System.nanoTime() - started
        val bursts = Bursts.thin(normal, seen, hashes = hashes, sharpness = quality, pinned = taken)
        val excluded = normal.map { it.id }.toHashSet().apply { removeAll(bursts.kept.map { it.id }.toSet()) }
        val ordered = PhotoSelection.prioritize(bursts.kept, photos, priorityIds)
        val arranged = PairPlan.arrange(ordered, settings.frameWidth, settings.frameHeight, {
            val inPlan = ordered.mapTo(HashSet()) { it.id }
            // Partners to borrow: photos not in the plan that are due soonest, never shown first, and not known to be soft.
            photos.asSequence().filter { it.sized && it.id !in inPlan && PairSelection.misfit(settings.frameWidth, settings.frameHeight, CollageLayout.Size(it.width, it.height)) && keep(it) }
                .sortedBy { seen[it.id]?.lastShownMs ?: 0L }.take(PairPlan.POOL).toList()
        })
        return CycleBuild(arranged, bursts.similar, bursts.heldBack, deckNanos, excluded)
    }
}
