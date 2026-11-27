package com.framerevive.app

import java.time.LocalDate
import kotlin.random.Random

/** What the chooser needs from its surroundings, so it needs no Android types. */
class SlideContext(
    val photos: List<FramePhoto>, val cachedIds: Set<String>, val isCached: (FramePhoto) -> Boolean,
    val size: (FramePhoto) -> CollageLayout.Size?, val usableForCollage: (FramePhoto) -> Boolean,
    val hashes: Map<String, Long>, val width: Int, val height: Int,
    val collages: Boolean, val collageEvery: Int, val themes: Set<String>, val pairs: Boolean,
    val seen: Map<String, PlayRecord> = emptyMap(),
    /** Whether a photo is sharp enough to show; unscored photos pass. Called per photo, so it must not read preferences or files. */
    val sharp: (FramePhoto) -> Boolean = { true },
    /** The Fill the screen setting. With it and [pairs] on, unused space is never shown: nothing is letterboxed. */
    val fill: Boolean = true) {
    /** Whether bars may not appear at all, so a photo that would not fill the frame is paired or tiled, or not shown. */
    val strict: Boolean get() = fill && pairs
    /** A photo's size: the album's own, or the downloaded file's when the album did not say. */
    fun dims(p: FramePhoto): CollageLayout.Size? = if (p.sized) CollageLayout.Size(p.width, p.height) else size(p)
}

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
    /** Sends [photo] back [slots] places, so something else is shown first. */
    fun defer(photo: FramePhoto, slots: Int)
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
        PairSelection.choose(ctx.width, ctx.height, queue.upcoming, ctx::dims, { ctx.isCached(it) && ctx.sharp(it) }, avoid).takeIf { it.size == 2 }
}

/** A collage as Preview built it, with the notes the controls page shows. */
class CollagePreview(val photos: List<FramePhoto>, val themeId: String?, val notes: List<String>)

/**
 * Two, three or five photos tiled to cover the frame. The enabled themes take turns, so consecutive collages differ; each
 * offers candidate photos and the shape fitting picks the combination that crops least. The next collage's photos
 * are chosen before it falls due so they can be downloaded, and when a theme has nothing that fits, the next one in
 * turn is tried before the collage is given up.
 */
class CollagePresentation(private val cadence: CollageCadence) : Presentation {
    override val mode = SlideMode.COLLAGE
    /** The next collage's candidates, chosen ahead of time, and the theme that offered them. */
    var plan: List<FramePhoto> = emptyList()
    private var planTheme: CollageTheme? = null
    var lastThemeId: String? = null
    var lastDay: LocalDate? = null
    var shown = 0; private set
    var skipped = 0; private set
    var fallbacks = 0; private set
    /** Position in the enabled themes of the one whose turn it is. */
    private var turn = 0
    private var proposedTheme: CollageTheme? = null
    private var firstTheme: CollageTheme? = null

    override fun due(ctx: SlideContext, cadence: CollageCadence) = ctx.collages && cadence.due(ctx.collageEvery)

    private fun enabled(ctx: SlideContext): List<CollageTheme> = CollageThemes.all.filter { it.id in ctx.themes }.ifEmpty { listOf(CollageThemes.DAY) }

    /** The enabled themes starting with the one whose turn it is. */
    private fun inTurn(ctx: SlideContext): List<CollageTheme> {
        val list = enabled(ctx)
        val start = turn % list.size
        return list.drop(start) + list.take(start)
    }

    /**
     * The collage that fits best: as many photos as tile the frame within the crop limit, up to [count], and never fewer
     * than two. When nothing tiles, the first candidates are shown whole on a blurred background, again as many as there
     * are.
     */
    private fun fit(ctx: SlideContext, pool: List<FramePhoto>, count: Int, avoid: Set<String>, last: FrameSlide?,
                    lookahead: Int = CollageSelection.LOOKAHEAD): List<FramePhoto> {
        val fitting = CollageSelection.choose(count, ctx.width, ctx.height, pool, ctx.size, ctx.usableForCollage, avoid, hashes = ctx.hashes, lookahead = lookahead)
        if (fitting.size in CollageCadence.MIN_PHOTOS..CollageCadence.MAX_PHOTOS) return fitting
        // With the screen to be filled there is no letterboxed fallback: when nothing tiles, the collage is skipped.
        if (ctx.strict) return emptyList()
        for (n in count downTo CollageCadence.MIN_PHOTOS) {
            val whole = CollageCadence.candidates(pool, last?.photos.orEmpty(), n, ctx.usableForCollage)
            if (whole.isNotEmpty()) return whole
        }
        return emptyList()
    }

