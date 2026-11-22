# Plan: curation and presentation overhaul

Status: proposed, not started. Written from the code on `show-pair` (build 67 era), then revised after an
independent review (see "Review notes" at the end).

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

Done when the simulation runs in `testDebugUnitTest` and baseline numbers are recorded here.
Risk: extraction changes behavior; the characterization tests guard it.

## Phase 1: fix the known defect

- Make the seasonal pool a deck too (uses play history). When shorter than its quota it stops contributing
  instead of wrapping; the shortfall goes back to the recent deck so the slider stays honest.

Done when the simulation shows zero within-cycle repeats at default settings and a test fails if the modulo
wrap returns.

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
