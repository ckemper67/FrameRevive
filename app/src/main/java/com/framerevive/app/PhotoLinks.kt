package com.framerevive.app

import java.net.URLEncoder

/** What opens a photo of a shared album in Google Photos: the album's page address and its access key. */
data class ShareLink(val base: String, val key: String) {
    val valid: Boolean get() = base.startsWith(PREFIX) && key.isNotBlank()
    companion object { const val PREFIX = "https://photos.google.com/share/" }
}

/** Addresses that open a photo in Google Photos, and the places on the current picture that open each of them. */
object PhotoLinks {
    private val ID = Regex("[A-Za-z0-9_-]+")

    /** A picture's share of the width and height, from 0 to 1, and the photo it shows. */
    data class Tile(val id: String, val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** The photo's page in the shared album, or null when the album or the id does not look right. */
    fun url(share: ShareLink?, id: String): String? {
        if (share == null || !share.valid || !ID.matches(id)) return null
        return "${share.base.trimEnd('/')}/photo/$id?key=${URLEncoder.encode(share.key, "UTF-8")}"
    }

    fun whole(id: String) = listOf(Tile(id, 0f, 0f, 1f, 1f))

    /** The tiles of a collage or pair as shares of its output size, for [panels] that refer to [photos] by index. */
    fun tiles(panels: List<CollageLayout.Panel>, photos: List<FramePhoto>, outWidth: Int, outHeight: Int): List<Tile> =
        panels.map { Tile(photos[it.photo].id, it.box.left / outWidth, it.box.top / outHeight, it.box.right / outWidth, it.box.bottom / outHeight) }

    /** One line per tile that has an address: `left,top,right,bottom,address`. The page opens the one that is clicked. */
    fun lines(tiles: List<Tile>, linkFor: (String) -> String?): String =
        tiles.mapNotNull { t -> linkFor(t.id)?.let { "%.4f,%.4f,%.4f,%.4f,%s".format(java.util.Locale.ROOT, t.left, t.top, t.right, t.bottom, it) } }.joinToString("\n")
}
