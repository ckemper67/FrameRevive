package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random

/** What the chooser needs from its surroundings, so it needs no Android types. */
class SlideContext(
    val photos: List<FramePhoto>, val cachedIds: Set<String>, val isCached: (FramePhoto) -> Boolean,
    val size: (FramePhoto) -> CollageLayout.Size?, val usableForCollage: (FramePhoto) -> Boolean,
    val hashes: Map<String, Long>, val width: Int, val height: Int,
    val collages: Boolean, val collageEvery: Int, val collageSource: String, val pairs: Boolean,
    val seen: Map<String, PlayRecord> = emptyMap(),
    /** Whether a photo is sharp enough to show; unscored photos pass. */
    val sharp: (FramePhoto) -> Boolean = { true })

/** The queue as a presentation sees it: what is coming, what it may not take, and how to take it. */
interface SlideQueue {
    val head: FramePhoto?
    /** The rest of the cycle, next first. */
    val upcoming: List<FramePhoto>
    val recentIds: Set<String>
    /** [avoid] plus everything already shown this cycle or thinned out of it. */
    fun ineligible(avoid: Set<String>): Set<String>
    /** Removes [photos] from the rest of the cycle, so they do not play again. */
    fun take(photos: List<FramePhoto>)
    /** Moves past the head, which stays in the cycle as played. */
    fun advance()
}

/**
 * One way of putting photos on the screen. The chooser asks each in turn whether it is due and whether it can
 * make a slide from the queue; the first that can wins. A new layout is a new class in the list.
 */
interface Presentation {
    val mode: SlideMode
    fun due(ctx: SlideContext, cadence: CollageCadence): Boolean
    /** Photos for one slide, or null when this presentation cannot make one now. Must not consume the queue. */
    fun propose(ctx: SlideContext, queue: SlideQueue, avoid: Set<String>, last: FrameSlide?): List<FramePhoto>?
    /** The proposal became the slide on screen. */
    fun chosen(queue: SlideQueue, photos: List<FramePhoto>) = queue.take(photos)
}

/** The ordinary slide: the next photo in the cycle. */
class SinglePresentation : Presentation {
    override val mode = SlideMode.SINGLE
    override fun due(ctx: SlideContext, cadence: CollageCadence) = true
    override fun propose(ctx: SlideContext, queue: SlideQueue, avoid: Set<String>, last: FrameSlide?) = queue.head?.let { listOf(it) }
    override fun chosen(queue: SlideQueue, photos: List<FramePhoto>) = queue.advance()
}

/** Show Pair: two photos of the orientation opposite the frame fill the screen. */
class PairPresentation : Presentation {
    override val mode = SlideMode.PAIR
    override fun due(ctx: SlideContext, cadence: CollageCadence) = ctx.pairs
    override fun propose(ctx: SlideContext, queue: SlideQueue, avoid: Set<String>, last: FrameSlide?): List<FramePhoto>? =
        PairSelection.choose(ctx.width, ctx.height, queue.upcoming, ctx.size, { ctx.isCached(it) && ctx.sharp(it) }, avoid).takeIf { it.size == 2 }
}

/** A collage as Preview built it, with the notes the controls page shows. */
class CollagePreview(val photos: List<FramePhoto>, val theme: CollageTheme?, val notes: List<String>)

/**
 * Two to four photos tiled to cover the frame, from one capture date or from the newest photos. The date is
 * chosen before the collage falls due so its photos can be downloaded, and the other source serves when the
 * chosen one has nothing eligible and ready.
 */
class CollagePresentation(private val cadence: CollageCadence, private val random: Random) : Presentation {
    override val mode = SlideMode.COLLAGE
    var day: List<FramePhoto> = emptyList()
    var theme: CollageTheme? = null
    var lastTheme: CollageTheme? = null
    var lastDay: LocalDate? = null
    var shown = 0; private set
    var skipped = 0; private set
    var fallbacks = 0; private set
    private var proposedTheme: CollageTheme? = null
    private var firstTheme: CollageTheme? = null

    override fun due(ctx: SlideContext, cadence: CollageCadence) = ctx.collages && cadence.due(ctx.collageEvery)

