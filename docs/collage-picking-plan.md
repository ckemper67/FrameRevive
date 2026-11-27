# Plan: picking the photos for a collage

Status (2026-11-27): stage 1, the planner that chooses from the album's sizes and times, is built (`CollagePlanner`);
stages 2 to 4 are proposed. The layouts, the 25 percent crop cutoff and the grey mat are already in (see
`collage-themes.md` and `preloading-mix-collages.md`); this plan is about which photos go into them and when they
are chosen. The design comes from a review of the problem; the measurements were made on the owner's real album with
exact photo shapes.

How the pieces divide the work: a theme (a day, one outing) only chooses an occasion, and the themes still take turns
so consecutive collages differ. The planner builds the candidate pools around that occasion (the moment, the day, the
week), finds the best collage in each, and scores them: a bonus for the number of photos, minus the worst crop, minus a
small penalty for a wider window (0 for a moment, 0.03 for a day, 0.08 for a week), so a closer occasion is preferred
and a week still wins when the day lacks the shapes, as on a holiday. Only the chosen photos and two spares are
downloaded, a pair that tiles on its own first.

## Where we are

- A collage is 5, 3 or 2 photos. Five interlock (a wide strip, then two columns of small over large), three are one
  picture and two beside or below it, two are a pair. A photo may lose at most 25.1 percent to cropping.
- A theme (a day, one outing) offers up to 12 candidates. About two slides before the collage is due, ten of them
  are queued for download. The picker then looks at the ten that arrived and tries every way of taking 5, then 3,
  then 2 of them, grouped by photo shape.
- Shapes are grouped by `round(width / height * 50)`, so a group is about 0.02 wide. The 4:3 and 3:4 shapes, the
  commonest in the album, sit within 0.01 of the cutoff in some slots, so a group's first photo can pass while
  another photo of the same group loses a few percent more than the limit.
- The layout search itself is cheap (0.07 ms for five photos on the development machine, 40 to 100 times that on the
  frame). What limits the result is the pool: ten photos, mostly from one camera.

## What the data says

Real album: 15,373 photos, 40 percent portrait and 59 percent landscape. The commonest shapes are 4:3 landscape
(6,110), 3:4 portrait (5,291) and 3:2 landscape (2,689). The median day with ten or more photos has 17 photos and
37 percent portraits; 36 percent of such days are under 20 percent or over 80 percent portrait.

Share of pools from which a collage of each size is possible (exact shapes, the 25.1 percent cutoff, the 12 five-photo
layouts and the 4 three-photo layouts):

| Pool | Five | Pair or three | None |
|---|---|---|---|
| One day, 10 random photos (today) | 62% | 34% | 4% |
| One day, 20 random photos | 72% | 25% | 3% |
| One day, the whole day | 75% | 22% | 3% |
| Day plus or minus 1, 10 photos | 65% | 32% | 2% |
| Day plus or minus 1, the whole window | 88% | 11% | 1% |
| Plus or minus 7 days, 10 photos | 77% | 23% | 0% |
| Plus or minus 7 days, 20 photos | 92% | 7% | 0% |
| Plus or minus 7 days, the whole window | 97% | 3% | 0% |
| Plus or minus 14 days, the whole window | 99% | 1% | 0% |

A bigger pool matters, but only if its photos do not all have to be downloaded: the frame's photo cache is capped at
512 MB, which is on the order of a thousand screen-sized photos, a small part of the album.

## The algorithm

1. **Sorted matching.** The crop of a photo in a slot depends only on the gap between their shapes (the ratio of
   their aspects), and rises with it. To minimize the worst crop of a set of photos in a layout, sort the photos by
   aspect, sort the layout's slots by aspect, and match in order. No other assignment can do better (any crossed
   pair can be uncrossed without raising the worst gap). This replaces the search over orders.
2. **Slot bands.** A slot of aspect `s` takes a photo of aspect `a` when `0.749 * s <= a <= s / 0.749`
   (crop at most 0.251). Both ends rise with `s`, so feasibility is a single pass: for each slot in order, take the
   smallest unused photo at or above its lower end and check it is not above its upper end. The bands are computed
   once per screen shape from the exact slot rectangles, never from rounded ratios; a pixel of geometry can flip the
   answer for the common 4:3 and 3:4 photos.
3. **Exact shapes.** Use each photo's own width and height, not the bucket. Shape groups, where wanted for
   spares, are defined by which slot types a photo fits (wide, tall, both, neither).
