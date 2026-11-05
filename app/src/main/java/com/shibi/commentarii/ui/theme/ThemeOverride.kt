package com.shibi.commentarii.ui.theme

import org.json.JSONObject

/** Bundled title fonts a user override may pick. Persisted by name, never by resource id. */
enum class ThemeFont { PATRICK_HAND, PACIFICO, SNIGLET, DANCING }

/**
 * A theme's user edits as a flat string map (docs/plans/theme-customize-plan.md section 3.4). Per-mode keys carry an
 * `l.` or `d.` prefix. Known keys are validated on the way in and bad entries dropped alone; unknown keys are kept so a
 * rewrite does not lose data from a newer app. Applied to a theme by [customized].
 */
class ThemeOverride private constructor(val entries: Map<String, String>) {

    fun isEmpty(): Boolean = entries.isEmpty()

    operator fun get(key: String): String? = entries[key]

    /** Sets [key] to [value] if the pair is valid (or the key is unknown); an invalid pair returns this unchanged. */
    fun with(key: String, value: String): ThemeOverride {
        val clean = ThemeOverrideCodec.normalize(key, value) ?: return this
        return ThemeOverride(entries + (key to clean))
    }

    /** Stores [argb] as an opaque colour (alpha forced to FF). */
    fun withColor(key: String, argb: Long): ThemeOverride =
        with(key, "%06X".format(argb and 0xFFFFFFL).let { "FF$it" })

    fun without(key: String): ThemeOverride = if (key in entries) ThemeOverride(entries - key) else this

    /** The colour at [key] as 0xAARRGGBB, or null when absent or unreadable. */
    fun color(key: String): Long? = entries[key]?.let { ThemeOverrideCodec.parseColor(it) }

    companion object {
        val Empty = ThemeOverride(emptyMap())
        internal fun of(entries: Map<String, String>) = ThemeOverride(entries)
    }
}

object ThemeOverrideCodec {
    const val VERSION = 1
    private const val VERSION_KEY = "v"
    private const val MAX_INDEX = 7
    private const val MOOD_COUNT = 5

    private val colorBases = setOf("bg", "onBg", "primary", "onPrimary", "card", "outlineVariant", "onTab", "ink", "outline", "paper", "cardEdge", "sky")
    private val indexedColorBases = mapOf(
        "tab" to MAX_INDEX, "accent" to MAX_INDEX, "title" to MAX_INDEX,
        // Mood tints apply; a stored onMood round-trips but the resolver derives the number colour.
        "mood" to MOOD_COUNT - 1, "onMood" to MOOD_COUNT - 1,
    )
    private val floatBases = setOf("decoAlpha")

    private val enumKeys: Map<String, (String) -> Boolean> = mapOf(
        "titleFont" to { v -> enumOk<ThemeFont>(v) },
        "titleColoring" to { v -> enumOk<TitleColoring>(v) },
        "labelStyle" to { v -> enumOk<LabelStyle>(v) },
        "titleTrail" to { v -> enumOk<Glyph>(v) },
        "labelTrail" to { v -> enumOk<Glyph>(v) },
        "check" to { v -> enumOk<Glyph>(v) },
        "paper" to { v -> enumOk<Paper>(v) },
        "headerStyle" to { v -> enumOk<HeaderStyle>(v) },
    )

    private inline fun <reified E : Enum<E>> enumOk(v: String) = enumValues<E>().any { it.name == v }

    /** Opaque colour as `FFRRGGBB` -> 0xAARRGGBB, else null. */
    fun parseColor(s: String): Long? {
        if (s.length != 8 || !s.startsWith("FF", ignoreCase = true)) return null
        return s.toLongOrNull(16)
    }

    private fun modeBase(key: String): String? =
        if (key.length > 2 && (key.startsWith("l.") || key.startsWith("d."))) key.substring(2) else null

    /** The cleaned value for a known key, [value] itself for an unknown key, or null when a known key's value is bad. */
    internal fun normalize(key: String, value: String): String? {
        if (key == VERSION_KEY) return null
        enumKeys[key]?.let { return value.takeIf(it) }
        when (key) {
            "tape" -> return value.takeIf { it == "true" || it == "false" }
            "bullets" -> {
                val parts = value.split(',')
                return value.takeIf { parts.size in 1..MAX_INDEX + 1 && parts.all { enumOk<Glyph>(it) } }
            }
        }
        val base = modeBase(key) ?: return value
        if (base in colorBases) return parseColor(value)?.let { "FF" + value.substring(2).uppercase() }
        if (base in floatBases) {
            val f = value.toFloatOrNull() ?: return null
            return value.takeIf { f in 0f..1f }
        }
        val dot = base.indexOf('.')
        if (dot > 0) {
            val max = indexedColorBases[base.substring(0, dot)] ?: return value
            val idx = base.substring(dot + 1).toIntOrNull() ?: return null
            if (idx !in 0..max) return null
            return parseColor(value)?.let { "FF" + value.substring(2).uppercase() }
        }
        return value
    }

    /** Parses stored JSON. Null means stock: unparseable text, no object, or a schema newer than [VERSION]. */
    fun decode(json: String?): ThemeOverride? {
        if (json.isNullOrBlank()) return null
        val obj = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val v = obj.opt(VERSION_KEY)
        val version = (v as? Number)?.toInt() ?: (v as? String)?.toIntOrNull() ?: return null
        if (version < 1 || version > VERSION) return null
        val out = linkedMapOf<String, String>()
        for (key in obj.keys()) {
            if (key == VERSION_KEY) continue
            val raw = obj.opt(key)
            if (raw == null || raw == JSONObject.NULL) continue
            normalize(key, raw.toString())?.let { out[key] = it }
        }
        return ThemeOverride.of(out)
    }

    fun encode(o: ThemeOverride): String {
        val obj = JSONObject().put(VERSION_KEY, VERSION)
        o.entries.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }
}