    /** Candidates from [source]. With [memoize] the chosen date is kept, so the photos named for download are the ones used. */
    private fun pool(source: CollageTheme, ctx: SlideContext, queue: SlideQueue, avoid: Set<String>, count: Int, memoize: Boolean): List<FramePhoto> =
        when (source) {
            CollageTheme.DAY -> {
                fun best() = CollageThemeSelection.bestDay(ctx.photos, count, ctx.cachedIds, queue.ineligible(avoid), setOfNotNull(lastDay), seen = ctx.seen)
                if (!memoize) best() else { if (day.size < count) day = best(); day }
            }
            CollageTheme.RECENT -> queue.upcoming.filter { it.id in queue.recentIds }
        }

    /** Shape and quality selection run inside the theme; the letterboxed layout is the last resort. */
    private fun fit(ctx: SlideContext, pool: List<FramePhoto>, count: Int, avoid: Set<String>, last: FrameSlide?): List<FramePhoto> {
        val fitting = CollageSelection.choose(count, ctx.width, ctx.height, pool, ctx.size, ctx.usableForCollage, avoid, hashes = ctx.hashes)
        return if (fitting.size in 2..4) fitting else CollageCadence.candidates(pool, last?.photos.orEmpty(), count, ctx.usableForCollage)
    }

    override fun propose(ctx: SlideContext, queue: SlideQueue, avoid: Set<String>, last: FrameSlide?): List<FramePhoto>? {
        val count = cadence.nextCount
        var source = theme ?: CollageThemeSelection.chosen(ctx.collageSource, random).also { theme = it; day = emptyList() }
        firstTheme = source
        var selected = fit(ctx, pool(source, ctx, queue, avoid, count, true), count, avoid, last)
        if (selected.size !in 2..4) {
            val alternative = CollageThemeSelection.other(source)
            fit(ctx, pool(alternative, ctx, queue, avoid, count, true), count, avoid, last).takeIf { it.size in 2..4 }?.let { selected = it; source = alternative }
        }
        if (selected.size !in 2..4) { skipped++; return null }
        proposedTheme = source
        return selected
    }

    override fun chosen(queue: SlideQueue, photos: List<FramePhoto>) {
        shown++
        if (proposedTheme != firstTheme) fallbacks++
        queue.take(photos)
        lastTheme = proposedTheme; theme = null; day = emptyList()
        if (proposedTheme == CollageTheme.DAY) lastDay = photos.first().date()
    }

    /** The photos to have ready, nearest first, naming a same-day collage's photos before it falls due. */
    fun preload(ctx: SlideContext, queue: SlideQueue, depth: Int): List<FramePhoto> {
        val soon = ctx.collages && cadence.singles >= ctx.collageEvery - 1
        val count = if (soon) maxOf(depth, 5) else depth
        // The date has to be chosen here, while there is still time to download it: choosing it only when the
        // collage falls due leaves nothing cached, the layout finds no usable shapes and playback silently
        // falls back to the recent source.
        val planned = if (soon) {
            if (CollageThemeSelection.chosen(ctx.collageSource, random) == CollageTheme.DAY && day.size < cadence.nextCount)
                day = CollageThemeSelection.bestDay(ctx.photos, cadence.nextCount, ctx.cachedIds, queue.ineligible(emptySet()), setOfNotNull(lastDay), seen = ctx.seen)
            day + queue.upcoming
        } else queue.upcoming
        return planned.distinctBy { it.id }.take(count)
    }

    /**
     * The collage playback would show for [count] photos right now, without touching the queue, the cadence or
     * the play history. It rotates the date like a real collage so repeated previews differ.
     */
    fun preview(ctx: SlideContext, queue: SlideQueue, count: Int): CollagePreview {
        val source = CollageThemeSelection.chosen(ctx.collageSource, random)
        val notes = mutableListOf<String>()
        for (candidate in listOf(source, CollageThemeSelection.other(source))) {
            val all = pool(candidate, ctx, queue, emptySet(), count, false)
            val ready = all.filter { it.id in ctx.cachedIds }
            val fitting = fit(ctx, ready, count, emptySet(), null)
            notes += "$candidate pool=${all.size} cached=${ready.size} fitted=${fitting.size}"
            if (fitting.size in 2..4) {
                lastTheme = candidate
                if (candidate == CollageTheme.DAY) lastDay = fitting.first().date()
                return CollagePreview(fitting, candidate, notes)
            }
        }
        return CollagePreview(emptyList(), null, notes)
    }

    /** Photos worth downloading so a preview that found nothing ready can succeed on a later try. */
    fun previewPool(ctx: SlideContext, queue: SlideQueue, count: Int): List<FramePhoto> =
        pool(CollageThemeSelection.chosen(ctx.collageSource, random), ctx, queue, emptySet(), count, false).take(6)

