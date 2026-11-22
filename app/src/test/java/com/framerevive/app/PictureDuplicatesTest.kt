package com.framerevive.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.MessageDigest

class PictureDuplicatesTest {
    private val day = 24L * 3600 * 1000

    @Test fun md5MatchesTheStandardVectorsAndMessageDigest() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", PictureDuplicates.md5(ByteArray(0)))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", PictureDuplicates.md5("abc".toByteArray()))
        val random = java.util.Random(7)
        for (size in listOf(1, 55, 56, 63, 64, 65, 119, 120, 1000, 100_003)) {
            val bytes = ByteArray(size).also { random.nextBytes(it) }
            val expected = MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals("size $size", expected, PictureDuplicates.md5(bytes))
        }
    }

    @Test fun aPictureIsARepeatOnlyWhenAnotherIdWithTheSameBytesWasShownRecently() {
        val d = PictureDuplicates()
        d.know("main", "aa"); d.know("fav", "aa"); d.know("other", "bb")
        val now = 100 * day
        val shown = mapOf("main" to now - 3 * day, "other" to now - day)
        assertEquals("main", d.recentTwin("fav", { shown[it] }, now))
        assertNull("a different picture is not a repeat", d.recentTwin("other", { shown[it] }, now))
        assertNull("a photo is not its own twin", d.recentTwin("main", { shown[it] }, now))
        assertNull("shown too long ago", d.recentTwin("fav", { shown[it]?.minus(60 * day) }, now))
        assertNull("never shown", d.recentTwin("fav", { null }, now))
        assertNull("unknown bytes", d.recentTwin("new", { shown[it] }, now))
    }

    @Test fun whatIsKnownSurvivesARestart() {
        val d = PictureDuplicates(); d.know("a", "0123456789abcdef0123456789abcdef")
        val file = java.io.File.createTempFile("md5", ".txt").apply { deleteOnExit() }
        file.writeText(d.export() + "bad line\nshort\tabc\n")
        val back = PictureDuplicates().apply { load(file) }
        assertEquals(1, back.known); assertEquals("0123456789abcdef0123456789abcdef", back.md5Of("a"))
    }
}
