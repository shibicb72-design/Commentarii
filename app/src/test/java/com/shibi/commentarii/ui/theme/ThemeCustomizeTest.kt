package com.shibi.commentarii.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ROSE = "rose_cream"
private const val CLOUD = "cloud_drift"
private val MODES = listOf(false, true)

private fun prefix(dark: Boolean) = if (dark) "d." else "l."
private fun modeName(dark: Boolean) = if (dark) "dark" else "light"
private fun AppTheme.mode(dark: Boolean) = if (dark) this.dark else light
private fun AppTheme.artColors(dark: Boolean) = art?.let { if (dark) it.dark else it.light }
private fun Color.hex() = "%08X".format(toArgb())
private fun Color?.hexOrNone() = this?.hex() ?: "none"
private fun Color.long() = toArgb().toLong() and 0xFFFFFFFFL

private val schemeGetters = ColorScheme::class.java.declaredMethods
    .filter { it.parameterCount == 0 && it.returnType == java.lang.Long.TYPE && it.name.startsWith("get") }

private fun schemeRoles(s: ColorScheme): Map<String, String> = schemeGetters.associate { m ->
    val name = m.name.removePrefix("get").substringBefore('-').replaceFirstChar { it.lowercase() }
    "scheme.$name" to Color((m.invoke(s) as Long).toULong()).hex()
}

private fun extrasRoles(e: CommentariiColors): Map<String, String> = buildMap {
    put("extras.tab", e.tab.hex())
    put("extras.onTab", e.onTab.hex())
    put("extras.sage", e.sage.hex())
    put("extras.onSage", e.onSage.hex())
    put("extras.card", e.card.hex())
    put("extras.onCard", e.onCard.hex())
    e.mood.forEachIndexed { i, c -> put("extras.mood.$i", c.hex()) }
    e.onMood.forEachIndexed { i, c -> put("extras.onMood.$i", c.hex()) }
    e.tabs.forEachIndexed { i, c -> put("extras.tabs.$i", c.hex()) }
}

private fun artRoles(a: ArtColors?): Map<String, String> = if (a == null) emptyMap() else buildMap {
    a.accents.forEachIndexed { i, c -> put("art.accents.$i", c.hex()) }
    put("art.ink", a.ink.hex())
    a.title.forEachIndexed { i, c -> put("art.title.$i", c.hex()) }
    put("art.outline", a.outline.hexOrNone())
    put("art.paper", a.paper.hex())
    put("art.decoAlpha", a.decoAlpha.toString())
    put("art.cardEdge", a.cardEdge.hexOrNone())
    put("art.sky", a.sky.hexOrNone())
}

/** Every colour of one mode by name: all ColorScheme roles, CommentariiColors and ArtColors. */
private fun roles(t: AppTheme, dark: Boolean): Map<String, String> =
    schemeRoles(t.mode(dark).scheme) + extrasRoles(t.mode(dark).extras) + artRoles(t.artColors(dark))

private fun changed(stock: AppTheme, out: AppTheme, dark: Boolean): Map<String, String> {
    val before = roles(stock, dark)
    val after = roles(out, dark)
    assertEquals("${stock.id}: resolved roles differ in shape", before.keys, after.keys)
    return before.filter { (k, v) -> after[k] != v }.mapValues { (k, v) -> "$v->${after[k]}" }
}