    fun resetForAlbum() { day = emptyList(); lastDay = null; theme = null }
}

/**
 * Owns the queue and asks the presentations, in order, for the next slide: a collage when one falls due, else a
 * pair when one fits, else a single. [extra] presentations are tried after the collage and before the pair.
 */
class SlideChooser(random: Random = Random.Default, extra: List<Presentation> = emptyList()) : SlideQueue {
    val cadence = CollageCadence()
    var playlist: List<FramePhoto> = emptyList()
    var index = 0
    override var recentIds: Set<String> = emptySet()

    val collage = CollagePresentation(cadence, random)
    private val presentations: List<Presentation> = listOf<Presentation>(collage) + extra + PairPresentation() + SinglePresentation()

    var collageDay: List<FramePhoto> get() = collage.day; set(value) { collage.day = value }
    var collageTheme: CollageTheme? get() = collage.theme; set(value) { collage.theme = value }
    var lastCollageTheme: CollageTheme? get() = collage.lastTheme; set(value) { collage.lastTheme = value }
    var lastCollageDay: LocalDate? get() = collage.lastDay; set(value) { collage.lastDay = value }
    val collagesChosen get() = collage.shown
    val collageSkips get() = collage.skipped
    val themeFallbacks get() = collage.fallbacks

    /** Photos shown since this cycle began, and photos its thinning passes left out. A collage may take neither. */
    private val played = HashSet<String>()
    private var excluded: Set<String> = emptySet()

    override val head: FramePhoto? get() = playlist.getOrNull(index)
    override val upcoming: List<FramePhoto> get() = playlist.drop(index)
    override fun ineligible(avoid: Set<String>): Set<String> = if (played.isEmpty() && excluded.isEmpty()) avoid else avoid + played + excluded
    override fun take(photos: List<FramePhoto>) {
        val ids = photos.map { it.id }.toSet()
        playlist = playlist.take(index) + playlist.drop(index).filterNot { it.id in ids }
    }
    override fun advance() { index++ }

    /** What collages did since the app started, for the controls page: a skip is a collage that fell due and found nothing. */
    fun collageReport(): String = "$collagesChosen shown, $collageSkips skipped for want of eligible ready photos, $themeFallbacks from the other source"

    /** Starts a cycle from a freshly built playlist. */
    fun load(cycle: List<FramePhoto>, recent: Set<String>, excludedIds: Set<String> = emptySet()) {
        playlist = cycle; recentIds = recent; index = 0
        played.clear(); excluded = excludedIds
    }

    /**
     * The one place a shown photo is recorded: in the play history the deck reads, and in this cycle's set so
     * no presentation shows it again before the cycle ends. Slides replayed from history are not claimed.
     */
    fun claim(ids: Collection<String>, seen: MutableMap<String, PlayRecord>, now: Long) {
        PhotoDeck.recorded(seen, ids, now)
        played += ids
    }

    /** A new album invalidates everything chosen from the old one. */
    fun resetForAlbum() { cadence.reset(); collage.resetForAlbum() }

    /** The photos to have ready, nearest first. */
    fun preloadPlan(ctx: SlideContext, depth: Int): List<FramePhoto> = collage.preload(ctx, this, depth)

    fun previewCollage(ctx: SlideContext, count: Int): CollagePreview = collage.preview(ctx, this, count)
    fun previewPool(ctx: SlideContext, count: Int): List<FramePhoto> = collage.previewPool(ctx, this, count)

    /** The next slide, or null when the queue is empty. The caller rebuilds an exhausted queue first. */
    fun next(ctx: SlideContext, last: FrameSlide?): FrameSlide? {
        if (playlist.isEmpty()) return null
        // A photo can be scored after the cycle was built (scoring happens as it downloads), so the last check
        // is here: a soft photo at the head is passed over, not shown.
        while (true) { val first = head ?: break; if (ctx.sharp(first)) break; advance() }
        if (head == null) return null
        val avoid = last?.photos?.map { it.id }?.toSet().orEmpty()
        for (presentation in presentations) {
            if (!presentation.due(ctx, cadence)) continue
            val photos = presentation.propose(ctx, this, avoid, last) ?: continue
            presentation.chosen(this, photos)
            return FrameSlide(photos, presentation.mode)
        }
        return null
    }
}
