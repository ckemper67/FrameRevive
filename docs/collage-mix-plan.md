# Plan: collages follow the mix settings

Status (2026-11-27): stages 1 and 2 are built (the theme turn scheduler, `ThemeTurns`; the recent-or-plain draw of the
anchor day, `AnchorDraw`); stages 3 and 4 are proposed. The design comes from a review of the problem, checked against the
code; the points where the code differs from the first proposal are marked.

As built, stage 2 works like this. Each collage of "A day" or "One outing" flips its own coin (recent with probability
`recentPercent / 100`) and draws its anchor from the best class of days, those as ready and as unseen as the best-ranked day
and as unseen in share as the most unseen of them (so a day already shown from is not drawn while a fully unseen one
exists, which keeps the rule that a collage prefers photos not yet shown). The weight of a day is its size (capped at 20
photos) times its unseen share, and for a recent draw also the singles' `Recency` weight of the day; for "newest", which
has no weight, a recent draw takes the newest 20 days of the class. A plain draw leaves the recency factor out. The day
ranking without a draw is unchanged, and "On this day" does not use the draw.

Simulated on the owner's album (864 days with five or more photos, today 27 November 2026, 4,000 draws each): at 100
percent recent and newest, every anchor is among the newest days; under "somewhat" at 100 percent, 38 percent of the
anchors are from the last two years against 11 percent in a plain draw, and 37 percent are from 2015 or older against 54
percent in a plain draw; at 0 percent every year has its natural share. Before this, the ranking took the biggest unseen day
and used recency only to break ties, so even "all recent" gave a decade-old day.

## Goal

The owner's mix settings steer the single photos but not the collages: with the slider at "all recent" a collage can still
be a decade-old day, and "On this day" collages appear at the same rate whatever the slider says. Make the choice of a
collage's occasion (its anchor day) follow the same three settings, with no new settings and no change to the layouts, the
crop rule or the planner's pools and scoring.

## The settings (they exist already, for the singles)

- **Recent versus memories** (`recentPercent`, 0 to 100): how many of a round's slots are memories, photos near today's
  date in earlier years. At 0 and 100 one pool is used outright.
- **Recency** (`Recency`: newest, somewhat, even): newest is a hard window of the newest unseen photos; somewhat gives a
  photo a weight that halves about every four months and bottoms out at 5 percent (`0.05 + 0.95 * exp(-age / 180 days)`);
  even gives every photo the same weight. `Recency.weigher(todayMs)` returns that weight for a photo, or null for newest.
- **Favorites** (`favoriteShare`: off, some about 1 in 20, often 1 in 10, lots 1 in 5): a share of the slots, not a count.

## Collages today

A collage appears every N single photos. The enabled themes (A day, One outing, On this day) take turns. For A day and One
outing the anchor is the best day of the whole album, ranked by: fully downloaded, then most photos not yet shown, then
shown longest ago, then the bigger day, then the more recent one; On this day ranks the same way among the days within the
memory window of today's date. The previous collage's day is avoided. None of the three settings is read.

## Proposal

1. **Theme turns follow the memories share (On this day only).** The themes still take turns, so consecutive collages
   differ. Other themes keep one turn each; On this day gets a turn in proportion to the memories share
   `(100 - recentPercent) / 100`, by a small deterministic stride scheduler (each theme has an accumulator advanced by its
   stride, the lowest fires; strides 1 for the other themes and `1 / memoryShare` for On this day). At 100 percent recent
   On this day never fires, as the singles then show no memories; when it is the only enabled theme it fires anyway. Weighting
   the turns of the other themes by the slider is not done: it would starve themes at the extremes.
