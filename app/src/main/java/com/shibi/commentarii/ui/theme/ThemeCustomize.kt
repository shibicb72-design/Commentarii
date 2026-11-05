package com.shibi.commentarii.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Background polarity (plan 3.5, trap 5): light mode needs luminance at least this, ... */
internal const val LIGHT_MIN_LUMINANCE = 0.5f

/** ... and dark mode at most this, so light status-bar icons still read. */
internal const val DARK_MAX_LUMINANCE = 0.18f

internal fun contrastOf(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return maxOf(la, lb) / minOf(la, lb)
}

/** Black or white, whichever contrasts more with [against]; always at least 4.58:1. */
internal fun bestExtreme(against: Color): Color =
    if (contrastOf(Color.White, against) >= contrastOf(Color.Black, against)) Color.White else Color.Black

/** [color] itself when it reads at [min] on [against], else moved toward black or white just far enough. */
internal fun ensureContrast(color: Color, against: Color, min: Float): Color =
    if (contrastOf(color, against) >= min) color else readableOn(against, color, bestExtreme(against), min + 0.05f)

internal fun polarityOk(c: Color, dark: Boolean): Boolean =
    if (dark) c.luminance() <= DARK_MAX_LUMINANCE else c.luminance() >= LIGHT_MIN_LUMINANCE

/**
 * The effective theme: [this] with the user's colour edits in [o] applied (docs/plans/theme-customize-plan.md 3.2, 3.5).
 * No override returns the same instance. Every edit is made readable here, so no stored value can give an unreadable app.
 * Non-colour keys (fonts, glyphs, paper, tape, decoAlpha, ...) are not applied yet.
 */
fun AppTheme.customized(o: ThemeOverride?): AppTheme {
    if (o == null || o.isEmpty()) return this
    val a = art
    val l = patchMode(id, light, a?.light, dark = false, o = o, p = "l.")
    val d = patchMode(id, dark, a?.dark, dark = true, o = o, p = "d.")
    val newArt = a?.let {
        ThemeArt(
            it.titleFont, it.titleColoring, it.titleTrail, it.labelStyle, it.labelFont, it.labelTrail, it.bullets, it.check,
            it.paper, it.tape, it.cardSalt, checkNotNull(l.art), checkNotNull(d.art),
        )
    }
    return AppTheme(
        id, nameRes, descriptionRes, l.mode, d.mode, titleFont, tabShape, cardShape, headerStyle, decoration, animated,
        periodMillis, staticPhase, scene, staticTimeSec, newArt,
    )
}

private class Patched(val mode: ThemeMode, val art: ArtColors?)