    override fun propose(ctx: SlideContext, queue: SlideQueue, avoid: Set<String>, last: FrameSlide?): List<FramePhoto>? {
        val count = CollageCadence.MAX_PHOTOS
        val themes = inTurn(ctx)
        firstTheme = themes.first()
        // The plan made ahead first (its photos are the ones downloaded), then each theme in turn.
        val attempts = ArrayList<Pair<CollageTheme, List<FramePhoto>>>()
        planTheme?.takeIf { plan.size >= CollageCadence.MIN_PHOTOS }?.let { attempts += it to plan }
        for (theme in themes) if (theme !== planTheme) attempts += theme to theme.candidates(ctx, queue, count, avoid, lastDay)
        for ((theme, pool) in attempts) {
            val selected = fit(ctx, pool, count, avoid, last)
            if (selected.size in CollageCadence.MIN_PHOTOS..CollageCadence.MAX_PHOTOS) { proposedTheme = theme; return selected }
        }
        skipped++
        return null
    }

    override fun chosen(queue: SlideQueue, photos: List<FramePhoto>) {
        shown++
        if (proposedTheme !== firstTheme) fallbacks++
        queue.take(photos)
        lastThemeId = proposedTheme?.id
        lastDay = photos.first().date()
        plan = emptyList(); planTheme = null
        // The next collage starts with the theme after the one just shown.
        proposedTheme?.let { used -> val position = CollageThemes.all.indexOf(used); if (position >= 0) turn = position + 1 }
    }

    /** The photos to have ready, nearest first, naming the next collage's photos before it falls due. */
    fun preload(ctx: SlideContext, queue: SlideQueue, depth: Int): List<FramePhoto> {
        // Two slides ahead: the fitting looks at several candidates, and all of them have to be downloaded by the time it runs.
        val soon = ctx.collages && cadence.singles >= ctx.collageEvery - 2
        val count = if (soon) maxOf(depth, CollageSelection.LOOKAHEAD) else depth
        // The photos have to be chosen here, while there is still time to download them: choosing them only when the
        // collage falls due leaves nothing cached, the layout finds no usable shapes and the collage is skipped.
        val planned = if (soon) {
            if (plan.size < CollageCadence.MIN_PHOTOS) {
                for (theme in inTurn(ctx)) {
                    val pool = theme.candidates(ctx, queue, CollageCadence.MAX_PHOTOS, emptySet(), lastDay)
                    if (pool.size < CollageCadence.MIN_PHOTOS) continue
                    // Choose the collage from the album's sizes and times, so only its photos and two spares are fetched;
                    // the theme's own candidates are the fallback when the planner finds none.
                    val choice = CollagePlanner.plan(ctx.width, ctx.height, ctx::dims, CollagePlanner.pools(ctx, queue, theme, pool))
                    plan = choice?.downloads ?: pool; planTheme = theme; break
                }
            }
            plan + queue.upcoming
        } else queue.upcoming
        return planned.distinctBy { it.id }.take(count)
    }

