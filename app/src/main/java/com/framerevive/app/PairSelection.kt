package com.framerevive.app

enum class FrameOrientation { PORTRAIT, LANDSCAPE }

/**
 * Show Pair: two photos whose orientation is opposite the frame fill the screen, the way Lenovo's frame
 * did. Photos that already match the frame stay single, and pairs needing more than [MAX_CROP] of each
 * photo are refused so very wide panoramas keep their whole frame instead of being cut down.
 */
object PairSelection {
    const val MAX_CROP = CollageLayout.MAX_CROP
    /** How far ahead a partner is looked for. Albums are mostly one orientation, so the other kind can be far apart. */
    const val LOOKAHEAD = 30

    fun orientation(width: Int, height: Int): FrameOrientation =
        if (height > width) FrameOrientation.PORTRAIT else FrameOrientation.LANDSCAPE

    fun pairable(frame: FrameOrientation, size: CollageLayout.Size): Boolean =
        if (frame == FrameOrientation.PORTRAIT) size.width > size.height else size.height > size.width

    /** Whether a photo of [size] is the wrong way round for the frame, which is what a pair exists for. */
    fun wrongOrientation(frame: FrameOrientation, size: CollageLayout.Size?): Boolean = size != null && pairable(frame, size)

    /**
     * The first of [candidates] that pairs with [leader] within the crop limit and is [ready], judged by the sizes alone
     * so a partner can be chosen, and downloaded, before it is needed.
     */
    fun partnerFor(width: Int, height: Int, leader: FramePhoto, candidates: List<FramePhoto>, size: (FramePhoto) -> CollageLayout.Size?,
                   avoid: Set<String> = emptySet(), maxCrop: Float = MAX_CROP, lookahead: Int = LOOKAHEAD,
                   ready: (FramePhoto) -> Boolean = { true }): FramePhoto? {
        val frame = orientation(width, height)
        val leaderSize = size(leader)?.takeIf { pairable(frame, it) } ?: return null
        for (partner in candidates.asSequence().filter { it.id != leader.id && it.id !in avoid }.take(lookahead)) {
            if (!ready(partner)) continue
            val partnerSize = size(partner)?.takeIf { pairable(frame, it) } ?: continue
            if (CollageLayout.pair(width, height, leaderSize, partnerSize, frame == FrameOrientation.PORTRAIT).crop <= maxCrop) return partner
        }
        return null
    }

    /**
     * Returns the leading upcoming photo with the first ready partner that fills the frame within the
     * crop limit, or nothing to leave the ordinary single photo in place.
     */
    fun choose(width: Int, height: Int, upcoming: List<FramePhoto>, size: (FramePhoto) -> CollageLayout.Size?,
               ready: (FramePhoto) -> Boolean, avoid: Set<String>, maxCrop: Float = MAX_CROP,
               lookahead: Int = LOOKAHEAD): List<FramePhoto> {
        val frame = orientation(width, height)
        val candidates = upcoming.asSequence().filter { it.id !in avoid }.take(lookahead).toList()
        val leader = candidates.firstOrNull() ?: return emptyList()
        if (!ready(leader)) return emptyList()
        val leaderSize = size(leader)?.takeIf { pairable(frame, it) } ?: return emptyList()
        for (partner in candidates.drop(1)) {
            if (partner.id == leader.id || !ready(partner)) continue
            val partnerSize = size(partner) ?: continue
            if (!pairable(frame, partnerSize)) continue
            val layout = CollageLayout.pair(width, height, leaderSize, partnerSize, frame == FrameOrientation.PORTRAIT)
            if (layout.crop <= maxCrop) return listOf(leader, partner)
        }
        return emptyList()
    }
}
