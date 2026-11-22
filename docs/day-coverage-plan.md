# Plan: cover days, and define "recent" by date

Status: proposed, for the owner's decision (2026-11-22). Nothing here is built. Companion to
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
- **Memories are already day-ish.** Photos within 14 days of today's date in earlier years, as a share of the
  slots set by the slider (50% by default, but the pool is small so it fills only a few percent).

## The proposal in three parts

### A. Days are the unit

Group photos by local calendar day (capture time plus the stored offset). A day's state comes from the existing
play history, so no new storage:

- `depth`: how many of the day's photos have been shown.
- `last`: when one of its photos was last shown.

The next day to show is the one with the lowest `depth`, ties broken by the oldest `last`, then at random. So
every day with photos is shown once before any day is shown twice, then a second photo from each, and so on.

Which photo of the day:
- an unseen one first, not adjacent in time to one already shown that day (so a second pick is from another
  moment);
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

## Critique

- **Day coverage matters most at the start.** At about 1,800 slots a day, one photo per day for 3,000 days takes
  under two days of display. After that the long-run effect is mainly the airtime a day gets, which is why the
  dial in part A is the real decision, not the coverage order.
- **Equal airtime per day under-represents big events.** A wedding and a trip to the shops would show the same.
  Hence the square-root suggestion. If you want pure coverage, it is one setting.
- **The recent share is limited by supply, and that is the main trade.** Example: a recent pool of about 600
  photos (assumed; to be measured) at a 2-day minimum gap can supply about 300 slots a day, 17% of 1,800. Asking
  for 30% would repeat photos daily. So the achievable share depends on how many photos you take; part C
  exists so that is visible, not a surprise.
- **A day is a coarse unit.** A morning at home and an afternoon at the zoo are two moments. A second photo from
  a day prefers a different moment. A late-night party split across midnight counts as two days; the offset
  uses the photo's own timezone as the album stores it.
- **"Never shown" stops being the right statistic.** Some photos from big days will wait weeks. The controls
  page should report days shown and photos shown separately.
- **It replaces work from earlier today.** The weighted recency draw and its setting would be removed, and
  "Photos per round" (renamed hours ago) goes hidden. Churn, but both settings would otherwise do something
  different from what their labels suggest.
- **We do not yet know the album.** Days with photos, photos per day, and how many photos are from the last six
  months are all unmeasured. The numbers above are assumptions. Step 0 measures them before anything changes.
- **Cost.** Grouping 15,000 photos by day happens once when the album is read, on the worker thread like the
  event grouping. Picking a round must stay cheap on the UI thread; the current deck build takes 50 to 130 ms
  on the frame, and this must not exceed that by much.

## Steps (each measurable, none visible until its own step)

**Step 0: measure.** Diagnostics: days with photos; photos per day (median, 90th percentile, biggest day);
photos in the last 6 months; days shown and photos shown so far. Make the simulation album realistic: photos per
day with a long tail, a recent stretch. No behaviour change.

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

1. **Airtime for a day:** equal, or square root of its photos (suggested)?
2. **Recent period:** six months as you said. Fine as a default?
3. **How often may a photo return?** The minimum gap decides how large the recent share can be. 2 days is
   suggested; what feels like too often?
4. **Mix:** three numbers (recent, this time of year, everything else) replacing the single slider?
5. **Day boundary:** the photo's local calendar day. Fine?
6. **Retire "Favor newer photos" and hide "Photos per round"?** Suggested yes.
7. **Start with Step 0?** It changes nothing and gives the real numbers to settle the rest.
