package com.framerevive.app

import android.content.Context
import android.graphics.Canvas
import android.view.View

/** A star's top-right corner and its size, in screen pixels. */
data class StarSpot(val x: Float, val y: Float, val size: Float)

/** Where the favorite stars of a collage or pair go: the top-right corner of each favorite's own tile, sized to the tile. */
object FavoriteStars {
    /** A star is this share of its tile's shorter side, between the given minimum and maximum, with a quarter of its size as margin. */
    const val SHARE = 0.08f

    /**
     * [panels] are in the collage's output size, [scale] turns that into screen pixels, and [photos] is indexed the way
     * the panels refer to it. A small tile gets a small star, so it never covers much of the picture.
     */
    fun spots(panels: List<CollageLayout.Panel>, photos: List<FramePhoto>, favorite: (String) -> Boolean, scale: Float, min: Float, max: Float): List<StarSpot> =
        panels.filter { favorite(photos[it.photo].id) }.map {
            val size = (minOf(it.box.width, it.box.height) * scale * SHARE).coerceIn(min, max)
            StarSpot(it.box.right * scale - size * 0.25f, it.box.top * scale + size * 0.25f, size)
        }
}

/** Draws a star at each spot. It takes no touches, so taps still reach the picture underneath. */
class FavoriteStarsView(context: Context) : View(context) {
    private var spots: List<StarSpot> = emptyList()

    fun show(stars: List<StarSpot>) { spots = stars; invalidate() }

    /** A spot's size is the star's visible width; the box around it also holds the shadow. */
    override fun onDraw(canvas: Canvas) {
        for (spot in spots) {
            val box = spot.size / (2f * StarShape.RADIUS_SHARE * 0.951f)
            // Place the visible star, not the box, so its right edge and top touch the spot.
            val left = spot.x - box * (0.5f + StarShape.RADIUS_SHARE * 0.951f)
            val top = spot.y - box * (0.5f - StarShape.RADIUS_SHARE)
            StarShape.draw(canvas, left, top, box)
        }
    }
}
