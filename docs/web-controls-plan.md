# Controls page: a rethink

Written 2026-11-26. A proposal for review. Nothing has been changed yet.

## What is wrong today

The controls page has three tabs: Everyday, Setup, Debug and updates.

- **Everyday is far too long.** It holds about 25 settings in five cards: the picture and its buttons, Playback and display (9 settings), Sleep schedule (5, which includes hand gestures and the star button), Holiday decorations (1), and Photo mix (10). On a phone that is several screens of scrolling before the reader finds what they came for.
- **The tabs mix kinds of task.** The frequent job (look at the picture, go next, favorite) sits above rarely used tuning (memory window, preloading, collage themes).
- **Related things are apart.** Favorites live in three places: the button and summary on Everyday, the share setting in Photo mix, and the album link and backup in Setup. "Preview collage" is under Debug while the collage settings are under Everyday. Hand gestures and the star button, which are about the frame's hardware, sit inside "Sleep schedule".
- **Names describe the code, not the task.** "Photo mix", "Memories window (+/- days)", "Favor newer photos" and "Skip blurry photos" need the reader to know how the frame chooses pictures.
- **One big form.** Everything on Everyday saves with one button, so changing the star button means scrolling past the whole mix and risking a stray edit elsewhere.

## Who uses it and for what

| Job | How often | Today |
|---|---|---|
| See what is on the frame, go next or back, pause, favorite, open it in Google Photos | Often | Top of Everyday |
| Check the frame is healthy, refresh the album | Sometimes | Top of Everyday and Debug |
| Brightness, seconds per photo, sleep times | Now and then | Everyday, middle |
| How pictures look: fill, blur, dates, pairs, collages | Rarely | Everyday, middle and bottom |
| What it chooses: recent vs memories, favorites share | Rarely | Everyday, bottom |
| Connect or change an album, favorites album | Once | Setup |
| Update the app, read diagnostics, pairing help | Rarely | Debug |

The page should make the first row effortless and keep the last rows out of the way.

## Proposed pages

Five tabs: **Now**, **Display**, **Mix**, **Albums**, **Advanced**. The tab bar fits one phone width.

### 1. Now (the picture controls)

The page the owner lands on. It should fit one phone screen with no scrolling.

- The health line (only expands when something needs attention).
- The picture, its caption, and the Google Photos link on click.
- One row: Previous, Pause or Resume, Next, Favorite.
- Playback state ("Playing", "Loading photo").
- Refresh album, and the favorites summary line ("12 favorites. Shown 3 today.").
- No settings, no Save button.

```
+-------------------------------+
| Working normally              |
+-------------------------------+
|        [ the picture ]        |
|  March 4, 2021                |
| [Prev] [Pause] [Next] [*Fav]  |
| Playing - 12 favorites        |
| Refresh album                 |
+-------------------------------+
```

### 2. Display (how the frame looks and behaves)

All the settings about the screen and the box itself, in three short cards so the page can be scanned:

- **Pictures:** seconds per photo, fill the screen, blurred background, gentle transitions, show pairs, photo dates and where they go, holiday decorations.
- **Screen and sleep:** daytime brightness, use ambient light sensor for brightness, nightly sleep schedule with its two times.
- **Buttons and gestures:** hand gestures, star button.

About 15 settings in all, but grouped, with one Save for the page. If it is still too long, "Buttons and gestures" and "Holiday decorations" can fold behind a "More" disclosure.

### 3. Mix (what the frame chooses to show)

The curation settings, written as questions the owner can answer:

- **How much is new:** recent photos vs memories, the memories window.
- **Favorites:** how often (the share). The setting moves here from three places, next to the favorites summary.
- **Collages:** on or off, how often, the themes. "Preview collage" moves here from Debug.
- **Fine tuning** (folded): favor newer photos, skip blurry photos, automatic preloading.
- The "Your album" card (photo counts, how the mix plays out) sits at the top, as it explains the settings below it.

### 4. Albums

Everything about where pictures come from, today split between Setup and the top of Everyday.

- Connect or switch the shared album.
- The favorites album: link, check, summary.
- Favorites backup and restore.
- Last refreshed, refresh album, album errors.

### 5. Advanced

For the rare job and for support.

- Connection and activity, diagnostics and the copy button.
- App update.
- Controls address and HTTPS pairing help.
- Project and downloads.

## Behavior to keep or change

- **Deep links and bookmarks.** Old addresses use `#everyday`, `#setup` and `#debug`. Keep them working: `#everyday` opens Now, `#setup` opens Albums, `#debug` opens Advanced.
- **Saving.** One Save per page, in a bar at the bottom of that page, shown only when something changed. Now has none. Display and Mix each have one. Albums keeps its own buttons (Connect, Check again).
- **Safety of the split.** The server today reads some fields as always present (for example it turns the sleep schedule off when the checkbox is missing from the form). Splitting one form into several would silently reset anything not on the page being saved. So each form must carry a marker field saying which section it is, and the server must only touch the fields of that section. Several settings already work this way (`transitionSettings`, `pairSettings`, `ambientSettings`); the older ones (seconds, brightness, schedule, crop, date, sleep times) do not.
- **Names.** Rename the settings in plain words: "What to show" style headings, the memories window as "How far back memories reach", and so on. Wording can follow after the move.
- **The health line.** Keep it at the top of Now. On the other pages show a one-line version only when something needs attention.

## Open questions

1. **Five tabs or four?** Display and Mix could be one "Settings" tab with two sections, but that brings back a long page.
2. **Where does brightness live?** It is a screen setting (Display), but the owner may adjust it often and want it on Now. A single slider on Now could mirror it.
3. **Do Buttons and gestures deserve their own tab?** Probably not, but they are the odd ones out on Display.
4. **Auto-save?** Toggles could save the moment they change, removing the Save bar. That is nicer on a phone, but it changes the sleep times and number fields, which need a deliberate Save.
5. **Names for the tabs.** "Now" and "Advanced" feel right. "Display" and "Mix" are the weakest; "Look" and "What to show" are alternatives.

## Plan of work

1. **Move without changing behavior.** Split the one page into the five tabs, keep every field name, add section markers to each form, and make the server save by section. Add the old-address redirects. Update the web tests to match, including a test that saving one section never changes another.
2. **Make the Now page fit.** Lay out the picture and the one row of buttons for a phone, and check it at 360 px wide.
3. **Wording.** Rename the settings and group the fine tuning behind "More".
4. **Polish.** Save bar that appears on change, a one-line health notice on every tab, and a check on a real phone and the frame.

Each step ships on its own and leaves a working page.

## Where it lives

This redesign is for this fork (`development`), not for upstream. Upstream documents a three-tab page (README, `docs/web-controls.md`, screenshots), so a reorganization would change what its owners see and need new documents and screenshots. See "What to offer upstream, and in what order" in `upstreaming-plan.md`.

- The section-aware save (commit `2af4f9c`) is safe to send upstream: it changes no behavior for their page.
- If picture controls go upstream, they come as a "Now showing" card at the top of Everyday, inside the three tabs.
- A tab redesign can be offered upstream later, as an issue with phone screenshots, if they want it.
- In the fork, build it after the stack is stable, so it does not conflict with the series.