    /**
     * The collage playback would show for [count] photos right now, without touching the queue, the cadence or
     * the play history. It moves on to the next theme and day like a real collage so repeated previews differ.
     */
    fun preview(ctx: SlideContext, queue: SlideQueue, count: Int): CollagePreview {
        val notes = mutableListOf<String>()
        for (theme in inTurn(ctx)) {
            val all = theme.candidates(ctx, queue, count, previewed, lastDay)
            val ready = all.filter { it.id in ctx.cachedIds }
            var fitting = fit(ctx, ready, count, emptySet(), null)
            notes += "${theme.id} pool=${all.size} cached=${ready.size} fitted=${fitting.size}"
            // Only the preview widens: when the occasion's own photos do not make a full collage, look at the days around it,
            // a day, a week, two weeks either side, and keep the first window that does. Playback does not do this.
            if (fitting.size < count && ready.size >= CollageCadence.MIN_PHOTOS) {
                val anchor = ready.first().date()
                for (days in PREVIEW_WIDENING) {
                    val wide = widenedPool(ctx, anchor, days)
                    val wider = fit(ctx, wide, count, emptySet(), null, lookahead = wide.size)
                    notes += "${theme.id} within $days days: pool=${wide.size} fitted=${wider.size}"
                    if (wider.size > fitting.size) fitting = wider
                    if (fitting.size >= count) break
                }
            }
            if (fitting.size in CollageCadence.MIN_PHOTOS..CollageCadence.MAX_PHOTOS) {
                lastThemeId = theme.id; lastDay = fitting.first().date()
                turn = CollageThemes.all.indexOf(theme) + 1
                // Remember what was shown, so the next preview moves on to other photos and days. Playback records its
                // plays; a preview does not, and without this the two best days would take turns for ever.
                previewed += fitting.map { it.id }
                while (previewed.size > PREVIEW_MEMORY) previewed.remove(previewed.first())
                return CollagePreview(fitting, theme.id, notes)
            }
        }
        // Everything near the top has been previewed lately: start over rather than show nothing.
        if (previewed.isNotEmpty()) { previewed.clear(); return preview(ctx, queue, count) }
        return CollagePreview(emptyList(), null, notes)
    }

    /** The downloaded photos from the days within [days] of [anchor], nearest first, for a preview. */
    private fun widenedPool(ctx: SlideContext, anchor: LocalDate, days: Int): List<FramePhoto> {
        val centre = anchor.toEpochDay()
        return ctx.photos.filter { it.dated && it.id in ctx.cachedIds && it.id !in previewed && kotlin.math.abs(it.date().toEpochDay() - centre) <= days }
            .sortedBy { kotlin.math.abs(it.date().toEpochDay() - centre) }
    }
    private val PREVIEW_WIDENING = listOf(1, 7, 14)

    /** The photos of the last few previews (about a dozen collages), which a preview avoids. */
    private val previewed = LinkedHashSet<String>()
    private val PREVIEW_MEMORY = 60

    /** Photos worth downloading so a preview that found nothing ready can succeed on a later try. */
    fun previewPool(ctx: SlideContext, queue: SlideQueue, count: Int): List<FramePhoto> =
        inTurn(ctx).firstNotNullOfOrNull { theme -> theme.candidates(ctx, queue, count, previewed, lastDay).takeIf { it.isNotEmpty() } }.orEmpty().take(6)

    /** The catalog changed, so candidates chosen from the old one are stale. */
    fun forgetPlan() { plan = emptyList(); planTheme = null }

    fun resetForAlbum() { forgetPlan(); lastDay = null; lastThemeId = null; turn = 0 }
}

/**
 * Owns the queue and asks the presentations, in order, for the next slide: a collage when one falls due, else a
 * pair when one fits, else a single. [extra] presentations are tried after the collage and before the pair.
 */
class SlideChooser(@Suppress("UNUSED_PARAMETER") random: Random = Random.Default, extra: List<Presentation> = emptyList()) : SlideQueue {
    private companion object {
        const val DEFER_SLOTS = 6
        const val MAX_DEFERRALS = 6
        const val MAX_PASSES = 12
    }
    val cadence = CollageCadence()
    var playlist: List<FramePhoto> = emptyList()
    var index = 0
    override var recentIds: Set<String> = emptySet()

    val collage = CollagePresentation(cadence)
    private val presentations: List<Presentation> = listOf<Presentation>(collage) + extra + PairPresentation() + SinglePresentation()

    /** The next collage's candidates, chosen ahead of time. */
    val collagePlan: List<FramePhoto> get() = collage.plan
    val lastCollageTheme: String? get() = collage.lastThemeId
    val lastCollageDay: LocalDate? get() = collage.lastDay
    fun forgetCollagePlan() = collage.forgetPlan()
    val collagesChosen get() = collage.shown
    val collageSkips get() = collage.skipped
    val themeFallbacks get() = collage.fallbacks

