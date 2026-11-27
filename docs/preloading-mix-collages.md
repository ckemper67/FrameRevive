# Preloading, photo mix and collages

## Where a collage's photos come from

A collage draws from **one day or one stretch of a day** (a *theme*), because the ordinary stream already
alternates unrelated photos; repeating that inside a collage adds nothing. The controls page has one switch per
theme, and collages take turns between the themes that are on. If one cannot find photos that fit, the next is tried;
if none can, the collage is skipped and a single photo is shown. At least one theme stays on.

- **A day:** different parts of one capture date, chosen far apart in time.
- **One outing:** one stretch of a day, like a walk or a party: a seed photo and photos within an hour of it, again
  far apart in time.

More themes are proposed in `collage-themes.md`.

## What a collage is made of

1. **When:** after every 20 single slides (setting 5 to 100). Every collage aims for five photos and settles for three or
   two when five do not tile the frame; the count is not chosen ahead of time.
2. **Which day:** days with at least the wanted number of dated photos are ranked: a day whose photos are already
   downloaded first, then the day with the most photos not shown yet, then the one shown longest ago, then the bigger
   and the newer. The previous collage's date is skipped when another qualifies. Days are the photo's own local
   day (its capture time plus its own timezone offset); undated photos never form a day.
3. **Candidates:** the theme offers 8 to 12 photos, not yet shown first, spread through time, and never two
   within two minutes of each other, so a collage does not show frames of one instant. The first few are queued for
   download as soon as the collage is nearly due, so playback keeps moving until they arrive.
4. **Fit:** from the first 10 usable candidates (downloaded, not blurry, not in the previous slide), five photos are
   tried against the layouts (the interlocking arrangement for five, one and two for three, a proportional split for two),
   rejecting near-identical pairs by hash and any arrangement that crops a photo by more than a quarter (75 percent stays
   visible, as in the frame's original app); the least-cropped wins. If five do not tile, three are tried, then
   two, and the letterboxed layout (as many candidates as there are, shown whole) is the last resort. The layout depends
   only on the photos' aspect ratios, so photos are grouped by aspect and each distinct set is fitted once, which keeps
   a pool of 10 cheap. All ten are queued for download two slides before the collage is due.
5. **After:** the photos count as shown and leave the round.

On the owner's real album the old pool (a day's photos in album order) put two photos within two minutes of each other
in 51% (2 photos), 85% (3) and 94% (4) of collages; the themes do so in 0 to 5%.

## Soft shots are left out

Lenovo's frame claimed to ignore blurry or grainy shots when it chose photos. Every slide here (single photos,
pairs and collages) goes through a quality gate built from one grey-scale pass over a 256-pixel analysis copy; the analysis is cached per photo, so each photo
is scored once, on a background worker, in roughly ten milliseconds including the small decode.

Measured on the metric (341x256 test frame): a sharp reference image scores 0.0101, the same image blurred by a
3-pixel radius 0.0002, and by 6 pixels 0.00007 — the focus measure separates them by more than an order of
magnitude, and the maths itself costs about 0.5 ms per frame on a desktop CPU.

- **Sharpness** is the variance of the 4-neighbour Laplacian, normalised by the photo's own pixel variance, so
  it measures structure rather than brightness.
- **Contrast, mean brightness and clipped fraction** come from the same pass and reject frames that are flat,
  nearly black, nearly white or mostly blown out.
- Blur is judged **comparatively**: by default a photo is dropped when it is below 25% of the album's median
  sharpness (the "Skip blurry photos" setting also offers off, 12%, the original collage rule, and 40%), and only once at least 30 photos have been scored and the album median is above 0.004. Without
  enough evidence, nothing is dropped — a soft-but-legitimate album is never filtered against itself.

Scores live in `photo-quality.json` next to the photo cache. Debug reports how many photos are scored, the
album median, and how many are kept out of collages. Photos that have not been scored yet are used normally, so
the gate only ever removes images the frame has actually seen.

Limitations: film-like grain is not detected (noise scores high on the focus measure), and the gate applies to
collages only — pairs and single photos are never hidden.

Debug's **Preview collage** cycles through 2, 3 and 4 cached photos without changing the saved frequency or pause state. The collage counter restarts with the app; it is not an exact wall-clock schedule.

Burst playback has been removed for now.
