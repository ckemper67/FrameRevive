# Plan: collage themes

Status: **A day** and **One outing** are built (2026-11-22, uncommitted until the owner says otherwise); the other themes
are proposed. Collage themes replace the "Collage source" setting with one switch per theme; Mixed is removed.

Result on the owner's real album (300 sequential collages per theme, all photos downloaded, a collage aims for four
photos and settles for fewer, the frame's letterbox fallback when nothing tiles within the crop limit):

| Source | Tiled within the crop limit | Photos 4 / 3 / 2 | Two photos within 2 minutes | Median span |
| --- | --- | --- | --- | --- |
| Old same-day pool, 6 candidates | 100% | 55% / 45% / 0% | 91% | 7 min |
| A day, 6 candidates | 98% | 46% / 50% / 3% | 0% | 315 min |
| A day, 8 candidates | 98% | 68% / 28% / 3% | 0% | 296 min |
| A day, 10 candidates (used) | 98% | 74% / 22% / 3% | 0% | 296 min |
| A day, 12 candidates | 98% | 77% / 19% / 3% | 0% | 292 min |
| One outing, 6 candidates | 97% | 27% / 65% / 7% | 0% | 41 min |
| One outing, 10 candidates (used) | 97% | 35% / 57% / 7% | 0% | 40 min |