@Suppress("LongMethod", "CyclomaticComplexMethod")
private fun patchMode(id: String, mode: ThemeMode, art: ArtColors?, dark: Boolean, o: ThemeOverride, p: String): Patched {
    val palette = id != AppThemes.DEFAULT_ID
    fun opaque(key: String): Color? = o.color(p + key)?.let { Color(it).copy(alpha = 1f) }

    var s = mode.scheme
    val e = mode.extras

    // bg / onBg -> shared surface group, then primary re-checked.
    val bgEdit = opaque("bg")?.takeIf { polarityOk(it, dark) }
    val onBgEdit = opaque("onBg")
    val surfacesMoved = bgEdit != null || onBgEdit != null
    val bg = bgEdit ?: s.background
    var onBg = s.onBackground
    if (surfacesMoved) {
        onBg = ensureContrast(onBgEdit ?: onBg, bg, 4.5f)
        s = s.withSurfaceGroup(bg, onBg, palette)
        val osv = ensureContrast(s.onSurfaceVariant, s.surfaceContainerHigh, 4.5f)
        s = s.copy(onSurfaceVariant = osv, secondary = if (palette) osv else s.secondary)
    }

    // primary -> surfaceTint, tertiary, inversePrimary; onPrimary against it.
    val primaryEdit = opaque("primary")
    if (primaryEdit != null || surfacesMoved) {
        val surf = s.surfaceContainerHigh
        // A stock primary that still reads stays untouched: readableOn goes through a lerp, which can shift a channel.
        if (primaryEdit != null || contrastOf(s.primary, surf) < 4.5f) {
            val primary = ensureContrast(readableOn(surf, primaryEdit ?: s.primary, onBg), surf, 4.5f)
            s = s.copy(primary = primary, surfaceTint = primary, tertiary = primary, inversePrimary = primary)
        }
    }
    val onPrimaryEdit = opaque("onPrimary")
    if (onPrimaryEdit != null || s.primary != mode.scheme.primary) {
        val op = ensureContrast(onPrimaryEdit ?: s.onPrimary, s.primary, 4.5f)
        // Palette themes pair onTertiary with onPrimary; Rose & Cream's only follows once its tertiary has actually moved.
        s = s.copy(onPrimary = op, onTertiary = if (palette || s.tertiary != mode.scheme.tertiary) op else s.onTertiary)
    }
    opaque("outlineVariant")?.let { s = s.copy(outlineVariant = it) }

    // tabs / onTab -> containers on palette themes. One onTab serves every tab, so the tab moves.
    var tabs = e.tabs
    var onTab = e.onTab
    val onTabEdit = opaque("onTab")
    val tabEdits = tabs.indices.map { opaque("tab.$it") }
    if (onTabEdit != null || tabEdits.any { it != null }) {
        onTab = onTabEdit ?: e.onTab
        tabs = tabs.mapIndexed { i, t -> ensureContrast(tabEdits[i] ?: t, onTab, 4.5f) }
        if (palette) {
            s = s.copy(
                primaryContainer = tabs[0], onPrimaryContainer = onTab,
                secondaryContainer = tabs[1 % tabs.size], onSecondaryContainer = onTab,
                tertiaryContainer = tabs[2 % tabs.size], onTertiaryContainer = onTab,
            )
        }
    }

    // card -> surfaceContainerLowest; onCard against it (palette themes derive onCard from onBg).
    val cardEdit = opaque("card")?.takeIf { polarityOk(it, dark) }
    val card = cardEdit ?: e.card
    var onCard = if (palette && surfacesMoved) onBg else e.onCard
    if (cardEdit != null) s = s.copy(surfaceContainerLowest = card)
    if (cardEdit != null || surfacesMoved) onCard = ensureContrast(onCard, card, 4.5f)

    // Mood tints are editable; the number colour is derived, never stored.
    val moodEdits = e.mood.indices.map { opaque("mood.$it") }
    val mood = e.mood.mapIndexed { i, m -> moodEdits[i] ?: m }
    val onMood = e.onMood.mapIndexed { i, om -> if (moodEdits[i] != null) ensureContrast(om, mood[i], 4.5f) else om }

    val extras = CommentariiColors(
        tab = tabs[0],
        onTab = onTab,
        sage = if (palette) tabs[1 % tabs.size] else e.sage,
        onSage = if (palette) onTab else e.onSage,
        card = card,
        onCard = onCard,
        mood = mood,
        onMood = onMood,
        tabs = tabs,
    )

    val newArt = art?.let { a ->
        val inkEdit = opaque("ink")
        val accentEdits = a.accents.indices.map { opaque("accent.$it") }
        val ink = inkEdit ?: a.ink
        val accents = if (inkEdit != null || accentEdits.any { it != null }) {
            a.accents.mapIndexed { i, c -> ensureContrast(accentEdits[i] ?: c, ink, 4.5f) }
        } else {
            a.accents
        }
        val titleEdits = a.title.indices.map { opaque("title.$it") }
        // An outlined title reads through its outline (checked below), so its fills keep the stock pastels, as ThemeContrastTest allows.
        val title = when {
            a.outline != null -> a.title.mapIndexed { i, c -> titleEdits[i] ?: c }
            bgEdit != null || titleEdits.any { it != null } ->
                a.title.mapIndexed { i, c -> ensureContrast(titleEdits[i] ?: c, s.background, 3f) }
            else -> a.title
        }
        val outlineEdit = opaque("outline")
        val outline = a.outline?.let { stock ->
            if (outlineEdit != null || bgEdit != null) ensureContrast(outlineEdit ?: stock, s.background, 4.5f) else stock
        }
        ArtColors(
            accents = accents,
            ink = ink,
            title = title,
            outline = outline,
            paper = opaque("paper") ?: a.paper,
            decoAlpha = a.decoAlpha,
            cardEdge = a.cardEdge?.let { opaque("cardEdge") ?: it },
            sky = a.sky?.let { opaque("sky") ?: it },
        )
    }
    return Patched(ThemeMode(s, extras, mode.titleBrush), newArt)
}
