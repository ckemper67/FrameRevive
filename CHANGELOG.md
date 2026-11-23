# Changelog

## 0.5.25-alpha

- Add optional subtle holiday decorations to photos and collages, with Off, Automatic and eight manual themes. Automatic selection uses the frame date.
- Keep successful ADB stderr warnings from stopping installation, while failing commands still stop it.
- Add 18 installer scenarios using a native fake ADB executable, tested in Windows PowerShell 5.1 and PowerShell 7.
- Update Gradle to 9.8.2, Android build plugin to 9.5.0, AndroidX Core to 1.19.1, Material to 1.14.0, ConstraintLayout to 2.2.2 and the Java setup action to 6.2.0. Compile against SDK 37 with Android 10 minimum support.

## 0.5.26-alpha

- Add `tools/deploy-frame.sh`, which checks the frame's certificate against your keychain, uploads an APK and installs it without printing the controls link.
- Add a favorites album: connect a second shared album and every photo in it becomes a favorite. A main-album photo with the same capture time (when only one has it) is hidden so nothing shows twice. Un-starring one of its photos on the frame hides it until it is starred again.
- Skip a single photo whose bytes (md5) match another photo shown in the last 30 days, which also catches the same picture under two ids.
- Rename `tools/album_dates.py` to `tools/album_metadata.py` and add each photo's id to its CSV (`takenMs,offsetMs,id`), so two albums can be compared. Older files without the id column still read.
- Add Show Pair: two photos of the orientation opposite the frame fill the screen, as Lenovo's frame did.
  Portrait frames stack two landscape photos; landscape frames place two portrait photos side by side.
- Share one zoom factor across both halves, refuse pairs that would crop more than 30%, and fall back to the
  single photo with the blurred background.
- Count a pair as one photo for the collage cadence, and keep four photos preloaded while pairing is on.
- Request a square box of the panel's long edge so cropped halves keep full resolution.
- Let a developer sign both build types with their own key through a gitignored `keystore.properties`, and
  document the update rule that follows from it.
- Add the Show pairs switch to Everyday and the native settings dialog, plus Debug orientation status.
- Choose collage photos by shape: a collage now covers the frame with the arrangement that crops each photo
  least, drops to a smaller count when the wanted one cannot fit, and keeps the letterboxed layout only when
  no combination tiles the frame.
- Decode each collage cell at the size it needs, so cropped cells are not upscaled.
- Give every multi-photo slide a white passe-partout: a 28 px (about 7 mm) mount between the photos, shared
  rather than doubled, so pictures never bleed together. The outer photos run to the screen edge with no border.
- Group photos into events by capture time and admit only three frames of a burst into a cycle, so one
  afternoon cannot fill the queue. Held-back frames return in later cycles.
- Skip near-identical frames: a 64-bit difference hash labels bursts of the same moment, a cycle keeps
  one frame of a burst out of every eight slides, and a collage refuses two shots of the same moment.
- Retry failed downloads and album syncs with exponential backoff and jitter, from 2 seconds to a
  fifteen-minute cap, instead of retrying per slide or waiting for the hourly timer.
- Choose where capture dates go: inside each picture (default) or as one line under a group, the way
  Google's frame shows a group. A same-day group then reads as a single date.
- Play the whole album as a deck: photos never shown come first, then the ones shown longest ago, so
  nothing repeats within a cycle and nothing is left unseen. Coverage is reported on the controls page.
- Collages draw from one capture date by default; the controls page offers a Mixed option for an occasional
  collage from the newest photos. A date offers up to twelve candidates so the layout can choose shapes that
  compose instead of forcing whatever four photos it had, and Preview runs the same selection as playback.
- Update the frame from the controls page without touching it: an accessibility service confirms Android's
  installation prompt for FrameRevive only, so uploads install hands-off over WiFi and survive reboots.
- Label every picture of a pair or collage with its own capture date, inset inside the photo rather than in the
  passe-partout, and skip undated album entries instead of showing January 1970.
