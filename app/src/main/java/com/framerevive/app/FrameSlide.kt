package com.framerevive.app

enum class SlideMode { SINGLE, PAIR, COLLAGE }

/** History stores catalog references, never decoded images. */
data class FrameSlide(val photos: List<FramePhoto>, val mode: SlideMode = if (photos.size == 1) SlideMode.SINGLE else SlideMode.COLLAGE) {
    init {
        require(photos.size in 1..CollageCadence.MAX_PHOTOS); require(photos.map { it.id }.distinct().size == photos.size)
        require(mode != SlideMode.SINGLE || photos.size == 1); require(mode != SlideMode.PAIR || photos.size == 2)
    }
    /** Curated collage only; pairs use the ordinary one-photo-per-interval cadence. */
    val collage: Boolean get() = mode == SlideMode.COLLAGE
    val pair: Boolean get() = mode == SlideMode.PAIR
    val id: String get() = photos.joinToString("") { "${it.id.length}:${it.id}" }
}

class CollageCadence {
    @Volatile var singles = 0
        private set
    fun due(every: Int): Boolean { require(every in 5..100); return singles >= every }
    fun displayed(collage: Boolean) { singles = if(collage) 0 else (singles+1).coerceAtMost(100) }
    fun reset() { singles=0 }
    companion object {
        /** The most photos a collage may have. Every collage aims for this many and settles for fewer when they do not tile. */
        const val MAX_PHOTOS=5
        const val MIN_PHOTOS=2
        /** The photo counts that have an arranged layout (see [CollageLayout.templates]); there is none for four. */
        private val LAYOUT_SIZES=listOf(5,3,2)
        /** The counts to try for a collage of up to [count] photos, the most first. */
        fun layoutSizes(count: Int): List<Int> = LAYOUT_SIZES.filter { it <= count }
        fun candidates(upcoming: List<FramePhoto>, previous: List<FramePhoto>, count: Int=MAX_PHOTOS, ready: (FramePhoto)->Boolean): List<FramePhoto> {
            require(count in MIN_PHOTOS..MAX_PHOTOS)
            val avoid=previous.map { it.id }.toSet()
            val selected=upcoming.filterNot { it.id in avoid }.distinctBy { it.id }.take(count)
            return if(selected.size == count && selected.all(ready)) selected else emptyList()
        }
    }
}

/** Pixel geometry kept independent of Android for coverage and fit tests. */
object CollageLayout {
    /**
     * Most of a photo a cell, a pair or the whole screen may lose to centre cropping: a photo is cropped to fill only
     * when at least 75 percent of it stays visible, the cutoff the frame's original app uses. A thousandth is added so
     * a 3:4 photo on a 9:16 screen, which loses exactly a quarter, is not turned away by rounding.
     */
    const val MAX_CROP = 0.251f
    /**
     * Passe-partout between the photos of a multi-photo slide, shared rather than doubled, so two neighbouring
     * pictures show one mount, not two. One pixel is about 0.25 mm on this 21.5-inch panel, so 10 is roughly a 2.5 mm
     * mount, the width the frame's original app uses.
     */
    const val MAT = 10
    /** The mount's colour, a very light grey, the one the frame's original app uses. */
    const val MAT_COLOR = 0xFFF4F5F7.toInt()
    /** Margin between the outermost photos and the edge of the screen: none, the photos run to the edge. */
    const val EDGE = 0
    data class Box(val left: Float,val top: Float,val right: Float,val bottom: Float) {
        val width get()=right-left
        val height get()=bottom-top
    }
    fun cells(width: Int,height: Int,gap: Int=MAT,edge: Int=EDGE): List<Box> {
        require(width > gap*3 && height > gap*3 && gap >= 0 && edge >= 0)
        val w=(width-gap-edge*2)/2f; val h=(height-gap-edge*2)/2f
        return (0..3).map { i -> val x=edge+(i%2)*(w+gap);val y=edge+(i/2)*(h+gap);Box(x,y,x+w,y+h) }
    }
    data class Size(val width:Int,val height:Int) { init { require(width>0 && height>0) } }
    data class Panel(val photo:Int,val box:Box)

