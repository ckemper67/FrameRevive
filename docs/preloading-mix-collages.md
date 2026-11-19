# Preloading, photo mix and collages (0.5.20)

Everyday includes the Recent/Memories slider, recent-photo count and seasonal memory window. Recent means capture date. Newly discovered photos still get their first showing before the usual mix.

Automatic preloading prepares four upcoming photos on disk, or five near the next collage. One background download runs at a time. Debug shows Ready, Loading, Queued and Failed states with capture dates, without private photo URLs. An existing download may finish after a plan changes.

## Occasional collages

Enabled by default, with a collage after 20 successfully displayed individual photos. Automatic collages cycle through 2, 3 and 4 photos. Choose 5–100 individual photos between collages or switch collages off. A collage lasts the normal photo interval. It uses distinct upcoming photos from the regular mix, avoiding the current photo. If the selected photos are not ready in the disk cache, ordinary playback continues and tries again later.

The photos are chosen before the layout: photos whose shapes can cover the frame together are preferred, and the arrangement that needs the least cropping of any single photo wins. If the wanted count cannot cover the frame within the 30% crop limit, the next smaller count is used; if no shapes tile the frame, the collage keeps the letterboxed layout with the complete photos and the blurred background. Each cell is decoded at the size it needs, so cropping does not upscale.

The layout is chosen from tall side-by-side panels, stacked panels, a larger main panel with two smaller panels, and a four-photo grid. The blurred-background setting fills unused cell space, and the date setting shows each photo's date. Pause, Next and Previous work with the whole collage. History contains photo references rather than retained bitmaps. After an album refresh, a historical collage is removed if any member is no longer available.

Pairs are separate from collages and run in between them; see [Show pairs](show-pair.md). A pair counts as one photo for the collage counter.

## Collage themes

**Mix** is the default: a collage draws from the ordinary upcoming playlist, so it can hold a recent photo next to a photo from a past year, exactly like the slideshow itself.

**Recent** keeps every member inside the newest *Recent photo count* set — the same rule the mix uses — so a collage never leaves the current photos.

**Same day** groups photos by capture date and picks one date instead of unrelated pictures. Dates with at least the wanted number of photos qualify; a date whose photos are all cached wins, then the date with the most photos, then the newest. The date used for one collage is skipped for the next one, and that date's photos are queued for download as soon as the collage is due so playback can keep moving until they arrive. If no date has enough photos, the collage is skipped and retried later.

Shape selection still applies inside the theme: the chosen photos are arranged to cover the frame, the count drops when that count cannot fit, and the letterboxed layout is kept only when no combination tiles the frame. A same-day collage therefore shows whole photos more often than a mixed one, because a single afternoon rarely provides four matching shapes.

Debug's **Preview collage** cycles through 2, 3 and 4 cached photos without changing the saved frequency or pause state. The collage counter restarts with the app; it is not an exact wall-clock schedule.

Burst playback has been removed for now.
