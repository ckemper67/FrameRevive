package com.framerevive.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

object FrameCollage {
    /** One image decoded at a time on the image worker; output capped at 1920 pixels.
     *  When [fill] is set the photos cover their cells, cropped by at most [CollageLayout.MAX_CROP];
     *  arrangements that cannot cover the frame fall back to the letterboxed layout. */
    fun render(photos: List<FramePhoto>,width: Int,height: Int,blur: Boolean,date: Boolean,size: (FramePhoto)->CollageLayout.Size,load: (FramePhoto,Int)->Bitmap,fill: Boolean=false,strict: Boolean=false): Bitmap {
        require(photos.size in 2..CollageCadence.MAX_PHOTOS)
        val scale=minOf(1f,1920f/maxOf(width,height))
        val w=maxOf(32,(width*scale).toInt());val h=maxOf(32,(height*scale).toInt())
        val output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        try {
            // Multi-photo slides are matted in the original app's light grey, so the gutters read as a photo mount rather
            // than a dark seam, and sit well with the frame's own colour.
            val canvas=Canvas(output);canvas.drawColor(CollageLayout.MAT_COLOR)
            val paint=Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            // Each picture carries its own date, inset from the cell edge so it never lands in the passe-partout.
            val label=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.WHITE;textSize=maxOf(14f,20f*scale);setShadowLayer(3f,1f,1f,Color.BLACK) }
            val inset=24f*maxOf(scale,0.75f)
            fun rect(box:CollageLayout.Box)=RectF(box.left,box.top,box.right,box.bottom)
            val sizes=photos.map(size)
            val placement=CollageLayout.place(width,height,sizes,fill)
            val panels=placement.panels
            val cover=placement.covers
            // With the screen to be filled, photos that cannot cover the frame are not drawn at all.
            check(cover || !strict) { "These photos do not cover the frame." }
            // Unused space is never shown: photos that cannot cover the frame are not drawn at all.
            check(cover) { "These photos do not cover the frame." }
            for(panel in panels) {
                val i=panel.photo;val cell=panel.box
                val bitmap=load(photos[i],CollageLayout.neededEdge(cell,sizes[i],cover))
                try {
                    val save=canvas.save();canvas.clipRect(rect(cell))
                    if(blur && !cover) {
                        val background=FramePhotoView.background(bitmap)
                        try { canvas.drawBitmap(background,null,rect(CollageLayout.fit(background.width,background.height,cell,true)),paint);canvas.drawColor(0x55000000) }
                        finally { background.recycle() }
                    }
                    canvas.drawBitmap(bitmap,null,rect(CollageLayout.fit(bitmap.width,bitmap.height,cell,cover)),paint)
                    if(date && photos[i].dated)
                        canvas.drawText(photos[i].date().format(FrameCaption.SHORT_DATE),cell.left+inset,cell.bottom-inset,label)
                    canvas.restoreToCount(save)
                } finally { bitmap.recycle() }
            }
            return output
        } catch(e:Exception) { output.recycle();throw e }
    }
}
