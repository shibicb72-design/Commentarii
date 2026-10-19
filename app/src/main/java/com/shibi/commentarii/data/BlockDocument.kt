package com.shibi.commentarii.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class BlockType { PARAGRAPH, H1, H2, BULLET, CHECKBOX }

data class TextSpan(val start: Int, val end: Int, val bold: Boolean = true)

data class DocBlock(
    val type: BlockType = BlockType.PARAGRAPH,
    val text: String = "",
    val checked: Boolean = false,
    val spans: List<TextSpan> = emptyList(),
)

data class BlockDocument(val blocks: List<DocBlock> = emptyList()) {

    fun toJson(): String {
        val arr = JSONArray()
        for (b in blocks) {
            val spans = JSONArray()
            for (s in b.spans) {
                spans.put(JSONObject().put("start", s.start).put("end", s.end).put("bold", s.bold))
            }
            arr.put(
                JSONObject()
                    .put("type", b.type.name)
                    .put("text", b.text)
                    .put("checked", b.checked)
                    .put("spans", spans),
            )
        }
        return JSONObject().put("blocks", arr).toString()
    }

    /** All block text joined by newlines (for full-text indexing). */
    fun flattenText(): String = blocks.map { it.text }.filter { it.isNotBlank() }.joinToString("\n")

    companion object {
        val EMPTY = BlockDocument()

        /** Lenient decode: malformed or empty input yields an empty document. */
        fun fromJson(json: String?): BlockDocument {
            if (json.isNullOrBlank()) return EMPTY
            return try {
                val arr = JSONObject(json).optJSONArray("blocks") ?: return EMPTY
                val blocks = (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    val type = runCatching { BlockType.valueOf(o.optString("type", "PARAGRAPH")) }
                        .getOrDefault(BlockType.PARAGRAPH)
                    val spansArr = o.optJSONArray("spans")
                    val spans = if (spansArr == null) {
                        emptyList()
                    } else {
                        (0 until spansArr.length()).mapNotNull { j ->
                            val s = spansArr.optJSONObject(j) ?: return@mapNotNull null
                            TextSpan(s.optInt("start"), s.optInt("end"), s.optBoolean("bold", true))
                        }
                    }
                    DocBlock(type, o.optString("text", ""), o.optBoolean("checked", false), spans)
                }
                BlockDocument(blocks)
            } catch (_: JSONException) {
                EMPTY
            }
        }
    }
}
