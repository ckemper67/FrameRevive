# Plan: curation and presentation overhaul

Status (2026-11-22): Phases 0 to 2 are mostly built on branch `curation-phase-2` (on `origin`) and deployed to the
frame once. Open: the `recentCount` rename, `ensureSeasonal()` still in the activity, and all of Phases 3 and 4. Each
phase section below carries its own progress notes. Written from the code on `show-pair` (build 67 era), then
revised after an independent review (see "Review notes" at the end).

## Why

Photo selection grew by accretion. `MainActivity.rebuild()` runs four passes in sequence
(`PhotoSelection.playlist`, `PhotoSimilarity.thin`, `Bursts.thin`, `prioritize`), and `showNext()` then picks
the presentation with a hard-coded if-chain: collage if due, else pair if one fits, else single. Each new
feature in `playback-plan.md` would add another filter or another branch.

Problems found by reading the code:

1. **The no-repeat guarantee covers half the stream.** `PhotoSelection.playlist` builds the recent half from
   `PhotoDeck`, but the seasonal half is `seasonal.shuffled(random)` with no play history, and the ratio loop
   wraps with `memories[memoryIndex++ % memories.size]`. At the defaults (300 recent, 50%) a cycle is 600
   turns, so a 40-photo seasonal pool plays each photo 7-8 times per cycle (300 memory slots / 40 photos).
   Confirmed by reading; Phase 0 measures it.
2. **Collages bypass curation.** `CollageThemeSelection.bestDay` reads the whole album. It ignores the deck,
   the burst limit and the priority queue; only the quality gate applies.
3. **Three notions of "same moment":** `Bursts` (time), `PhotoSimilarity` (hash), `CollageThemeSelection`
   (calendar day), each with its own state and applied at a different stage.
4. **Naming drift.** `recentCount` is now a deck cycle size but is labelled "recent captures". `recentIds`
   (newest N by capture) still drives the RECENT collage pool.
5. **The composition is untested.** The pure logic is well tested, but `rebuild`, `showNext`, `planPreloads`
   and `ensureSeasonal` live in the Activity behind `@Volatile` fields. `poolFor` appears in both `showNext`
   (`MainActivity.kt:642`) and the Preview path in `applyRemote` (`:1052`).
6. **`playHistory` is shared without synchronization** (to verify in Phase 0). It is a plain `MutableMap`
   read on the UI thread and written by the history writer; `@Volatile` on neighbouring fields does not
   protect map mutation.

## Target structure

Two objects, not four layers. The code is about 3,400 lines; a Curator/Presentation/Director split would add
ceremony before it pays for itself.

```
PlaybackEngine  Pure Kotlin, no Android types. Owns the playlist and the decision of what comes
                next: the logic now in rebuild(), showNext() and planPreloads().
SlideFactory    Proposes a slide for the engine: single, pair, collage (same-day, recent).
                One class per presentation behind a small interface, so a new layout is a new class.
MainActivity    Timing, history, rendering, preloading I/O. No selection branches.
```

One rule: every photo reaches the screen through a single gate, and only that gate records plays.

**When to split further:** the `Curator` (a scorer built from small signals such as staleness, season, event
saturation, quality, recency decay, time of day) is carved out of `PlaybackEngine` when the second new
scoring signal lands in Phase 4. Not before.

## Phase 0: make current behavior measurable

- Extract `rebuild()` into `PlaybackEngine`. It already calls pure functions, so this is a wiring move.
- Extract `showNext()`. This is the hard part, because it mixes presentation choice with Android calls.
  - Inject cache readiness (`cached(it).exists()` / `cachedIds`) and display geometry as parameters.
  - Move the collage-date precomputation from `planPreloads` (`collageDay`, lines ~598-603) into the
    engine's state, because `showNext` consumes it.