    private fun permutations(indices:List<Int>):List<List<Int>> =
        if(indices.isEmpty()) listOf(emptyList()) else indices.flatMap { i -> permutations(indices-i).map { listOf(i)+it } }

    private fun stripes(width:Int,height:Int,columns:Int,rows:Int,gap:Int,edge:Int):List<Box> {
        val w=(width-gap*(columns-1)-edge*2).toFloat()/columns;val h=(height-gap*(rows-1)-edge*2).toFloat()/rows
        return (0 until columns*rows).map { i -> val x=edge+(i%columns)*(w+gap);val y=edge+(i/columns)*(h+gap);Box(x,y,x+w,y+h) }
    }

    /**
     * The five-photo arrangement of the frame's original app, built for a portrait screen and turned on its side for a
     * landscape one: a wide strip across one end, then two columns, small over large in one and large over small in
     * the other, so the pictures interlock instead of lining up. The strip is 175/512 of the length. A small cell is
     * 81/337 of its column in the original; here it is also a little taller (0.27 and 0.30 of the column), because a
     * 4:3 photo, the commonest shape, loses a hair over a quarter in the original's small cell and a day's photos
     * mostly share one shape. The strip can sit at either end and the columns either way round. That is 12 layouts.
     * The slots come out about 16:9 (the strip and the two small cells) and 9:16 (the two large ones).
     */
    private fun pinwheel(width:Int,height:Int,gap:Int,edge:Int):List<List<Box>> {
        val portrait=height>=width
        val w=(if(portrait) width else height)-edge*2f;val h=(if(portrait) height else width)-edge*2f
        val e=edge.toFloat()
        val strip=(h-gap)*175f/512f;val rest=h-gap-strip
        val column=(w-gap)/2f
        val out=ArrayList<List<Box>>()
        for(smallShare in SMALL_SHARES) {
            val small=(rest-gap)*smallShare;val large=rest-gap-small
            for(stripFirst in listOf(true,false)) for(smallFirstOnLeft in listOf(true,false)) {
                val stripTop=if(stripFirst) e else e+rest+gap
                val restTop=if(stripFirst) e+strip+gap else e
                fun cells(x:Float,smallFirst:Boolean)=if(smallFirst) listOf(Box(x,restTop,x+column,restTop+small),Box(x,restTop+small+gap,x+column,restTop+rest))
                    else listOf(Box(x,restTop,x+column,restTop+large),Box(x,restTop+large+gap,x+column,restTop+rest))
                val boxes=listOf(Box(e,stripTop,e+w,stripTop+strip))+cells(e,smallFirstOnLeft)+cells(e+column+gap,!smallFirstOnLeft)
                out+=if(portrait) boxes else boxes.map { Box(it.top,it.left,it.bottom,it.right) }
            }
        }
        return out
    }
    /** A small cell's share of its column: the original app's 81/337, and two taller ones. */
    private val SMALL_SHARES=listOf(81f/337f,0.27f,0.30f)

    /**
     * Every arrangement we will try for [count] photos. Two photos are a pair, laid out by [pair]. Three are one picture
     * and two beside or below it, and five are the interlocking arrangement of [pinwheel]: the shapes of the frame's
     * original app, not a grid. Four have only columns, rows and a grid, which playback no longer asks for.
     */
    fun templates(width:Int,height:Int,count:Int,gap:Int=MAT,edge:Int=EDGE):List<List<Box>> {
        require(count in 2..CollageCadence.MAX_PHOTOS && gap>=0 && edge>=0 && width>gap*5+edge*2 && height>gap*5+edge*2)
        if(count==5) return pinwheel(width,height,gap,edge)
        val templates=ArrayList<List<Box>>()
        if(count!=3) { templates+=stripes(width,height,count,1,gap,edge);templates+=stripes(width,height,1,count,gap,edge) }
        if(count==4) templates+=cells(width,height,gap,edge)
        if(count==3) {
            val e=edge.toFloat()
            val midX=(width-gap)/2f
            val midY=(height-gap)/2f
            val left=Box(e,e,midX,height-e)
            val right=Box(midX+gap,e,width-e,height-e)
            val top=Box(e,e,width-e,midY)
            val bottom=Box(e,midY+gap,width-e,height-e)
            templates+=listOf(left,Box(right.left,right.top,right.right,midY),Box(right.left,midY+gap,right.right,right.bottom))
            templates+=listOf(right,Box(left.left,left.top,left.right,midY),Box(left.left,midY+gap,left.right,left.bottom))
            templates+=listOf(top,Box(bottom.left,bottom.top,midX,bottom.bottom),Box(midX+gap,bottom.top,bottom.right,bottom.bottom))
            templates+=listOf(bottom,Box(top.left,top.top,midX,top.bottom),Box(midX+gap,top.top,top.right,top.bottom))
        }
        return templates
    }

