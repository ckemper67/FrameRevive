# Plan: cover days, and define "recent" by date

Status: proposed, for the owner's decision (2026-11-22). Nothing here is built. The album measurements (Step 0)
were taken the same day with `tools/album_dates.py`; they are in "Measured on the owner's album" and replace the
assumptions made earlier in this document. Companion to
`curation-plan.md`; it would replace parts of Phase 4 item 1 (recency decay).

## What the owner asked

1. It matters more to remember a particular day than to show every picture, so prefer covering more days over
   showing all pictures.
2. Do recent pictures come up more often? That should be date based ("the last six months"), not a number of
   pictures.
3. Does this make sense, and is the plan coherent? Critique it.

## Correction: the frame's real pace (2026-11-22)

The first version of this plan assumed the app's defaults, 30 seconds a photo and about 15 awake hours, which is
1,800 photos a day. The owner's frame is set to **240 seconds a photo** and sleeps from 23:00 to 07:00 (16 awake
hours): **240 photos a day**, 7.5 times fewer. The new "Your album" card, which reads the real settings, showed
it; the settings were on the controls page all along and should have been read first. Every conversion from screen
time to days in the earlier figures was wrong by that factor, and some conclusions change:

- **A full pass is 65 days, not 8.6.** One photo from each of the 2,208 days takes 9.2 days, not 1.2. A deck that
  walks photos one by one reaches only 78% of the days after 44 days of display (simulation); choosing days first
  reaches every day in about 9. So day coverage is a large gain here, not a refinement.
- **Repetition is not the problem; time is.** A photo is on screen for 4 minutes and the average photo returns
  every 65 days. Nothing returns too soon, so the "minimum gap between showings" idea and the cap on the recent
  share (the recent pool could supply 93% of slots at a 2-day gap) both disappear.
- **The default 50% memories share is not too high.** A memory photo returns every 9.6 days and any other photo
  every 115 days. What stands out is the imbalance, 12 times the airtime per photo, which is a matter of taste.
- **The recency preference lasts longer** (a few weeks, not days) but still fades: the last six months take 13%
  of slots in the first rounds and 4% after 44 days, against 2.9% of the photos.

The tables below are recomputed at 240 photos a day. Set `FRAMEREVIVE_SECONDS` and `FRAMEREVIVE_AWAKE_HOURS` when
running `RealAlbumSimulation` and `AlbumCardPreview` so they match (see `tuning-the-strategy.md`).

## What the frame does today (checked in the code)

- **The unit is the photo.** The deck walks the album photo by photo: never-shown photos first, then the one
  shown longest ago. A day with 150 photos gets 150 chances, a day with one photo gets one. If the album has
  about 3,000 days with photos (to be measured), a 150-photo day gets about 30 times the airtime of a
  one-photo day.
- **"Recent" is a count in two places.** "Photos per round" (300) sizes the round, and the "newest photos"
  collage source is the newest 300 photos by capture time. Neither is a date.