- Account for `ensureSeasonal()`: it computes on a worker and then posts back to the handler and may call
  `rebuild()`. Model it as an explicit "seasonal set changed" input to the engine. Characterization tests
  will not catch timing-dependent rebuild triggers, so give that trigger its own test.
- Write characterization tests (fixed seed, fixed output) before the move.
- Verify `playHistory` thread safety; fix it (copy-on-write snapshot or a synchronized wrapper) or document
  why it is safe.
- Add `PlaybackSimulation` under `app/src/test/`: a synthetic album through the real engine for N slides, all
  photos cached, fake clock. Album shaped like this frame: 15k photos, a few dense burst days, mixed
  orientations, a seasonal pocket.
- Report: repeats within a cycle, coverage after N slides, capture-year histogram, share of each
  presentation, burst share per cycle.

Progress: `PlaybackEngine.buildCycle` (the playlist, thinning, burst and priority passes of `rebuild()`) is
extracted, and `PlaybackSimulation` runs it. `SlideChooser` now owns the queue and the presentation choice
from `showNext()` and the collage-date planning from `planPreloads()`, behind a `SlideContext` that supplies
cache readiness and screen geometry; `SlideChooserTest` covers it. Still in the activity: `ensureSeasonal()`,
the Preview copy of `poolFor` (Phase 3), and delegated properties (`by chooser::playlist`) that exist only
until the remaining readers move over. The simulation does not drive `SlideChooser` yet, so the presentation
mix is still unmeasured. `playHistory` is already a `ConcurrentHashMap`, so that item is settled.

Baseline (defaults, seed 7, album of 14,746 photos with a 40-photo seasonal pocket, playlist level only,
no hashes or sharpness scores). Presentation mix needs the `showNext` extraction and is not measured yet:

| Cycle | Slots | Seasonal slots | Repeated slots | Max plays of one photo | Burst share | Coverage |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 585 | 285 | 247 | 8 | 5.0% | 2.0% |
| 3 | 584 | 284 | 246 | 8 | 5.0% | 6.0% |
| 5 | 586 | 286 | 248 | 8 | 7.3% | 10.0% |

Every repeat is in the seasonal half, which confirms problem 1. With 100% recent there are none (tested).

Slide-level baseline: `SlideChooser` driven for 2,400 slides (about four cycles), same album, every photo
cached and usable, landscape frame 1920x1080, shapes 60% landscape / 35% portrait / 5% square, collage every
20 singles from the same-day source.

| Pairs | Singles | Pairs | Collages | Collages due | Skipped | RECENT fallbacks | Collage photos outside the queue | Repeated slots | Coverage |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| on | 2011 | 275 | 114 | 114 | 0 | 0 | 342 of 342 | 1089 | 11.0% |
| off | 2286 | 0 | 114 | 114 | 0 | 0 | 342 of 342 | 1259 | 8.0% |

Reading: every collage photo (342 of 342) comes from outside the cycle's queue, so collages bypass the deck,
burst limit and priority queue (problem 2). Pairs are about 11% of slides and cost coverage nothing; with
pairs off the same slide count covers less, because nothing is consumed two at a time. Skips and RECENT
fallbacks are zero only because every photo is cached here; on the frame they depend on what has downloaded,
so Phase 2 should report them from the controls page too.

Done when the simulation runs in `testDebugUnitTest` and baseline numbers are recorded here.
Risk: extraction changes behavior; the characterization tests guard it.

## Phase 1: fix the known defect

- Make the seasonal pool a deck too (uses play history). When shorter than its quota it stops contributing
  instead of wrapping; the shortfall goes back to the recent deck so the slider stays honest.

Done when the simulation shows zero within-cycle repeats at default settings and a test fails if the modulo
wrap returns.

Result (branch `curation-phase-1`): `PhotoSelection.playlist` builds the memories as a deck as well. The
slider sets a quota of memory slots (`recentCount * (100 - p) / p`); the pool fills what it can, the shortfall
is added to the recent deck, and the two are interleaved evenly with no wrap. Same album and seed as the
baseline, playlist level:

