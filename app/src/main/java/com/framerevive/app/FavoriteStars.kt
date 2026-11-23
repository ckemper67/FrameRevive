package com.framerevive.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD54F.toInt(); textAlign = Paint.Align.RIGHT }
    private var spots: List<StarSpot> = emptyList()

    fun show(stars: List<StarSpot>) { spots = stars; invalidate() }

    override fun onDraw(canvas: Canvas) {
        for (spot in spots) {
            paint.textSize = spot.size
            paint.setShadowLayer(spot.size / 8f, spot.size / 24f, spot.size / 24f, Color.BLACK)
            canvas.drawText("\u2605", spot.x, spot.y + spot.size * 0.8f, paint)
        }
    }
}
