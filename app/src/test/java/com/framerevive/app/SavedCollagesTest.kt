package com.framerevive.app

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SavedCollagesTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun aSavedCollageIsListedFoundAndKeepsItsPicture() {
        val store = SavedCollages(File(temp.root, "c"))
        val saved = store.save(listOf("a", "b", "c"), byteArrayOf(1, 2, 3), 1000L)
        assertEquals(listOf(saved), store.list())
        assertEquals(saved, store.find(listOf("c", "a", "b")))
        assertNull(store.find(listOf("a", "b")))
        assertArrayEquals(byteArrayOf(1, 2, 3), store.picture(saved.id))
    }

    @Test fun savingTheSameCollageTwiceKeepsOneRecord() {
        val store = SavedCollages(File(temp.root, "c"))
        val first = store.save(listOf("a", "b"), byteArrayOf(1), 1L)
        val second = store.save(listOf("b", "a"), byteArrayOf(2), 2L)
        assertEquals(first, second)
        assertEquals(1, store.list().size)
        assertArrayEquals(byteArrayOf(1), store.picture(first.id))
    }

    @Test fun twoCollagesSavedInTheSameMillisecondGetDifferentIds() {
        val store = SavedCollages(File(temp.root, "c"))
        val a = store.save(listOf("a", "b"), byteArrayOf(1), 5L)
        val b = store.save(listOf("c", "d"), byteArrayOf(2), 5L)
        assertNotEquals(a.id, b.id)
        assertArrayEquals(byteArrayOf(2), store.picture(b.id))
    }

    @Test fun theListSurvivesARestartNewestFirst() {
        val dir = File(temp.root, "c")
        SavedCollages(dir).apply { save(listOf("a", "b"), byteArrayOf(1), 10L); save(listOf("c", "d", "e"), byteArrayOf(2), 20L) }
        val again = SavedCollages(dir).apply { load() }
        assertEquals(listOf(listOf("c", "d", "e"), listOf("a", "b")), again.list().map { it.photoIds })
    }

    @Test fun deletingRemovesTheRecordAndThePicture() {
        val dir = File(temp.root, "c")
        val store = SavedCollages(dir)
        val saved = store.save(listOf("a", "b"), byteArrayOf(1), 10L)
        assertTrue(store.delete(saved.id))
        assertFalse(store.delete(saved.id))
        assertTrue(store.list().isEmpty())
        assertNull(store.picture(saved.id))
        assertFalse(File(dir, "${saved.id}.jpg").exists())
        assertTrue(SavedCollages(dir).apply { load() }.list().isEmpty())
    }

    @Test fun aDamagedIndexLineIsSkippedAndAPictureNameCannotEscapeTheFolder() {
        val dir = File(temp.root, "c").apply { mkdirs() }
        File(dir, "index.tsv").writeText("ok1\t1\t0\ta,b\nbroken\n../evil\t1\t0\ta,b\nsolo\t1\t0\ta")
        val store = SavedCollages(dir).apply { load() }
        assertEquals(listOf("ok1"), store.list().map { it.id })
        assertNull(store.picture("../evil"))
    }
}
