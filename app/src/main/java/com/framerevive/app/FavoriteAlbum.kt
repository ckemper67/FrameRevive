package com.framerevive.app

/**
 * The second shared album, "Frame favorites". Its photos are favorites as they are: each has its own id and picture, so
 * nothing has to be matched to the main album to show one. The two albums give a photo different ids, so a picture
 * would show twice without a check: a favorite hides its twin in the main album.
 *
 * The twin is the main photo with the same content key, Google's identity for an original file, which is the same in
 * every album and tells the frames of a burst apart. A favorite without a key (an album read before keys were kept)
 * falls back to the capture time: it hides the main photo only when exactly one has that time, and a time several
 * photos share hides nothing, so the worst case is a repeat.
 */
object FavoriteAlbum {
    /** [photos] without its videos; the same list when it has none, so the usual case copies nothing. */
    private fun stills(photos: List<FramePhoto>) = if (photos.any { it.video }) photos.filter { !it.video } else photos

    /** [photos] is what playback works from; [live] counts favorites shown from the album, [hidden] the main twins they replace. */
    class Merged(val photos: List<FramePhoto>, val live: Int, val hidden: Int)

    /**
     * The main album without the twins of live favorites, plus the live favorites. A vetoed photo (un-starred here) is
     * left out, and its twin is not hidden, so nothing else changes. Videos are left out of both albums: the frame shows
     * pictures, and the still frame of a video is a poor one.
     */
    fun merge(mainAlbum: List<FramePhoto>, favorites: List<FramePhoto>, vetoed: Set<String>): Merged {
        val main = stills(mainAlbum)
        val album = stills(favorites)
        val mainIds = main.mapTo(HashSet(main.size * 2)) { it.id }
        val live = album.filter { it.id !in vetoed && it.id !in mainIds }
        if (live.isEmpty()) return Merged(main, 0, 0)
        val keys = live.filter { it.key.isNotEmpty() }.mapTo(HashSet()) { it.key }
        val mainByTime = HashMap<Long, Int>(main.size * 2)
        for (p in main) if (p.dated) mainByTime.merge(p.takenMs, 1, Int::plus)
        val twinTimes = live.filter { it.key.isEmpty() && it.dated && mainByTime[it.takenMs] == 1 }.mapTo(HashSet()) { it.takenMs }
        val kept = if (keys.isEmpty() && twinTimes.isEmpty()) main
            else main.filter { !((it.key.isNotEmpty() && it.key in keys) || (it.dated && it.takenMs in twinTimes)) }
        return Merged(kept + live, live.size, main.size - kept.size)
    }
}