    /** Photos shown since this cycle began, and photos its thinning passes left out. A collage may take neither. */
    private val played = HashSet<String>()
    private var excluded: Set<String> = emptySet()
    /** How often each photo that does not fill the frame was sent back this cycle for want of a partner. */
    private val deferrals = HashMap<String, Int>()

    override val head: FramePhoto? get() = playlist.getOrNull(index)
    override val upcoming: List<FramePhoto> get() = playlist.drop(index)
    override fun ineligible(avoid: Set<String>): Set<String> = if (played.isEmpty() && excluded.isEmpty()) avoid else avoid + played + excluded
    override fun take(photos: List<FramePhoto>) {
        val ids = photos.map { it.id }.toSet()
        playlist = playlist.take(index) + playlist.drop(index).filterNot { it.id in ids }
    }
    override fun advance() { index++ }
    override fun defer(photo: FramePhoto, slots: Int) {
        val at = playlist.indexOfFirst { it.id == photo.id }.takeIf { it >= index } ?: return
        val list = playlist.toMutableList()
        list.removeAt(at); list.add(minOf(at + slots, list.size), photo)
        playlist = list
    }

    /** What collages did since the app started, for the controls page: a skip is a collage that fell due and found nothing. */
    fun collageReport(): String = "$collagesChosen shown, $collageSkips skipped for want of eligible ready photos, $themeFallbacks from the other source"

    /** Starts a cycle from a freshly built playlist. */
    fun load(cycle: List<FramePhoto>, recent: Set<String>, excludedIds: Set<String> = emptySet()) {
        playlist = cycle; recentIds = recent; index = 0
        played.clear(); excluded = excludedIds; deferrals.clear()
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
    fun preloadPlan(ctx: SlideContext, depth: Int): List<FramePhoto> {
        val base = collage.preload(ctx, this, depth)
        // A photo coming up that will not fill the frame needs its partner downloaded in time, however far ahead the partner is.
        if (!ctx.pairs) return base
        val ahead = upcoming
        val partners = ahead.take(2).filter { PairSelection.misfit(ctx.width, ctx.height, ctx.dims(it)) }
            .mapNotNull { PairSelection.partnerFor(ctx.width, ctx.height, it, ahead, ctx::dims) }
        return (base + partners).distinctBy { it.id }
    }

    /** Whether [photo] would go up alone without filling the frame, which Show Pair exists to prevent. */
    private fun lonelyMisfit(ctx: SlideContext, photo: FramePhoto) = ctx.pairs && PairSelection.misfit(ctx.width, ctx.height, ctx.dims(photo))

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
        var passes = 0
        while (true) {
            var sentBack = false
            for (presentation in presentations) {
                if (!presentation.due(ctx, cadence)) continue
                val photos = presentation.propose(ctx, this, avoid, last) ?: continue
                // A photo that would not fill the frame never goes up alone while pairs are on: it waits for a partner a few slots
                // later, and after a few waits it is left out of this cycle. With the screen to be filled, nothing else is
                // shown instead, so the frame keeps its current picture; otherwise, if every photo left is in that case, one is
                // shown whole rather than leaving the frame with nothing.
                if (presentation.mode == SlideMode.SINGLE && lonelyMisfit(ctx, photos[0]) &&
                    (ctx.strict || (passes < MAX_PASSES && upcoming.any { it.id != photos[0].id && !lonelyMisfit(ctx, it) }))) {
                    val photo = photos[0]
                    val waits = (deferrals[photo.id] ?: 0) + 1
                    deferrals[photo.id] = waits
                    if (waits > MAX_DEFERRALS) take(listOf(photo)) else defer(photo, DEFER_SLOTS)
                    sentBack = true; break
                }
                presentation.chosen(this, photos)
                return FrameSlide(photos, presentation.mode)
            }
            if (!sentBack) return null
            passes++
            if (ctx.strict && passes >= MAX_PASSES) return null
            while (true) { val first = head ?: break; if (ctx.sharp(first)) break; advance() }
            if (head == null) return null
        }
    }
}