| Cycle | Slots | Seasonal slots | Repeated slots (was) | Max plays | Coverage (was) |
| --- | --- | --- | --- | --- | --- |
| 1 | 600 | 40 | 0 (247) | 1 (8) | 4% (2%) |
| 3 | 587 | 40 | 0 (246) | 1 (8) | 11% (6%) |
| 5 | 589 | 40 | 0 (248) | 1 (8) | 19% (10%) |

At slide level (2,400 slides, pairs on) repeats fall from 1,089 to 287 and coverage rises from 11% to 17%. The
287 that remain come from collages, which still draw from outside the queue (Phase 2). Memories are now 40
of about 600 slots (under 7%) rather than half, because 40 photos cannot fill a 50% share without replaying
them; this is the intended trade, and it means the slider only moves the memory share when the pool is large.
The burst share per cycle is about 10% (it was 5-7%), because more of each cycle now comes from the recent
deck; Phase 2 reviews it.

Three tests in `PhotoSelectionTest` and `PhotoMixTest` asserted the wrap (300 plays of one memory, 30 memory
slots from 3 photos) and were rewritten to the new rules.

## Phase 2: one gate for every photo

- All presentations draw from the engine's candidate list; `claim(photos)` is the only place plays are
  recorded. `CollageThemeSelection.bestDay` becomes a grouping step over those candidates instead of a scan
  of the whole album.
- Merge `Bursts` and `PhotoSimilarity` into one event concept (time groups refined by hash). Keep their
  tests, retargeted. Album-wide grouping already runs on a worker in `ensureSeasonal()`; keep it there. Per
  cycle thinning stays cheap and on the cycle.
- Data compatibility: `photo-history.json` (despite the name, tab-separated lines) keeps its current format and loads without migration. Any new
  per-photo state goes in a separate file with its own version field.
- Rename `recentCount` in code to a deck-size name; settle the UI label separately.

Progress (branch `curation-phase-2`), first slice: `SlideChooser.claim` is now the only place a play is
recorded (the activity calls it from `markDisplayed`). The chooser keeps the set of photos shown this cycle and
the set the cycle's similarity and burst passes left out (`CycleBuild.excluded`); `bestDay` receives both as
`avoid`, so a collage cannot take a photo that was already played or thinned out. The chooser also counts
collages chosen, skipped and served by the other source. Slide level, same album and seed, pairs on: repeated
slots 287 -> 0, coverage 17% -> 18%, 339 collage photos still come from outside the queue but are all eligible.
Collages taking the occasional different photo is accepted (no switch to the old behaviour). Second slice: `bestDay` reads the play history, preferring a date whose photos have not been shown (else the
one shown longest ago) and listing unseen or oldest-shown photos first. Collage photos already shown in an
earlier cycle: 245 of 339 -> 0 over 2,400 slides; coverage 18% -> 19%. Third slice: `Bursts` and `PhotoSimilarity` are one pass. `Bursts.thin` takes the hashes: inside each event a
frame that repeats one already kept is left out (ten identical shots give one frame, not three), and across
events only an exact repeat (the same picture saved twice) is dropped, which keeps it linear on the UI thread.
The windowed `PhotoSimilarity.thin` is gone; `dhash`, `near` and `hasDuplicates` remain. The simulation gives
burst frames near-identical hashes: near-identical frames left in a cycle 2 -> 0, coverage unchanged (19%).
What is lost: a near-duplicate pair on different days (not an exact repeat) is no longer caught, which the old
window did only by chance in a shuffled playlist. The controls page and diagnostics now show collages shown, skipped and served by the other source since the
app started (`SlideChooser.collageReport`). Not done yet: the `recentCount` rename (needs a UI label).

Done when collage photos obey the same repeat and burst limits as singles and coverage is no worse than the
Phase 0 baseline. Risk: a strict gate starves same-day collages; keep the RECENT fallback and report how
often it triggers.