4. **Priority walk.** The sets of photos that can fill a given layout form a transversal matroid, so walking the
   candidates in the theme's priority order (not yet shown first, longest unseen, spread in time) and keeping each
   one that leaves the set fillable gives the best-ranked feasible set. With at most five slots each check is a
   tiny matching (25 steps), so a pool of 60 over 16 layouts is about 25,000 steps, a few milliseconds on the frame.
5. **Pairs.** For two photos stacked on a portrait screen the proportional split gives both the same crop; with
   `S = 1/a1 + 1/a2` the pair works when `S` is between 1.325 and 2.361 and is best when `S` is near 1.769. A sorted
   two-pointer scan finds the best pair in a pool.
6. **Count and variety.** Score a candidate as `bonus[count] - worstCrop - (variety penalty if the layout repeats the
   last collage's)`, with starting values `bonus = {5: 0.5, 3: 0.35, 2: 0.3}` and a penalty of 0.05. A good pair
   (a wide photo with a square one, say) then beats a poor five, and pairs keep appearing as variety, which the owner
   likes. The numbers are tuned by looking at real collages, not fixed in advance.
7. **Near-duplicates and blur.** These need downloaded pixels, so they are checked after download and are not part
   of the matroid walk; the fallback below covers a rejection.

## Choosing from metadata

Today the candidates are queued for download first and the picker chooses among what arrives. Sizes and capture times
are known for every photo from the album data, so the choice can be made first and only the chosen photos fetched.

What it buys, from the table and the code:

- **A larger pool at no download cost.** Choosing from a whole day instead of ten photos raises the share of fives
  from 62 to 75 percent; from the days within a week, from 77 to 97 percent. Today a bigger pool would mean more
  downloads.
- **Fewer downloads.** Ten queued now; a five-photo collage needs five, plus two spares, so about seven (and four
  or five for threes and pairs).
- **Less wasted work on the frame**, which downloads slowly.

What it costs:

- The planning step changes. It must pick the photos and their spares from metadata, queue them in a useful order,
  and later verify them (blur, near-duplicates) and substitute from the spares.
- A late or rejected photo needs a fallback so a collage is never skipped when a pair would do.

How it would work:

1. At plan time (about two slides before the collage), run the walk over the theme's pool by metadata and pick the
   collage, then a fallback pair, then a spare for a wide slot and one for a tall slot.
2. Download in that order: the fallback pair first, then the rest of the collage, then the spares.
3. At display time, take the planned photos that arrived and passed the blur and duplicate checks, replace a failed
   photo by a same-shape spare, and run the existing fit on the result. If five cannot be had, three, then the pair
   already downloaded.

The existing fit and layouts stay as they are: the final step still runs on a pool of seven or fewer photos, which is
cheap.

## Staged plan

1. **Planner from metadata (built).** Replace the theme's 12 candidates by the walk's choice plus spares and the new download
   order. Keep the existing fit at display time. Measure the share of fives, the downloads per collage and the time
   per decision on the frame (the Deck build line in Diagnostics shows slow code).
2. **Exact shapes** in the picker's grouping, so no photo exceeds the limit.
3. **Count scoring and variety**, tuned by looking at real collages.
4. **Optional:** a third theme, "A few weeks", whose pool is a wider window (the data above suggests it helps most).
   The Preview button already tries a wider window when its own day is short of shapes; playback does not.

## Risks and what not to do

- A pool made of one shape (a day of only 3:4 portraits) can never make a five; no picker fixes that. Themes with a
  wider window help, and the pair is the fallback.
- Occasion coherence: a wide window can pull photos from other days. A theme-level preference for the occasion's own
  day, and a small score penalty for photos from further away, keep collages feeling like one occasion.
- The near-duplicate check is a heuristic on top of the walk and can reject a set that is otherwise feasible; the
  fallback chain (five, three, two) covers it.
- Do not use a pool of more than about 60: beyond that the gain is small.
- Do not download more than ten photos per collage.
- Do not change the layouts or the crop cutoff as part of this.
- Do not add a learned model for scoring; the problem is small and exact.

## Open decisions

1. Do the planner first (stage 1), or exact shapes and scoring first?
2. How many photos should a "wide" window add before a collage stops feeling like one occasion?
3. The starting values of the count bonuses: how often should a pair appear when a five is possible?
