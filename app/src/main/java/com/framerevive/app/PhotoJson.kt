package com.framerevive.app

import org.json.JSONObject

/** How a photo is written to and read from a saved album. Fields that are empty are left out, and older files still read. */
object PhotoJson {
    fun write(p: FramePhoto, withDiscovered: Boolean = false): JSONObject {
        val json = JSONObject().put("id", p.id).put("url", p.url).put("takenMs", p.takenMs).put("offsetMs", p.offsetMs)
        if (withDiscovered) json.put("discoveredMs", p.discoveredMs)
        if (p.width > 0 && p.height > 0) json.put("w", p.width).put("h", p.height)
        if (p.key.isNotEmpty()) json.put("k", p.key)
        if (p.video) json.put("v", true)
        if (p.addedMs > 0) json.put("a", p.addedMs)
        return json
    }

    fun read(p: JSONObject): FramePhoto =
        FramePhoto(p.getString("id"), p.getString("url"), p.getLong("takenMs"), p.optLong("offsetMs"), p.optLong("discoveredMs"),
            width = p.optInt("w"), height = p.optInt("h"), key = p.optString("k"), video = p.optBoolean("v"), addedMs = p.optLong("a"))
}
