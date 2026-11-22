package com.framerevive.app

import java.io.File

/** A star the owner gave a photo here, or one carried over from Google. A vetoed seed stays on record so it is not seeded again. */
data class Favorite(val starredMs: Long, val origin: String = FRAME, val vetoed: Boolean = false) {
    companion object {
        const val FRAME = "frame"
        const val GOOGLE = "google"
    }
}

/** How much of the day favorites get: a share of the slots, the same whether there are 20 favorites or 2,000. */
enum class FavoriteShare(val id: String, val percent: Int) {
    OFF("off", 0), SOMETIMES("some", 5), OFTEN("often", 10);
    companion object {
        val DEFAULT = SOMETIMES
        fun of(id: String?): FavoriteShare = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * The photos the owner has starred, by photo id. Ids that are not in the album right now stay on record: an album
 * switch or a short sync must not cost the owner their list, so nothing here is pruned except by un-starring.
 *
 * Safe to call from any thread. [ids] is a snapshot that playback can read without locking.
 */
class Favorites {
    private val records = LinkedHashMap<String, Favorite>()
    @Volatile var ids: Set<String> = emptySet()
        private set

    val count: Int get() = ids.size
    val fromGoogle: Int @Synchronized get() = records.count { (_, f) -> !f.vetoed && f.origin == Favorite.GOOGLE }

    fun isFavorite(id: String): Boolean = id in ids

    /** Stars the photo, or removes its star. Returns whether it is a favorite afterwards. */
    @Synchronized fun toggle(id: String, now: Long): Boolean {
        val current = records[id]
        val now2 = when {
            current != null && !current.vetoed -> {
                // A seeded star is kept as a veto so the next seed does not bring it back; one given here is simply removed.
                if (current.origin == Favorite.GOOGLE) records[id] = current.copy(vetoed = true) else records.remove(id)
                false
            }
            else -> { records[id] = Favorite(now, Favorite.FRAME); true }
        }
        publish()
        return now2
    }

    /** Adds seeded favorites, skipping ones already on record (including vetoed ones). Returns how many were added. */
    @Synchronized fun seed(seeded: Collection<String>, now: Long): Int {
        var added = 0
        for (id in seeded) if (id !in records) { records[id] = Favorite(now, Favorite.GOOGLE); added++ }
        publish()
        return added
    }

    /** The file format, also used to export and import: one line per photo, tab-separated. */
    @Synchronized fun export(): String {
        val text = StringBuilder(records.size * 96)
        for ((id, f) in records) text.append(id).append('\t').append(f.starredMs).append('\t').append(f.origin).append('\t').append(if (f.vetoed) 1 else 0).append('\n')
        return text.toString()
    }

    /** Merges exported text in. Photos already on record keep their own record. Returns how many were added. */
    @Synchronized fun import(text: String): Int {
        var added = 0
        for ((id, f) in parse(text)) if (id !in records) { records[id] = f; added++ }
        publish()
        return added
    }

    fun load(file: File) {
        val text = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
        import(text)
    }

    private fun publish() { ids = records.filterValues { !it.vetoed }.keys.toSet() }

    companion object {
        /** Lines that are not four fields are skipped, so a damaged file loses a line, never the list. */
        fun parse(text: String): Map<String, Favorite> {
            val out = LinkedHashMap<String, Favorite>()
            for (line in text.lineSequence()) {
                val parts = line.split('\t')
                if (parts.size != 4 || parts[0].isEmpty()) continue
                val origin = if (parts[2] == Favorite.GOOGLE) Favorite.GOOGLE else Favorite.FRAME
                out[parts[0]] = Favorite(parts[1].toLongOrNull() ?: 0L, origin, parts[3] == "1")
            }
            return out
        }

        /** Written beside the target and renamed, so a power cut cannot leave a half-written list. */
        fun write(file: File, text: String) {
            val bytes = text.toByteArray()
            val temp = File(file.parentFile, file.name + ".tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) { file.writeBytes(bytes); temp.delete() }
        }
    }
}

/** Says in plain words what a favorites share means for this many favorites at this pace. */
object FavoriteReport {
    fun sentence(count: Int, percent: Int, slotsPerDay: Double): String {
        if (percent <= 0) return "Favorites are not shown more often."
        if (count <= 0) return ""
        // A list shorter than the day's favorite slots shows each once a day at most.
        val perDay = (slotsPerDay * percent / 100.0).coerceAtMost(count.toDouble()).coerceAtLeast(0.0)
        if (perDay <= 0.0) return ""
        val shown = Math.round(perDay).toInt().coerceAtLeast(1)
        return "About $shown a day; each one comes back ${interval(count / perDay)}."
    }

    private fun interval(days: Double): String = when {
        days < 1.5 -> "about every day"
        days < 14 -> "about every ${Math.round(days)} days"
        days < 60 -> "about every ${Math.round(days / 7)} weeks"
        else -> "about every ${Math.round(days / 30)} months"
    }
}
