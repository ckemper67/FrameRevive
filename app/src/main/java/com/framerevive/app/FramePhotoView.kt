package com.framerevive.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.appcompat.widget.AppCompatImageView

/** A small, preblurred image fills the unused space behind the sharp original. */
class FramePhotoView(context: Context) : AppCompatImageView(context) {
    var blurredBackground = true
        set(value) { field = value; invalidate() }
    /** The "Fill the screen" setting: a photo is cropped to fill the frame when that takes little off it (see [FrameFill]). */
    var fillScreen = true
    private var cropped = false
    private var previousCropped = false
    var gentleTransitions = true
        set(value) { field=value;if(!value) finishTransition() }
    private var photo: Bitmap? = null
    private var backdrop: Bitmap? = null
    private var previousPhoto: Bitmap? = null
    private var previousBackdrop: Bitmap? = null
    private var transition: ValueAnimator? = null
    private var progress=1f
    /** 1 slides the picture to the right, -1 to the left (the way a hand waved); 0 cross-fades. */
    private var slideDirection=0
    private val backdropPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val solidPaint = Paint()

    /** Only the current and immediately previous image are retained, even during rapid presses. */
    fun setPhoto(photo: Bitmap, background: Bitmap, slide: Int = 0) {
        finishTransition()
        slideDirection = slide
        val oldPhoto=this.photo;val oldBackground=backdrop
        previousCropped=cropped
        this.photo=photo;backdrop=background
        val w = if (width > 0) width else resources.displayMetrics.widthPixels
        val h = if (height > 0) height else resources.displayMetrics.heightPixels
        cropped = fillScreen && FrameFill.fills(photo.width, photo.height, w, h)
        scaleType = if (cropped) ScaleType.CENTER_CROP else ScaleType.FIT_CENTER
        setImageBitmap(photo)
        if(gentleTransitions && oldPhoto != null && isShown) {
            previousPhoto=oldPhoto;previousBackdrop=oldBackground;progress=0f
            transition=ValueAnimator.ofFloat(0f,1f).apply {
                duration=if(slideDirection != 0) 400 else 600
                if(slideDirection != 0) interpolator=android.view.animation.DecelerateInterpolator()
                addUpdateListener {
                    progress=it.animatedValue as Float
                    if(progress>=1f) { previousPhoto=null;previousBackdrop=null }
                    invalidate()
                }
                start()
            }
        }
        invalidate()
    }

    fun finishTransition() {
        transition?.cancel();transition=null;progress=1f
        previousPhoto=null;previousBackdrop=null;invalidate()
    }

    fun release() {
        finishTransition();setImageDrawable(null);photo=null;backdrop=null
    }

    override fun onDetachedFromWindow() { finishTransition();super.onDetachedFromWindow() }

    private fun drawBackdrop(canvas:Canvas,background:Bitmap?,crop:Boolean) {
        // Filled as a rectangle, not with drawColor, which would paint the whole screen and wipe out a photo sliding beside it.
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRect(bounds, solidPaint.apply { color = Color.BLACK })
        background?.takeIf { blurredBackground && !crop }?.let { b ->
            val scale = maxOf(width.toFloat() / b.width, height.toFloat() / b.height)
            val w = b.width * scale; val h = b.height * scale
            canvas.drawBitmap(b, null, RectF((width-w)/2, (height-h)/2, (width+w)/2, (height+h)/2), backdropPaint)
            canvas.drawRect(bounds, solidPaint.apply { color = 0x55000000 })
        }
    }

    private fun drawOld(canvas: Canvas, old: Bitmap) {
        drawBackdrop(canvas,previousBackdrop,previousCropped)
        val scale=if(previousCropped) maxOf(width.toFloat()/old.width,height.toFloat()/old.height)
            else minOf(width.toFloat()/old.width,height.toFloat()/old.height)
        val w=old.width*scale;val h=old.height*scale
        canvas.drawBitmap(old,null,RectF((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2),backdropPaint)
    }

    override fun onDraw(canvas: Canvas) {
        val old=previousPhoto
        if(old != null && progress<1f && slideDirection != 0) {
            // The old picture leaves in the direction of the wave and the new one follows it in from the other side.
            val saved=canvas.save()
            canvas.translate(slideDirection*progress*width,0f); drawOld(canvas,old)
            canvas.restoreToCount(saved)
            canvas.translate(slideDirection*(progress-1f)*width,0f)
            drawBackdrop(canvas,backdrop,cropped);super.onDraw(canvas)
        } else if(old != null && progress<1f) {
            drawOld(canvas,old)
            val layer=canvas.saveLayerAlpha(0f,0f,width.toFloat(),height.toFloat(),(progress*255).toInt())
            drawBackdrop(canvas,backdrop,cropped);super.onDraw(canvas);canvas.restoreToCount(layer)
        } else { drawBackdrop(canvas,backdrop,cropped);super.onDraw(canvas) }
    }

    companion object {
        /** Runs on the image worker; bounded to 96 pixels along the longest edge. */
        fun background(photo: Bitmap): Bitmap {
            val scale = 96f / maxOf(photo.width, photo.height)
            val w = maxOf(1, (photo.width * scale).toInt())
            val h = maxOf(1, (photo.height * scale).toInt())
            val small = Bitmap.createScaledBitmap(photo,w,h,true)
            var pixels = IntArray(w*h)
            small.getPixels(pixels,0,w,0,0,w,h)
            if (small !== photo) small.recycle()
            repeat(3) {
                val result = IntArray(w*h)
                for (y in 0 until h) for (x in 0 until w) {
                    var red=0; var green=0; var blue=0; var count=0
                    for (yy in maxOf(0,y-4)..minOf(h-1,y+4)) for (xx in maxOf(0,x-4)..minOf(w-1,x+4)) {
                        val c=pixels[yy*w+xx]; red+=Color.red(c); green+=Color.green(c); blue+=Color.blue(c); count++
                    }
                    result[y*w+x]=Color.rgb(red/count,green/count,blue/count)
                }
                pixels=result
            }
            return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
        }
    }
}
