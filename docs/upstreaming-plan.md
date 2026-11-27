# Upstreaming plan

Written 2026-11-26 from the history of `development` (this branch, named `favorites-f3` until 2026-11-26). Status: a proposal. Nothing here has been extracted or tested yet.

## Where things stand

- `development` is a stack: `show-pair` -> `curation-phase-0..4` -> `favorites-f1..f3`. Each branch contains the one before.
- It already contains upstream's latest (`upstream/main` at `4aff43d`), so every extraction below would branch from `upstream/main`. `origin/main` is the one that lags (`f73807a`).
- The branch is 64 commits ahead of `origin/main`. Three of them are upstream's own (`fa039cc`, `3a89d8c`, `4aff43d`), so 61 are ours.
- Almost every feature touches `MainActivity.kt` and `FrameWeb.kt`, so each extraction needs some conflict resolution.

## Terms

**Moment.** Frames captured close together: one party, one walk, one wave breaking. The frame groups the album into moments by capture time (a gap of more than an hour starts a new one) and lets only a few frames of a moment into a cycle, so one afternoon cannot fill the queue. Held-back frames come back in later cycles, and a frame that repeats one already kept from its moment is left out.

The code and older documents call this "bursts" (`Bursts`, `burstHeld`, `BurstsTest`, the "Bursts:" Diagnostics line). Upstream used "burst" for a different idea: replaying rapid shots as a flipbook, which it tried and dropped because near-identical frames in sequence are unwatchable. Keeping the name "burst" would suggest we revived that. So "moment" is the name for our concept.

When the series is built, rename at the start, in the commit that introduces it (`7183de5`, P6), and follow it through the later commits (`b419d03`, `d4dbff5` and the commits that report it): `Bursts` to `Moments`, `BurstResult` to `MomentResult`, `burstHeld` to `momentHeld` and so on, with the user-visible wording on the controls page and in Diagnostics. Doing it there avoids a rename that conflicts with every later PR. `development` itself keeps the old names.

## Features

| # | Feature | Commits | Depends on |
|---|---|---|---|
| 1 | Sensor work | `36e4ac6` upside-down portrait (branch `upside-down-portrait`), `cc423be` installed build SHA in diagnostics, `2488b91` ambient brightness (branch `ambient-brightness`) | nothing |
| 2 | Picture controls | `ad47b27` current picture on the Everyday page, `fe23bcd` controls in one row, `f96831e` fading messages, `7a85f17` open the picture in Google Photos when clicked, `a8a5337` look up an album's Google Photos address at startup | in practice more than it looks: the current-picture code uses the dates rows, favorites and Show Pair, which upstream lacks (see the first attempt below); the Google Photos link also touches `PrivatePhotosClient` and, to open each picture of a collage or pair, needs the tile layout from 3 |
| 3 | Tiling (pairs, collages, fill) | `4f2b753`, `3f2d4dd`, `694b7e6`, `18c097a`, `602dc16`, `4eac8ed`, plus dates in `eadb88e` and `b654529` | later parts depend on 4 |
| 4 | Planning | `4207404`, `f2fa7ca`, `19aabcb`, `90d363d`, `04e2f6c`, `52801a1`, `b419d03`, `b48147a`, `83d7fca`, `d2b9528`, `f66c8f1`, `2bbdeb5`, `2589ccd`, `e425c67`, `d4dbff5`, `77671c7`, `34b1186` and the plan documents | the early tiling commits sit below it |
| 5 | Favorites | `d010603`, `e55ba79`, `c7b61a6`, `f31d0d2`, `095e2a0`, `11ddf54`, `6980bd5`, `1be2025` | 4 (`PlaybackEngine`, `PhotoSelection`) |

Other candidates:

- Deploy tooling: `80c7be6`, `8ac0c9f`, `b93e151`. Self-contained (`tools/` and docs).
- Reliability: `32684f5` retry backoff, `7023b90` downloads off the UI thread, `1274d7d` preload planning. Small, look independent.
- Album analysis tools: the Python scripts `album_metadata`, `album_census`, `album_compare` in `760b8ee`, `8c08102`, `8120b64`. Tied to the planning documents.
- Signing and WiFi update: `5d3e821` and `f130c98`. Look separate; check whether upstream already covers them.
- Curation seeds that belong with planning: `7183de5` moments (bursts) and near-duplicates, `6e7c771` deck, `a4fa876` plan and version mark.

## What to offer upstream, and in what order

Upstream is a one-person project: `letsteststuff` made 27 of the 38 commits, and the rest are the original author and dependabot. It releases whole versions (0.5.x-alpha) with a changelog entry and a release note, documents a three-tab controls page, and lists planned work in `ROADMAP.md` (network folders; the burst flipbook is deferred).

What we hold is 114 files and about 10,000 added lines (3,400 of app code, 3,300 of tests, 2,100 of documents) in 61 commits. Reviewing that is a lot to ask of one person, and not all of it is wanted. So the plan is to offer it in pieces and let them choose:

