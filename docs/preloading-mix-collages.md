# Preloading, photo mix and collages (0.5.20)

Everyday includes the Recent/Memories slider, recent-photo count and seasonal memory window. Recent means capture date. Newly discovered photos still get their first showing before the usual mix.

Automatic preloading prepares four upcoming photos on disk, or five near the next collage. One background download runs at a time. Debug shows Ready, Loading, Queued and Failed states with capture dates, without private photo URLs. An existing download may finish after a plan changes.

## Occasional collages

Enabled by default, with a collage after 20 successfully displayed individual photos. Automatic collages cycle through 2, 3 and 4 photos. Choose 5–100 individual photos between collages or switch collages off. A collage lasts the normal photo interval. It uses distinct upcoming photos from the regular mix, avoiding the current photo. If the selected photos are not ready in the disk cache, ordinary playback continues and tries again later.

The photos are chosen before the layout: photos whose shapes can cover the frame together are preferred, and the arrangement that needs the least cropping of any single photo wins. If the wanted count cannot cover the frame within the 30% crop limit, the next smaller count is used; if no shapes tile the frame, the collage keeps the letterboxed layout with the complete photos and the blurred background. Each cell is decoded at the size it needs, so cropping does not upscale.

The layout is chosen from tall side-by-side panels, stacked panels, a larger main panel with two smaller panels, and a four-photo grid. The blurred-background setting fills unused cell space, and the date setting shows each photo's date. Pause, Next and Previous work with the whole collage. History contains photo references rather than retained bitmaps. After an album refresh, a historical collage is removed if any member is no longer available.

Pairs are separate from collages and run in between them; see [Show pairs](show-pair.md). A pair counts as one photo for the collage counter.

## What a collage is made of

Each collage picks one of two sources at random and keeps that choice while it waits for downloads:

- **Recent:** every member comes from the newest *Recent photo count* set — the same rule the photo mix uses — so the collage never leaves the current photos.
- **Same day:** every member was taken on one capture date, so the collage is one afternoon or one birthday. Dates with at least the wanted number of photos qualify; a date whose photos are all cached wins, then the date with the most photos, then the newest. The date used for one collage is skipped for the next, and that date's photos are queued for download as soon as the collage is due so playback keeps moving until they arrive.

If the chosen source has nothing ready, the other source is tried for that collage; if neither works the collage is skipped and retried later. Shape selection still applies: the chosen photos are arranged to cover the frame, the count drops when that count cannot fit, and the letterboxed layout is the only remaining fallback. A same-day collage shows whole photos more often than a recent one, because a single afternoon rarely provides four matching shapes.

## Soft shots are left out

Lenovo's frame claimed to ignore blurry or grainy shots when it chose photos. Collages here use a quality gate
built from one grey-scale pass over a 256-pixel analysis copy; the analysis is cached per photo, so each photo
is scored once, on a background worker, in roughly ten milliseconds including the small decode.

Measured on the metric (341x256 test frame): a sharp reference image scores 0.0101, the same image blurred by a
3-pixel radius 0.0002, and by 6 pixels 0.00007 — the focus measure separates them by more than an order of
magnitude, and the maths itself costs about 0.5 ms per frame on a desktop CPU.

- **Sharpness** is the variance of the 4-neighbour Laplacian, normalised by the photo's own pixel variance, so
  it measures structure rather than brightness.
- **Contrast, mean brightness and clipped fraction** come from the same pass and reject frames that are flat,
  nearly black, nearly white or mostly blown out.
- Blur is judged **comparatively**: a photo is dropped only when it is below 12% of the album's median
  sharpness, and only once at least 30 photos have been scored and the album median is above 0.004. Without
  enough evidence, nothing is dropped — a soft-but-legitimate album is never filtered against itself.

Scores live in `photo-quality.json` next to the photo cache. Debug reports how many photos are scored, the
album median, and how many are kept out of collages. Photos that have not been scored yet are used normally, so
the gate only ever removes images the frame has actually seen.

Limitations: film-like grain is not detected (noise scores high on the focus measure), and the gate applies to
collages only — pairs and single photos are never hidden.

Debug's **Preview collage** cycles through 2, 3 and 4 cached photos without changing the saved frequency or pause state. The collage counter restarts with the app; it is not an exact wall-clock schedule.

Burst playback has been removed for now.
