package com.framerevive.app

import java.io.File

/** A collage the owner starred: the photos in the order they were laid out, and when it was saved and last shown again. */
data class SavedCollage(val id: String, val savedMs: Long, val lastShownMs: Long, val photoIds: List<String>)

/**
 * Collages the owner saved as favorites, kept in a folder on the frame: an index file and one picture (the JPEG that
 * was on screen) for each. A collage is saved with the photos it was made of, so it can be built again later; the
 * picture is what the owner can download. Nothing here is uploaded.
 *
 * Safe to call from any thread.
 */
class SavedCollages(private val dir: File) {
    private val index = File(dir, "index.tsv")
    private val records = LinkedHashMap<String, SavedCollage>()

    @Synchronized fun load() {
        records.clear()
        val text = runCatching { index.readText() }.getOrNull() ?: return
        for (line in text.lineSequence()) {
            val p = line.split('\t')
            if (p.size != 4 || p[0].isEmpty() || !p[0].all { it.isLetterOrDigit() }) continue
            val ids = p[3].split(',').filter { it.isNotEmpty() }
            if (ids.size < 2) continue
            records[p[0]] = SavedCollage(p[0], p[1].toLongOrNull() ?: 0L, p[2].toLongOrNull() ?: 0L, ids)
        }
    }

    /** Newest first. */
    @Synchronized fun list(): List<SavedCollage> = records.values.sortedByDescending { it.savedMs }

    /** The saved collage made of exactly these photos, if there is one (the order of the photos does not matter). */
    @Synchronized fun find(photoIds: List<String>): SavedCollage? = records.values.firstOrNull { it.photoIds.toSet() == photoIds.toSet() }

    /** Saves the collage with its picture and returns the record; a collage already saved is returned as it is. */
    @Synchronized fun save(photoIds: List<String>, jpeg: ByteArray, now: Long): SavedCollage {
        find(photoIds)?.let { return it }
        dir.mkdirs()
        var id = now.toString(36)
        while (id in records) id += "x"
        val record = SavedCollage(id, now, 0L, photoIds)
        File(dir, "$id.jpg").writeBytes(jpeg)
        records[id] = record
        persist()
        return record
    }

    @Synchronized fun picture(id: String): ByteArray? =
        if (id in records) runCatching { File(dir, "$id.jpg").readBytes() }.getOrNull() else null

    @Synchronized fun delete(id: String): Boolean {
        if (records.remove(id) == null) return false
        File(dir, "$id.jpg").delete()
        persist()
        return true
    }

    private fun persist() {
        val text = records.values.joinToString("\n") { "${it.id}\t${it.savedMs}\t${it.lastShownMs}\t${it.photoIds.joinToString(",")}" }
        Favorites.write(index, text)
    }
}