1. **Ask first.** Open one issue or discussion that lists about five candidate changes with a line each, and ask which ones they want. Send pull requests only for those.
2. **Small pieces, one or two at a time.** Each with a before and after from the real frame.
3. **Respect their page and releases.** Keep the three tabs. If the picture controls go upstream, they arrive as a "Now showing" card at the top of Everyday. The five-tab redesign (`web-controls-plan.md`) stays in this fork. Add changelog lines, and leave version bumps and release notes to them.
4. **Send user-facing documents only** (for example `show-pair.md`, `favorites-plan.md` trimmed to a guide). The plans, the handoff and the album tools stay in the fork.
5. **Offer the large middle last.** The engine refactor and the planning stack (P5 to P11, about 6,000 lines) have no user-visible effect of their own, and they overlap the deferred burst work. Offer them only if asked.

Candidates for the first list, smallest first:

1. The upside-down portrait fix (Q1): one line in the manifest.
2. Ambient brightness (Q3), using the lux to backlight table the device ships for Android's automatic brightness. On the tested Lenovo frame this is Lenovo's own table. The app reads it from the device and copies none of it, so it applies to any frame that has one, and with no usable table the brightness stays at the fixed daytime level.
3. The installed-build line in diagnostics (Q2), which exposes a stale APK after a web deploy.
4. Show Pair (P1): a portrait frame no longer wastes space on a landscape photo.
5. Reliability (Q6, P4): retry backoff and steadier downloads.

## Known snags

- Several commits mix features. `4eac8ed` (fill the screen) touches `PlaybackEngine` and `SlideChooser`, so it either waits for planning or carries part of it. `1be2025` serves both favorites and the album tools. The Google Photos link (`7a85f17`) sits in picture controls but clicks on a collage or pair tile need the tiling code; it was also written on top of favorites, so check it applies without them.
- `FrameWeb.kt` and `MainActivity.kt` change in nearly every commit, so cherry-picks will conflict. Expect to hand-merge.
- Upside-down portrait and ambient brightness are each one commit on `upstream/main` (branches `upside-down-portrait` and `ambient-brightness`, both on the fork), built and tested there. The build SHA commit has not been tried.

## First attempt: picture controls (2026-11-26)

Tried in a throwaway clone, branching from `upstream/main` (`4aff43d`) and cherry-picking `ad47b27` first. Nothing was kept; the repository is untouched.

- `ad47b27` (current picture on the Everyday page) conflicts in `CHANGELOG.md`, `FrameWeb.kt`, `MainActivity.kt` and `FrameWebTest.kt`. `FrameRemote.kt` and `FrameRemoteTest.kt` apply cleanly.
- Each conflict region carries lines from other features: the changelog section lists every earlier change, `FrameWeb.kt` has the favorite button form, the tests include the favorites and recency tests, and `MainActivity.kt` has the map entries for favorites and the deck.
- The commit's own code also uses things `upstream/main` does not have: `FrameCaption.groupedDates` (the dates rows), `favorites.isFavorite` (favorites) and `slide.pair` (Show Pair). `FrameSlide.collage`, `lastSlide`, `markDisplayed` and `FrameRemote` do exist upstream.
- So picture controls cannot be cherry-picked. Either it is rewritten for upstream (a "Now showing" line without the dates rows, the star or the pair label), or it waits until tiling, the dates commits and favorites have gone upstream.
- `fe23bcd` (controls in one row) and the Google Photos link (`7a85f17`, `a8a5337`) were not tried. Expect the same: they were written on top of the stack and edit the same `FrameWeb.kt` markup.

The same is likely true of the other groups. Treat the commit lists above as where each feature came from, not as cherry-pick sets.

Decision: send them in dependency order as stacked PRs, so the later ones apply without rewriting, and split the four large commits listed under "Commits to split". The analysis behind this is in "Proposed series" below.

## Proposed series

Method: for every commit, the identifiers it defines (classes, objects, functions, constants, camelCase names) and which later commits use them. Rolled up by feature, this gave a layering. It is a heuristic: a shared line of context counts as a use, and a real use through a generic name can be missed. Treat it as a strong hint and let the build and tests decide.

What the layering showed:

- Independent of everything: the sensor work, the deploy script, the signing tools, the retry backoff. (The WiFi update needs one name from Show Pair, `SlideMode`.)
- `4f2b753` (Show Pair and same-day collages, about 1,000 lines of code) is the root. Almost everything else uses names it defines, so it goes first, split into parts.
- The deck, moments and engine commits depend on that root and on each other in a line; the engine refactor (`4207404` to `83d7fca`) sits under the later commits for recency, quality, day planning, collage themes and fill.
- Favorites uses the deck, moments, engine and day-planning code. The picture controls and the Google Photos link were written on top of favorites: the page's one row of buttons includes the Favorite button, and the link also handles the favorites album. So they follow favorites naturally.
- "Fill the screen" (`602dc16`, `4eac8ed`) uses the engine, deck, recency and favorites code, so it goes last.

