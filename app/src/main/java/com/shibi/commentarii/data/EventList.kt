package com.shibi.commentarii.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A dated line on the monthly page. [epochDay] is a LocalDate epoch day. */
data class EventItem(val epochDay: Long, val text: String = "")

/** Payload of the EVENTS block: items kept sorted by date (stable for equal dates). */
data class EventList(val items: List<EventItem> = emptyList()) {

    fun sorted(): EventList = EventList(items.sortedBy { it.epochDay })

    fun toJson(): String {
        val arr = JSONArray()
        for (e in items) arr.put(JSONObject().put("epochDay", e.epochDay).put("text", e.text))
        return JSONObject().put("items", arr).toString()
    }

    /** Event text joined by newlines (for full-text indexing). */
    fun flattenText(): String = items.map { it.text }.filter { it.isNotBlank() }.joinToString("\n")

    companion object {
        val EMPTY = EventList()

        /** Lenient decode: malformed or empty input yields an empty list. */
        fun fromJson(json: String?): EventList {
            if (json.isNullOrBlank()) return EMPTY
            return try {
                val arr = JSONObject(json).optJSONArray("items") ?: return EMPTY
                EventList(
                    (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        EventItem(o.optLong("epochDay"), o.optString("text", ""))
                    },
                )
            } catch (_: JSONException) {
                EMPTY
            }
        }
    }
}
