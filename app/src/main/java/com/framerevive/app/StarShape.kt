package com.framerevive.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The favorite star: a rounded, solid gold five-point star with a soft shadow, the same one wherever a favorite is
 * marked. It sits in a square box that leaves room for the shadow, so a box placed at a screen corner with an even
 * margin looks evenly spaced. An outline version, white and unfilled, shows a star being added or removed.
 */
object StarShape {
    /** Gold of the star on the frame's original app, read from its artwork. */
    const val GOLD = 0xFFFEC81E.toInt()
    /** The star's tip to the box's centre, as a share of the box's side: the star fills about 73 percent of the box's width. */
    const val RADIUS_SHARE = 0.386f
    /** The inner corners lie at this share of the tip's distance from the centre, which makes a full, friendly star. */
    const val INNER_SHARE = 0.5f
    /** The outline's line is this share of the box's side wide. */
    const val OUTLINE_SHARE = 0.07f

    /** The ten corners of the star, alternating tip and inner corner, starting with the top tip, as x, y pairs. */
    fun corners(centerX: Float, centerY: Float, radius: Float): FloatArray {
        val out = FloatArray(20)
        for (i in 0 until 10) {
            val angle = -PI / 2 + i * PI / 5
            val r = if (i % 2 == 0) radius else radius * INNER_SHARE
            out[2 * i] = (centerX + r * cos(angle)).toFloat()
            out[2 * i + 1] = (centerY + r * sin(angle)).toFloat()
        }
        return out
    }

    // Created on first drawing, so the geometry above can be used (and tested) without Android's graphics classes.
    private val fill by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GOLD; style = Paint.Style.FILL } }
    private val line by lazy { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND } }
    private val path by lazy { Path() }

    private fun shape(left: Float, top: Float, size: Float, radius: Float): Path {
        val c = corners(left + size / 2f, top + size / 2f, radius)
        path.reset()
        path.moveTo(c[0], c[1])
        for (i in 1 until 10) path.lineTo(c[2 * i], c[2 * i + 1])
        path.close()
        return path
    }

    /** Draws the gold star in the square box with its top-left corner at ([left], [top]) and a side of [size]. */
    fun draw(canvas: Canvas, left: Float, top: Float, size: Float) {
        fill.pathEffect = CornerPathEffect(size * 0.05f)
        fill.setShadowLayer(size * 0.05f, 0f, size * 0.02f, 0x80000000.toInt())
        canvas.drawPath(shape(left, top, size, size * RADIUS_SHARE), fill)
    }

    /** Draws the outline star in the same box; its line stays inside the gold star's extent. */
    fun drawOutline(canvas: Canvas, left: Float, top: Float, size: Float) {
        line.strokeWidth = size * OUTLINE_SHARE
        line.pathEffect = CornerPathEffect(size * 0.05f)
        line.setShadowLayer(size * 0.05f, 0f, size * 0.02f, 0x80000000.toInt())
        canvas.drawPath(shape(left, top, size, size * RADIUS_SHARE - line.strokeWidth), line)
    }
}

/**
 * The star in the corner of a single photo. It rests as the gold star on a favorite, and shows what a toggle did, as
 * the frame's original app does: adding shows the outline, then the gold star with a small pop; removing shows the
 * gold star, then the outline, which stays a couple of seconds and fades away.
 */
class FavoriteBadgeView(context: Context) : View(context) {
    private var outline = false
    private var running: Runnable? = null

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        if (outline) StarShape.drawOutline(canvas, 0f, 0f, size) else StarShape.draw(canvas, 0f, 0f, size)
    }

    /** Stops any transition and shows the plain gold star, or nothing. */
    fun rest(favorite: Boolean) {
        stop()
        outline = false
        alpha = 1f; scaleX = 1f; scaleY = 1f
        visibility = if (favorite) VISIBLE else GONE
        invalidate()
    }

    /** Outline, then gold with a pop; [done] runs when it ends. */
    fun showAdded(done: () -> Unit) {
        stop(); begin(outline = true)
        later(HOLD_MS) {
            outline = false; invalidate()
            animate().scaleX(POP).scaleY(POP).setDuration(POP_MS).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(POP_MS).withEndAction(done).start()
            }.start()
        }
    }

    /** Gold, then outline that stays and fades; [done] runs when it ends. */
    fun showRemoved(done: () -> Unit) {
        stop(); begin(outline = false)
        later(HOLD_MS) {
            outline = true; invalidate()
            later(OUTLINE_STAYS_MS) {
                animate().alpha(0f).setDuration(FADE_MS).withEndAction(done).start()
            }
        }
    }

    private fun begin(outline: Boolean) {
        this.outline = outline
        alpha = 1f; scaleX = 1f; scaleY = 1f
        visibility = VISIBLE; invalidate()
    }

    private fun later(ms: Long, action: () -> Unit) {
        val r = Runnable { running = null; action() }
        running = r
        postDelayed(r, ms)
    }

    private fun stop() {
        running?.let { removeCallbacks(it) }
        running = null
        animate().cancel()
    }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    companion object {
        /** How long the first star shows before it turns into the other (the original app: 800 ms). */
        const val HOLD_MS = 800L
        /** Each half of the pop, up to 1.3 times the size and back (the original: 500 ms up, held). */
        const val POP_MS = 250L
        const val POP = 1.3f
        /** The outline after a removal stays this long, then fades over [FADE_MS] (the original: 2500 and 500 ms). */
        const val OUTLINE_STAYS_MS = 2500L
        const val FADE_MS = 500L
    }
}