- Try a device-owner silent install when one is available and fall back to the prompt when Android hides it.
- Emit the frame certificate's validity dates as UTCTime, as RFC 5280 requires before 2050. Strict validators (curl/OpenSSL, some trust stores) rejected the old GeneralizedTime encoding with "format error in certificate's notBefore field"; the identity rotates once on upgrade.
- Pick a collage source per collage at random: either the newest photos or a single capture date, preferring
  dates whose photos are already cached, with the other source as the fallback. No new setting.
- Collage themes replace the collage source. Two themes, switched on or off separately on the controls page, take
  turns: A day (different parts of one day) and One outing (a seed photo and the photos within an hour of it). Both
  choose photos far apart in time and never within two minutes of each other; before, a collage took the first photos
  of a day in album order and put two photos within two minutes of each other in 51 to 94% of collages on a real
  album. Days are now counted with each photo's own timezone, and undated photos can no longer form a day. The
  Mixed source is gone.
- Collages no longer cycle through 2, 3 and 4 photos. Each aims for four and settles for three or two only when four
  do not tile the frame, and the shape fitting looks at 10 candidates instead of 6 (photos are grouped by aspect ratio,
  so each distinct set of shapes is fitted once). On a real album with the themes, 74% of day collages and 35% of outing
  collages have four photos, and 97 to 98% tile within the crop limit instead of showing whole photos on a blurred
  background (before: 49% of four-photo day collages tiled).
- The dates under a group of pictures follow the pictures: read along each row in the direction of the frame's language
  (left to right, or right to left for Arabic and Hebrew) and top to bottom, each distinct date once, instead of being
  sorted by date.
- Star a photo on the frame and keep it: a Favorite button in the on-frame controls and on the Everyday page, a star shown
  while a favorite is on screen, and a list saved to the frame that survives restarts, updates and album changes (photos
  not in the current album stay on the list). On a collage the button asks which picture. A Star button setting lets the
  front star button favorite the photo instead of pausing (pausing stays the default). The Debug page shows the list to
  save and a box to restore it.
- Show favorites more often: a Favorites setting (Off, Now and then about 1 photo in 20, Often about 1 in 10). The share is
  the same however many favorites there are, so the controls page says how often each one comes back. Favorites come in
  turn, least recently shown first, spread through the day and never back to back; a starred photo is shown even if it is
  a little soft, and several starred frames of one moment give one slot.
- Dates on the screen follow the frame's language ("March 4, 2021" in English, 2021-03-04 style written the local way in
  Japanese, French and so on) instead of a fixed English pattern, both under a group and on each picture of a collage.
- Remove the Photos per round setting. The frame plans about one awake day at a time: the photos it shows in a day at
  its pace, with a margin for skipped near-identical frames, kept between 300 and 2,000. The plan is rebuilt when the
  date changes, when it runs out, and when the seconds per photo or the sleep schedule are changed, so it follows the
  pace instead of a fixed number.
- Stop showing near-identical frames: from a burst the frame now picks the photos farthest apart in time, treats
  photos taken within two minutes of one chosen as the same picture (the perceptual hash exists for few photos), and
  measures gaps in absolute time so a timezone change cannot split a burst. On a real 15,000-photo album the share of
  shown photos with a twin on the same day fell from about 11% to 1%.
- Add `tools/album_dates.py`, which reads a shared album's capture times (times only, no ids or addresses) so a
  maintainer can run the playback simulation on a real album's shape; see docs/tuning-the-strategy.md.
- Apply the soft-photo gate to every slide, not only collages, with a "Skip blurry photos" choice (off, only
  very blurry at 12%, blurry at 25% which is the default, anything noticeably soft at 40%). Photos it refuses are not drawn for a round, a soft photo that
  reaches the front is passed over, and Diagnostics shows sharpness bands and the sharpness of the photo on
  screen.
