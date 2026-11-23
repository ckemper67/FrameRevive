package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoLinksTest {
    private val share = ShareLink("https://photos.google.com/share/AF1QipAlbum", "key+/=1")

    @Test fun aPhotoOpensOnItsPageInTheSharedAlbumWithTheKeyEncoded() {
        assertEquals("https://photos.google.com/share/AF1QipAlbum/photo/AF1QipPhoto-1_x?key=key%2B%2F%3D1", PhotoLinks.url(share, "AF1QipPhoto-1_x"))
        assertEquals("a trailing slash on the album does not double up", "https://photos.google.com/share/AF1QipAlbum/photo/p?key=k",
            PhotoLinks.url(ShareLink("https://photos.google.com/share/AF1QipAlbum/", "k"), "p"))
    }

    @Test fun nothingIsBuiltForAnAddressOrIdThatDoesNotLookRight() {
        assertNull(PhotoLinks.url(null, "p"))
        assertNull("another site", PhotoLinks.url(ShareLink("https://evil.example/share/x", "k"), "p"))
        assertNull("no key", PhotoLinks.url(ShareLink("https://photos.google.com/share/x", ""), "p"))
        assertNull("an id that could change the address", PhotoLinks.url(share, "p/../../x?y=1"))
        assertNull(PhotoLinks.url(share, ""))
    }

    @Test fun theTilesOfACollageBecomeSharesOfThePictureAndOneLineEach() {
        val photos = listOf(FramePhoto("a", "u", 1), FramePhoto("b", "u", 1))
        val panels = listOf(CollageLayout.Panel(1, CollageLayout.Box(0f, 0f, 100f, 200f)), CollageLayout.Panel(0, CollageLayout.Box(100f, 0f, 400f, 200f)))
        val tiles = PhotoLinks.tiles(panels, photos, 400, 200)
        assertEquals(listOf("b" to 0.25f, "a" to 1.0f), tiles.map { it.id to it.right })
        val text = PhotoLinks.lines(tiles) { "https://photos.google.com/x/$it" }
        assertEquals(listOf("0.0000,0.0000,0.2500,1.0000,https://photos.google.com/x/b", "0.2500,0.0000,1.0000,1.0000,https://photos.google.com/x/a"), text.lines())
    }

    @Test fun aTileWithoutAnAddressIsLeftOutAndAWholePictureCoversEverything() {
        assertEquals("", PhotoLinks.lines(PhotoLinks.whole("a")) { null })
        assertTrue(PhotoLinks.lines(PhotoLinks.whole("a")) { "u" }.startsWith("0.0000,0.0000,1.0000,1.0000,u"))
    }
}
