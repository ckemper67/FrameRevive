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

## What the frame does today (checked in the code)

- **The unit is the photo.** The deck walks the album photo by photo: never-shown photos first, then the one
  shown longest ago. A day with 150 photos gets 150 chances, a day with one photo gets one. If the album has
  about 3,000 days with photos (to be measured), a 150-photo day gets about 30 times the airtime of a
  one-photo day.
- **"Recent" is a count in two places.** "Photos per round" (300) sizes the round, and the "newest photos"
  collage source is the newest 300 photos by capture time. Neither is a date.
- **Recent photos are favoured only at the start.** The weighted draw added on 2026-11-22 ("Favor newer
  photos") applies only while never-shown photos remain. Once every photo has been shown once, the deck falls
  back to least recently shown first, with no preference for recent ones. At 30 seconds a photo and about 15
  awake hours (the default schedule) the frame shows about 1,800 photos a day, so a 15,500-photo album is
  fully seen in about nine days. After that, a photo from last week and one from 2012 come up equally often.
  That setting does less than it appeared to, and the plan below replaces it.
- **The burst limit keeps the most similar frames.** Per round, a burst (3 or more frames within 60 minutes)
  contributes at most 3. `Bursts.thin` ranks a burst's frames by "not yet shown", then sharpness, and keeps the
  top 3. Sharpness is known for only about 5% of photos, so for the rest every frame ties; the sort is stable
  and the frames are in time order, so it keeps the first three in time: consecutive shots. Measured on the
  1,147 bursts of 4 or more frames in the real album: those three are a median 8 seconds apart (within 10
  seconds in 57% of bursts) and cover a median 1.9 minutes of a 26-minute burst. Three frames chosen farthest
  apart in time are a median 185 seconds apart (within 10 seconds in 12%) and cover 26 minutes. Next round the
  rule prefers frames not shown yet, so a burst comes out in consecutive chunks of three. The perceptual hash
  that should catch near-identical frames exists for only the photos that were downloaded, about 5%.
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
- **Time:** at about 1,800 slots a day, one photo from each of the 2,208 days takes 1.2 days of display; a
  full pass photo by photo takes 8.6 days.
- **What the deck does to days today** (simulation, about 5.8 days of display, "somewhat" setting): 39% of slots
  go to days with 20 or more photos (8% of days); 78% of days reached; recent six months get 4% of slots, against
  2.9% of the photos, so the recent preference has faded to nothing. With the old hard window the figures are
  31% of slots on big days, 58% of days reached, and 5% recent.
- **The default 50% memories share is far too high for this album.** Memories get half the screen time, about
  900 slots a day, from 1,155 photos, so each memory photo returns about every 1.3 days while every other photo
  returns about every 16 days.

How much time a day gets, as a dial: airtime proportional to the photo count to the power a (a=1 is today, a=0
is every day equal). Days between showings of one photo, by the size of its day, at 1,800 slots a day:

| a | 1 photo | 3 photos | 17 photos | 53 photos | 150 photos | big days' share of time |
| --- | --- | --- | --- | --- | --- | --- |
| 1.00 (today) | 8.6 | 8.6 | 8.6 | 8.6 | 8.6 | 41% |
| 0.75 | 4.6 | 6.1 | 9.4 | 12.5 | 16.2 | 31% |
| 0.60 | 3.3 | 5.2 | 10.4 | 16.4 | 24.8 | 25% |
| 0.50 | 2.7 | 4.7 | 11.3 | 19.9 | 33.5 | 21% |
| 0.25 | 1.8 | 4.0 | 14.7 | 34.5 | 75.3 | 13% |
| 0.00 (equal) | 1.2 | 3.7 | 20.9 | 65.0 | 184.0 | 8% |

Total screen time is fixed, so favouring days means a quiet day's photo returns sooner and a big day's photo later.
At a=0 a one-photo day's picture would come back every 1.2 days and a 150-photo day's pictures once every six
months; both are extremes. Around a=0.5 to 0.6 a quiet day's photo returns every 3 days and a big day's every
3 to 5 weeks.

Shares of screen time and how often one photo of each pool returns (days), with the real pool sizes:

| Recent / memories / rest | Recent photo | Memory photo | Other photo |
| --- | --- | --- | --- |
| 10% / 15% / 75% | 2.5 | 4.3 | 10.3 |
| 10% / 10% / 80% | 2.5 | 6.4 | 9.6 |
| 15% / 15% / 70% | 1.7 | 4.3 | 11.0 |
| 0% / 50% / 50% (today) | none | 1.3 | 15.4 |

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
- "Photos per round" stops being a user setting; the round becomes an internal planning batch (still 300).
- "Favor newer photos" (the weighted draw) is retired; the recent period replaces it. A hard edge at six months
  is acceptable because it is a share of slots, not a per-photo chance: a photo crossing the edge moves from
  one pool to the other once, and nothing jumps on screen.
- The "newest photos" collage source becomes "from the recent period", so it follows the same date.
- The mix control becomes three numbers ("out of every 10 pictures: 3 from the last 6 months, 1 from this time
  of year, 6 from everything else") instead of one slider.

### C. Say what it means in time

Percentages hide the consequence. On the controls page, next to the settings, show what they produce, from the
album's own numbers: "a photo from the last 6 months returns about every 4 days, an older one about every 11;
every day in the album is shown at least once every 2 days." These are computed from the pool sizes, the
shares, the seconds per photo and the awake hours. A minimum gap between showings of one photo (suggested 2
days while enough other photos exist) keeps a small recent pool from repeating all day.

### D. Collages from moments

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

- **Day coverage matters most at the start.** At about 1,800 slots a day, one photo per day for all 2,208 days
  takes 1.2 days of display. After that the long-run effect is the airtime a day gets, which is why the dial in
  part A is the real decision, not the coverage order.
- **Equal airtime per day is too extreme on this album.** It would return a one-photo day's picture every 1.2
  days and a 150-photo day's pictures about twice a year (table above). The middle of the dial, a between 0.5 and
  0.6, keeps quiet days in view (every 3 days) without burying big events (every 3 to 5 weeks). Suggested
  default 0.6; a is a single number, so it can be adjusted.
- **The recent share is limited by supply, and that is the main trade.** The last 6 months hold 448 photos. At
  a 2-day minimum gap they can supply 224 slots a day, 12% of 1,800; at 3 days, 8%. So 10% is about the most
  that does not repeat photos every day or two, and it already brings a recent photo back every 2.5 days
  against 10 for the rest, four times as often.
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
shown from that moment (shown ones come from the play history), ties by the existing "not yet shown, then
sharpest" order. It replaces the time-blind ranking in the burst limit (which keeps the first three frames),
drives collage picks (part D) and later the choice within a day (part A). The per-round cap of 3 per moment
stays until the day deck makes it implicit; the perceptual-hash check stays as a second guard where a hash
exists, but nothing relies on it. Includes the absolute-time fix to the grouping (with a test for two photos
taken as the phone changes timezone). Measure on the real album, in `RealAlbumSimulation`: the median gap
between the frames of a burst that a round keeps (today 8 seconds), the share of rounds' photos that have another
photo within 2 minutes in the same display day, and the cohesion of collages (median time span, how often they
mix moments). Expected from the measurements above: the burst gap rises from seconds to minutes, collage span
falls from about 135 to about 40 minutes.

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

1. **Airtime for a day:** the exponent a from the table. Suggested 0.6 (a quiet day's photo returns every 3
   days, a big day's every 3 to 4 weeks); 0.75 is gentler, 0.5 stronger.
2. **Recent period:** six months as you said. Fine as a default?
3. **How often may a photo return?** The minimum gap decides how large the recent share can be. 2 days is
   suggested; on this album that caps recent at about 12% of the screen time. What feels like too often?
4. **Mix:** three numbers (recent, this time of year, everything else) replacing the single slider?
5. **Day boundary:** the photo's local calendar day. Fine?
6. **Retire "Favor newer photos" and hide "Photos per round"?** Suggested yes.
7. **Memories share:** the current 50% brings each memory photo back every 1.3 days. Suggested 10 to 15%. Do
   you want memories to be a large part of what you see, or an occasional treat?
8. **Collage moments:** is 2 hours the right gap for a collage, or would you prefer tighter (1 hour: median span
   19 minutes, a qualifying moment on 84% of days instead of 89%)?
9. **Step 0 is done** (album measured). Next: Step 0b (background round build) or Step 1 (the day deck)?
