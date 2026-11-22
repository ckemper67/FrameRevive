# Plan: multiple photo streams

Status: **on hold** (owner, 2026-11-22). Nothing here is scheduled or built, and no code was written for it.
This is the dedicated plan to pick up later. Written from the code on `curation-phase-4` and revised the same
day after the owner's answers (see "Decisions so far"); the open questions at the end are what to answer first.

The Lenovo frame let you keep several photo streams and flip between them with an up or down hand gesture.
FrameRevive today plays exactly one shared album. This plan adds streams without disturbing playback.

## What a stream is

A **stream** is a named photo source that the frame can play: a shared album link, and later an RSS or Atom
feed (see "Feed streams"). Exactly one stream is active. Up and down gestures move to the next or previous stream; the name shows on screen for a moment.
Everything the frame already does (deck, memories, collages, pairs, dates) keeps working inside the active
stream, unchanged.

## Decisions so far (owner, 2026-11-22)

- Streams are **separate albums**, as on the Lenovo.
- **Settings are shared** by all streams: seconds per photo, mix, age bias, dates, blur. The
  only differences between streams come from what the stream's kind implies (feeds, below), never from a
  per-stream settings screen. This drops the old optional step for per-stream settings.
- **Feeds** (RSS or Atom) become a second kind of stream. They pull only pictures that match the frame's
  orientation and never show collages.

## What the code does today, and what that means

| Today | Where | Consequence for streams |
| --- | --- | --- |
| One album: `albumId`, `sharedLink`, `title`, `photos` | `MainActivity`, `album.json` | Needs a list of streams and one catalog file per stream |
| Switching album clears history, priority ids and chooser state | `sync()` ("changingAlbum") | Must become "switch stream" that keeps each stream's own state |
| Play history is keyed by photo id, shared by everything | `playHistory`, `photo-history.json` | Fine as one map (ids are unique), but `PhotoDeck.pruned` keeps only the current album's ids; it must keep the union of all streams |
| Cache cleanup deletes files not in the current album | `sync()`, `photo-cache` | Must keep the union, or switching back re-downloads everything |
| Hashes and quality scores are keyed by photo id | `photo-hashes.txt`, `photo-quality.json` | Work as they are |
| `playlist`, `index`, collage state live in one `SlideChooser` | `SlideChooser` | One chooser per stream, so switching back resumes where it left off |
| Back/forward history of shown slides | `PhotoHistory` | One per stream, so "previous" never jumps into another stream |
| Settings are global (mix, collages, pairs, age bias, seconds) | `prefs` | Stay global to begin with |
| Gesture device sends key codes 289..294; only the horizontal pair is used | `FrameGestures`, `dispatchKeyEvent` | Up and down are the unused pair; which code is which direction is unconfirmed (see Step 0) |
| A 15,000-photo catalog is parsed into memory | `readCatalog` | Keep only the active stream in memory; load another off the UI thread on a switch |

## Design

```
StreamSet        Pure Kotlin. The list of streams, which is active, next/previous with wrap-around, add,
                 rename, remove, reorder. Persisted as streams.json. No Android types.
StreamStore      Reads and writes one catalog file per stream (album-<id>.json) and streams.json.
                 Migrates the current album.json into stream 1 on first start; nothing is lost.
Per-stream state A SlideChooser and a PhotoHistory per stream, created when the stream is first shown and kept
                 while the app runs. Play history, hashes and quality stay global.
MainActivity     Shows the active stream; switching = save position, load the other catalog off the UI
                 thread, rebuild its cycle, show a name pill. No selection logic added here.
FrameGestures    Adds a vertical axis and one result type: next, previous, stream up, stream down.
```

Switching order: up goes to the next stream in the list, down to the previous, wrapping at the ends. (The
direction can be flipped with one setting if it feels backwards.)

On-screen: a pill at the bottom for about two seconds, "Family  2 of 4", in the style of the existing caption.
It is also the only feedback that a gesture was seen, so it appears even when there is only one stream and the
gesture does nothing ("Only one stream").

Controls page: a **Streams** card listing each stream with rename, reorder, remove, and "play now"; an
**Add stream** form that takes a name and a shared album link, checked the same way the single album link is
checked today. A limit of six streams keeps memory and sync work bounded. Album links are private, so the list
shows names and the number of photos, not the links.

## Steps (each independently shippable and revertable)

**Step 0: find the vertical codes (small, do first).** The gesture device reports 289..294. The existing
mapping implies 291 and 292 are vertical on an unrotated frame, but only left and right were ever confirmed on
hardware. Add a line to the controls page showing the last raw gesture key code and when it came, for every
code in 289..294, including the ones that do nothing. Waving up and down in front of the frame then tells us
the real codes and which is which, with no `adb` needed. Nothing else depends on guessing.

