package com.framerevive.app

/**
 * The second shared album, "Frame favorites". Its photos are favorites as they are: each has its own id and picture, so
 * nothing has to be matched to the main album to show one. The photo ids differ between albums, so the only use of the
 * capture time is to avoid showing a picture twice: a favorites photo whose capture time belongs to exactly one photo of
 * the main album hides that twin. A time several photos share (a burst) hides nothing, and the worst case is a repeat.
 */
object FavoriteAlbum {
    /** [photos] is what playback works from; [live] counts favorites shown from the album, [hidden] the main twins they replace. */
    class Merged(val photos: List<FramePhoto>, val live: Int, val hidden: Int)

    /**
     * The main album without the twins of live favorites, plus the live favorites. A vetoed photo (un-starred here) is
     * left out, and its twin is not hidden, so nothing else changes.
     */
    fun merge(main: List<FramePhoto>, album: List<FramePhoto>, vetoed: Set<String>): Merged {
        val mainIds = main.mapTo(HashSet(main.size * 2)) { it.id }
        val live = album.filter { it.id !in vetoed && it.id !in mainIds }
        if (live.isEmpty()) return Merged(main, 0, 0)
        val mainByTime = HashMap<Long, Int>(main.size * 2)
        for (p in main) if (p.dated) mainByTime.merge(p.takenMs, 1, Int::plus)
        val twinTimes = live.filter { it.dated && mainByTime[it.takenMs] == 1 }.mapTo(HashSet()) { it.takenMs }
        val kept = if (twinTimes.isEmpty()) main else main.filter { !(it.dated && it.takenMs in twinTimes) }
        return Merged(kept + live, live.size, main.size - kept.size)
    }
}
