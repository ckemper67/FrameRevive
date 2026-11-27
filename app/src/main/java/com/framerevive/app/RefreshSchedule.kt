package com.framerevive.app

/**
 * When the album is read in full. It is read every hour; a restart (an install, a crash) used to read it again at once,
 * which takes minutes on this frame and is wasted when the catalog on disk is minutes old. The count check, which asks
 * the album's size every two minutes, still notices a changed album at once and starts a read of its own.
 */
object RefreshSchedule {
    const val INTERVAL_MS = 60L * 60L * 1000L

    /** How long after a start the first full read should wait: what is left of the hour since the last good read. */
    fun firstDelay(nowMs: Long, lastSuccessMs: Long, haveCatalog: Boolean): Long {
        if (!haveCatalog || lastSuccessMs <= 0L) return 0L
        val since = nowMs - lastSuccessMs
        return if (since in 0 until INTERVAL_MS) INTERVAL_MS - since else 0L
    }
}