- **Recent photos are favoured only at the start.** The weighted draw added on 2026-11-22 ("Favor newer
  photos") applies only while never-shown photos remain. Once every photo has been shown once, the deck falls
  back to least recently shown first, with no preference for recent ones. At the owner's 240 photos a day the
  first pass takes about 65 days, so the preference lasts a few weeks (the last six months take 13% of slots in
  the first rounds, 4% after 44 days) and then every photo comes up equally often.
- **The burst limit let near-identical frames through (found and fixed 2026-11-22, uncommitted).** Per round a
  burst (3 or more frames within 60 minutes) contributed at most 3, chosen by "not yet shown", then sharpness. Sharpness
  is known for about 5% of photos, so the order fell back to time order, and the perceptual hash that should catch
  near-identical frames exists for only the downloaded photos, about 5%. A first measurement of this used the album's
  whole bursts, which is not what the code sees (it sees one round's photos), so the claim that it "keeps three
  consecutive frames" overstated it. Measured properly, through the real pipeline at the owner's 240 photos a day:
  about 11% of shown photos had a twin shown the same display day, captured within 2 minutes (19% with the old hard
  window), and the cap held back 70 to 115 frames a round. Three changes: pick the frames farthest apart in time from
  those already chosen and those already shown; treat frames within two minutes of one chosen as the same picture even
  without a hash (rapid sequences give one frame); and measure gaps in absolute time, not the local clock. Result
  (default setting, about 31 days of display): twins 10.9% down to 1.1%, and 78% of days reached after 7,600 slots
  instead of 10,500. Rounds get shorter, about 380 slots instead of 520, because rapid sequences contribute one frame.
- **Memories are already day-ish.** Photos within 14 days of today's date in earlier years, as a share of the
  slots set by the slider (50% by default, but the pool is small so it fills only a few percent).

## Measured on the owner's album (2026-11-22)

From `tools/album_dates.py` and the real-album simulation (`RealAlbumSimulation`, see
`tuning-the-strategy.md`). Capture times only; nothing identifies a photo.

- **Shape:** 15,481 photos on 2,208 days (2006 to 2026). Median 3 photos a day, 90th percentile 17, 99th 53,
  most 150. 586 days (27%) have one photo. **177 days (8% of days) hold 41% of all photos.** Biggest days: 150,
  135, 107, 104 and 99 photos. Per year: roughly 500 to 1,700 photos on 100 to 150 days, fewer in 2019 to 2022.
- **Recent:** the last 6 months hold 448 photos on 95 days (last 3 months: 276 on 59; last 12: 703 on 170).
- **Memories today** (within 14 days of 22 November, earlier years): 1,155 photos on 171 days. The pool is not
  small, which the synthetic album had hidden.
- **Time:** at 240 photos a day (240 seconds each, 16 awake hours), one photo from each of the 2,208 days takes
  9.2 days of display; a full pass photo by photo takes 65 days.
- **What the deck does to days today** (simulation, about 44 days of display, "somewhat" setting): 39% of
  slots go to days with 20 or more photos (8% of days); 78% of days reached; recent six months get 4% of slots,
  against 2.9% of the photos. With the old hard window the figures are 31% of slots on big days, 58% of days
  reached, and 5% recent.
- **The 50% memories share at this pace.** Memories get half the screen time, 120 slots a day, from 1,155
  photos: each memory photo returns about every 9.6 days and each of the 171 memory days is visited about every
  1.4 days, while every other photo returns about every 115 days. It is not too often, but it is 12 times the
  airtime per photo of everything else, so how large the share should be is a taste question.

How much time a day gets, as a dial: airtime proportional to the photo count to the power a (a=1 is today, a=0
is every day equal). At 240 photos a day, days between showings of one photo by the size of its day, and days
between visits to the day itself (any photo of it), which is what "remembering a day" depends on:

| a | 1 photo | 3 photos | 17 photos | 53 photos | 150 photos | big days' share of time |
| --- | --- | --- | --- | --- | --- | --- |
| **One photo returns every (days)** | | | | | | |
| 1.00 (today) | 64.5 | 64.5 | 64.5 | 64.5 | 64.5 | 41% |
| 0.75 | 34.8 | 45.8 | 70.7 | 93.9 | 121.8 | 31% |
| 0.50 | 20.5 | 35.5 | 84.5 | 149.2 | 251.0 | 21% |
| 0.25 | 13.2 | 30.0 | 110.3 | 258.7 | 564.5 | 13% |
| 0.00 (equal) | 9.2 | 27.6 | 156.4 | 487.6 | 1,380 | 8% |
| **The day is visited every (days)** | | | | | | |
| 1.00 (today) | 64.5 | 21.5 | 3.8 | 1.2 | 0.4 | |
| 0.75 | 34.8 | 15.3 | 4.2 | 1.8 | 0.8 | |
| 0.50 | 20.5 | 11.8 | 5.0 | 2.8 | 1.7 | |
| 0.25 | 13.2 | 10.0 | 6.5 | 4.9 | 3.8 | |
| 0.00 (equal) | 9.2 | 9.2 | 9.2 | 9.2 | 9.2 | |

Total screen time is fixed, so favouring days means a quiet day is visited sooner and a big day's photos return
later. Today a 150-photo day is visited about twice a day while a one-photo day is visited every two months. At a=0
every day is visited every 9 days, but a 150-photo day's pictures return only every 3.8 years. Around a=0.25 every
day is visited every 4 to 13 days (a quiet day every 13, a typical one every 10, a big one every 4), and a big
day's individual photos return about every year and a half. Repetition is not a concern at this pace in either
direction.

Shares of screen time and how often one photo of each pool returns (days), with the real pool sizes at 240 a day:

| Recent / memories / rest | Recent photo | Memory photo | Other photo |
| --- | --- | --- | --- |
| 10% / 15% / 75% | 18.7 | 32.1 | 77.1 |
| 20% / 15% / 65% | 9.3 | 32.1 | 89.0 |
| 30% / 15% / 55% | 6.2 | 32.1 | 105.1 |
| 25% / 20% / 55% | 7.5 | 24.1 | 105.1 |
| 0% / 50% / 50% (today) | none | 9.6 | 115.7 |

## Days, timezones and moments (measured 2026-11-22)

The owner travels, so "a day" depends on the timezone, and a day can hold several moments. Measured on the real
album with `tools/album_dates.py` (which now reports these statistics too).

- **Timezones are recorded.** 98.4% of photos carry their own UTC offset; there are 11 distinct offsets. UTC-7
  29%, UTC+2 25%, UTC-8 24%, UTC-4 9%, UTC+1 4%, UTC-10 4%. About 4,550 photos (29%) were taken at +1 or +2,
  so Europe is a large part of the album.
- **Which day: keep the photo's own local day** (capture time plus its own offset), which `FramePhoto.date()` and
  the seasonal pool already use. The alternatives lose on this album: counting days in UTC would put 27% of
  photos (4,243) on a different day; counting everything in one home zone (UTC-8) would move 5% of photos (795),
  12% of the European ones, so a Paris morning would land on the previous day.
- **Travel days are rare.** 107 days (5%) hold photos from two or more timezones (1,390 photos). They stay one day
  each; they usually hold several moments in different places.
- **Some offsets are missing.** 241 photos (1.6%) have offset 0, which is either real (UK and Ireland in winter)
  or a missing timezone read as UTC. 120 of them lie within 6 hours of a photo with a known offset, and for 89
  of those the neighbour is a US zone, so they are probably missing and may sit on the wrong day (an evening
  photo read as UTC lands on the next day). The effect is small (fewer than 60 days) and ambiguous for the rest,
  so it is not worth a repair rule yet.
- **Moments.** A moment is a run of photos with no gap over 2 hours between neighbours, measured in absolute time,
  so a timezone change cannot split or join one. There are 3,092 moments over 2,208 days: 1,523 days (69%) hold
  one moment, 492 hold two, 186 hold three or more (with a 1-hour gap, 60% of days hold one). The big days are
  mostly one or two moments: the five biggest (150, 135, 107, 104 and 99 photos) hold 1, 2, 2, 1 and 2. The
  150-photo day is one continuous event, so splitting days into moments would not change how big days are
  treated.
- **Midnight is fine as the day boundary.** Only 18 of 3,092 moments cross local midnight. Starting the day at
  03:00, 04:00 or 05:00 splits 22, 20 and 11, so a late-night rollover would change little.

What this means for the plan:
- The unit of coverage stays the local calendar day, and moments play no part in coverage. (They do have a job
  in collages; see part D.) To vary the pictures within a day, the next photo is the unseen one farthest in time from those already shown that day. That covers a
  day with several moments and a long single event alike, and needs no gap constant, so coverage needs no moment
  gap. The 2-hour gap matters only for collages.
- Separate from this plan, `Bursts.events` (the existing burst thinning) sorts and measures gaps in local clock
  time (capture time plus offset). Between two photos taken minutes apart as the phone changes timezone, the
  local gap jumps by hours, so a burst can be split or joined wrongly. It should use absolute capture time; a test
  with such a pair pins it. It is a small independent fix.

## The proposal in three parts

### A. Days are the unit

Group photos by local calendar day (capture time plus the stored offset). A day's state comes from the existing
play history, so no new storage:

- `depth`: how many of the day's photos have been shown.
- `last`: when one of its photos was last shown.

The next day to show is the one with the lowest `depth`, ties broken by the oldest `last`, then at random. So
every day with photos is shown once before any day is shown twice, then a second photo from each, and so on.

Which photo of the day:
- an unseen one first, and among those the one farthest in time from the photos already shown from that day, so
  a second pick comes from another part of the day (and, on a long single event, from another stretch of it);
- never one the sharpness gate refuses, and never a near-duplicate of one shown (the existing event pass keeps
  that job inside a day);
- the quality score is known for only a small share of photos (709 of 15,481 on 2026-11-22), so the day's
  "best" photo cannot be chosen by sharpness; a soft pick is passed over after it downloads and another photo
  of the same day is used.

How much airtime a day gets (a dial, see decisions):
- **Equal:** every day has the same share, whatever its size.
- **Some extra for big days (suggested):** share proportional to the square root of the photo count, so a
  150-photo day gets about 12 times a one-photo day (instead of 150 times), and a 9-photo day gets 3 times.
  Big days are usually events worth remembering, but a day is "remembered" by a few pictures, not all.

Collages and pairs fit unchanged: a same-day collage already draws from one day; its photos count towards that
day's depth. Undated photos go into one pseudo-day so they cannot flood the frame.

### B. "Recent" by date, and three pools

Replace the count by a period. New setting **Recent period (months)**, default 6. The cycle then draws from
three pools, each run with the day deck from part A:

| Pool | What it holds | Default share |
| --- | --- | --- |
| Recent | captured in the last 6 months | to be agreed (see decisions) |
| Memories | within 14 days of today's date, earlier years | as the slider today |
| Everything else | the rest of the album | the remainder |

A share is a **maximum**, not a promise. When a pool cannot fill its share without repeating photos too soon,
the shortfall goes to the next pool (the same rule the memories use now). That is the honest answer to "show
recent pictures more often": as often as the recent pool can supply without nagging.

Consequences:
- "Photos per round" stops being a user setting; the round becomes an internal planning batch (still 300). Done
  2026-11-22: removed from the controls page, the save handler and the diagnostics. The plan is sized from the pace
  instead (`PlaybackEngine.plannedSlots`): an awake day's photos times 1.6, between 300 and 2,000, rebuilt at the
  start of each day, when it runs out and when the pace changes. At 240 photos a day that is 384 (192 recent, 192
  memories at a 50% mix).
- "Favor newer photos" (the weighted draw) is retired; the recent period replaces it. A hard edge at six months
  is acceptable because it is a share of slots, not a per-photo chance: a photo crossing the edge moves from
  one pool to the other once, and nothing jumps on screen.
- The "newest photos" collage source becomes "from the recent period", so it follows the same date.
- The mix control becomes three numbers ("out of every 10 pictures: 3 from the last 6 months, 1 from this time
  of year, 6 from everything else") instead of one slider.

### C. Say what it means in time

Status (2026-11-22, uncommitted, not deployed): the album card is built as pure Kotlin (`AlbumStats`, `AlbumCard`)
and previewed against the real album on the dev machine (`AlbumCardPreview`, see `tuning-the-strategy.md`). It
reports the album's shape, the recent windows, the memory pool, what the frame has shown (photos and days reached,
days not shown in the last 30 days) and, for the current rules, how often a photo returns: the memory share from
the real quota formula (`PhotoSelection.memorySlots`), the album size and the photos shown a day (seconds per
photo and the awake hours from the sleep schedule). The readout for the planned pools and day exponent is added
when those settings exist.

Percentages hide the consequence. On the controls page, next to the settings, show what they produce, from the
album's own numbers: "a photo from the last 6 months returns about every 9 days, an older one about every 90;
every day in the album is shown at least once every 2 days." These are computed from the pool sizes, the
shares, the seconds per photo and the awake hours. A minimum gap between showings of one photo (suggested 2
days while enough other photos exist) keeps a small recent pool from repeating all day.

### D. Collages from moments

Optional. It makes collages more cohesive (one outing instead of a random assortment from the day) but also narrower:
a same-day collage can show different parts of a day, which arguably represents "a day" better. Collages are about
11 a day at the owner's pace, around 14% of the photos shown. A cheaper variant needs no grouping: choose a seed
photo and fill the collage from photos within an hour or two of it. Do this only if the owner wants cohesive
collages. The measurements follow.

A same-day collage can show the morning at home beside the evening restaurant. Measured on the real album (the
1,033 days with 4 or more photos, the only ones that can make a 4-photo collage): four random photos from a day
mix two or more moments on 41% of days (2-hour gap) to 58% (1-hour gap), with a median time span of about 135
minutes; four photos from inside one moment span a median 40 minutes (19 at a 1-hour gap). And a moment of 4 or
more photos exists on 89% of those days (84% at 1 hour), so building the collage from a moment is almost always
possible.

- Group collage candidates by **moment** (a run of photos with no gap over 2 hours, in absolute time) instead of
  by calendar day, with the day as the fallback when no moment has enough photos for the wanted count (2 to 4).
- Bursts and moments are the same idea, runs of photos close together in time, kept today at two gaps (60
  minutes and, here, 2 hours) in code that grew separately. They become one concept: a moment, computed once
  when the album is read, on the worker, in absolute time (this needs the fix noted above). The burst limit,
  the collage grouping and the choice within a day all use it, with one gap, 2 hours.
- Ranking stays as today (fully downloaded first, then not recently shown, then most photos, then most recent),
  now over moments. Inside a moment, prefer photos far apart in time and keep the existing near-duplicate check,
  so a collage is cohesive without showing four frames of the same instant.
- The "avoid the previous collage's date" rule becomes "avoid the previous collage's moment".
- Choosing the moment early for downloading (`preloadPlan`) works unchanged.
- This is independent of parts A to C: it changes only which photos a collage draws from, so it can be built
  first. It needs no new setting.

## Critique

- **Day coverage is a large gain at this pace.** One photo per day for all 2,208 days takes 9.2 days of display,
  while the photo-by-photo deck needs 65 days to see everything and reaches 78% of the days after 44. After the
  first pass, what a day gets is the airtime dial in part A, which is the real decision.
- **Equal airtime per day is too extreme, but the middle is low.** At a=0 a one-photo day returns every 9 days and
  a 150-photo day's pictures every 3.8 years. Because repetition is no concern at 240 a day, the dial can sit much
  nearer equal than first thought: a around 0.25 visits every day every 4 to 13 days. Suggested default 0.25; a
  is a single number, so it can be adjusted.
- **The recent share is a question of taste, not supply.** The last 6 months hold 448 photos on 95 days. At 240 a
  day even a 2-day minimum gap would let them supply 93% of the slots, so nothing limits the share. At 20% (48
  slots a day) a recent photo returns every 9 days against 89 for the rest, and each recent day is visited every
  2 days; at 40% each recent day would be on screen almost daily.
- **A day is a coarse unit, but a measured one.** 31% of days hold two or more moments, so a second photo should
  come from another part of the day; "farthest in time from what was already shown that day" does that without
  defining moments. Timezones are handled by using each photo's own offset; a day that spans a flight stays one
  day. Neither is a reason to change the unit; moments do pay off for collages (part D).
- **"Never shown" stops being the right statistic.** Some photos from big days will wait weeks. The controls
  page should report days shown and photos shown separately.
- **It replaces work from earlier today.** The weighted recency draw and its setting would be removed, and
  "Photos per round" (renamed hours ago) goes hidden. Churn, but both settings would otherwise do something
  different from what their labels suggest.
- **The album is now measured, and it disagreed with several assumptions.** It has 2,208 days, not 3,000;
  448 recent photos, not 600; and a memory pool of 1,155 photos, not 40. The last one matters most: it made the
  50% memories default look harmless in the simulation when it actually returns each memory photo every 1.3
  days.
- **Cost.** Grouping 15,000 photos by day happens once when the album is read, on the worker thread like the
  event grouping. Picking a round must stay cheap; the deck build took 50 to 130 ms on the frame before
  2026-11-22. Step 0b moves the build off the UI thread so that a heavier day deck does not cause a visible
  hitch.

## Steps (each measurable, none visible until its own step)

**Step 0: measure.** Diagnostics: days with photos; photos per day (median, 90th percentile, biggest day);
photos in the last 6 months; days shown and photos shown so far. Make the simulation album realistic: photos per
day with a long tail, a recent stretch. No behaviour change.

**Step 0b: build the next round in the background.** Today `rebuild()` runs on the UI thread. It is called when
the app starts, when a round ends, when the date changes at midnight, when settings are saved, when an album
sync finishes, when the seasonal refresh lands, and when the queue runs dry. On the frame a round took 49 to
133 ms before 2026-11-22 and 262 to 619 ms right after the weighted draw and the per-photo sharpness gate
(the fix for the gate was deployed the same day; the first build after that install read 262 ms, and the
warm number is still to be read). The frame is 40 to 100 times slower than a desktop, and the stall watchdog
only reports stalls over 15 seconds, so a hitch of a few hundred milliseconds is invisible to it.

Design: a round is already a pure function of its inputs (`PlaybackEngine.buildCycle`). Run it on the existing
worker with a snapshot of the inputs (album, seasonal set, play history, scores, hashes, a copy of the priority
ids, the settings) and post the result back to the UI thread. Build ahead: start the next round when the current
one has about 10% left, hold it, and swap it in when the queue empties, so nothing waits. After a settings
change keep playing the old queue until the new round arrives, then swap. Discard a result if the album or
settings changed while it was being built. At swap, drop photos that were shown during the build so none
repeats, and carry the cycle's shown set across. The first build at startup and after an album change stays
synchronous, since the frame needs a queue at once.

Decide by the warm "deck build" reading: at or under 130 ms, do this together with Step 1, since the day deck
makes a round heavier; above 150 ms, do it first on its own. Either way, group photos by day once when the
album is read, on the worker like the burst grouping, so each round stays light.

Risks and tests: the real risks are races with the album sync and with settings saves, and a few photos
repeating if the history changes during a build. The swap rule, the stale-result discard and the carried-over
shown set are pure and testable without Android; the simulation is unchanged. The first reading after a fresh
install is always the slowest (code runs uncompiled), so judge on a later build.

**Step 0c: one moment concept, picked by time (independent, can go first).** Replace the separate burst and
collage groupings by one `Moments` grouping (absolute time, one gap of 2 hours, computed once on the worker), and
one selection rule: from a moment, take the photo farthest in time from those already chosen and those already
shown from that moment, ties by the existing "not yet shown, then sharpest" order.

Done (2026-11-22, uncommitted, not deployed): the burst limit uses that rule, treats frames within two minutes as the
same picture, and measures gaps in absolute time (with tests, including two photos taken as the phone changes
timezone). Results on the real album are in "What the frame does today". The per-round cap of 3 per burst stays, and
the hash check stays as a second guard. The burst gap is still 60 minutes.

Optional, awaiting the owner's preference (not recommended by default, see part D): collages drawn from a moment
instead of a calendar day, with the day as fallback, using
`Bursts.events` with a 2-hour gap; and a cohesion measure in `RealAlbumSimulation` (median time span of a collage's
photos, how often it mixes moments; expected from the measurements above: about 135 minutes down to about 40). Open
side effect to decide: the rounds are now about 380 slots, so the "Photos per round" label no longer says exactly how
many photos a round holds. Either draw more candidates to fill the round after thinning, or reword the label.

**Step 1: day deck.** Group by day, order by depth then last shown, choose the photo within a day, with the
airtime dial. Simulation compares today's deck and the day deck: share of slots by day size, days covered after
1, 2 and 7 days of display, repeats within a pass, coverage of photos.

**Step 2: date-based pools.** Recent period, three pools, shares as maxima, shortfall rules, minimum gap. The
"newest photos" collage source follows the period. Simulation reports slots per photo per day by age band
(under 6 months, 6 to 24 months, older) and the time between showings.

**Step 3: say it in time.** The readout on the controls page, and the retired settings removed. Deploy and read
the real numbers.

**Step 4, optional: memories by closeness.** Rank memories by distance from today's date (this exact day first)
instead of treating the whole 14-day window alike.

## Decisions for the owner

1. **Airtime for a day:** the exponent a from the table. Suggested 0.25: every day is visited every 4 to 13 days
   (a quiet day every 13, a typical one every 10, a big one every 4). 0.5 favours big days more; 0 is strictly equal.
2. **Recent period:** six months as you said. Fine as a default?
3. **Minimum gap between showings of one photo:** at your pace nothing returns within weeks, so such a limit would
   never act. Suggested: do not build it. Agree?
4. **Mix:** three numbers (recent, this time of year, everything else) replacing the single slider?
5. **Day boundary:** the photo's local calendar day. Fine?
6. **Retire "Favor newer photos"?** Suggested yes, when the recent period replaces it. ("Photos per round" is already gone.)
7. **Memories share:** at 50% each memory photo returns every 9.6 days and each of the 171 memory days is visited
   every 1.4 days, 12 times the airtime per photo of everything else. At 25% a memory photo returns every 19
   days and a memory day every 2.9; at 15%, every 32 and 4.8. Do you want memories to be a large part of what you
   see, or an occasional treat?
8. **Collage moments:** is 2 hours the right gap for a collage, or would you prefer tighter (1 hour: median span
   19 minutes, a qualifying moment on 84% of days instead of 89%)?
9. **Step 0 is done** (album measured). Next: Step 0b (background round build) or Step 1 (the day deck)?
