# Upstreaming plan

Written 2026-11-26 from the history of `favorites-f3`. Status: a proposal. Nothing here has been extracted or tested yet.

## Where things stand

- `favorites-f3` is a stack: `show-pair` -> `curation-phase-0..4` -> `favorites-f1..f3`. Each branch contains the one before.
- It already contains upstream's latest (`upstream/main` at `4aff43d`), so every extraction below would branch from `upstream/main`. `origin/main` is the one that lags (`f73807a`).
- The branch is 64 commits ahead of `origin/main`. Three of them are upstream's own (`fa039cc`, `3a89d8c`, `4aff43d`), so 61 are ours.
- Almost every feature touches `MainActivity.kt` and `FrameWeb.kt`, so each extraction needs some conflict resolution.

## Features

| # | Feature | Commits | Depends on |
|---|---|---|---|
| 1 | Sensor work | `b09c8f4` upside-down portrait, `cc423be` installed build SHA in diagnostics, `8001906` ambient brightness | nothing |
| 2 | Picture controls | `ad47b27` current picture on the Everyday page, `fe23bcd` controls in one row, `f96831e` fading messages, `7a85f17` open the picture in Google Photos when clicked, `a8a5337` look up an album's Google Photos address at startup | in practice more than it looks: the current-picture code uses the dates rows, favorites and Show Pair, which upstream lacks (see the first attempt below); the Google Photos link also touches `PrivatePhotosClient` and, to open each picture of a collage or pair, needs the tile layout from 3 |
| 3 | Tiling (pairs, collages, fill) | `4f2b753`, `3f2d4dd`, `694b7e6`, `18c097a`, `602dc16`, `4eac8ed`, plus dates in `eadb88e` and `b654529` | later parts depend on 4 |
| 4 | Planning | `4207404`, `f2fa7ca`, `19aabcb`, `90d363d`, `04e2f6c`, `52801a1`, `b419d03`, `b48147a`, `83d7fca`, `d2b9528`, `f66c8f1`, `2bbdeb5`, `2589ccd`, `e425c67`, `d4dbff5`, `77671c7`, `34b1186` and the plan documents | the early tiling commits sit below it |
| 5 | Favorites | `d010603`, `e55ba79`, `c7b61a6`, `f31d0d2`, `095e2a0`, `11ddf54`, `6980bd5`, `1be2025` | 4 (`PlaybackEngine`, `PhotoSelection`) |

Other candidates:

- Deploy tooling: `80c7be6`, `8ac0c9f`, `b93e151`. Self-contained (`tools/` and docs).
- Reliability: `32684f5` retry backoff, `7023b90` downloads off the UI thread, `1274d7d` preload planning. Small, look independent.
- Album analysis tools: the Python scripts `album_metadata`, `album_census`, `album_compare` in `760b8ee`, `8c08102`, `8120b64`. Tied to the planning documents.
- Signing and WiFi update: `5d3e821` and `f130c98`. Look separate; check whether upstream already covers them.
- Curation seeds that belong with planning: `7183de5` bursts and near-duplicates, `6e7c771` deck, `a4fa876` plan and version mark.

## Suggested order

1. Sensor work, as three small PRs.
2. Picture controls and deploy tooling. If the Google Photos link must open each picture of a collage or pair, send the link after the early tiling commits, or in the first picture-controls PR hold it back until then.
3. Tiling: the early pairs and collages first, then the fill logic.
4. Planning.
5. Favorites, last, because it builds on the planning code.

## Known snags

- Several commits mix features. `4eac8ed` (fill the screen) touches `PlaybackEngine` and `SlideChooser`, so it either waits for planning or carries part of it. `1be2025` serves both favorites and the album tools. The Google Photos link (`7a85f17`) sits in picture controls but clicks on a collage or pair tile need the tiling code; it was also written on top of favorites, so check it applies without them.
- `FrameWeb.kt` and `MainActivity.kt` change in nearly every commit, so cherry-picks will conflict. Expect to hand-merge.
- The ambient brightness and build SHA commits add hooks in `MainActivity.kt` that should apply on their own, but this has not been tried.

## First attempt: picture controls (2026-11-26)

Tried in a throwaway clone, branching from `upstream/main` (`4aff43d`) and cherry-picking `ad47b27` first. Nothing was kept; the repository is untouched.

- `ad47b27` (current picture on the Everyday page) conflicts in `CHANGELOG.md`, `FrameWeb.kt`, `MainActivity.kt` and `FrameWebTest.kt`. `FrameRemote.kt` and `FrameRemoteTest.kt` apply cleanly.
- Each conflict region carries lines from other features: the changelog section lists every earlier change, `FrameWeb.kt` has the favorite button form, the tests include the favorites and recency tests, and `MainActivity.kt` has the map entries for favorites and the deck.
- The commit's own code also uses things `upstream/main` does not have: `FrameCaption.groupedDates` (the dates rows), `favorites.isFavorite` (favorites) and `slide.pair` (Show Pair). `FrameSlide.collage`, `lastSlide`, `markDisplayed` and `FrameRemote` do exist upstream.
- So picture controls cannot be cherry-picked. Either it is rewritten for upstream (a "Now showing" line without the dates rows, the star or the pair label), or it waits until tiling, the dates commits and favorites have gone upstream.
- `fe23bcd` (controls in one row) and the Google Photos link (`7a85f17`, `a8a5337`) were not tried. Expect the same: they were written on top of the stack and edit the same `FrameWeb.kt` markup.

The same is likely true of the other groups. Treat the commit lists above as where each feature came from, not as cherry-pick sets.

Decision still open: rewrite each feature for `upstream/main`, or send them in dependency order (tiling, then planning, then favorites, then picture controls) so the later ones apply without rewriting. The second means larger early PRs; the first means more writing and testing per feature.

## How to check before opening a PR

1. Clone the repository into a scratch directory and fetch `upstream/main`.
2. For each feature, cherry-pick its commits onto `upstream/main` in order, and record which apply cleanly and which conflict.
3. Build the clean ones with `:app:assembleDebug :app:testDebugUnitTest lintDebug`.
4. Open one PR per feature, in the order above, and rebase the later ones as earlier ones merge.