### Series

Quick wins (independent, send any time, in parallel):

| PR | What | Commits |
|---|---|---|
| Q1 | Allow upside-down portrait | `36e4ac6` (branch `upside-down-portrait`) |
| Q2 | Installed build SHA in diagnostics | `cc423be` |
| Q3 | Ambient light sensor for brightness | `2488b91` (branch `ambient-brightness`; the message is the PR text) |
| Q4 | Deploy script | `80c7be6`, `8ac0c9f`, `b93e151` |
| Q5 | Signing and re-signing tools | `5d3e821` |
| Q6 | Retry backoff | `32684f5` |
| Q7 | WiFi update and silent install | `f130c98` (adapt the one `SlideMode` use) |

The playback story, each PR on top of the one before. Under "What to offer upstream, and in what order" these go out one or two at a time and only if wanted; P1 to P4 are the first candidates, P5 to P11 are held back, and P13 arrives as a card on Everyday, not a new tab:

| PR | Story | Commits |
|---|---|---|
| P1 | Show Pair, with the white mat | `4f2b753` (pair part), `3f2d4dd`, `694b7e6` |
| P2 | Same-day collages chosen by shape | `4f2b753` (collage part) |
| P3 | Soft photos kept out of collages | `4f2b753` (`PhotoQuality` part) |
| P4 | Steadier downloads | `7023b90`, `1274d7d` |
| P5 | Never repeat, never miss: the deck | `6e7c771` |
| P6 | Thin moments (bursts of frames) and near-identical frames | `7183de5` |
| P7 | The playback engine and its simulation (a refactor, no behavior change) | `4207404`, `f2fa7ca`, `19aabcb`, `04e2f6c`, `52801a1`, `b48147a`, `83d7fca` |
| P8 | Better picks: seasonal deck, moments in one pass, twins | `90d363d`, `b419d03`, `d4dbff5` |
| P9 | Favor newer photos; skip soft photos on every slide | `f66c8f1` (split in two) |
| P10 | Collage themes, four-photo layouts, dates in reading order | `18c097a` (split in three), `eadb88e`, `b654529`, `cc394a4` |
| P11 | Plan by the day, with the Your album card | `2bbdeb5`, `d2b9528`, `2589ccd`, `e425c67` (split), plus the album tools `760b8ee`, `8c08102`, `8120b64` |
| P12 | Favorites | `d010603`, `e55ba79`, `c7b61a6`, `f31d0d2`, `095e2a0`, `11ddf54`, `6980bd5`, `1be2025` |
| P13 | Picture controls as a "Now showing" card on Everyday: current picture, one row, Google Photos link | `ad47b27`, `fe23bcd`, `f96831e`, `7a85f17`, `a8a5337` |
| P14 | Fill the screen, no unused space | `602dc16`, `4eac8ed` |

Documents (`curation-plan.md`, `playback-plan.md`, `day-coverage-plan.md`, `streams-plan.md`, the handoff, the changelog entries) go with the PR whose behavior they describe. The changelog is one growing section, so each PR should add only its own lines.

### Commits to split

- `4f2b753` (1,018 code lines): Show Pair (`PairSelection`, the pair slide), same-day collages (`CollageSelection`, `CollageThemeSelection`), the soft-photo gate (`PhotoQuality`) and the cache change (`FrameCache`). Four parts, three PRs (P1 to P3); the `FrameCache` part goes with P1 if the pair needs it, else with P4.
- `18c097a` (946 code lines): collage themes, the free photo count with four-photo layouts, and dates in reading order (`FrameCaption`). Three parts.
- `f66c8f1` (389): recency weighting and the soft-photo gate for every slide. Two parts.
- `e425c67` (516): the Your album card and the blur wording. Two parts.

### How to build it

- Create one branch per PR, each on the one before, starting from `upstream/main`. Reorder the independent commits to the front.
- Take unsplit commits with cherry-pick and resolve conflicts once; for the splits, apply the commit and stage by file or by hunk.
- At every branch run `:app:assembleDebug :app:testDebugUnitTest lintDebug`. A series that builds at each step is the point.
- Rebase the later PRs as earlier ones merge. Open the quick wins first, because they need no one else.
- Do this in a clone, never on `development`, which stays as the working history.

## How to check before opening a PR

1. Clone the repository into a scratch directory and fetch `upstream/main`.
2. For each feature, cherry-pick its commits onto `upstream/main` in order, and record which apply cleanly and which conflict.
3. Build the clean ones with `:app:assembleDebug :app:testDebugUnitTest lintDebug`.
4. Open one PR per feature, in the order above, and rebase the later ones as earlier ones merge.