- Skip noticeably soft photos in collages. One grey-scale pass over a 256-pixel copy gives a normalised
  Laplacian focus measure plus contrast, brightness and clipping; photos far below the album's own median
  sharpness, or plainly black, blown out or flat, are left out. Scored once per photo, cached in
  `photo-quality.json`; collages only, so pairs and single photos are never hidden.

## 0.5.24-alpha

- Show the active Google Photos shared-album link in Debug connection status, with wrapping for phone screens.
- Keep the link out of copied diagnostics; new setup and legacy sessions show appropriate empty-state text.

## 0.5.23-alpha

- Make shared Google Photos links the supported setup path; remove cookie-import instructions, signed-in album picker and renewal actions.
- Retain existing encrypted sessions and selected albums temporarily, with a migration notice. New imports, including plaintext developer imports, are no longer consumed.
- Keep the old complete catalog until a replacement shared album loads successfully.
- Update installation/recovery guidance and record network-folder/SMB sources on the roadmap; network folders are not implemented yet.

## 0.5.22-alpha

- Remove the on-frame loading label during photo changes. Keep the current image visible until the next one is ready.
- Keep loading status on the private webpage and preserve the 600 ms crossfade.

## 0.5.21-alpha

- Add an optional 600 ms crossfade between photos and collages, enabled by default in Everyday.
- Keep the current photo visible during loading, and fade only when the replacement is ready.
- Retain at most the current and previous image; rapid presses replace any unfinished fade.
- Finish the fade when pausing, sleeping, leaving the activity or switching transitions off.

## 0.5.20-alpha

- Alternate 2-, 3- and 4-photo collages while preserving the saved frequency.
- Choose portrait, landscape and mixed layouts using photo dimensions; keep every full photo visible.
- Preview cycles through all three sizes, and Debug reports the next automatic collage size.
- Decode only one image at a time while composing, with a bounded output size.

## 0.5.19-alpha

- Replace experimental burst playback with optional four-photo collages after every 20 individual photos; adjustable from 5 to 100.
- Keep each photo fully visible, with per-cell blurred backgrounds and optional dates.
- Wait for four cached images before automatic collages; keep individual playback moving while they prepare.
- Preserve pause and slide history, and add a Debug collage preview and cadence status.
- Retain automatic preloading, adjustable photo mix and the full-screen restoration fix.

## 0.5.18-alpha

- Restore immersive full-screen mode on resume and focus, including after returning from Android Settings.
- Require local color/composition similarity and matching framing before a timestamp candidate can animate. Reject uniform frames and obvious scene changes; show the review result in Debug.
- Keep the owner's burst toggle off after the failed timestamp-only preview until visual revalidation.
- Increased version code to 23. Build, 61 unit tests and lint passed. Installed over Wi-Fi; unrelated-candidate rejection and fullscreen after Settings were verified. True-burst visual confirmation is pending.

## 0.5.17-alpha

- Preload upcoming photos using one background worker and a bounded replacement plan. Add Ready/Loading/Queued/Failed states to Debug.
- Add a Recent/Memories percentage slider, recent capture count, and seasonal date window; preserve the 300/14/50 defaults and first-display priority.
- Add automatic optional burst playback for conservative timestamp groups of 3–12 images. Prepare frames from disk at bounded resolution, play chronologically, then hold the final photo at normal resolution.
- Keep manual navigation, pause and scheduled sleep able to interrupt sequences. Preloading pauses while inactive/asleep.
- Increased version code to 22. Build, 56 unit tests and lint passed; installed over Wi-Fi. Live preload/mix/burst interruption and lifecycle checks passed. Owner visual confirmation is pending.

## 0.5.16-alpha

- Rank the recent 300 strictly by capture timestamp, not discovery/upload time. Keep the one-time priority showing for additions and the seasonal memories pool.
- Add regression tests for an old upload entering priority but not displacing recent captures, and for seasonal eligibility.
- Increased version code to 21.

## 0.5.15-alpha

- Advance Plus/Minus on initial press rather than waiting for release; ignore repeats and release duplicates. Star retains short/hold behavior.

