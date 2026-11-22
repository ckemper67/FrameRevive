# Favorites: star on the frame, seed from Google, show them more often (proposal)

Status: a plan for review. Nothing is built. The app has no notion of a favorite today.

Three things are wanted, and they are separable, so the plan builds them in an order that lets each be judged alone:

1. Star a photo on the frame and keep it across restarts, updates and album refreshes.
2. Seed the list from Google, so the stars already given on a phone carry over.
3. Show favorites more often, without the frame turning into a loop of the same few hundred pictures.

The third is the hard one and the one that should be tuned first, because seeding can add hundreds of favorites at once.

## 1. What a favorite is

A favorite is a photo id (Google's media key, the first field of each album row, which is also what the play history and the deck key on) with a little metadata:

| Field | Meaning |
| --- | --- |
| `id` | The media key. Stable across syncs, so a star survives an album refresh. |
| `starredMs` | When it was starred or seeded. New stars are brought back sooner. |
| `origin` | `frame` (starred here) or `google` (seeded). |
| `vetoed` | Seeded from Google but un-starred on the frame. Stops the next seed from bringing it back. |

Stored in its own file in `filesDir` (`favorites.tsv`, tab-separated, written beside the target and renamed, like `PhotoDeck.save`). It is deliberately not part of the play history: the history is pruned to the current album and is meant to be forgettable, a favorite is the owner's data.

Rules that follow from that:

- A favorite whose photo is not in the album right now (album switched, sync came back short) is kept dormant, never deleted. It is simply not a candidate. Pruning happens only when the owner un-stars.
- The set lives in memory as a `Set<String>` loaded at start. Playback never reads the file or preferences per photo (the frame is 40 to 100 times slower than the Mac; see the performance rule). Writes happen on the existing play-history thread.
- The controls page gets an export and import of the file, so a "clear data" or a replaced frame does not lose the list. It holds ids and timestamps only, no secrets.
- One-way with Google: the frame never writes a star back to Google. The client is read-only on an unofficial interface, and the official Library API no longer lets an app change items it did not create. Frame stars stay on the frame.

## 2. Starring on the frame

The frame has three inputs, and each is already spoken for:

| Input | Today |
| --- | --- |
| Plus / Minus buttons | next / previous |
| Star button (key 295), short | pause or resume |
| Star button, hold 2.5 s | open the controls overlay |
| Wave left / right | next / previous |
| Tap the screen | open the controls overlay |

Options:

- **A. A Favorite button in the controls overlay and on the Everyday web page.** No input conflict, discoverable, works today. Costs two interactions (open the overlay, press). Safe first step.
- **B. The physical star button, as a setting.** **Star button: Pause or resume / Favorite this photo.** The default stays Pause or resume, so nothing changes until the owner picks it. With Favorite, a short press stars the photo on screen (a second press un-stars it), and pause and resume stay available in the controls overlay and on the Everyday page, which already have them. Holding the button for 2.5 seconds still opens the controls either way. The setting is on the controls page next to the other button and gesture options, saved in preferences, and read once into memory (never per photo). `FrameButtons.Action` gains `TOGGLE_FAVORITE`, and `front-buttons.md` documents both modes. A double-press scheme was dropped: it would delay every pause by the double-press window for the sake of one feature.
- **C. Double-tap on the screen.** Easy, but taps open the controls today and the frame is often reached with a hand wave, not a touch.

Recommendation: A and the B setting together in the first release (the setting costs little because the pause path already exists), C not at all.

Details that matter:

- Bind the star to the slide that was on screen when the button went down, not when the handler runs, so a press during a transition cannot star the next photo.
- Feedback is a small filled star drawn near the caption for about two seconds ("Added to favorites"), and a favorite shows a faint star while it is on screen. Pressing again un-stars and says so. The pause badge and the star must not collide.
- A collage or pair has several pictures. v1: the overlay button stars the whole slide only when it is a single photo, and for a collage it opens a short choice listing the pictures by position (the same left-to-right, top-to-bottom order the dates use). Starring every picture of a collage would mark pictures the owner did not mean.
- Starring is allowed while paused and in preview; un-starring a seeded favorite sets `vetoed`.

## 3. Seeding from Google

The honest state of the options, with what is verified and what is not:

| Route | Works | Risk |
| --- | --- | --- |
| **A. Unofficial web client, the Favorites view** | Needs a signed-in session. **Ruled out for this frame**: it runs on a shared link, and a link request carries no account, so Google cannot tell it whose favorites to return. Signed-in setup (a cookie export from a browser, 0.5.2 and earlier) was retired in 0.5.23 and its code only ever used the album list and album contents requests; favorites were never read. | Reviving it means asking for the account cookies again, which was the reason it was retired. Not recommended. |
| **B. A dedicated "Frame favorites" album** | Both modes, including the shared-link mode, because the frame already syncs an album from either. Starring on the phone means adding to that album. | Two actions instead of one. And one thing to verify: that an item has the same id in two albums, or the frame cannot match it to the main album. Fallback is matching on capture time. |
| **C. Google Takeout, offline on the dev machine** | Takeout's per-photo JSON has a `favorited` flag. Same pattern as `tools/album_dates.py`: a script that writes a local file the frame can import. Good for a one-time seed. | Takeout carries a title and a capture time in seconds, not the media key, so it matches by time. Burst photos share a second, so a favorite could match a neighbor (resolve by keeping only exact-millisecond or unique matches and reporting the rest). It is a snapshot, not a sync. |
| **D. Official Library API or Picker** | Official and stable. | As I understand it, since 2025 an app can only read what it created, so it cannot list favorites of the whole library; the Picker makes the owner choose photos by hand. Treat as ruled out for seeding, but confirm before dropping it. |

Recommendation: B, with C as the one-time bulk import. B fits how the frame already works (a shared link, no account) and needs no new unofficial request: a second shared album link, synced the same way as the main one, whose members are the favorites. Both routes produce a list of ids, so the store, the stars and the weighting do not care which one fed them.

History checked (git): the frame was first signed-in only; 0.5.3-alpha (2026-10-21) added shared links as a cookie-free source; 0.5.23/0.5.24 (2026-10-29) retired new signed-in sessions and album selection. The owner's frame uses the shared link, so A is not available without undoing that decision.

The one thing to verify for B (spike F0): that a photo has the same id in the main album and in the favorites album. The existing tool (`tools/album_dates.py`) can read both links on the dev machine; the owner makes a small shared album with a few photos that are also in the main album, and the ids are compared. If they differ, the frame matches on capture time (second and size) instead, and tolerates a few misses.

Seed semantics: a seed adds ids with `origin = google`, never removes anything, and skips `vetoed` ids. Re-seeding on the daily album check is fine; un-starring on Google does not remove it from the frame (the frame has no way to tell a Google un-star from a missing seed). The controls page shows "N favorites (M from Google)" and a button "Check Google again".

## 4. Showing favorites more often

### What is already there

Slots come from a plan sized from the pace (384 slots at the owner's 240 photos a day), made of the recent window, a seasonal memories share (`memorySlots`), and the rest, drawn from a deck so nothing repeats until a pass is done. Bursts are thinned, soft photos are gated, collages come from themes. The day-coverage plan proposes three pools for those. Favorites are a fourth source and are best treated as a quota taken first and independent of how the rest is divided, so the day-coverage decisions do not have to be settled first.

### The risk

Screen time is the scarce thing, and favorites are a small set. At 240 photos a day:

| Favorites | Share of the day | Slots a day | A given favorite returns every |
| --- | --- | --- | --- |
| 100 | 10% | 24 | 4 days |
| 300 | 10% | 24 | 12 days |
| 300 | 25% | 60 | 5 days |
| 1,000 | 10% | 24 | 42 days |

### Decision: the share does not depend on how many favorites there are

The owner asked for the selection to be independent of the number of favorites. So the setting is a share of the day and nothing else: with 20 favorites or 2,000, favorites get the same fraction of the slots. Earlier drafts planned from a return interval (`N / targetDays`) so that a short list would not repeat quickly; that couples the share to N and is dropped.

What that means, plainly, so the setting is chosen with open eyes. How often one favorite comes back is now a result, not a choice:

| Favorites | At 5% of the day (12 a day) | At 10% (24 a day) |
| --- | --- | --- |
| 50 | every 4 days | every 2 days |
| 100 | every 8 days | every 4 days |
| 300 | every 25 days | every 12 days |
| 1,000 | every 83 days | every 42 days |

A short list repeats often and a long one rarely. That is easy to understand ("favorites get one slot in twenty"), predictable for the rest of the plan, and it makes the effect on the other pools identical whatever the owner has starred. The cost is that a very short list can feel stale, so the Your album card says it in words and the owner can lower the setting: "12 favorites a day; each of your 100 favorites comes back about every 8 days".

### The design

- The one setting is non-technical: **Favorites: Off / Now and then / Often**, a share of the day of 0 / 5 / 10 percent. Default Now and then (5%). At the owner's 240 photos a day that is 12 slots.
- `favoriteSlots = share * plannedSlots`, computed once per plan. It does not look at N. If there are fewer favorites than slots, the pass simply starts over (see the deck below); the only other adjustment is the plain rule that a favorite is never shown twice within the same collage or pair and never back to back.
- Favorites are drawn from their own deck: a shuffled pass through the favorites that is not repeated until finished, ordered by `lastShownMs` (the play history already records it), so the least recently shown comes first. A new pass reshuffles but never puts a photo shown in the last hour at the front. Newly starred or seeded photos are not shown immediately (the owner just saw it) but go near the front of the deck, so a new star is seen again within days.
- Spread through the day, not in a clump: take favorite slots at even intervals of the plan (every k-th slot, jittered), never two favorites in a row. In a collage, favorites are not given a bonus in v1; collages keep the theme logic that ensures spread and tiling. A theme for favorites ("Favorites together") is possible later.
- A favorite is not double counted: a photo that is both a favorite and a seasonal memory uses a favorites slot and is removed from the memories candidates for that plan, so the combined share is never above the sum of the two shares.

### Interaction with the existing curation

- **Bursts**: favorites are never the photo that thinning drops. If a starred photo is in a burst, it wins the burst (and still stays two minutes away from its siblings, which the thinning already guarantees).
- **Sharpness gate**: a photo the owner starred is exempt from the soft-photo gate. Their choice outranks the measurement.
- **Near-duplicates and pairs**: unchanged, but a pair may include a favorite.
- **Recency decay** ("Favor newer photos"): applies to the rest of the plan, not to favorites; their order is by last shown.
- **Plan size**: favorite slots are part of the planned total, not added to it, so the day length does not change.
- **Performance**: favorites are one more in-memory set and one more deck; deck build must stay near today's roughly 230 ms. Judge by the Deck build ms line on the controls page.

### Measuring before turning it on

The real album has no favorites yet, so the simulation needs stand-ins. Extend `RealAlbumSimulation` with favorite sets of 50, 200 and 500 photos drawn in three ways (random, clustered in recent years, clustered on a few days) and report, at the owner's real pace:

- favorites a day (must equal the share times the plan for every size of list, which is the independence check) and the median return interval of a favorite, reported for information;
- the longest streak without a favorite and any two favorites back to back;
- the share of favorites that are twins of each other (should be near 1%, like the rest);
- the effect on the other pools: memories slots and recent-window slots a day before and after.

Acceptance: favorites a day within 5% of the share for 50, 200 and 500 favorites alike, no two favorites in a row, no change to the twin share, memories and recent shares reduced by no more than what the favorites took.

## 5. Phases

Each on its own branch, stacked, with the simulation numbers recorded in this document.

| Phase | Content | Why this order |
| --- | --- | --- |
| F0 | Spike: does an item keep its id across two shared albums (B)? Needs the owner to make a small shared album of a few photos that are also in the main album. No app code. | Decides whether B matches by id or by capture time. |
| F1 | Store (`favorites.tsv`), in-memory set, star in the overlay and on the Everyday page, the Star button setting, feedback, un-star, export and import, the card line. Tests for persistence across restart, album refresh and album switch. | Useful alone; nothing changes in what is shown. |
| F2 | Weighting: a fixed share of the plan, favorites deck, spacing, burst and gate exemptions, the three-way setting, Your album card text, simulation report. | Must be tuned before seeding can add hundreds. |
| F3 | Seeding by the route F0 found, with the `vetoed` rules and the Check Google again button. | Last, because it adds the volume the weighting has to handle. |
| F4 | Starring one picture of a collage, optional favorites collage theme. | Polish. |

## Progress

- F1 built on branch `favorites-f1`: `Favorites` store and file (`favorites.tsv`), Favorite button in the overlay and on the Everyday page, a star badge while a favorite is on screen, the Star button setting, backup and restore on the Debug page, tests. Favorites do not yet change what is shown (F2).

## 6. Decisions needed

1. ~~Signed in or a shared link?~~ Answered: a shared link, so seeding is route B (second shared album) plus C (Takeout).
2. ~~The physical star button~~ Answered: a setting, Pause or resume (default) or Favorite this photo.
3. Roughly how many favorites on Google? No longer affects the plan (the share is independent of it); it only tells how often each one will return.
4. A collage: choose which picture to star, as proposed, or star all of them?
5. Default setting "Now and then" (5% of the day, 12 photos at your pace), or more? Also answered in part: the number of favorites does not change the share.

## 7. Risks and what would change the plan

- Seeding is two steps for the owner (add to the favorites album on the phone), not one star. That is the price of staying cookie-free.
- Ids may differ between albums; then B matches by capture time and tolerates a few misses.
- A short list of favorites repeats often because the share is fixed. The card says how often each comes back so the setting can be lowered. F2 still precedes F3 so the share is proven before seeding adds volume.
- Starring on the frame and starring on a phone drift apart (one way only); the card makes the counts visible so the owner can tell.
