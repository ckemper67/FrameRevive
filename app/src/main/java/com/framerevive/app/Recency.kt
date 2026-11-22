package com.framerevive.app

import kotlin.math.exp

/**
 * How strongly the deck favours newer captures when it picks the unseen photos for a round.
 *
 * Without it the pick is cut off hard: only the newest few hundred unseen photos are candidates, so a round
 * is all recent photos until those run out. With it every unseen photo is a candidate and its chance falls
 * smoothly with age, never to zero, so older years surface from the first round. Photos the frame has never
 * shown still come before photos it has; this only decides which of the unseen ones.
 */
enum class Recency(val id: String, private val tauDays: Double, private val floor: Double) {
    /** The earlier behaviour: a hard window of the newest unseen photos. */
    NEWEST("newest", 0.0, 0.0),
    /** A photo's chance halves roughly every four months and bottoms out at 5% of a new photo's. */
    SOMEWHAT("somewhat", 180.0, 0.05),
    /** Every unseen photo is equally likely, whatever its year. */
    EVEN("even", 1.0, 1.0);

    /** Null means the hard window; otherwise the weight of a photo of [takenMs] on [todayMs] (plain arithmetic, no calendar). */
    fun weigher(todayMs: Long): ((FramePhoto) -> Double)? = when (this) {
        NEWEST -> null
        EVEN -> { _ -> 1.0 }
        else -> { p ->
            val ageDays = ((todayMs - (p.takenMs + p.offsetMs)) / DAY_MS.toDouble()).coerceAtLeast(0.0)
            floor + (1.0 - floor) * exp(-ageDays / tauDays)
        }
    }

    companion object {
        val DEFAULT = SOMEWHAT
        private const val DAY_MS = 86_400_000L
        fun of(id: String?): Recency = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