    /** Pick a template and ordering that maximize visible photo area without cropping. */
    fun arrange(width:Int,height:Int,sizes:List<Size>,gap:Int=MAT):List<Panel> {
        require(sizes.size in 2..CollageCadence.MAX_PHOTOS)
        var best=emptyList<Panel>();var bestScore=-1f
        for(template in templates(width,height,sizes.size,gap)) for(order in permutations(sizes.indices.toList())) {
            val score=template.indices.sumOf { i -> val size=sizes[order[i]];val fitted=fit(size.width,size.height,template[i]);(fitted.width*fitted.height).toDouble() }.toFloat()
            if(score>bestScore+1f) { bestScore=score;best=template.mapIndexed { i,box -> Panel(order[i],box) } }
        }
        return best
    }

    /** Share of a photo lost when it is centre cropped to cover a cell; symmetric in aspect mismatch. */
    fun crop(cell:Box,size:Size):Float {
        require(cell.width>0f && cell.height>0f)
        val ratio=(cell.width/cell.height)/(size.width.toFloat()/size.height)
        return if(ratio>=1f) 1f-1f/ratio else 1f-ratio
    }

    data class Fill(val panels:List<Panel>,val crop:Float)

    /**
     * Template and photo order that cover the frame with the least cropping, or null when every
     * arrangement would cut away more than [maxCrop] of some photo. Two photos reuse the pair layout,
     * which splits the halves proportionally so both lose the same share.
     */
    fun fill(width:Int,height:Int,sizes:List<Size>,gap:Int=MAT,maxCrop:Float=MAX_CROP):Fill? {
        require(sizes.size in 2..CollageCadence.MAX_PHOTOS)
        if(sizes.size==2) {
            val layout=pair(width,height,sizes[0],sizes[1],height>width,gap)
            return if(layout.crop<=maxCrop) Fill(layout.boxes.mapIndexed { i,box -> Panel(i,box) },layout.crop) else null
        }
        // Photos of one shape are interchangeable, so the search places shapes, not photos, and the photos of a shape
        // are handed out in order afterwards. Each template is searched slot by slot and a partial arrangement is dropped
        // as soon as one of its cells is already no better than the best so far (or over the limit): five photos
        // against twelve layouts would otherwise try 1,440 orders, which is slow on the frame.
        val shapes=sizes.distinct()
        val photosOfShape=shapes.map { s -> sizes.indices.filter { sizes[it] == s } }
        val remaining=IntArray(shapes.size) { photosOfShape[it].size }
        val chosen=IntArray(sizes.size)
        var bestShapes:IntArray?=null;var bestTemplate=emptyList<Box>();var bestCrop=Float.MAX_VALUE
        for(template in templates(width,height,sizes.size,gap)) {
            fun place(slot:Int,worst:Float) {
                if(worst>maxCrop || worst>=bestCrop-0.001f) return
                if(slot==template.size) { bestCrop=worst;bestShapes=chosen.copyOf();bestTemplate=template;return }
                for(s in shapes.indices) if(remaining[s]>0) {
                    remaining[s]--;chosen[slot]=s
                    place(slot+1,maxOf(worst,crop(template[slot],shapes[s])))
                    remaining[s]++
                }
            }
            place(0,0f)
        }
        val picked=bestShapes ?: return null
        val used=IntArray(shapes.size)
        return Fill(bestTemplate.mapIndexed { i,box -> Panel(photosOfShape[picked[i]][used[picked[i]]++],box) },bestCrop)
    }

