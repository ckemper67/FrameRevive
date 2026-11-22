package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FavoritesTest {
    private fun file(): File = File.createTempFile("favorites", ".tsv").apply { delete(); deleteOnExit() }

    @Test fun startingAndTogglingAgainStarsThenUnstars() {
        val f = Favorites()
        assertTrue(f.toggle("a", 1000)); assertTrue(f.isFavorite("a")); assertEquals(1, f.count)
        assertFalse(f.toggle("a", 2000)); assertFalse(f.isFavorite("a")); assertEquals(0, f.count)
        assertEquals("a star given here leaves no record when removed", "", f.export())
    }

    @Test fun theListSurvivesARestartAndKeepsPhotosThatAreNotInTheAlbum() {
        val file = file(); val f = Favorites()
        f.toggle("in-album", 1000); f.toggle("other-album", 2000)
        Favorites.write(file, f.export())
        val after = Favorites().apply { load(file) }
        assertEquals(setOf("in-album", "other-album"), after.ids)
        assertEquals("the file is the whole list, nothing prunes by album", f.export(), after.export())
    }

    @Test fun anUnstarredSeedIsVetoedAndStaysOutWhenSeededAgain() {
        val f = Favorites()
        assertEquals(2, f.seed(listOf("g1", "g2"), 1000)); assertEquals(2, f.fromGoogle)
        assertFalse(f.toggle("g1", 2000))
        assertEquals(setOf("g2"), f.ids); assertEquals(1, f.fromGoogle)
        assertEquals("the vetoed one is not seeded again, the new one is", 1, f.seed(listOf("g1", "g2", "g3"), 3000))
        assertEquals(setOf("g2", "g3"), f.ids)
        assertTrue("starring it again here brings it back as the owner's own", f.toggle("g1", 4000))
        assertEquals(setOf("g1", "g2", "g3"), f.ids); assertEquals(2, f.fromGoogle)
    }

    @Test fun importMergesAndKeepsWhatIsAlreadyOnRecord() {
        val a = Favorites().apply { toggle("x", 1000); toggle("y", 2000) }
        val b = Favorites().apply { toggle("y", 9000); toggle("z", 3000) }
        assertEquals(1, b.import(a.export()))
        assertEquals(setOf("x", "y", "z"), b.ids)
        assertTrue("b's own record of y wins", b.export().contains("y\t9000\tframe\t0"))
        assertEquals("importing twice adds nothing", 0, b.import(a.export()))
    }

    @Test fun aDamagedFileLosesLinesNotTheList() {
        val text = "good\t1000\tframe\t0\nbroken line\n\t5\tframe\t0\ntoo\tfew\nalso-good\tnot-a-number\tgoogle\t0\n"
        val f = Favorites(); assertEquals(2, f.import(text))
        assertEquals(setOf("good", "also-good"), f.ids)
        val missing = Favorites().apply { load(File("/nonexistent/favorites.tsv")) }
        assertEquals(0, missing.count)
    }

    @Test fun theIdSnapshotIsStableWhileTheListChanges() {
        val f = Favorites(); f.toggle("a", 1)
        val before = f.ids
        f.toggle("b", 2)
        assertEquals("a reader holding the old snapshot is not disturbed", setOf("a"), before)
        assertEquals(setOf("a", "b"), f.ids)
    }
}