private fun ratio(a: Color, b: Color): Float {
    val l1 = maxOf(a.luminance(), b.luminance())
    val l2 = minOf(a.luminance(), b.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
}

/** Every plan 3.5 pair plus ThemeContrastTest's extras, for one resolved mode. */
private fun pairFailures(t: AppTheme, dark: Boolean): List<String> {
    val out = mutableListOf<String>()
    fun need(label: String, a: Color, b: Color, min: Float) {
        val r = ratio(a, b)
        if (r < min) out += "$label %.2f < $min (%s on %s)".format(r, a.hex(), b.hex())
    }
    val m = t.mode(dark)
    val s = m.scheme
    val e = m.extras
    need("primary/surfaceContainerHigh", s.primary, s.surfaceContainerHigh, 4.5f)
    need("onPrimary/primary", s.onPrimary, s.primary, 4.5f)
    need("onBg/bg", s.onBackground, s.background, 4.5f)
    need("onSurfaceVariant/surfaceContainerHigh", s.onSurfaceVariant, s.surfaceContainerHigh, 4.5f)
    need("onCard/card", e.onCard, e.card, 4.5f)
    need("onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer, 4.5f)
    need("onTertiary/tertiary", s.onTertiary, s.tertiary, 4.5f)
    e.tabs.forEachIndexed { i, tab -> need("onTab/tabs[$i]", e.onTab, tab, 4.5f) }
    e.mood.forEachIndexed { i, mood -> need("onMood[$i]/mood[$i]", e.onMood[i], mood, 4.5f) }
    t.artColors(dark)?.let { a ->
        a.accents.forEachIndexed { i, c -> need("ink/accents[$i]", a.ink, c, 4.5f) }
        // Same logic as ThemeContrastTest: an outlined title is carried by its outline, not its fill.
        if (a.outline != null) {
            need("outline/bg", a.outline, s.background, 4.5f)
        } else {
            a.title.forEachIndexed { i, c -> need("title[$i]/bg", c, s.background, 3f) }
        }
    }
    return out
}

private fun polarityFailures(t: AppTheme, dark: Boolean): List<String> {
    val m = t.mode(dark)
    fun bad(c: Color) = if (dark) c.luminance() > 0.18f else c.luminance() < 0.5f
    return buildList {
        if (bad(m.scheme.background)) add("background ${m.scheme.background.hex()} has the wrong polarity")
        if (bad(m.extras.card)) add("card ${m.extras.card.hex()} has the wrong polarity")
    }
}

private fun assertNoFailures(failures: List<String>) {
    assertTrue("${failures.size} failures:\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
}

/** Pure JVM: the resolver only needs the override's map, not org.json. See [ThemeCustomizeTestCodec] for stored JSON. */
class ThemeCustomizeTest {
    private val surfaceGroup = listOf(
        "background", "onBackground", "surface", "onSurface", "surfaceVariant", "onSurfaceVariant", "inverseSurface", "inverseOnSurface",
        "outline", "surfaceBright", "surfaceDim", "surfaceContainerLow", "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest",
    ).map { "scheme.$it" }.toSet()
    private val primaryGroup =
        listOf("primary", "surfaceTint", "tertiary", "inversePrimary", "onPrimary", "onTertiary").map { "scheme.$it" }.toSet()
    private val containers = listOf(
        "primaryContainer", "onPrimaryContainer", "secondaryContainer", "onSecondaryContainer", "tertiaryContainer", "onTertiaryContainer",
    ).map { "scheme.$it" }.toSet()

    private fun AppTheme.isPalette() = id != ROSE

    private fun nudged(c: Color, by: Int = 4): Long {
        fun ch(v: Float): Long {
            val i = (v * 255f + 0.5f).toInt()
            return (if (i > 127) i - by else i + by).toLong()
        }
        return 0xFF000000L or (ch(c.red) shl 16) or (ch(c.green) shl 8) or ch(c.blue)
    }

    /** A colour that reads on [against] and is not any stock value. */
    private fun pickReadable(against: Color): Long =
        listOf(0xFFF5E6D3L, 0xFF14202CL).maxBy { ratio(Color(it), against) }

    private fun MutableList<String>.isolation(
        label: String, stock: AppTheme, dark: Boolean, o: ThemeOverride, allowed: Set<String>, required: Set<String> = emptySet(),
    ) {
        val out = stock.customized(o)
        val diff = changed(stock, out, dark)
        val tag = "${stock.id}/${modeName(dark)} $label"
        (diff.keys - allowed).forEach { add("$tag changed $it ${diff[it]}") }
        (required - diff.keys).forEach { add("$tag did not change $it") }
        changed(stock, out, !dark).forEach { (k, v) -> add("$tag leaked into ${modeName(!dark)}: $k $v") }
        if (out.mode(dark).titleBrush !== stock.mode(dark).titleBrush) add("$tag replaced the title brush")
    }

    private fun forEachMode(block: (AppTheme, Boolean) -> Unit) {
        for (t in AppThemes.all) for (dark in MODES) block(t, dark)
    }

    // a. no override

    @Test
    fun aNullAndEmptyOverrideReturnTheSameInstanceForAllEight() {
        assertEquals(8, AppThemes.all.size)
        for (t in AppThemes.all) {
            assertSame(t.id, t, t.customized(null))
            assertSame(t.id, t, t.customized(ThemeOverride.Empty))
            assertSame(t.id, t, t.customized(ThemeOverride.Empty.withColor("l.bg", 0xFFEEEEEE).without("l.bg")))
        }
    }

    // b. isolation

    @Test
    fun aBgEditChangesOnlyTheSurfaceGroupThePrimaryRecheckAndOnCardOnPalette() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val allowed = surfaceGroup + primaryGroup +
                (if (t.isPalette()) setOf("scheme.secondary", "extras.onCard") else emptySet()) +
                artRoles(t.artColors(dark)).keys.filter { it.startsWith("art.title.") || it == "art.outline" }
            val p = prefix(dark)
            val big = if (dark) 0xFF404850 else 0xFFC8D0D8
            for (v in listOf(nudged(t.mode(dark).scheme.background), big)) {
                failures.isolation("bg=%X".format(v), t, dark, ThemeOverride.Empty.withColor(p + "bg", v), allowed, setOf("scheme.background", "scheme.surface"))
            }
        }
        assertNoFailures(failures)
    }

    @Test
    fun anOnBgEditChangesOnlyTheSurfaceGroupThePrimaryRecheckAndOnCardOnPalette() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val allowed = surfaceGroup + primaryGroup + (if (t.isPalette()) setOf("scheme.secondary", "extras.onCard") else emptySet())
            val required = setOf("scheme.onBackground", "scheme.onSurface") + (if (t.isPalette()) setOf("extras.onCard") else emptySet())
            val v = if (dark) 0xFFFAFAFA else 0xFF111111
            failures.isolation("onBg", t, dark, ThemeOverride.Empty.withColor(prefix(dark) + "onBg", v), allowed, required)
        }
        assertNoFailures(failures)
    }

    @Test
    fun anUneditedStockPrimaryThatStillReadsStaysByteIdenticalAfterABgEdit() {
        var stayed = 0
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val stock = t.mode(dark).scheme
            val out = t.customized(ThemeOverride.Empty.withColor(prefix(dark) + "bg", nudged(stock.background))).mode(dark).scheme
            val tag = "${t.id}/${modeName(dark)}"
            if (ratio(stock.primary, out.surfaceContainerHigh) >= 4.5f) {
                stayed++
                listOf(
                    "primary" to (stock.primary to out.primary),
                    "surfaceTint" to (stock.surfaceTint to out.surfaceTint),
                    "tertiary" to (stock.tertiary to out.tertiary),
                    "inversePrimary" to (stock.inversePrimary to out.inversePrimary),
                    "onPrimary" to (stock.onPrimary to out.onPrimary),
                    "onTertiary" to (stock.onTertiary to out.onTertiary),
                ).forEach { (n, pair) -> if (pair.first != pair.second) failures += "$tag $n ${pair.first.hex()}->${pair.second.hex()}" }
            } else if (out.primary == stock.primary) {
                failures += "$tag primary ${stock.primary.hex()} no longer reads but was kept"
            }
        }
        assertNoFailures(failures)
        assertTrue("the check never applied: stayed=$stayed", stayed >= 8)
    }

    @Test
    fun aPrimaryEditChangesOnlyItsDependentsAndOnPrimaryOnlyIfNeeded() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val v = if (dark) 0xFFE0D0FF else 0xFF3A2A70
            val o = ThemeOverride.Empty.withColor(prefix(dark) + "primary", v)
            val stock = t.mode(dark).scheme
            val required = setOf("scheme.primary", "scheme.surfaceTint", "scheme.tertiary", "scheme.inversePrimary")
            failures.isolation("primary", t, dark, o, primaryGroup, required)
            val out = t.customized(o).mode(dark).scheme
            if (ratio(stock.onPrimary, out.primary) >= 4.5f && out.onPrimary != stock.onPrimary) {
                failures += "${t.id}/${modeName(dark)} onPrimary moved though stock still reads"
            }
        }
        assertNoFailures(failures)
    }

    @Test
    fun anOnPrimaryEditChangesOnlyOnPrimaryAndOnPaletteOnTertiary() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            if (t.isPalette()) {
                val stock = t.mode(dark).scheme
                val o = ThemeOverride.Empty.withColor(prefix(dark) + "onPrimary", pickReadable(stock.primary))
                failures.isolation("onPrimary", t, dark, o, setOf("scheme.onPrimary", "scheme.onTertiary"), setOf("scheme.onPrimary", "scheme.onTertiary"))
            }
        }
        assertNoFailures(failures)
    }

    @Test
    fun aTabEditChangesOnlyThatTabAndItsContainerOnPalette() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val e = t.mode(dark).extras
            for (i in e.tabs.indices) {
                val o = ThemeOverride.Empty.withColor("${prefix(dark)}tab.$i", pickReadable(e.onTab))
                val expected = buildSet {
                    add("extras.tabs.$i")
                    if (i == 0) add("extras.tab")
                    if (t.isPalette()) {
                        if (i == 0) add("scheme.primaryContainer")
                        if (i == 1) { add("extras.sage"); add("scheme.secondaryContainer") }
                        if (i == 2) add("scheme.tertiaryContainer")
                    }
                }
                failures.isolation("tab.$i", t, dark, o, expected, expected)
            }
            // An index past this theme's tabs has nothing to apply to.
            failures.isolation("tab.7", t, dark, ThemeOverride.Empty.withColor("${prefix(dark)}tab.7", 0xFF808080), emptySet())
        }
        assertNoFailures(failures)
    }

    @Test
    fun anOnTabEditThatStillReadsMovesNoTabAndReachesContainersOnlyOnPalette() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val o = ThemeOverride.Empty.withColor(prefix(dark) + "onTab", if (dark) 0xFFFFFFFF else 0xFF0C0C0C)
            val onRoles = listOf("onPrimaryContainer", "onSecondaryContainer", "onTertiaryContainer").map { "scheme.$it" }.toSet()
            val expected = setOf("extras.onTab") + (if (t.isPalette()) onRoles + "extras.onSage" else emptySet())
            failures.isolation("onTab", t, dark, o, expected, expected)
        }
        assertNoFailures(failures)
    }

    @Test
    fun aCardEditChangesOnlyTheCardAndSurfaceContainerLowest() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val expected = setOf("extras.card", "scheme.surfaceContainerLowest")
            val v = if (dark) 0xFF2B2B33 else 0xFFFAFAF0
            failures.isolation("card", t, dark, ThemeOverride.Empty.withColor(prefix(dark) + "card", v), expected, expected)
        }
        assertNoFailures(failures)
    }

    @Test
    fun aCardAtThePolarityEdgeKeepsOnCardReadable() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val onCard = t.mode(dark).extras.onCard
            // At the dark limit, stock near-white onCard no longer reads, so it has to move.
            val v = if (dark) 0xFF757575 else 0xFFBCBCBC
            val allowed = setOf("extras.card", "scheme.surfaceContainerLowest", "extras.onCard")
            failures.isolation("card edge", t, dark, ThemeOverride.Empty.withColor(prefix(dark) + "card", v), allowed)
            val out = t.customized(ThemeOverride.Empty.withColor(prefix(dark) + "card", v)).mode(dark).extras
            if (ratio(onCard, Color(v)) >= 4.5f && out.onCard != onCard) failures += "${t.id}/${modeName(dark)} onCard moved though it reads"
            if (ratio(out.onCard, out.card) < 4.5f) failures += "${t.id}/${modeName(dark)} onCard does not read"
        }
        assertNoFailures(failures)
    }

    @Test
    fun outlineVariantIsItsOwnRole() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val v = if (dark) 0xFF505A64 else 0xFFB0BAC4
            val expected = setOf("scheme.outlineVariant")
            failures.isolation("outlineVariant", t, dark, ThemeOverride.Empty.withColor(prefix(dark) + "outlineVariant", v), expected, expected)
            assertEquals(Color(v), t.customized(ThemeOverride.Empty.withColor(prefix(dark) + "outlineVariant", v)).mode(dark).scheme.outlineVariant)
        }
        assertNoFailures(failures)
    }

    @Test
    fun artEditsChangeOnlyThatArtRole() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val a = t.artColors(dark)
            val p = prefix(dark)
            if (a == null) {
                // Rose & Cream has no art, so art keys have nothing to apply to.
                for (k in listOf("ink", "accent.0", "title.0", "outline", "paper", "cardEdge", "sky")) {
                    failures.isolation(k, t, dark, ThemeOverride.Empty.withColor(p + k, 0xFF808080), emptySet())
                }
            } else {
                val ink = if (dark) 0xFFFFFFFF else 0xFF0C0C0C
                failures.isolation("ink", t, dark, ThemeOverride.Empty.withColor(p + "ink", ink), setOf("art.ink"), setOf("art.ink"))
                val accent = ThemeOverride.Empty.withColor(p + "accent.0", pickReadable(a.ink))
                failures.isolation("accent.0", t, dark, accent, setOf("art.accents.0"), setOf("art.accents.0"))
                val paper = ThemeOverride.Empty.withColor(p + "paper", 0xFF7A8A9A)
                failures.isolation("paper", t, dark, paper, setOf("art.paper"), setOf("art.paper"))
                val outline = setOf("art.outline").filter { a.outline != null }.toSet()
                failures.isolation("outline", t, dark, ThemeOverride.Empty.withColor(p + "outline", if (dark) 0xFFFFFFFF else 0xFF050505), outline, outline)
                val edge = setOf("art.cardEdge").filter { a.cardEdge != null }.toSet()
                failures.isolation("cardEdge", t, dark, ThemeOverride.Empty.withColor(p + "cardEdge", 0xFF7A8A9A), edge, edge)
                if (a.outline == null) {
                    val title = ThemeOverride.Empty.withColor(p + "title.0", if (dark) 0xFFF0C0D0 else 0xFF703040)
                    failures.isolation("title.0", t, dark, title, setOf("art.title.0"), setOf("art.title.0"))
                }
            }
        }
        assertNoFailures(failures)
    }

    /**
     * Sunny Journal's letters are pale fills carried by an outline, so ThemeContrastTest checks outline/bg, not title/bg, and its
     * stock fills sit under 3:1 on purpose. Editing another role must not repaint them.
     */
    @Test
    fun anOutlinedThemesStockTitleFillsSurviveATitleOrBgEdit() {
        val t = AppThemes.byId("sunny_journal")
        val failures = mutableListOf<String>()
        for (dark in MODES) {
            val stock = checkNotNull(t.artColors(dark))
            val p = prefix(dark)
            assertTrue("outlined theme expected", stock.outline != null)
            val edit = if (dark) 0xFFF0C0D0 else 0xFF703040
            val onTitle = checkNotNull(t.customized(ThemeOverride.Empty.withColor(p + "title.0", edit)).artColors(dark))
            if (onTitle.title[0] != Color(edit)) failures += "${modeName(dark)} title.0 not applied: ${onTitle.title[0].hex()}"
            stock.title.indices.drop(1).forEach { i ->
                if (onTitle.title[i] != stock.title[i]) failures += "${modeName(dark)} title.0 edit moved title.$i ${stock.title[i].hex()}->${onTitle.title[i].hex()}"
            }
            val bgEdit = nudged(t.mode(dark).scheme.background)
            val onBg = checkNotNull(t.customized(ThemeOverride.Empty.withColor(p + "bg", bgEdit)).artColors(dark))
            stock.title.indices.forEach { i ->
                if (onBg.title[i] != stock.title[i]) failures += "${modeName(dark)} bg edit moved title.$i ${stock.title[i].hex()}->${onBg.title[i].hex()}"
            }
        }
        assertNoFailures(failures)
    }

    @Test
    fun aReadablePrimaryEditIsUsedAsPicked() {
        val failures = mutableListOf<String>()
        val rnd = Random(4242)
        forEachMode { t, dark ->
            val surf = t.mode(dark).scheme.surfaceContainerHigh
            var tried = 0
            while (tried < 40) {
                val v = 0xFF000000L or rnd.nextLong(0x1000000L)
                // Clear of the threshold, so a real adjustment cannot be the cause.
                if (ratio(Color(v), surf) < 5f) continue
                tried++
                val got = t.customized(ThemeOverride.Empty.withColor(prefix(dark) + "primary", v)).mode(dark).scheme.primary
                if (got != Color(v)) failures += "${t.id}/${modeName(dark)} picked %08X, got %s".format(v, got.hex())
            }
        }
        assertNoFailures(failures)
    }

    // c. parity with paletteMode

    private fun safePalette(dark: Boolean, tabCount: Int): Palette {
        val lightTabs = listOf(0xFFD8E4F4, 0xFFF4DCE4, 0xFFDDF0DC, 0xFFF6ECC8, 0xFFE6DCF6, 0xFFFFE0CC, 0xFFDDEEEE, 0xFFEEEEDD)
        val darkTabs = listOf(0xFF3A4A60, 0xFF5A3A4A, 0xFF3A5A46, 0xFF5A5030, 0xFF46406A, 0xFF603F30, 0xFF305A5A, 0xFF4A4A30)
        return if (dark) {
            Palette(0xFF141A22, 0xFFE9EEF5, 0xFFB9C8F0, 0xFF1A2340, 0xFF1E252E, 0xFF3A4450, darkTabs.take(tabCount), 0xFFF5F8FC, emptyList())
        } else {
            Palette(0xFFEEF2F7, 0xFF1E2430, 0xFF4A3F8F, 0xFFFFFFFF, 0xFFFBFBF8, 0xFFC9D0DA, lightTabs.take(tabCount), 0xFF1E2430, emptyList())
        }
    }

    private fun ThemeOverride.withPalette(prefix: String, x: Palette): ThemeOverride {
        var o = withColor(prefix + "bg", x.bg)
            .withColor(prefix + "onBg", x.onBg)
            .withColor(prefix + "primary", x.primary)
            .withColor(prefix + "onPrimary", x.onPrimary)
            .withColor(prefix + "card", x.card)
            .withColor(prefix + "outlineVariant", x.outlineVariant)
            .withColor(prefix + "onTab", x.onTab)
        x.tabs.forEachIndexed { i, tab -> o = o.withColor("${prefix}tab.$i", tab) }
        return o
    }

    @Test
    fun aFullyEditedPaletteThemeEqualsPaletteModeOfTheMergedValues() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            if (t.isPalette()) {
                val pal = safePalette(dark, t.mode(dark).extras.tabs.size)
                val out = t.customized(ThemeOverride.Empty.withPalette(prefix(dark), pal)).mode(dark)
                val want = paletteMode(pal, dark)
                val tag = "${t.id}/${modeName(dark)}"
                val got = schemeRoles(out.scheme) + extrasRoles(out.extras).filterKeys { "ood." !in it }
                val exp = schemeRoles(want.scheme) + extrasRoles(want.extras).filterKeys { "ood." !in it }
                exp.forEach { (k, v) -> if (got[k] != v) failures += "$tag $k expected $v got ${got[k]}" }
                assertEquals(exp.keys, got.keys)
            }
        }
        assertNoFailures(failures)
    }

    @Test
    fun settingARoleToItsStockValueChangesNothing() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            if (t.isPalette()) {
                val m = t.mode(dark)
                val a = checkNotNull(t.artColors(dark))
                val p = prefix(dark)
                var o = ThemeOverride.Empty
                    .withColor(p + "bg", m.scheme.background.long())
                    .withColor(p + "onBg", m.scheme.onBackground.long())
                    .withColor(p + "onPrimary", m.scheme.onPrimary.long())
                    .withColor(p + "card", m.extras.card.long())
                    .withColor(p + "outlineVariant", m.scheme.outlineVariant.long())
                    .withColor(p + "onTab", m.extras.onTab.long())
                    .withColor(p + "ink", a.ink.long())
                    .withColor(p + "paper", a.paper.long())
                m.extras.tabs.forEachIndexed { i, c -> o = o.withColor("${p}tab.$i", c.long()) }
                m.extras.mood.forEachIndexed { i, c -> o = o.withColor("${p}mood.$i", c.long()) }
                a.accents.forEachIndexed { i, c -> o = o.withColor("${p}accent.$i", c.long()) }
                failures.isolation("all stock values", t, dark, o, emptySet())
            }
        }
        assertNoFailures(failures)
    }

    // d. seeded random overrides

    private val singleKeys = listOf("bg", "onBg", "primary", "onPrimary", "card", "outlineVariant", "onTab", "ink", "outline", "paper", "cardEdge", "sky")
    private val allKeys = singleKeys + (0..7).flatMap { listOf("tab.$it", "accent.$it", "title.$it") } + (0..4).flatMap { listOf("mood.$it", "onMood.$it") }

    private fun randomColor(r: Random, m: ThemeMode): Long {
        fun near(c: Color): Long {
            val spread = { v: Float -> ((v * 255f).toInt() + r.nextInt(-8, 9)).coerceIn(0, 255).toLong() }
            return 0xFF000000L or (spread(c.red) shl 16) or (spread(c.green) shl 8) or spread(c.blue)
        }
        fun gray(g: Int) = 0xFF000000L or (g.toLong() shl 16) or (g.toLong() shl 8) or g.toLong()
        return when (r.nextInt(7)) {
            0 -> 0xFF000000L or r.nextLong(0x1000000L)
            1 -> gray(r.nextInt(0x60, 0xA0)) // mid grey, poor on everything
            2 -> near(m.scheme.background) // invisible as a foreground
            3 -> near(m.scheme.onBackground) // invisible as a background
            4 -> if (r.nextBoolean()) 0xFF000000L else 0xFFFFFFFFL // out of polarity in one mode or the other
            5 -> gray(if (r.nextBoolean()) r.nextInt(0xB4, 0xC4) else r.nextInt(0x6E, 0x7E)) // either side of the polarity limits
            else -> near(m.extras.tab)
        }
    }

    private fun randomOverride(r: Random, stock: ThemeMode, p: String): ThemeOverride {
        val density = listOf(0.1f, 0.35f, 0.8f, 1f)[r.nextInt(4)]
        var o = ThemeOverride.Empty
        allKeys.forEach { k -> if (r.nextFloat() < density) o = o.withColor(p + k, randomColor(r, stock)) }
        if (o.isEmpty()) o = o.withColor(p + allKeys[r.nextInt(allKeys.size)], randomColor(r, stock))
        return o
    }

    @Test
    fun seededRandomOverridesAlwaysPassEveryContrastPairAndPolarity() {
        val failures = mutableListOf<String>()
        var iterations = 0
        var moved = 0
        var rejected = 0
        AppThemes.all.forEachIndexed { index, t ->
            for (dark in MODES) {
                val seed = 20261105 + index * 2 + (if (dark) 1 else 0)
                val r = Random(seed)
                repeat(ITERATIONS) { n ->
                    val p = prefix(dark)
                    var o = randomOverride(r, t.mode(dark), p)
                    val touchOther = n % 4 == 0
                    if (touchOther) o = o.withColor(prefix(!dark) + "bg", randomColor(r, t.mode(!dark)))
                    val out = t.customized(o)
                    iterations++
                    val diff = changed(t, out, dark)
                    if (diff.isNotEmpty()) moved++
                    if (o.color(p + "bg") != null && "scheme.background" !in diff) rejected++
                    val problems = pairFailures(out, dark) + polarityFailures(out, dark)
                    val leaked = if (touchOther) emptyMap() else changed(t, out, !dark)
                    val translucent = roles(out, dark).filterKeys { it in diff && it != "art.decoAlpha" && it != "art.outline" }
                        .filterValues { !it.startsWith("FF") }
                    if (problems.isNotEmpty() || leaked.isNotEmpty() || translucent.isNotEmpty()) {
                        failures += "${t.id}/${modeName(dark)} seed=$seed iteration=$n: $problems leaked=$leaked translucent=$translucent\n  entries=${o.entries}"
                    }
                }
            }
        }
        assertNoFailures(failures)
        assertEquals(8 * 2 * ITERATIONS, iterations)
        assertTrue("too few random overrides changed anything: $moved of $iterations", moved > iterations * 9 / 10)
        assertTrue("the seeds never produced an out-of-polarity bg: $rejected", rejected > 100)
    }

    /** Reports a stock pair under minimum instead of hiding it: the random test above assumes all of these hold. */
    @Test
    fun everyStockThemeAlreadyPassesThePairsThatTheResolverPromises() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            (pairFailures(t, dark) + polarityFailures(t, dark)).forEach { failures += "${t.id}/${modeName(dark)} $it" }
        }
        assertNoFailures(failures)
    }

    // e. polarity

    @Test
    fun polarityLimitsAreTheDocumentedOnes() {
        assertEquals(0.5f, LIGHT_MIN_LUMINANCE)
        assertEquals(0.18f, DARK_MAX_LUMINANCE)
        assertTrue(polarityOk(Color(0xFFBCBCBC), dark = false))
        assertTrue(!polarityOk(Color(0xFFBBBBBB), dark = false))
        assertTrue(polarityOk(Color(0xFF757575), dark = true))
        assertTrue(!polarityOk(Color(0xFF767676), dark = true))
    }

    @Test
    fun allStockBackgroundsAndCardsPassPolarity() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val m = t.mode(dark)
            if (!polarityOk(m.scheme.background, dark)) failures += "${t.id}/${modeName(dark)} bg ${m.scheme.background.hex()}"
            if (!polarityOk(m.extras.card, dark)) failures += "${t.id}/${modeName(dark)} card ${m.extras.card.hex()}"
        }
        assertNoFailures(failures)
    }

    @Test
    fun outOfPolarityBgAndCardAreRejectedAndStockKept() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val p = prefix(dark)
            val bad = if (dark) listOf(0xFFFFFFFF, 0xFFF0F0F0, 0xFF767676) else listOf(0xFF000000, 0xFF101010, 0xFFBBBBBB)
            for (v in bad) {
                failures.isolation("bg %X".format(v), t, dark, ThemeOverride.Empty.withColor(p + "bg", v), emptySet())
                failures.isolation("card %X".format(v), t, dark, ThemeOverride.Empty.withColor(p + "card", v), emptySet())
            }
        }
        assertNoFailures(failures)
    }

    @Test
    fun aRejectedBgDoesNotStopOtherEditsInTheSameOverride() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val p = prefix(dark)
            val o = ThemeOverride.Empty
                .withColor(p + "bg", if (dark) 0xFFFFFFFF else 0xFF000000)
                .withColor(p + "outlineVariant", 0xFF7A8A9A)
            val expected = setOf("scheme.outlineVariant")
            failures.isolation("rejected bg + outlineVariant", t, dark, o, expected, expected)
        }
        assertNoFailures(failures)
    }

    // f. mood

    @Test
    fun aMoodEditAppliesAndItsNumberColourIsDerivedToRead() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val p = prefix(dark)
            val stock = t.mode(dark).extras
            for (i in stock.mood.indices) {
                for (v in listOf(0xFF000000, 0xFFFFFFFF, 0xFF777777, 0xFFB0D0E0, 0xFF304050)) {
                    val o = ThemeOverride.Empty.withColor("${p}mood.$i", v)
                    failures.isolation("mood.$i=%X".format(v), t, dark, o, setOf("extras.mood.$i", "extras.onMood.$i"), setOf("extras.mood.$i"))
                    val out = t.customized(o).mode(dark).extras
                    val tag = "${t.id}/${modeName(dark)} mood.$i=%X".format(v)
                    if (out.mood[i] != Color(v)) failures += "$tag not applied: ${out.mood[i].hex()}"
                    val r = ratio(out.onMood[i], out.mood[i])
                    if (r < 4.5f) failures += "$tag number reads at %.2f".format(r)
                    if (ratio(stock.onMood[i], Color(v)) >= 4.5f && out.onMood[i] != stock.onMood[i]) failures += "$tag number moved though stock reads"
                }
            }
        }
        assertNoFailures(failures)
    }

    @Test
    fun aStoredOnMoodKeyIsIgnored() {
        val failures = mutableListOf<String>()
        forEachMode { t, dark ->
            val p = prefix(dark)
            failures.isolation("onMood only", t, dark, ThemeOverride.Empty.withColor("${p}onMood.0", 0xFF123456), emptySet())
            val withMood = ThemeOverride.Empty.withColor("${p}mood.0", 0xFFB0D0E0)
            val withBoth = withMood.withColor("${p}onMood.0", 0xFFB0D0E1) // would be invisible if it were honoured
            assertEquals(
                "${t.id}/${modeName(dark)}",
                t.customized(withMood).mode(dark).extras.onMood[0],
                t.customized(withBoth).mode(dark).extras.onMood[0],
            )
        }
        assertNoFailures(failures)
    }

    // g. other keys

    @Test
    fun skyAppliesOnCloudDriftOnly() {
        val failures = mutableListOf<String>()
        assertEquals(CLOUD, AppThemes.all.single { it.art?.light?.sky != null }.id)
        forEachMode { t, dark ->
            val v = 0xFF7A8A9AL
            val o = ThemeOverride.Empty.withColor(prefix(dark) + "sky", v)
            val expected = if (t.id == CLOUD) setOf("art.sky") else emptySet()
            failures.isolation("sky", t, dark, o, expected, expected)
            if (t.id == CLOUD) assertEquals(Color(v), t.customized(o).artColors(dark)?.sky)
        }
        assertNoFailures(failures)
    }

    @Test
    fun nonColourKeysChangeNoColour() {
        val failures = mutableListOf<String>()
        val o = ThemeOverride.Empty
            .with("titleFont", "PACIFICO")
            .with("titleColoring", TitleColoring.PER_WORD.name)
            .with("labelStyle", LabelStyle.STAMP.name)
            .with("titleTrail", Glyph.STAR.name)
            .with("labelTrail", Glyph.STAR.name)
            .with("check", Glyph.HEART.name)
            .with("paper", Paper.LINED.name)
            .with("headerStyle", HeaderStyle.PLAIN.name)
            .with("bullets", "STAR,HEART")
            .with("tape", "true")
            .with("l.decoAlpha", "0.25")
            .with("d.decoAlpha", "0.25")
            .with("future.thing", "abc")
        assertEquals(13, o.entries.size)
        forEachMode { t, dark -> failures.isolation("non-colour keys", t, dark, o, emptySet()) }
        assertNoFailures(failures)
    }

    @Test
    fun aColourEditKeepsEveryNonColourFieldOfTheTheme() {
        for (t in AppThemes.all) {
            val out = t.customized(ThemeOverride.Empty.withColor("l.outlineVariant", 0xFF7A8A9A))
            assertEquals(t.id, out.id)
            assertEquals(t.nameRes, out.nameRes)
            assertEquals(t.descriptionRes, out.descriptionRes)
            assertSame(t.titleFont, out.titleFont)
            assertSame(t.tabShape, out.tabShape)
            assertSame(t.cardShape, out.cardShape)
            assertEquals(t.headerStyle, out.headerStyle)
            assertSame(t.decoration, out.decoration)
            assertEquals(t.animated, out.animated)
            assertEquals(t.periodMillis, out.periodMillis)
            assertEquals(t.staticPhase, out.staticPhase)
            assertSame(t.scene, out.scene)
            assertEquals(t.staticTimeSec, out.staticTimeSec)
            val a = t.art
            if (a == null) {
                assertNull(out.art)
            } else {
                val b = checkNotNull(out.art)
                assertSame(a.titleFont, b.titleFont)
                assertEquals(a.titleColoring, b.titleColoring)
                assertEquals(a.titleTrail, b.titleTrail)
                assertEquals(a.labelStyle, b.labelStyle)
                assertSame(a.labelFont, b.labelFont)
                assertEquals(a.labelTrail, b.labelTrail)
                assertEquals(a.bullets, b.bullets)
                assertEquals(a.check, b.check)
                assertEquals(a.paper, b.paper)
                assertEquals(a.tape, b.tape)
                assertEquals(a.cardSalt, b.cardSalt)
            }
        }
    }

    // h. Rose & Cream

    private val rose get() = AppThemes.byId(ROSE)

    @Test
    fun aRoseOnPrimaryEditLeavesTertiaryAndOnTertiaryStock() {
        val failures = mutableListOf<String>()
        for (dark in MODES) {
            val stock = rose.mode(dark).scheme
            val v = if (dark) 0xFF200808 else 0xFFFFF0E0
            val out = rose.customized(ThemeOverride.Empty.withColor(prefix(dark) + "onPrimary", v)).mode(dark).scheme
            val tag = modeName(dark)
            if (out.onPrimary != Color(v)) failures += "$tag onPrimary not applied: ${out.onPrimary.hex()}"
            if (out.tertiary != stock.tertiary) failures += "$tag tertiary ${stock.tertiary.hex()}->${out.tertiary.hex()}"
            if (out.onTertiary != stock.onTertiary) failures += "$tag onTertiary ${stock.onTertiary.hex()}->${out.onTertiary.hex()}"
        }
        assertNoFailures(failures)
    }

    @Test
    fun aRosePrimaryEditLeavesOnTertiaryReadableOnTertiary() {
        val failures = mutableListOf<String>()
        val r = Random(310)
        for (dark in MODES) {
            repeat(200) { n ->
                val v = 0xFF000000L or r.nextLong(0x1000000L)
                val s = rose.customized(ThemeOverride.Empty.withColor(prefix(dark) + "primary", v)).mode(dark).scheme
                val tag = "${modeName(dark)} #$n primary=%X".format(v)
                if (s.tertiary != s.primary) failures += "$tag tertiary did not follow"
                if (ratio(s.onTertiary, s.tertiary) < 4.5f) failures += "$tag onTertiary %.2f".format(ratio(s.onTertiary, s.tertiary))
                if (ratio(s.onPrimary, s.primary) < 4.5f) failures += "$tag onPrimary %.2f".format(ratio(s.onPrimary, s.primary))
            }
        }
        assertNoFailures(failures)
    }

    private companion object {
        const val ITERATIONS = 300
    }
}

