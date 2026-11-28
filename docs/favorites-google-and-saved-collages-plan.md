# Plan: favorites and Google, and saving a collage as a favorite

Status (2026-11-27): part 1, the list of stars not yet in Google, is built (`FavoritesInGoogle`, a card on the controls page);
parts 2 and 3 are proposed. It follows `favorites-plan.md` (the favorites store, the star button, the
"Frame favorites" album) and `collage-mix-plan.md`. The options were reviewed; where the review was unsure of Google's rules,
the point is marked to verify.

## What the owner wants, and what is settled

- Google Photos stays the ultimate source of truth for favorites, because it works across the owner's other devices.
- Google's side is reached the way it already is, with no account on the frame: the frame reads the "Frame favorites" shared
  album every hour and treats its photos as favorites. The owner stars photos in Google Photos through a link to the photo
  (a real Google favorite) and keeps the favorites in that shared album, which is also how they are exported.
- A collage that is saved as a favorite should come back, as a collage, in the slideshow.

## What cannot be done

As far as is known, Google's Photos API has no way to set the favorite flag on a photo, and since 2025 an app can only create
media items and manage the ones it created. So a star set on the frame cannot become a real Google favorite by itself, and
every automatic route is a stand-in (an album or an uploaded copy). Uploading copies needs a Google sign-in on the frame, a
credential on a shared device, and uses the owner's storage; it is not planned. Adding to a shared album by link without
signing in is not believed to work and is not relied on (to verify before any use).

## Part 1: making the manual step easy

A star set on the frame stays on the frame until the owner adds the photo to the Frame favorites album. Make that a short
list instead of something to remember.

1. **Stars not yet in Google** on the controls page: the frame's own stars that the favorites album does not contain, newest
   first, each with a link that opens the photo in Google Photos (the picture shown now already opens there), so the owner can
   star it and add it to the album from a phone. A star counts as in Google once the favorites album read matches it (by
   the byte copy or the capture time, as the album's photos are already matched), so the list shrinks as the owner works.
2. **Which side wins.** A photo in the Frame favorites album is a favorite; a star set on the frame is provisional until then.
   Un-starring on the frame removes only the frame's star; a photo that is in the album stays a favorite and is removed there
   by the owner (the existing veto rule keeps a photo out of the pool if the owner un-stars a favorite that came from Google).
3. Nothing is uploaded and no credential is stored.

To verify before building: that the link for a shared-album photo shows the star in the owner's Google Photos (the owner
reports the deep link works for starring); how often matching by byte copy or capture time wrongly keeps a star on the list.

## Part 2: a collage as a favorite

- **What is saved.** When the owner stars during a collage (with the Star button set to favorite), the frame saves a recipe, the
  photos of the collage (their ids and the position of each), and the rendered picture (the 1080 by 1920 JPEG it already has in
  memory, about 1 MB). The recipe is what makes it replayable; the picture lets the owner download it and lets the frame show it
  when a photo is no longer available.
- **How it comes back.** When a collage's turn comes up, a saved collage replaces the planned one now and then, the one shown
  longest ago first. It is rarer than a favorite photo: at most one collage slot in ten, scaled by the favorites setting (some,
  often or lots give one in forty, twenty and ten; off gives none), since a saved collage is a special occasion. Its photos
  are downloaded ahead like any collage's, and the layout is fitted again on them. If a photo is missing from the album, the
  saved picture is shown instead, as one slide, until the owner removes it.
- **Where it lives.** On the frame, in its private storage, with an index file (id, date saved, photos, last shown). Saved
  collages are listed on the controls page with a thumbnail, a download link and a delete button. At about 1 MB each, a
  hundred saved collages use about 100 MB; there is no limit for now, as saving a collage is expected to be rare.
- **Google.** A saved collage is a derivative image, not a photo of the album, so it is not uploaded. The owner can download
  the picture from the controls page and put it in Google Photos by hand if they want it there.
- **Its photos.** Saving a collage does not star its photos one by one: "I liked that collage" is not "I liked those five photos".

## Staged plan

1. The list of stars not yet in Google, with the links (built).
2. Saving a collage (recipe and picture) when the owner stars during one, listed on the controls page with download and delete.
3. Replaying saved collages in the collage turns by the favorites share, with the fallback to the saved picture.

## Not done

Signing in to Google on the frame; uploading copies or collages; adding to the shared album automatically; any unofficial
Google request; replaying a saved collage with substituted photos (it falls back to the saved picture instead).

## Decisions

1. ~~How often should saved collages replay?~~ Answered: rarer than a simple favorite. The proposal above (at most one collage
   slot in ten, scaled by the favorites setting) follows that and can be tuned by looking at it on the frame.
2. ~~Shared storage as well?~~ Answered: the controls-page download is enough.
3. ~~A limit on how many are kept?~~ Answered: no limit for now; saving a collage is expected to be rare.