2. **A day and One outing lean recent or old by the settings.** Each such collage makes a draw mode from the seed and the
   turn: a recent draw with probability `recentPercent / 100`, otherwise a plain draw. A recent draw weights each candidate
   day by the singles' own recency weight of the day (`Recency.weigher`, taken at the day's middle photo); a plain draw
   weights all days equally, so the existing ranking (unseen first, longest unseen) surfaces forgotten days. For newest, whose
   weigher is null, a recent draw takes only the newest days, as the singles do. One definition of how old is too old serves
   both. *Difference from the first proposal:* it used its own age formulas and a lexicographic tiebreak; the weight must
   be the existing `Recency` weigher, and the weight must act as a draw, not as a tiebreak. A weight placed after the
   other keys of a lexicographic sort differs between every pair of days, so it would silently make the bigger-day and
   longest-unseen keys irrelevant and always pick the single newest day. The days are first ranked as today, and the draw is
   among the best few (those with the most unseen photos and downloaded), weighted by recency.
3. **Favorites weight the anchor, not the pool.** A day containing favorites gets a weight multiplier scaled by the share
   setting and by the day's favorite density (starting values 1 plus 0.5, 1 or 2 times favorites over photos for some, often
   and lots; off is no boost). It enters the weight of the draw above, not the sort. Favorites are not forced into a collage:
   the planner picks by shape and crop, and forcing one could block a five-photo layout.
4. **On this day is not weighted by recency.** It picks seasonal days to rediscover old memories; preferring recent years
   would defeat that. Not yet shown first stays its priority.

## Determinism and testing

The choice is a pure function of the day statistics, the settings, the turn counter and a seed; the turn counter is stored
by the caller. Unit tests: the scheduler's sequence at 0, 50 and 100 percent with one, two and three themes, and the
single-theme fallback; the draw mode for a seed and turn; recent draws favor newer days under somewhat and take only the
newest under newest; even ties; a favorite-rich day outweighs an equal day without; 0 percent never makes a recent draw and
100 percent always does; off gives no boost; no day is chosen twice in a row.

Before shipping, simulate on the real album (15,373 photos over 20 years): 1,000 runs of 10 collages for each slider value
in 0, 25, 50, 75 and 100, each recency and favorites off and often. Compare the age of the anchors with the slider (at 100
percent and newest, at least 60 percent of anchors from the last three years), the share of On this day collages against
`(100 - recentPercent) / 100` of its base share (within 5 points), repeats of the previous day (under 5 percent), the share
of collages on a day with a favorite, and how many distinct days are used.

## Interaction with what exists

- The deck and the play history are not touched. Collages still claim their photos after they are shown, which removes
  them from the cycle; the new weights only read the history (not yet shown, shown longest ago).
- Cost: a few multiplications per candidate day on top of the ranking, well under a millisecond on the development machine
  with the day index. Per-day statistics such as the favorites count can live in the index.
- The collage context gains the three settings (`recentPercent`, recency, favorite share), read from the same values the
  singles use. The web page gets one sentence under the mix settings saying they also apply to collage occasions.

## Staged plan

1. The theme turn scheduler (small, testable, changes only how often On this day comes up; built).
2. The recency-weighted draw for A day and One outing, with the simulation (built).
3. The favorites weight on the anchor.
4. The web sentence and this document's update in `collage-themes.md`.

## Not done

Weighting the other themes' turns by the slider or by favorites; forcing favorites into a collage; reusing the deck's draw
machinery (only the weight function is shared); making On this day obey the recency setting; any change to the planner.

## Open decisions

1. ~~At 100 percent recent, should On this day collages stop entirely or keep a floor?~~ Answered: they stop entirely, as the
   singles then show no memories.
2. ~~Should the recent-or-plain draw be made for each collage or planned for each cycle?~~ Answered: for each collage. Each
   collage flips its own coin, then is built wholly from that kind (recent or plain); a single collage is never a mixture.
3. ~~For the plain draw, which days are eligible?~~ Answered: any day of the album, ranked as today by unseen and longest
   unseen; the seasonal case is the On this day theme.
4. ~~How strong should the favorites weight be?~~ Answered: decided by the simulation, which shows how often favorite-rich
   days are chosen at each level; the starting values are guesses.
