package com.framerevive.app

enum class SlideMode { SINGLE, PAIR, COLLAGE }

/** History stores catalog references, never decoded images. */
data class FrameSlide(val photos: List<FramePhoto>, val mode: SlideMode = if (photos.size == 1) SlideMode.SINGLE else SlideMode.COLLAGE) {
    init {
        require(photos.size in 1..4); require(photos.map { it.id }.distinct().size == photos.size)
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
    private var collageNumber=0
    val nextCount get()=2+collageNumber%3
    fun collageShown() { collageNumber=(collageNumber+1)%3 }
    fun reset() { singles=0;collageNumber=0 }
    companion object {
        fun candidates(upcoming: List<FramePhoto>, previous: List<FramePhoto>, count: Int=4, ready: (FramePhoto)->Boolean): List<FramePhoto> {
            require(count in 2..4)
            val avoid=previous.map { it.id }.toSet()
            val selected=upcoming.filterNot { it.id in avoid }.distinctBy { it.id }.take(count)
            return if(selected.size == count && selected.all(ready)) selected else emptyList()
        }
    }
}

/** Pixel geometry kept independent of Android for coverage and fit tests. */
object CollageLayout {
    /** Most of a photo a paired or collaged cell may lose to centre cropping. */
    const val MAX_CROP = 0.30f
    /**
     * White passe-partout for multi-photo slides: the same width around the slide and between the photos, so
     * two neighbouring pictures show one mount, not two. One pixel is about 0.25 mm on this 21.5-inch panel,
     * so 28 is roughly a 7 mm mount.
     */
    const val MAT = 28
    data class Box(val left: Float,val top: Float,val right: Float,val bottom: Float) {
        val width get()=right-left
        val height get()=bottom-top
    }
    fun cells(width: Int,height: Int,gap: Int=MAT): List<Box> {
        require(width > gap*3 && height > gap*3 && gap >= 0)
        val w=(width-gap*3)/2f; val h=(height-gap*3)/2f
        return (0..3).map { i -> val x=gap+(i%2)*(w+gap);val y=gap+(i/2)*(h+gap);Box(x,y,x+w,y+h) }
    }
    data class Size(val width:Int,val height:Int) { init { require(width>0 && height>0) } }
    data class Panel(val photo:Int,val box:Box)

    private fun permutations(indices:List<Int>):List<List<Int>> =
        if(indices.isEmpty()) listOf(emptyList()) else indices.flatMap { i -> permutations(indices-i).map { listOf(i)+it } }

    private fun stripes(width:Int,height:Int,columns:Int,rows:Int,gap:Int):List<Box> {
        val w=(width-gap*(columns+1)).toFloat()/columns;val h=(height-gap*(rows+1)).toFloat()/rows
        return (0 until columns*rows).map { i -> val x=gap+(i%columns)*(w+gap);val y=gap+(i/columns)*(h+gap);Box(x,y,x+w,y+h) }
    }

    /** Every arrangement we will try for [count] photos: columns, rows, a grid, and single-plus-two shapes. */
    fun templates(width:Int,height:Int,count:Int,gap:Int=MAT):List<List<Box>> {
        require(count in 2..4 && gap>=0 && width>gap*5 && height>gap*5)
        val templates=ArrayList<List<Box>>()
        templates+=stripes(width,height,count,1,gap);templates+=stripes(width,height,1,count,gap)
        if(count==4) templates+=cells(width,height,gap)
        if(count==3) {
            val midX=(width-gap)/2f
            val midY=(height-gap)/2f
            val left=Box(gap.toFloat(),gap.toFloat(),midX,height-gap.toFloat())
            val right=Box(midX+gap,gap.toFloat(),width-gap.toFloat(),height-gap.toFloat())
            val top=Box(gap.toFloat(),gap.toFloat(),width-gap.toFloat(),midY)
            val bottom=Box(gap.toFloat(),midY+gap,width-gap.toFloat(),height-gap.toFloat())
            templates+=listOf(left,Box(right.left,right.top,right.right,midY),Box(right.left,midY+gap,right.right,right.bottom))
            templates+=listOf(right,Box(left.left,left.top,left.right,midY),Box(left.left,midY+gap,left.right,left.bottom))
            templates+=listOf(top,Box(bottom.left,bottom.top,midX,bottom.bottom),Box(midX+gap,bottom.top,bottom.right,bottom.bottom))
            templates+=listOf(bottom,Box(top.left,top.top,midX,top.bottom),Box(midX+gap,top.top,top.right,top.bottom))
        }
        return templates
    }

    /** Pick a template and ordering that maximize visible photo area without cropping. */
    fun arrange(width:Int,height:Int,sizes:List<Size>,gap:Int=MAT):List<Panel> {
        require(sizes.size in 2..4)
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
        require(sizes.size in 2..4)
        if(sizes.size==2) {
            val layout=pair(width,height,sizes[0],sizes[1],height>width,gap)
            return if(layout.crop<=maxCrop) Fill(layout.boxes.mapIndexed { i,box -> Panel(i,box) },layout.crop) else null
        }
        var best=emptyList<Panel>();var bestCrop=Float.MAX_VALUE
        for(template in templates(width,height,sizes.size,gap)) for(order in permutations(sizes.indices.toList())) {
            val worst=template.indices.maxOf { i -> crop(template[i],sizes[order[i]]) }
            if(worst<bestCrop-0.001f) { bestCrop=worst;best=template.mapIndexed { i,box -> Panel(order[i],box) } }
        }
        return if(bestCrop<=maxCrop) Fill(best,bestCrop) else null
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

    fun pair(width: Int, height: Int, first: Size, second: Size, portrait: Boolean, gap: Int = MAT): PairLayout {
        require(width > gap * 3 && height > gap * 3 && gap >= 0)
        if (portrait) {
            val cell = (width - gap * 2).toFloat()
            val firstNatural = cell * first.height / first.width
            val secondNatural = cell * second.height / second.width
            val zoom = (height - gap * 3).toFloat() / (firstNatural + secondNatural)
            val top = gap + firstNatural * zoom
            return PairLayout(listOf(Box(gap.toFloat(), gap.toFloat(), width - gap.toFloat(), top),
                Box(gap.toFloat(), top + gap, width - gap.toFloat(), top + gap + secondNatural * zoom)), zoom)
        }
        val cell = (height - gap * 2).toFloat()
        val firstNatural = cell * first.width / first.height
        val secondNatural = cell * second.width / second.height
        val zoom = (width - gap * 3).toFloat() / (firstNatural + secondNatural)
        val left = gap + firstNatural * zoom
        return PairLayout(listOf(Box(gap.toFloat(), gap.toFloat(), left, height - gap.toFloat()),
            Box(left + gap, gap.toFloat(), left + gap + secondNatural * zoom, height - gap.toFloat())), zoom)
    }

    fun fit(width: Int,height: Int,cell: Box,fill: Boolean=false): Box {
        require(width>0 && height>0)
        val scale=if(fill) maxOf(cell.width/width,cell.height/height) else minOf(cell.width/width,cell.height/height)
        val x=cell.left+(cell.width-width*scale)/2;val y=cell.top+(cell.height-height*scale)/2
        return Box(x,y,x+width*scale,y+height*scale)
    }
}
