# Playback plan: what to add next, and in what order

Written from the code as it stands (build 52, `show-pair`), not from a feature list. Each item says what
data it needs, where it lands, and how it can be proven on the frame rather than assumed.

## 0. Prerequisite: stability

Nothing here is verifiable while the UI thread can stall. Build 52 fixes two such bugs (a SHA-256 request on
the render path deadlocking behind the TLS threads, and a placeholder repaint that span). Two rules hold for
everything below:

- **No per-photo work on the UI thread.** Photo work belongs in the preloader's single fetch thread or in a
  worker; the renderer draws what is cached and asks for the rest.
- **The watchdog stays.** `Main thread:` in Diagnostics is how a freeze names its own cause.

## 1. Where the frame actually stands

| Capability | Today | Where |
| --- | --- | --- |
| Diptych for the wrong orientation | Yes — `Show pairs`, shapes chosen, ≤30% crop | `PairSelection` |
| Shape-aware collages 2–4 | Yes, count degrades when shapes cannot fill | `CollageLayout`, `CollageSelection` |
| Same-day collages | Default source since build 49 | `CollageThemeSelection` |
| Per-picture capture dates | Yes, inset inside each photo | `FrameCollage` |
| Quality gate | Sharpness vs album median, exposure, contrast — 256px pass, persisted | `PhotoQuality`, `photo-quality.json` |
| Brightness per photo | Already measured (`PhotoScore.mean`) | same |
| Season/"this week in history" | Yes — memories within ±N days of today's date | `PhotoSelection.distance` |
| Shuffle without replacement | Yes - recent and seasonal pools are decks over a persisted play history (curation Phases 1-2) | `PhotoSelection.playlist`, `PhotoDeck` |
| Whole-album coverage | Yes - the deck reaches the whole album, never-shown photos first | `PhotoDeck` |
| Burst/event grouping | Yes - one event pass limits bursts and drops near-duplicates | `Bursts` |
| Time-of-day mood, colour harmony | No (brightness is measured but unused for this) | — |
| Pan/zoom, face detection, filmstrip, then-&-now | No | — |

Historical note: the next paragraph describes the gap as it was before the deck. It is closed now (see
`curation-plan.md`). The single most consequential gap was not on the idea list, it was in that table: **of 15,479 photos, only the
newest 300 and the seasonal slice are ever eligible.** With `recentCount = 300` and a ±14-day window, most of
the album will never appear. "Deck" and "decay" both point at it.

## 2. Tier A — cheap, uses data already on the frame

**A1. Whole-album deck with persisted play counts** (M)
Persist `files/photo-history.json` as `{id: {t: lastShownMs, n: playCount}}`, written every ~20 slides via the
existing atomic write. The playlist draws from **all** album photos, staleness-weighted, so a rebuild or a
restart does not resurrect a photo shown an hour ago. Prune ids that leave the album.
*Prove it:* play N slides, then check the page reports repeats = 0 and coverage = N distinct ids / album size.
*This is the highest-value item on the list.*

**A2. Recency decay instead of a hard "newest 300"** (S/M)
Weight `w(photo) = floor + (1 - floor) * exp(-age / τ)`, τ ≈ 180 days, floor ≈ 0.05 so old photos still surface.
Replace `recentCount` with a "recent pool" that the decay draw samples; keep the existing recent/memories
percent slider so seasonal memories are not starved.
*Prove it:* distribution of capture years over 200 slides, before and after, reported from the page.

**A3. Time-of-day mood** (S)
`PhotoScore.mean` is already stored, so this costs nothing to compute. In the evening prefer lower-luminance
photos, in daylight prefer brighter ones — as a *soft* preference (swap in from the next few candidates), never
a filter, so the deck cannot starve.
*Prove it:* mean brightness of slides in a morning run vs an evening run.

**A4. Caption pill: Month + Year** (S)
Keep the per-picture date, and add an optional small pill (`JUL 2019`) for a calmer look. **Location is not
possible**: the album index carries only `id, url, takenMs, offsetMs` — no GPS, no EXIF. Saying so is part of
the plan, not a limitation to discover later.

**A5. Presets for the memory window** (S)
`memoryDays` already exists (0 = exact day, 14 = the fortnight around today in past years). Add named presets —
*This day*, *This week (3)*, *This month (~15)*, *This season (~45)* — plus the same-date-only fixpoint. Mostly
UI; the maths is `PhotoSelection.distance`.

## 3. Tier B — needs a new measure or a new layout

