# Tuning the photo-selection strategy against your own album

How the frame chooses photos depends on the shape of an album: how many days have photos, how many photos a
big day holds, how much is recent. A made-up album cannot tell you that. This workflow reads your album's
capture times, keeps nothing else, and runs the real selection code over them so a strategy can be judged on
real numbers before it goes on a frame.

## Privacy

`tools/album_dates.py` writes one line per photo: `takenMs,offsetMs`. No photo ids, no addresses, no images, no
album title. The default output, `local/album-dates.csv`, is in a folder git ignores. Capture dates still say
something about a life (when you travel, when a child was born), so treat the file as private: do not commit it
or attach it to an issue. If you want to share what you found, paste the summary the script prints, which holds
counts and ranges, not individual dates, apart from the five busiest days.

The shared album link grants access to the album, so the script reads it from an environment variable, never
from the command line, where it would show in process lists and shell history. It never prints the link or its
access key, and error messages have both removed.

## Steps

1. **Get the link.** Use the same shared album link the frame uses (`https://photos.app.goo.gl/...`).
2. **Read the dates.** Needs Python 3, nothing else installed.

   ```bash
   export FRAMEREVIVE_ALBUM_LINK='https://photos.app.goo.gl/...'
   python3 tools/album_dates.py
   ```

   It prints a summary and writes `local/album-dates.csv`. `--from-csv FILE` summarizes a file without any
   network, and `--out FILE` changes where the CSV goes. The summary lists photos and days, photos per day
   (median, 90th and 99th percentile, most), how many days have a single photo, how much of the album sits on
   big days, the busiest days, the last 1, 3, 6, 12 and 24 months, each year, the timezones in use (and how many
   photos have offset 0, which may be a missing timezone), how many days hold several moments (photos within 2
   hours of each other), how many moments cross midnight, and how many days mix timezones.
3. **Run the simulation.**

   ```bash
   ./gradlew :app:testDebugUnitTest --tests '*RealAlbumSimulation*' --rerun -i
   ```

   (`--rerun` because Gradle does not notice that the CSV changed.) The test finds `local/album-dates.csv`, or
   the file named by `FRAMEREVIVE_ALBUM_DATES`, and skips itself when there is none. For each setting of
   "Favor newer photos" it plays rounds with a fake clock and prints, after 1, 5, 10 and 20 rounds:

   - **photos shown** and **days reached**: how much of the album, and how many of its days, have appeared;
   - **slots on days with 20+ photos**: how much of the screen time big days take (compare it with the share of
     photos they hold, which the first line reports);
   - **slots from the last 6 months**: how often recent photos come up.

   The reports also plan at that pace, as the frame does (a plan of about one awake day's photos), and time is
   converted to days at the same pace. Set `FRAMEREVIVE_SECONDS` (seconds per photo) and
   `FRAMEREVIVE_AWAKE_HOURS` to your frame's settings; the controls page's "Your album" card shows the photos a
   day it works out from them. The defaults are the app's own, 30 seconds and 15 hours, which is 1,800 photos a
   day. A slower frame (240 seconds for 16 hours is 240 a day) takes much longer to get through an album, which
   changes what matters: run the report at your real pace.

## Trying another strategy

`RealAlbumSimulation.realAlbumReport` builds each round with the real `PlaybackEngine.buildCycle`. Change the
`CycleSettings` it passes (`roundSize`, `memoryDays`, `recentPercent`, `recency`) or change the selection code
itself, rerun, and compare the lines. The same file's `report` function also takes the "big day" size (20
photos) and the "recent" period (6 months) as parameters.

## Previewing the "Your album" card

The controls page has a "Your album" card in the Photo mix tab: the album's shape (photos, days, photos per day,
how much sits on big days), the last 1 to 24 months, each year, the memory pool for today, what the frame has
shown so far, and what the current settings mean in time ("a memory photo comes back about every 1.3 days"). It
is built from `AlbumStats` (the numbers) and `AlbumCard` (the HTML), pure Kotlin with no Android types, so it
can be developed on the dev machine against a real album:

```bash
FRAMEREVIVE_SECONDS=240 FRAMEREVIVE_AWAKE_HOURS=16 ./gradlew :app:testDebugUnitTest --tests '*AlbumCardPreview*' --rerun -i
```

This writes `local/controls-preview.html` (the whole controls page with the card in it) and
`local/album-card.html` (the card alone, with the page's styles); open either in a browser. The play history
comes from running the real selection pipeline for `FRAMEREVIVE_PREVIEW_ROUNDS` rounds (default 20, about six
days of display; 0 shows a frame that has shown nothing yet). To iterate, edit the wording or layout in
`AlbumCard.kt`, or the numbers in `AlbumStats.kt`, rerun, and refresh the browser. The frame builds the same card
when its controls page is opened, so it needs a new build only once the card is as wanted.

## What the simulation does not model

- Near-duplicate and burst thinning (it uses no image hashes) and the sharpness gate (no scores).
- Collages and pairs. It measures which photos a round contains, not how slides are composed.
- Photos that are not downloaded yet, or an album that changes while playing.
- The real timing of the frame. See `docs/day-coverage-plan.md` for why that matters.

## Limits of the script

It reads the public shared-album page and its album pages the way the frame does, using Google's unofficial web
interface, so it can break without notice; when it does it says the format changed and stops. It is read-only,
uses no account cookies, and only talks to `photos.google.com` and `photos.app.goo.gl`, refusing a redirect
anywhere else. Its tests (`python3 -m unittest discover -s tools -p 'test_*.py'`) use a fake network and cover
the link rules, paging, the CSV contents, the summary, and that no link, key or identifier is ever printed.
Protocol details are adapted from xob0t/google_photos_web_client (MIT), as in `tools/probe_google_photos.py`.