**Step 1: streams as data (no visible change).** `StreamSet` and `StreamStore`, migration of `album.json` into
stream 1, history pruning and cache cleanup over the union of streams. With one stream the frame behaves
exactly as now. Tests: navigation and wrap-around, migration from a real-shaped `album.json`, pruning keeps
other streams' history, a stream removed means its catalog and cache entries are cleaned up.

**Step 2: switching in the activity.** Per-stream chooser and history, switch off the UI thread, name pill,
no gesture yet (a temporary controls-page "play stream" button proves it). Tests: switching and switching back
resumes the same cycle position, the play-history map is untouched by a switch, preloads follow the active
stream only.

**Step 3: gestures.** Vertical mapping per rotation with the confirmed codes, a shared 800 ms cooldown so a
wave cannot trigger two actions, and a setting "Up and down switch streams". Tests: every rotation, the
cooldown across axes, the setting off.

**Step 4: managing streams from the controls page.** Add, rename, reorder, remove, play now. Tests: link and
name validation, the six-stream limit, removing the active stream moves to its neighbour.

**Step 5: feed streams.** See the next section. It needs Steps 1 and 2 (a stream has a kind) and nothing from
Steps 3 and 4, so it can be built before or after the gestures.

## Feed streams

A feed stream turns an RSS or Atom feed into a photo stream. The owner's example is a feed of nice landscape
photos or classical art; a photographer's feed or a space-picture feed would work the same way. Because such
pictures do not go out of date, a feed is treated as a pool to walk through, not as news. It is a different kind of source from an album, so it gets its own client
and its own rules, but everything after "a list of photos" is the same code.

**Rules a feed stream implies** (set by the stream's kind, not by settings):

| | Album stream | Feed stream |
| --- | --- | --- |
| Source | shared album link | feed URL |
| Orientation | every photo plays; pairs and collages combine mixed shapes | only photos matching the frame's orientation are pulled |
| Collages | by the shared settings | never |
| Pairs | by the shared settings | never (a pair needs the opposite orientation, which is not pulled) |
| Memories ("this week in past years") | by the shared mix | off: feed items carry a publish date, not a capture date |
| Order | the deck, with the age bias | the deck over the kept items, with no age bias (a painting does not get stale); a new item gets the existing one-time priority showing |
| Items that leave the source | removed at the next sync | kept, up to a cap, even after the feed drops them |
| Size | any | too-small pictures are skipped (see below) |

In code this is one small `StreamPolicy` per kind (collages, pairs, memories, orientation filter) consulted when
the `SlideContext` is built, so the existing presentations need no change: `ctx.collages` and `ctx.pairs`
are simply false for a feed, and the cycle is built with a 100% recent mix. A new kind later is a new policy.

**Reading a feed.** `FeedClient` fetches over HTTPS with Android's own `XmlPullParser` (no new library) and finds
one picture per item, in this order: `media:content` of type image, an `enclosure` of type `image/*`, the first
`<img>` in `content:encoded` or the description; `media:thumbnail` is only a fallback because it is small. Both
RSS 2.0 and Atom are read. JSON Feed is a later extra. It sends `ETag` and `If-Modified-Since`, so an unchanged
feed costs one small request. Default check: hourly, never more often than every 15 minutes.

**Catalog.** Each item becomes a `FramePhoto`: the id is a stable hash of the picture URL (so a re-published
item is not shown twice), `takenMs` is the item's publish date, `discoveredMs` is when the frame first saw it,
and the item's title is stored for the caption. Items are kept up to a cap (suggested: 500, oldest dropped first,
along with their cached file) with no age limit, so a daily "picture of the day" feed builds a gallery over time. The catalog is one more `album-<id>.json`, so the rest of the frame treats it like
any album.

**Only pull the right orientation.** The frame knows its orientation (`PairSelection.orientation`); a photo
matches when it is not the opposite shape, so squares count as matching. Width and height come from the feed
when it gives them (`media:content width and height`). When it does not, the frame learns them from the file's
first bytes with a ranged request, falling back to a normal download if the server ignores ranges, and a
picture of the wrong shape is deleted after probing. Its size is kept, so it is never fetched again, and if the
frame is rotated the kept sizes re-filter the catalog at once with no new downloads. The filter runs when a
cycle is built, so it follows rotation.

