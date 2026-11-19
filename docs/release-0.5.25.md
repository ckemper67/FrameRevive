# FrameRevive 0.5.25 Alpha — Show Pair

- Two photos of the orientation opposite the frame now fill the screen, as Lenovo's own frame did. A portrait
  frame stacks two landscape photos; a landscape frame places two portrait photos side by side.
- Both halves share one zoom factor, so both photos lose the same proportion of their edges. Pairs needing
  more than 30% are refused and the photo plays as a single with the blurred background.
- Pairs last one normal photo interval, count as one photo toward the collages, and work with pause, Next and
  Previous. Preloading keeps four photos ready while pairing is on.
- Photos are requested as a square box of the panel's long edge, so a cropped half keeps full resolution. The
  first refresh after updating re-fetches larger copies and the smaller cached files are dropped.
- **Show pairs** is on by default in Everyday and in the on-frame settings dialog. Debug reports the frame
  orientation, whether the current slide is a pair, and the crop limit.
- Collages now choose photos by shape too: the arrangement that crops each photo least covers the frame, a
  count that cannot fit drops to the next smaller one, and only combinations that tile nothing keep the
  letterboxed layout. Each cell is decoded at the size it needs.
- Each collage draws at random either from your newest photos or from one capture date, favouring dates that
  are already cached, and falls back to the other source when nothing is ready. No new setting.
- Collages leave out noticeably soft photos: one grey-scale pass per photo yields a normalised focus measure
  plus contrast, brightness and clipping, judged against the album's own median. Scored once per photo and
  cached; Debug reports the counts.
- Retains collages, gentle transitions, the photo mix, sleep/wake, HTTPS controls and webpage APK updates.

## Install or update

New Windows installation: download **FrameRevive-0.5.25-Windows.zip**, extract it, and follow
**README-FIRST.md**. Use a USB data cable and Bluetooth mouse to enable and authorize USB debugging.
Platform-Tools are required; Android Studio is optional.

Existing installation: upload **FrameRevive-0.5.25-alpha.apk** in **Debug & updates** and confirm Android
installation on the frame. Do not uninstall first.

## Validation and limits

Android build and 92 unit tests pass. The pair geometry, planner, crop limit and cache naming are covered by
unit tests, including portrait and landscape frames and mixed aspect ratios. On-frame confirmation of pair
rendering is still pending; the frame was not connected over USB when this build was produced.

See [Show pairs](show-pair.md) for the measurements and behaviour.
