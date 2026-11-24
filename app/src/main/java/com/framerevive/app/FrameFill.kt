package com.framerevive.app

/**
 * "Fill the screen" for one photo. A photo is cropped to fill the frame only when that cuts away no more than [MAX_CROP]
 * of it, the same limit a pair or collage tile uses. A photo whose shape is too far from the frame's stays whole with the
 * blurred background, so no photo is ever cropped hard.
 */
object FrameFill {
    const val MAX_CROP = CollageLayout.MAX_CROP

    /** The share of the photo cut away when it covers a [screenW] by [screenH] frame, from 0 (same shape) up to 1. */
    fun crop(photoW: Int, photoH: Int, screenW: Int, screenH: Int): Float {
        if (photoW <= 0 || photoH <= 0 || screenW <= 0 || screenH <= 0) return 1f
        val fit = minOf(screenW.toFloat() / photoW, screenH.toFloat() / photoH)
        val cover = maxOf(screenW.toFloat() / photoW, screenH.toFloat() / photoH)
        return 1f - fit / cover
    }

    fun fills(photoW: Int, photoH: Int, screenW: Int, screenH: Int): Boolean = crop(photoW, photoH, screenW, screenH) <= MAX_CROP
}