Two changes together removed the tiling setback that fixed 2-3-4 photo counts and a pool of 6 had caused: the count is
no longer chosen ahead of time (every collage aims for four and settles for fewer), and the shape fitting looks at 10
candidates. Fitting is cheap, about 0.2 ms on the desktop, because photos are grouped by aspect ratio and each distinct
set of shapes is fitted once, however many combinations of photos it covers. A pool of 12 adds little over 10 (77% against
74% four-photo day collages), so 10 is used; all ten are queued for download two slides before the collage falls due. The
old pool put two photos within two minutes of each other in 91% of collages (a day's photos in album order); the themes
do so in none. An outing has fewer four-photo collages (35%) than a day because few outings have three more photos
within an hour, hence more three-photo ones.

## Where collages stand

A collage replaces a slide after every 20 singles (about 11 a day at 240 photos a day, around 14% of the photos
shown) and has 2, 3 or 4 photos. Its photos come from a "collage source": **Same day** (one calendar date, one year)
or **Mixed** (randomly the newest 300 photos or a same-day set). Shape fitting then picks, from the first 6 usable
candidates, the combination that crops least. See "How collages are built" below.

The owner's view: **Mixed is not useful**, because combining landscape photos for a portrait frame already mixes
unrelated photos for free. **Same day** is nice for seeing anniversaries over time (note: today's same day is one
date in one year, so it never shows other years; that idea is a new theme below). **Moments** would be another.
The owner likes a **seed photo** approach for moments.

## What the data can support

Only capture time and timezone are known (no location, faces or captions), so themes can be built from time.
Measured on the owner's real album (15,481 photos, 2,208 days):

| Idea | What it needs | Availability on the real album |
| --- | --- | --- |
| One outing (seed photo plus a time window) | capture times | with a window of +/-1 hour, 73% of photos can seed a 4-photo collage and 81% a 3-photo one; the 4 photos span a median 39 minutes and the closest pair is a median 140 seconds apart (+/-30 minutes: 67%, 17 min, 64 s; +/-2 hours: 78%, 79 min, 322 s) |
| A day (today's same day) | capture dates | 1,033 days have 4 or more photos |
| This day through the years | month and day of each photo | every calendar date has photos from 2 or more years (361 of 365 exactly; 337 have 3 or more, 300 have 4 or more); within +/-3 days every date has 3 or more years. Today (22 November) has photos from 9 different years |
| A trip | timezone offset away from home, or runs of consecutive days | with home as the US Pacific zone: 217 trips (runs of days away, gaps up to 2 days), 62 of 4 or more days, 36 of 7 or more; trips of 4+ days hold 34% of the photos and 26% of the days |
| Lately (the last two weeks) | capture dates | last month 59 photos on 13 days; enough for 2 to 4 photos |
| Special dates (birthdays) | a list of dates from the owner | needs a setting |
| Same place / same person | GPS, faces | not possible with the data we have |

(A first count of trips used the two most common offsets as "home" and wrongly counted Europe, +2 hours, as home
because it is 25% of the album. Home needs a better rule: group offsets within an hour of each other, so winter
and summer time merge, and call the biggest group home; or let the owner set it.)

## The themes

| Theme | What you see | How it is built | Sizes | Verdict |
| --- | --- | --- | --- | --- |
| **One outing** | photos from one stretch of a day, like one walk or one party | pick a seed photo (ranked as day collages are: not shown lately, downloaded first); take photos within +/-1 hour of it; choose the ones farthest apart in time (at least 2 minutes apart) that fit the layout | 2 to 4 | yes; cohesive; uses the time-spread rule already built |
| **A day** | different parts of one date | as today, but with a larger candidate pool and the same time-spread choice | 2 to 4 | keep; "remember a day" |
| **This day through the years** | the same calendar date in different years; each picture's own date already shows the year | take today's month and day (widen by a day at a time until 2 to 4 distinct years are found); one photo per year, spread across the years, not shown lately | 2 to 4 | yes; likely the strongest; available every day of the year; with 2 photos it is "then and now" |
| **A trip** | the story of a trip: one photo from each of several days of it | detect trips (days away from home, or runs of consecutive days); pick a trip not shown lately; one photo per day spread through it | 3 to 4 | later; each collage visits up to 4 days, which helps day coverage; needs the home rule |
| **Lately** | what is new | photos from the last 14 days, spread over those days; replaces "newest 300 photos" with a date | 2 to 4 | yes; small; matches "recent by date" |
| **Special dates** | a birthday or anniversary through the years | the owner lists dates (with an optional label); on that date collages use "this day through the years" for it | 2 to 4 | later; high personal value; needs a setting |
| ~~Mixed~~ | random newest photos | | | remove; the layout already combines unrelated photos |

## How it would fit

- A theme is a small class that returns candidate photos (8 to 12, already spread in time or by year) for a wanted
  count; the existing shape fitting then picks the combination that crops least. A theme can say it is not
  available (no trip, no photos for today's date), and the next one is tried.
- The collage presentation rotates through the enabled themes, so consecutive collages differ. The choice is made
  early, as the date is today, so the photos download before the collage is due.
- The "Collage source" setting becomes "Collage themes", a set of checkboxes with plain descriptions. An existing
  "same day" setting becomes {A day}; "mixed" becomes {A day, Lately}.
- Collage photos count as shown (play history and the round), and a collage's photos count towards their days'
  visits, so trip and anniversary collages help day coverage: each shows up to 4 different days.
- Optionally a small label on the slide ("This day through the years").

## Floating holidays for "this day through the years"

Assessment (2026-11-22). The matching key of the theme is a calendar date (month and day) or a holiday. Every day
uses the date; when today is also a floating holiday, the holiday key is added and preferred, because it is rarer
and more meaningful. For each earlier year the holiday's date is computed and the photos on it (or within a day or
two) are gathered.

- **Easy, plain date arithmetic:** holidays that are the nth weekday of a month (Thanksgiving, Memorial Day, Labor
  Day, Mother's Day, Father's Day, MLK Day, Presidents' Day) and the Easter family (Easter Sunday, Good Friday,
  Palm Sunday, Mardi Gras at Easter minus 47 days, Ash Wednesday, Easter Monday). `HolidayTheme.kt` already computes
  Easter for the decorations, so the function can be shared. Roughly 50 lines and tests, standard `java.time`.
- **Hard, skip for now:** lunar and lunisolar holidays (Chinese New Year, Passover, Hanukkah, Eid, Diwali). Android's
  ICU can compute them but not in plain unit tests, so they would need a hand-checked table. Which holidays count is
  also regional; default to US ones, with the special dates list later for anything else.
- **The owner's album supports the main ones.** Years (of 19) with photos on the day, within one day, and photos on
  the day: Thanksgiving 14, 16, 154; Easter Sunday 15, 15, 152; Father's Day 8, 13, 68; Labor Day 10, 15, 39;
  Memorial Day 5, 12, 63; Mother's Day 4, 11, 32; Good Friday 6, 11, 34; Mardi Gras 5, 8, 18. Mardi Gras is thin;
  Thanksgiving and Easter are strong. A per-holiday window (a day either side, or the weekend for Easter and
  Thanksgiving) keeps the thin ones usable.
- A slide label such as "Thanksgiving through the years" suits this, since the pictures' own dates differ every year.

## Bursts in a window instead of holiday rules (owner's idea, 2026-11-22)

Instead of computing each holiday's date, take a window of days around today in every earlier year and find the
busiest day in it: photo bursts mark the occasions (Christmas morning, a birthday), so they are found without any
table, work for lunar and regional holidays and for personal occasions, and a window that reaches ahead shows an
occasion's photos in the days leading up to it. Measured on the owner's album (years 2008 to 2025):

- **Christmas is found well.** With a window from 3 days back to 10 ahead of today, the busiest day is Dec 24 to 26
  in 16 of 18 years when today is Dec 16 or 18, 15 on Dec 20, 12 from Dec 22 to 26, then it stops: 4 of 18 on Dec 28
  and none on Dec 30. That is the lead-up behaviour wanted: Christmas photos from about Dec 16 until Christmas, then
  gone. A narrower window (+/-3) misses it entirely, so the window must reach the holiday. The typical busiest day
  holds about 40 photos.
- **Quieter holidays are not reliably the busiest day.** The busiest day in a +/-7 window around Nov 25 is Thanksgiving
  (within a day) in 6 of 16 years with 4 or more photos, and in 13 of 16 when the window is centred on Thanksgiving
  itself, so other occasions in the same fortnight compete. Easter is inside a fixed window in only 10 of 18 years
  (it moves by 35 days) and is the busiest day in 6 of 14; July 4th in 2 of 14. The family photographs other things
  more in those weeks.
- **Generic availability is excellent.** In a +/-7 window around any day of the year, some day has 4 or more photos
  in a median 14 of 18 years (3 or more years for all 365 days), 8 or more photos in a median 9 years (3 or more
  years on 347 days), 15 or more in a median 5 years (3 or more on 286 days). So "the big day of this time of year"
  exists nearly every day.

Assessment: the idea simplifies the general case a lot and gives the lead-up for free, but it does not replace holiday
rules where a holiday must show up reliably or be named. A hybrid is the natural design:

1. **Coming up through the years (no tables).** Window from 3 days back to about 14 ahead; per earlier year the
   busiest day, counted only when it has enough photos (suggest 8 or more); one photo per year in the collage, or all
   from one year's burst. Covers Christmas, birthdays, anniversaries, recurring trips.
2. **Holiday anchors (a small table) for the holidays that must show.** When today is within about 7 to 10 days
   before a floating holiday, use that holiday's date in each year (plus or minus a day) instead of the busiest day,
   and name it ("Thanksgiving through the years"). Easter, Thanksgiving, Mother's Day and the others from the
   assessment above.

The per-day counts are computed once when the album loads, so finding a year's busiest day in a window is cheap.

## Critique

- Too many themes dilute each; start with **One outing, A day, This day through the years, Lately**, add **A trip**
  and **Special dates** when the first four are liked.
- A pool of 6 candidates is too small for shape fitting to find a layout that crops under 30%; themes should supply
  8 to 12. Photos of one stretch of a day are often the same shape, so fitting may fail more often for outings; the
  fallback is a smaller collage.
- "This day through the years" overlaps the memory pool (within 14 days of today). It is the same idea shown as a
  collage; the memories share and the collage share should be set together.
- Anniversary photos come from many years and are rarely downloaded, so they need the early download that day
  collages already use.
- A seed with fewer neighbours than wanted gives a smaller collage; at +/-1 hour 73% of photos can seed four.

## Measuring

Add a theme report to `RealAlbumSimulation`: per theme, how often it is available, the number of photos, the span
of their capture times, the number of distinct years or days, and whether the layout fits within the crop limit.
Compare with today's same-day collage (about 135 minutes median span, 41% mixing moments).

## Decisions for the owner

1. Which themes first? Suggested: One outing, A day, This day through the years, Lately.
2. The outing window: +/-1 hour (a median 39 minutes of the day) or tighter or looser?
3. Show a small theme label on the slide, or only the dates on the pictures?
4. A trip: worth building later? How should "home" be set: guessed from the photos or chosen by the owner?
5. Special dates: a list of birthdays and anniversaries entered on the controls page, later?

## How collages are built today

1. **When:** after every 20 single slides (setting 5 to 100); the count cycles 2, 3, 4.
2. **Source:** same day ranks the album's days (enough downloaded photos first, then most unseen, then shown longest
   ago, then bigger and newer), avoiding the previous collage's date, and takes up to 12 candidates, downloaded
   first, then not shown yet; mixed picks randomly between that and the newest 300 photos.
3. **Fit:** the first 6 usable candidates (downloaded, not blurry, not in the previous slide); every combination of
   the wanted count is tried against the layouts (columns, rows, grid, L-shapes for three), rejecting near-identical
   pairs by hash and any arrangement that crops a photo by more than 30%; the least-cropped wins; a smaller count is
   tried before a letterboxed layout.
4. **Draw:** a 10 px mat in the original app's light grey (`#F4F5F7`) between photos and none at the edge; each picture carries its own date.
5. **After:** the photos count as shown and leave the round; the date is remembered so the next collage differs.