**B1. Burst and event clustering** (M)
Group by capture time: consecutive photos whose gap is ≤ 60 minutes on one local date form an event; ≥3 photos
makes it a burst. Keep the 2–3 best by sharpness/contrast for ordinary rotation; the remainder drop to a
low-priority filler pool instead of flooding the queue. Pure timestamps — no downloads, no new analysis.
*Prove it:* for a known dense day (2014-03-24 has 150 photos), the page reports the event count and how many of
that day's photos are in the filler pool.

**B2. Event grid layout** (M)
Hero on the left at ~60% width, 2–3 supporting shots stacked right. One new template family in `CollageLayout`
that must obey the same ≤30% crop budget, so `CollageSelection` can use it unchanged. This is where the 21.5"
panel earns its keep: four recognisable faces instead of four thumbnails.

**B3. Colour harmony** (M)
Extend the existing 256px analysis to mean RGB and a warmth value, stored in a versioned `photo-quality.json`
(v2). Then: avoid a dark night shot following a snow shot (compare consecutive slides' `mean`), and allow
triptychs grouped by warmth. Re-analysis touches only cached photos and runs off the UI thread.

**B4. Then & Now** (M)
Two photos from the same calendar window at least three years apart, side by side, with the year marked on each
(per-picture labels already exist). Be honest about what it is: **same window, not same person.** Nothing in the
album index can identify people, so a real "same person" pairing needs C1.

## 4. Tier C — expensive, decide deliberately

**C1. Face detection** (L) — ML Kit adds megabytes and CPU to a 2019 Android 10 panel; it would enable face-safe
pan/zoom and person-based Then & Now. Recommendation: **defer.** A "never crop the upper third" rule (C2)
captures most of the benefit at none of the cost.

**C2. Ken Burns pan/zoom** (M) — no pan or zoom exists today (`FramePhotoView` is a crossfade). A slow
scale/translate over the slide time, constrained to a safe zone, is the calm, high-perceived-quality change.

**C3. Chronological filmstrip** (M) — 3–4 sequential shots from one event as a horizontal strip. Depends on B1's
ordering; layout work similar to B2.

## 5. Order of work, one commit each

| # | Item | Version | Why here |
| --- | --- | --- | --- |
| 1 | Stability (52) | 52 | Prerequisite for proving anything |
| 2 | ~~A1 deck + history~~ **done** | 61 | Fixes repeats **and** the 98%-unseen album |
| 3 | A2 decay | 54 | Makes the deck's recency bias tunable |
| 4 | ~~B1 bursts~~ **done** | 67 | Stops a single afternoon flooding the queue |
| 5 | B2 event grid | 56 | Uses bursts where the panel is biggest |
| 6 | A3 time-of-day | 57 | Cheapest visible "calm" win |
| 7 | B3 colour harmony | 58 | Needs the v2 analysis pass |
| 8 | A4/A5 captions + presets | 59 | Small, independent |
| 9 | B4 Then & Now | 60 | Needs B1 for sane pairing |
| 10 | C2 Ken Burns | 61 | Presentation polish |
| 11 | C1/C3 faces, filmstrip | — | Decide after 10 |

Each row is its own commit with tests, deployed over WiFi, verified from the controls page, and revertable
alone. No item ships bundled with another.

## 6. Data contracts and costs

- `photo-quality.json` gains a version field; v1 entries are re-analysed lazily, once, on the fetch thread.
- `photo-history.json` is capped to album ids and flushed every 20 slides; a per-slide write would thrash flash
  on a device whose only storage is eMMC.
- Every new measure is computed from the 256px analysis copy that already exists — the decode is the cost, not
  the maths. No feature below may add a second decode.
- New `/about` style numbers on the page (coverage, repeats, year histogram) exist so the claims above can be
  checked instead of believed.

## 7. What is not possible, and stays out

- **Location captions** — no GPS in the album index.
- **EXIF camera data** — not exposed by the shared-album page either.
- **Person identification** without C1.
- **"Seen the whole album"** claims until A1 lands.

## 8. Verification recipe, applied to every item

1. `assembleDebug testDebugUnitTest lintDebug` green; the pure logic (weights, clustering, layouts) is unit
   tested, since that is where the bugs live.
2. Deploy over WiFi; confirm `Main thread: responsive` after the first slide.
3. Read the feature's number from the page (coverage, repeats, event count, mean brightness, year histogram) —
   before and after, same run length.
4. Only then commit the deployed build, and only that build.
