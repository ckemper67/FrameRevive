package com.framerevive.app

/**
 * Arranges a cycle for a frame that fills its screen: photos that would not fill it alone (a landscape photo on a portrait
 * frame, a square one) go in compatible pairs, side by side, so nothing has to wait for a partner at show time.
 *
 * A photo takes the nearest later one that pairs with it within the crop limit and the two are placed together where the
 * first stood. A photo with no partner in the cycle borrows one from the album, the longest unseen first, so no photo is
 * left out for want of a partner unless the whole album has none that fits.
 */
object PairPlan {
    /** How many borrowing candidates are looked at, nearest to being due first. */
    const val POOL = 300

    /**
     * [playlist] reordered, and possibly lengthened by borrowed partners. A frame size of zero, or photos whose size is
     * unknown, leave the plan as it was. [pool] is asked for only when a photo has no partner in the cycle.
     */
    fun arrange(playlist: List<FramePhoto>, width: Int, height: Int, pool: () -> List<FramePhoto>, maxCrop: Float = PairSelection.MAX_CROP): List<FramePhoto> {
        if (width <= 0 || height <= 0) return playlist
        val portrait = height > width
        fun size(p: FramePhoto) = CollageLayout.Size(p.width, p.height)
        fun misfit(p: FramePhoto) = p.sized && PairSelection.misfit(width, height, size(p))
        fun fits(a: FramePhoto, b: FramePhoto) = CollageLayout.pair(width, height, size(a), size(b), portrait).crop <= maxCrop

        val misfits = playlist.filter(::misfit)
        if (misfits.isEmpty()) return playlist
        val partner = HashMap<String, FramePhoto>()   // first of a pair -> the photo placed right after it
        val paired = HashSet<String>()                // every misfit that has a partner, in the cycle or borrowed
        for ((i, first) in misfits.withIndex()) {
            if (first.id in paired) continue
            val second = (i + 1 until misfits.size).map { misfits[it] }.firstOrNull { it.id !in paired && fits(first, it) } ?: continue
            partner[first.id] = second; paired += first.id; paired += second.id
        }
        val alone = misfits.filter { it.id !in paired }
        if (alone.isNotEmpty()) {
            val inCycle = playlist.mapTo(HashSet()) { it.id }
            val borrowable = pool().filter { it.id !in inCycle && misfit(it) }
            val taken = HashSet<String>()
            for (first in alone) {
                val second = borrowable.firstOrNull { it.id !in taken && fits(first, it) } ?: continue
                partner[first.id] = second; taken += second.id; paired += first.id
            }
        }
        val placedBesideItsFirst = partner.values.mapTo(HashSet()) { it.id }
        val out = ArrayList<FramePhoto>(playlist.size + alone.size)
        for (p in playlist) {
            if (p.id in placedBesideItsFirst) continue                  // it goes where its first stands
            if (misfit(p) && p.id !in paired) continue                  // no partner anywhere: left out of this cycle
            out += p
            partner[p.id]?.let { out += it }
        }
        return out
    }
}
