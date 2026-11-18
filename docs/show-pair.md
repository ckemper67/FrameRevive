# Show pairs (0.5.25)

Lenovo's own frame paired photos instead of leaving empty space, and FrameRevive now does the same. Its
launch feature list described it as **Show Pair**: "Allows to display 2 pictures of opposite orientation to
the frame to fill-in space", with the blurred background only when the option was off.

## What it does

- Portrait frame: two landscape photos, stacked top and bottom.
- Landscape frame: two portrait photos, side by side.
- A photo whose orientation already matches the frame stays a single photo.
- Both halves share one zoom factor, so each photo loses the same proportion of its own width (or height).
  Pairs that would lose more than 30% are refused, and the photo plays as a single with the blurred
  background instead — that is why a panorama stays whole while a 4:3 or 3:2 photo pairs.

Measured on a 1080×1920 portrait frame, with the frame's own geometry:

| Pair | Screen filled | Cropped from each photo |
| --- | --- | --- |
| Two 4:3 landscapes | 100% | 16% of width |
| Two 3:2 landscapes | 100% | 25% of width |
| 4:3 with 16:9 | 100% | 26% of width |
| Two 16:9 landscapes | refused (37%) | single photo, blurred background |

For comparison, a letterboxed 3:2 pair with the blurred background fills 73% of the same screen.

## Behaviour

- **Show pairs** is on by default in Everyday, matching Lenovo's default. Switching it off keeps the previous
  single-photo rendering with the blurred background. The native on-frame settings dialog has the same switch.
- A pair is one slide. It lasts the normal photo interval, counts as a single photo for the collage cadence,
  and Next/Previous move over the whole pair. History keeps photo references, so a pair can be revisited.
- A pair needs two photos that are already in the disk cache; the planner looks up to eight photos ahead and
  skips partners whose orientation does not fit or whose crop would be too large. When no partner is ready,
  the ordinary single photo plays and pairing is retried on the next photo.
- Preloading keeps four upcoming photos instead of three while pairs are on, so a partner is usually ready.
- The caption shows the leading photo's date, as a single photo would.
- Debug reports the frame orientation, whether the current slide is a pair, and the crop limit.

## Cache resolution

Photos are now requested as a square box of the panel's long edge (`=w1920-h1920` on this frame) instead of
the portrait/landscape box of the moment. Google scales a photo to fit the requested box, so the old request
cached a landscape photo at 1080×720 on a portrait frame — too small to crop to half the screen. The requested
box is part of the cache file name, so the first refresh after updating fetches the larger copies and the
smaller files are dropped by the usual cache cleanup.

## Files

- `PairSelection` — frame orientation, pairability and the pair planner (pure Kotlin, unit tested).
- `CollageLayout.pair` — two-cell geometry with one shared zoom factor and the crop fraction.
- `FrameCache` — square request box and the cache file name.
- `FrameSlide` — `SlideMode.SINGLE`, `PAIR` and `COLLAGE`; only `COLLAGE` is the curated collage cadence.
- `MainActivity` — pairing in `showNext`, pair rendering in `showSlide`, the settings switch and Debug status.
