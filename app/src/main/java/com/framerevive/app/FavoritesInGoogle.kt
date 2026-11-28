package com.framerevive.app

/**
 * Google is the source of truth for favorites, and the frame reaches it only through the shared "Frame favorites" album it
 * reads. A star set on the frame stays on the frame until the owner adds the photo to that album, so this finds the frame's
 * stars the album does not contain yet, for the controls page to list with a link to each. See
 * docs/favorites-google-and-saved-collages-plan.md.
 */
object FavoritesInGoogle {
    /** A star set on the frame whose photo the favorites album does not contain. */
    class Pending(val photo: FramePhoto, val starredMs: Long)

    /**
     * The photos of [stars] (id and time of the star, newest first) that [favoriteAlbum] does not contain. A photo is in
     * the album when one of its photos has the same content key, Google's identity for an original file, which is the same in
     * every album; a photo without a key falls back to the capture time. A star whose photo is not in [byId] (not in the
     * album now) is left out: there is nothing to open.
     */
    fun pending(stars: List<Pair<String, Long>>, byId: Map<String, FramePhoto>, favoriteAlbum: List<FramePhoto>): List<Pending> {
        if (stars.isEmpty()) return emptyList()
        val keys = favoriteAlbum.filter { it.key.isNotEmpty() }.mapTo(HashSet()) { it.key }
        val times = favoriteAlbum.filter { it.dated }.mapTo(HashSet()) { it.takenMs }
        return stars.mapNotNull { (id, starredMs) ->
            val photo = byId[id] ?: return@mapNotNull null
            val inAlbum = if (photo.key.isNotEmpty()) photo.key in keys else photo.dated && photo.takenMs in times
            if (inAlbum) null else Pending(photo, starredMs)
        }
    }
}