/** The stored-JSON path: decode needs org.json, so this half runs under Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeCustomizeTestCodec {
    @Test
    fun badJsonDecodesToNullAndResolvesToStock() {
        for (bad in listOf(null, "", "   ", "{not json", "[1,2]", "\"x\"", "{}", """{"l.bg":"FFF4F0FB"}""", """{"v":2,"l.bg":"FFF4F0FB"}""")) {
            val decoded = ThemeOverrideCodec.decode(bad)
            assertNull("input: $bad", decoded)
            for (t in AppThemes.all) assertSame(t.id, t, t.customized(decoded))
        }
    }

    @Test
    fun aStoredOverrideWithNoEntriesIsStock() {
        for (json in listOf("""{"v":1}""", """{"v":1,"l.bg":"nothex","d.primary":"80112233"}""")) {
            val decoded = ThemeOverrideCodec.decode(json)
            for (t in AppThemes.all) assertSame("${t.id}: $json", t, t.customized(decoded))
        }
    }

    @Test
    fun aDecodedOverrideResolvesLikeTheOneThatWasStored() {
        val o = ThemeOverride.Empty
            .withColor("l.bg", 0xFFF0F4F8)
            .withColor("d.primary", 0xFFE0D0FF)
            .withColor("l.sky", 0xFF7A8A9A)
            .withColor("d.mood.2", 0xFF304050)
        val decoded = checkNotNull(ThemeOverrideCodec.decode(ThemeOverrideCodec.encode(o)))
        for (t in AppThemes.all) {
            for (dark in MODES) assertEquals(t.id, roles(t.customized(o), dark), roles(t.customized(decoded), dark))
        }
    }

    @Test
    fun aBadEntryIsDroppedAloneAndAGoodOneStillApplies() {
        val decoded = checkNotNull(ThemeOverrideCodec.decode("""{"v":1,"l.bg":"nothex","l.outlineVariant":"FF7A8A9A","l.sky":"zz"}"""))
        for (t in AppThemes.all) {
            val out = t.customized(decoded)
            assertEquals(t.id, Color(0xFF7A8A9A), out.light.scheme.outlineVariant)
            assertEquals(t.id, t.light.scheme.background, out.light.scheme.background)
            assertEquals(t.id, t.art?.light?.sky, out.art?.light?.sky)
        }
    }
}