    /** Longest pixel edge needed to draw this photo into a cell, allowing for the crop when it covers. */
    fun neededEdge(cell:Box,size:Size,cover:Boolean):Int {
        require(cell.width>0f && cell.height>0f)
        val aspect=size.width.toFloat()/size.height
        val edge=if(!cover) maxOf(cell.width,cell.height)
            else if(cell.width/cell.height<aspect) cell.height*aspect else cell.width/aspect
        return edge.toInt().coerceIn(640,4096)
    }

    /**
     * Two photos of the orientation opposite the frame fill the screen, as Lenovo's Show Pair did.
     * Both halves share one zoom factor, so both photos lose the same proportion; [crop] reports it.
     */
    data class PairLayout(val boxes: List<Box>, val zoom: Float) {
        /** Share of each photo lost to centre cropping; above 1 it trims width, below 1 it trims height. */
        val crop: Float get() = if (zoom >= 1f) 1f - 1f / zoom else 1f - zoom
    }

    fun pair(width: Int, height: Int, first: Size, second: Size, portrait: Boolean, gap: Int = MAT, edge: Int = EDGE): PairLayout {
        require(width > gap * 3 && height > gap * 3 && gap >= 0 && edge >= 0)
        val e = edge.toFloat()
        if (portrait) {
            val cell = (width - edge * 2).toFloat()
            val firstNatural = cell * first.height / first.width
            val secondNatural = cell * second.height / second.width
            val zoom = (height - gap - edge * 2).toFloat() / (firstNatural + secondNatural)
            val top = e + firstNatural * zoom
            return PairLayout(listOf(Box(e, e, width - e, top),
                Box(e, top + gap, width - e, top + gap + secondNatural * zoom)), zoom)
        }
        val cell = (height - edge * 2).toFloat()
        val firstNatural = cell * first.width / first.height
        val secondNatural = cell * second.width / second.height
        val zoom = (width - gap - edge * 2).toFloat() / (firstNatural + secondNatural)
        val left = e + firstNatural * zoom
        return PairLayout(listOf(Box(e, e, left, height - e),
            Box(left + gap, e, left + gap + secondNatural * zoom, height - e)), zoom)
    }

    /** The size collages are drawn at: the screen, capped at 1920 pixels on its long edge. */
    fun outputSize(width: Int,height: Int): Pair<Int,Int> {
        val scale=minOf(1f,1920f/maxOf(width,height))
        return maxOf(32,(width*scale).toInt()) to maxOf(32,(height*scale).toInt())
    }

    /** Which photo goes in which box. [covers] is false for the letterboxed layout used when nothing fills the frame. */
    class Placement(val panels: List<Panel>,val covers: Boolean)

    /** The placement a collage of [sizes] gets on a [width] by [height] screen, as it is drawn. */
    fun place(width: Int,height: Int,sizes: List<Size>,fill: Boolean): Placement {
        val (w,h)=outputSize(width,height)
        val filled=if(fill) fill(w,h,sizes) else null
        return Placement(filled?.panels ?: arrange(w,h,sizes),filled!=null)
    }

    /**
     * Photo indices in reading order: along each row in the reading direction (left to right, or right to left when
     * [rightToLeft], as Arabic and Hebrew read), the rows from top to bottom.
     */
    fun readingOrder(panels: List<Panel>,rightToLeft: Boolean=false): List<Int> {
        val out=ArrayList<Int>(panels.size)
        val byTop=panels.sortedBy { it.box.top }
        var i=0
        while(i < byTop.size) {
            // Boxes whose tops are within a pixel or two share a row.
            var j=i+1
            while(j < byTop.size && byTop[j].box.top-byTop[i].box.top <= 1.5f) j++
            (if(rightToLeft) byTop.subList(i,j).sortedByDescending { it.box.left } else byTop.subList(i,j).sortedBy { it.box.left }).forEach { out += it.photo }
            i=j
        }
        return out
    }

    fun fit(width: Int,height: Int,cell: Box,fill: Boolean=false): Box {
        require(width>0 && height>0)
        val scale=if(fill) maxOf(cell.width/width,cell.height/height) else minOf(cell.width/width,cell.height/height)
        val x=cell.left+(cell.width-width*scale)/2;val y=cell.top+(cell.height-height*scale)/2
        return Box(x,y,x+width*scale,y+height*scale)
    }
}