- Bound photo work to one active and one latest pending request. Skip obsolete work before decoding/blur, and retain the current photo while loading.
- Add on-frame and web Loading photo status. Shorten image-specific connect/read timeouts without changing album-scan timeouts.
- Retain Previous/Next displayed-photo history across same-album refreshes, replacing temporary URLs and removing deleted photos.
- Increased version code to 20.

## 0.5.14-alpha

- Map verified front Plus/Minus buttons to photo navigation and Star to pause/resume; holding Star opens controls without toggling on release.
- Add persistent on-frame Paused indicator and web pause/resume controls. Keep scheduled sleep and album refresh independent of pause.
- Add a device-generated QR code for the private controls link, Close button, and 30-second overlay timeout.
- Vendor the MIT-licensed Nayuki Java QR encoder with its license notices; no QR network service is used.
- Increased version code to 19.

## 0.5.13-alpha

- Show the frame's own date, time, time-zone ID and UTC offset beside the sleep schedule. Refresh the reading through existing status polling rather than using the phone or laptop clock.
- Increased version code to 18.

## 0.5.12-alpha

- Fill unused photo space with a darkened, blurred copy while retaining the sharp full image. Enabled by default in fit mode; crop mode retains priority.
- Add a blurred-background switch in web and native settings. Blur is prepared on the image worker at a bounded 96-pixel resolution.
- Increased version code to 17.

## 0.5.11-alpha

- Simplify controls styling to neutral charcoal with blue accents, system typography, compact headings, and direct functional labels. Remove promotional copy.
- Keep the Everyday, Setup, and Debug tabs and existing controls unchanged.
- Increased version code to 16.

## 0.5.10-alpha

- Redesigned the private controls page with Everyday, Setup, and Debug & updates tabs, responsive cards, larger touch controls, and keyboard navigation.
- Keep playback and display settings on Everyday; place album setup and HTTPS pairing under Setup.
- Add GitHub repository/release links and a copyable diagnostics summary that excludes private links, keys, cookies, album titles, and raw error messages.
- Preserve background status updates without resetting unsaved display settings or the active tab.
- Increased version code to 15. Existing app data and device certificate are preserved.

## 0.5.9-alpha

- Restore native left/right hand gestures, orientation mapping, Previous navigation and a gesture setting. Owner confirmed both directions.

## 0.5.8-alpha

- Require paired HTTPS with persistent Android Keystore identity; rotate the old HTTP management key once.
- Bound management concurrency and connection lifetime. Update certificate pairing, installer readiness and recovery guidance.

## 0.5.7-alpha

- Explicitly wake a locked display when a settings change leaves an active scheduled-sleep interval, rather than only restoring brightness.
- Capture the old schedule before saving, including when the activity is paused behind the sleeping display. Apply the behavior to web and on-frame settings.
- Preserve ordinary settings saves without unnecessary wake requests; retain existing alarms and schedule semantics.
- Increased version code to 12. Existing album, controls hostname, and settings remain unchanged.


## 0.5.6-alpha

- Poll shared-album initial-page counts every two minutes while the slideshow is awake. Count changes trigger a full refresh; hourly scans remain enabled.
- Queue newly discovered IDs from the same album for first display, including old captures, then return to the normal photo mix. Persist unshown priority IDs across app restarts.
- Show last count check, observed count, check errors and pending priority items on local controls.
- Compare against full-scan metadata count, so unsupported/filtered album entries do not force repeated scans.
- Increased version code to 11. Existing album source, settings, and signing key are preserved.


## 0.5.5-alpha

- Added a bounded, streamed APK-upload endpoint protected by the existing management key and same-origin checks.
- Validate package identity, compatible Android version, installed signing certificate, and non-decreasing version before staging. Android handles confirmation and installation through a narrow FileProvider grant; no silent installation.
- Added install-source permission guidance, cancel/retry/discard controls, and update status on the local page.
- Added a recovery guide to the controls page, docs, and Windows installer ZIP.
- Increased version code to 10. Existing app data is preserved when installing an update.