**Only pictures big enough.** The panel is 1920 by 1080, and a thumbnail stretched to it looks poor. A picture
is skipped unless its long edge is at least 1280 pixels (a setting with that default). Many feeds put only a
small image in the description and the full one behind a link; for those the parser prefers `media:content`
and enclosures, and if every item is too small the controls page says so ("0 usable, 40 too small") instead of
showing nothing and leaving the owner to guess. Following links to larger versions is a later, per-source
extra.

**Showing a picture whole.** Paintings should not be cropped. The existing shared "crop" setting is off by
default, so a 4:3 painting on the 16:9 panel is shown whole with the blurred background at the sides, and the
orientation rule only removes pictures of the opposite shape. Nothing extra is needed, but this is the case to
check on the frame first.

**Safety.** A feed is untrusted input fetched by the frame. Limits: HTTPS only unless the owner opts in per
feed; no cookies and no credentials sent; at most 3 redirects, none to a non-web scheme or a private or
link-local address; feed at most 2 MB; picture at most 20 MB and a pixel cap checked from the header before any
decode; sensible timeouts; one download at a time like the album preloader; HTML in a description is parsed for
an `<img>` only and never rendered. The feed URL may contain a private token, so like album links it is never
shown on the controls page, only the feed's own title.

**Captions.** A feed picture's publish date is not when it was taken, so the date caption is off for feeds.
For art and landscapes the item's title is what a viewer wants ("Wanderer above the Sea of Fog - Friedrich"),
so the title is the caption by default, on one line, shortened to fit, in the existing caption style. It is a
shared setting like the date caption: one switch, "Show titles for feed pictures".

**Several feeds.** Each feed is its own stream, so "Landscapes" and "Classical art" can sit beside the family
albums and the up and down gestures move between them. Nothing special is needed for that; it follows from
streams being a list.

**Tests, no network.** Fixture feeds (RSS 2.0, Atom, a media-namespace feed, one with only an `<img>` in the
description, a malformed one) for the parser; an orientation filter test including squares and a rotation;
dedupe and retention; a fake HTTP client for conditional requests, redirects and size limits; a chooser test
that a feed context yields only singles; a simulation case where a feed stream plays for several cycles and
every slide is a single of the right orientation.

## Measuring

Add a two-stream case to `PlaybackSimulation`: alternate streams every N slides for several cycles. It must
show that each stream's cycle resumes where it stopped, that no photo appears in a stream it does not belong to,
that per-stream coverage matches playing the stream alone, and that a switch costs no extra repeats. After
deploying, the controls page should report per stream: photos, shown percentage, last played. A feed
stream also reports items found, kept, skipped for orientation, and the last check.

## Risks

- **Memory and load time.** Two 15,000-photo catalogs in memory on a 2019 frame is wasteful, so only the active
  one is loaded. The cost is a pause on the first switch to a stream. Mitigation: load off the UI thread, keep
  the current slide on screen until the new stream's first photo is ready, then show the pill.
- **Sync traffic.** Checking every stream's album for additions multiplies network work. Check the active
  stream as now and the others one at a time on a slow timer.
- **Cache pressure.** The 512 MiB cache is shared, and oldest files go first, so a stream that is not being
  watched loses its cache first. That is acceptable and self-healing.
- **Accidental switches.** A wave meant for next/previous could be read as up/down. The cooldown and a
  distinct vertical motion help, and the setting can turn it off. Confirm on hardware before relying on it.
- **Privacy.** Stream names and counts may show on the controls page; links must not.

## Questions for the owner

Answered: streams are separate albums; settings are shared.

Still open for streams:
1. Should there be an "All" stream that mixes every album, and should it be the default?
2. Should switching wrap around at the ends? (The plan assumes yes.)
3. Should the frame remember the last stream across sleep and restarts? (The plan assumes yes.)
4. A front-button way to switch streams as well, for anyone who cannot wave? (The plan suggests holding Plus
   or Minus.)

New, for feeds:
5. Which landscape and art feeds do you have in mind? One or two real addresses tell us which picture tags to
   support first. (Sources that publish full-size, freely usable pictures are best; the frame cannot judge
   usage rights, so that choice is the owner's.)
6. Are plain `http` feeds acceptable if you opt in for that feed, or HTTPS only?
7. Is the item's title the right caption for art and landscapes? (The plan assumes yes, with no date.)
8. How many feed pictures should the frame keep? (Suggested: the newest 500, no age limit.)
9. Is 1280 pixels on the long edge the right minimum for a feed picture, or should smaller ones be allowed?
10. If the frame is rotated, should a feed re-filter to the new orientation? (The plan assumes yes.)