## Phase 3: presentations as plug-ins

- Define the `Presentation` interface (`due(ctx)`, `propose(ctx, pool): Proposal?`) inside `SlideFactory`.
  Move Single, Pair and Collage (same-day, recent) into it. `FrameSlide.mode` becomes a presentation id.
- Preload design task: planning needs to know what is coming without side effects. The engine exposes a
  side-effect-free `peek()` (next presentation and the photos it would need), and `planPreloads` asks it
  instead of guessing from `collageCadence`. Design this before moving code.
- Delete the duplicate `poolFor`; Preview calls the engine with a forced presentation.
- Back/forward history keeps storing photo references (`FrameSlide.id`), so it is unaffected.

Progress (branch `curation-phase-3`): the `Presentation` interface (`due`, `propose`, `chosen`) exists with
`SinglePresentation`, `PairPresentation` and `CollagePresentation`; `SlideChooser` asks them in order and takes
extra presentations in its constructor, so a new layout is one class (tested with a fake). The simulation mix is
identical to Phase 2 (1831 singles, 455 pairs, 114 collages, same repeats and coverage). The activity's copy of
`poolFor` is gone: Preview calls `SlideChooser.previewCollage`, which shares the pool, eligibility and
letterboxed fallback with playback and leaves the queue, cadence, counters and history alone. Preview
previously skipped the letterboxed fallback, so it could show nothing where playback shows a collage; that is
fixed by the shared path. `preloadPlan` plays the part of `peek()`: it names the photos the next slide needs,
but it memoizes the chosen collage date on purpose (the photos must download before the collage falls due), so
it is not side-effect free as first designed. Not done: `FrameSlide.mode` is still the `SlideMode` enum, not a
presentation id; `MainActivity` still builds the `SlideContext` and calls `cachedSize`/`usableForCollage`.

Done when the simulation's presentation mix matches Phase 2 within tolerance, `MainActivity` has no
selection branches, and live and preview share one code path.

## Phase 4: new capabilities, one at a time

| Order | Item | Type |
| --- | --- | --- |
| 1 | Recency decay replacing the hard recent cutoff | signal |
| 2 | Event grid (hero plus 2-3 supporting shots) | presentation |
| 3 | Time-of-day brightness preference (soft, never a filter) | signal |
| 4 | Then & Now (same window, years apart; not same person) | presentation |
| 5 | Month and year caption pill, memory window presets | small, independent |

The Curator split happens here, when the second scoring signal (item 3) arrives.

Deferred: face detection, Ken Burns, filmstrip. Dropped: colour harmony (new analysis pass and layout for
little gain). Not possible: location captions (no GPS in the album index), person identification.

## Cross-cutting rules

- Every phase leaves `assembleDebug testDebugUnitTest lintDebug` green.
- No per-photo work on the UI thread; library facts (event id, hash, quality) are computed off-thread.
- Each new test must fail if its production change is reverted. Simulation assertions are the main ones.
- After Phases 1, 2 and 3, deploy over WiFi and read coverage and repeats from the controls page.
- Update `playback-plan.md` as phases land; fix `Architecture.md` (still describes 0.5.1).
- One change per phase, independently revertable. No commits without an explicit request.

## Open decisions

1. Phase 2 changes what collages look like occasionally. Accept, or keep the old behavior switchable during
   the transition?
2. UI label for the deck size setting.

## Review notes

An independent review verified problems 1-5 against the code and recommended: two objects instead of four
layers (adopted), a `showNext` extraction sub-task and `ensureSeasonal` timing note in Phase 0 (adopted), a
side-effect-free `peek()` for preloading (adopted), a data-compatibility note (adopted), and the
`playHistory` thread-safety check (adopted, marked "to verify"). It also suggested limiting event grouping
to the cycle for memory reasons; that was not adopted as stated, because `ensureSeasonal()` already groups
the whole album on a worker thread.