## 0.5.4-alpha

- Added current source, last refresh success/attempt, refresh errors, and timestamped photo-download errors to local controls, with background status polling.
- Keep on-frame controls hidden during playback; explicit taps reveal them for 15 seconds without photo changes extending the timer.
- Added an IPv4 mDNS hostname with a per-install random suffix, independent of the secret management key. The stable bookmark retains authentication. IP/DHCP-reservation fallback remains available.
- Added bounded DNS parser tests and clean HTTP response shutdown for early form rejection.
- Increased version code to 9. Existing source, session, album, and display settings are preserved.


## 0.5.3-alpha

- Added shared-album links as an anonymous, cookie-free source, with full pagination and atomic index replacement. Signed-in album access remains available.
- Saved the selected source for restart and hourly refresh. Shared-link requests send no account cookies and do not save server cookies.
- Photo HTTP 403 errors request an album-link refresh at most once per five minutes. Cached playback no longer repeatedly opens the error controls over a displayed photo.
- Increased version code to 8; upgrading preserves existing settings and the last complete photo index.
- Shared links and account/session data must never be committed or included in installer packages.


## 0.5.2-alpha

- Local controls submit actions without leaving the page, with button spinners and inline results. Album/setup progress updates without reloading unsaved display settings.
- Lenovo front button LEDs turn off when idle, light on proximity or interaction, and turn off after eight seconds. Uses the existing firmware LED service; no root or extra privileges. Other firmware may lack this service.
- Version code increased to 7. Existing albums, sessions, and settings are preserved on upgrade.
- Build, unit tests, and lint passed. On-frame checks confirmed inline controls and proximity-triggered LED timeout. Full overnight wake validation remains pending.


## Repository maintenance

- Promoted the native 0.5.1-alpha implementation to `main`, preserving both branch histories.
- Retired the unused React/WebView prototype, bundled web assets, old bridge, placeholder layout, and tracked IDE settings.
- Archived prototype planning documents and replaced the current documentation index, architecture, and roadmap.
- This cleanup does not change the maintained app's behavior or version.

## 0.5.1-alpha

- Replaced skipped background wake broadcasts with direct activity alarms.
- Restored explicit daytime brightness and exposed the next scheduled wake in local controls.
- Build, 21 unit tests, and Android lint passed before promotion. Short hardware wake tests passed; full overnight confirmation remains pending.

## 0.5-alpha

- Added guided local Google session import/renewal and private album selection without a prebuilt seed index.

## 0.4-alpha

- Established the standalone native slideshow, private Google Photos album access, local controls, caching, launcher support, and nightly scheduling.

See the [prototype changelog](docs/archive/CHANGELOG-prototype.md) for the retired implementation.
# 0.5.9-alpha — hand gestures

- Restore left-to-next and right-to-previous hand gestures using the frame's existing gesture input device, with no camera access.
- Follow Lenovo's direction mapping across display rotations and apply an 800 ms cooldown.
- Add a gesture on/off setting, last-gesture status and Previous buttons. Gestures pause during sleep and while on-frame controls are visible.
- Track up to 60 actually displayed photos for backward/forward navigation; reset history on app restart or completed album refresh.
- Build, 38 unit tests and lint passed. Owner confirmed both directions on the installed frame; normal settings restored.

# 0.5.8-alpha — security update

- Require HTTPS management with a persistent per-frame Android Keystore identity and explicit certificate pairing.
- Rotate the previously HTTP-exposed controls key once and update Windows/USB setup links.
- Bound request concurrency, aggregate headers and total handshake/header/body lifetime; close active sockets on shutdown.
- Add isolated TLS, slow-client, authentication and legitimate upload regression tests.
- Build, 33 unit tests, lint, and Android 10 HTTPS settings/upload/restart checks passed. The TLS key permissions support Conscrypt's prehashed/raw RSA signing while retaining signing-only key purpose and ECDHE/AES-GCM transport.

