package com.shibi.commentarii.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.shibi.commentarii.R

// Static themes v2 (docs/plans/themes2-plan.md, S5): paper texture + washi strips + sticker scene per theme.
// Palettes come from the plan table; titles/labels/cards are styled by ThemeArt/ThemeCard. All art is vector.

private fun tabsOf(c: ArtColors): List<Long> = c.accents.map { it.toArgb().toLong() and 0xFFFFFFFFL }

private fun artTabsDark(id: String): List<Long> = tabsOf(checkNotNull(themeArt(id)).dark)

private val WashiLight = Palette(
    0xFFFFFFFF, 0xFF2E2633, 0xFFA0527A, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE6D9E6,
    listOf(0xFFF9C9D6, 0xFFDCCFF5, 0xFFC6EBDA, 0xFFFCEBA8, 0xFFC7E3F8), 0xFF2E2633, emptyList(),
)
private val WashiDark by lazy {
    Palette(
        0xFF221E26, 0xFFF1EAF2, 0xFFF2B6CF, 0xFF3A1A2A, 0xFF2C2731, 0xFF4A4250,
        artTabsDark("washi_planner"), 0xFFFFF6FA, emptyList(),
    )
}
private val SunnyLight = Palette(
    0xFFFDF6E7, 0xFF33291F, 0xFFB5582E, 0xFFFFFFFF, 0xFFFFFDF7, 0xFFEADFC8,
    listOf(0xFFFFD3B0, 0xFFFFF0A0, 0xFFCDEFC0, 0xFFBFD9FF, 0xFFDCCBFF, 0xFFFFC9DE), 0xFF33291F, emptyList(),
)
private val SunnyDark by lazy {
    Palette(
        0xFF24201A, 0xFFF3EBDD, 0xFFF4B98E, 0xFF3D2410, 0xFF2E2921, 0xFF4B4336,
        artTabsDark("sunny_journal"), 0xFFFFF8EE, emptyList(),
    )
}
private val MatchaLight = Palette(
    0xFFF6F7EE, 0xFF26301F, 0xFF5C7A3A, 0xFFFFFFFF, 0xFFFFFFFA, 0xFFDCE2CC,
    listOf(0xFFD4E4B8, 0xFFEDE3C8, 0xFFC9DCC9, 0xFFF2D6C9, 0xFFE8E0F0), 0xFF26301F, emptyList(),
)
private val MatchaDark by lazy {
    Palette(
        0xFF1B2017, 0xFFE8EEDD, 0xFFB7D38F, 0xFF24361A, 0xFF242B1F, 0xFF3F4A36,
        artTabsDark("matcha_tea"), 0xFFF5FAEE, emptyList(),
    )
}
private val LavenderLight = Palette(
    0xFFF4F0FB, 0xFF272235, 0xFF6A5AA8, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFDDD6EC,
    listOf(0xFFDCD3F2, 0xFFC9D6F2, 0xFFF2D3E4, 0xFFD6EEF0, 0xFFF6E7C1), 0xFF272235, emptyList(),
)
private val LavenderDark by lazy {
    Palette(
        0xFF16141F, 0xFFECE8F6, 0xFFC3B6F2, 0xFF2A2058, 0xFF201D2C, 0xFF3D3850,
        artTabsDark("lavender_moon"), 0xFFF7F4FF, emptyList(),
    )
}

/**
 * Per-size scene helper: every position is a fraction of the layer, element sizes are `minDim * k` clamped to
 * 16..110dp and scaled by widthScale (1..1.25) so S24 Ultra and Z Fold cover/inner both hold.
 * Anchors sit in the bottom 22% / side gutters; scatter outside [avoid] is vivid, inside it stays at <= 0.16.
 */
private class Scene(size: Size, val density: Density, val dark: Boolean, id: String) {
    val art = checkNotNull(themeArt(id))
    val c: ArtColors = if (dark) art.dark else art.light
    val acc: List<Color> = c.accents
    val w = size.width
    val h = size.height
    val dp = density.density
    val widthScale =(w / (400f * dp)).coerceIn(1f, 1.25f)
    val avoid = Rect(w * 0.10f, h * 0.06f, w * 0.90f, h * 0.80f)
    val whole = Rect(0f, 0f, w, h)
    val paper = PaperCache(size, density, art.paper, id.hashCode())
    private val paperAlpha = if (dark) 0.18f else 0.35f
    private val vivid = 0.85f * c.decoAlpha
    private val faint = 0.16f * c.decoAlpha

    fun n(base: Int): Int = (base * widthScale).toInt().coerceAtLeast(1)
    fun sz(k: Float, maxDp: Float = 110f): Float = (minOf(w, h) * k * widthScale).coerceIn(16f * dp, maxDp * dp)
    fun deep(col: Color): Color = lerp(col, c.ink, 0.12f)
    fun ink(fill: Color, second: Color = fill, a: Float = 1f) = DoodleInk(fill, second, c.ink, alpha = a * c.decoAlpha)

    fun DrawScope.paperLayer(marginColor: Color = c.ink) = drawPaper(paper, c.paper, paperAlpha, marginColor)

    fun DrawScope.put(
        id: DoodleId, fx: Float, fy: Float, k: Float, rot: Float, ink: DoodleInk, sticker: Boolean = true, t: Float = 0f,
        maxDp: Float = 110f,
    ) = drawDoodle(id, Offset(w * fx, h * fy), sz(k, maxDp), ink, t, rot, sticker)

    fun DrawScope.tape(fx: Float, fy: Float, angle: Float, color: Color, pattern: Int, lenDp: Float = 72f) =
        drawWashiTape(Offset(w * fx, h * fy), lenDp * dp, 20f * dp, angle, color.copy(alpha = c.decoAlpha), pattern, c.ink)

    /** Glyph confetti: [outside] vivid pieces off the text column, [inside] faint ones anywhere. */
    fun DrawScope.confetti(
        seed: Int, outside: Int, inside: Int, glyphs: List<Glyph>, cols: List<Color>, sizeDp: ClosedFloatingPointRange<Float>,
    ) {
        val sized = sizeDp.start * widthScale..sizeDp.endInclusive * widthScale
        drawScatter(seed, n(outside), glyphs, cols, whole, avoid, vivid, sized, this@Scene.density)
        drawScatter(seed + 101, inside, glyphs, cols, whole, null, faint, sized, this@Scene.density)
    }

    fun DrawScope.doodleConfetti(
        seed: Int, outside: Int, inside: Int, ids: List<DoodleId>, inkFor: (Int) -> DoodleInk, sizeDp: ClosedFloatingPointRange<Float>,
    ) {
        val sized = sizeDp.start * widthScale..sizeDp.endInclusive * widthScale
        drawScatterDoodles(seed, n(outside), ids, inkFor, whole, avoid, sized, this@Scene.density)
        drawScatterDoodles(seed + 101, inside, ids, { inkFor(it).faintCopy() }, whole, null, sized, this@Scene.density)
    }

    private fun DoodleInk.faintCopy() = DoodleInk(fill, second, ink, blush, 0.16f * c.decoAlpha)
}

private val Coral = Color(0xFFF4A99B)
private val BearTan = Color(0xFFEBC7A0)
private val Gold = Color(0xFFF6D463)
private val SunOrange = Color(0xFFFFB86B)
private val Mint = Color(0xFF9ED9C3)

// ---- washi_planner: white dotted page, dashed mint border, bear + sticky note, plant, strips, confetti ----
private val WashiDeco = ThemeDecoration { size, density, dark ->
    val s = Scene(size, density, dark, "washi_planner")
    DecorationFrame {
        with(s) {
            paperLayer()
            drawPageBorder(Mint.copy(alpha = if (dark) 0.35f else 0.95f), density)
            // washi strips (pink top-left, mint mid-right, butter lower-left)
            tape(0.04f, 0.006f, -18f, acc[0], 0, 56f)
            tape(0.995f, 0.46f, 24f, acc[2], 0, 64f)
            tape(0.01f, 0.70f, -30f, acc[3], 0, 64f)
            // confetti: hearts, stars, sparkles, daisies
            val pop = listOf(Coral, Color(0xFFF08FB0), Color(0xFFB9A2EE), Color(0xFF7FCDAA), Gold, Color(0xFF8CC2EE))
            confetti(11, 6, 4, listOf(Glyph.HEART), pop.map { deep(it) }, 12f..20f)
            confetti(23, 6, 4, listOf(Glyph.STAR), pop.reversed().map { deep(it) }, 12f..20f)
            confetti(37, 6, 5, listOf(Glyph.SPARKLE), listOf(Gold, acc[4], acc[0]).map { deep(it) }, 10f..18f)
            doodleConfetti(41, 4, 3, listOf(DoodleId.DAISY), { ink(Color.White, Gold) }, 22f..32f)
            put(DoodleId.SPEECH_HEART, 0.92f, 0.31f, 0.12f, 8f, ink(acc[1], Coral, 0.9f), maxDp = 72f)
            // bottom anchors in the gutters, clear of the card column: plant (left), bear over sticky note (right)
            put(DoodleId.PLANT_POT, 0.07f, 0.935f, 0.27f, -3f, ink(acc[0], LeafGreen), maxDp = 72f)
            put(DoodleId.STICKY_NOTE, 0.92f, 0.955f, 0.27f, 5f, ink(acc[3], acc[0]), maxDp = 72f)
            put(DoodleId.BEAR_PEEK, 0.92f, 0.92f, 0.25f, 0f, ink(BearTan, acc[0]), maxDp = 72f)
        }
    }
}

// ---- sunny_journal: cream speckled paper, smiling sun + cloud, taped note, sprigs, cozy bottom row ----
private val SunnyDeco = ThemeDecoration { size, density, dark ->
    val s = Scene(size, density, dark, "sunny_journal")
    DecorationFrame {
        with(s) {
            paperLayer()
            tape(0.03f, 0.006f, -14f, acc[0], 1, 56f)
            tape(0.995f, 0.60f, 20f, acc[4], 1, 64f)
            // sun tucked into the top-right corner margin, clear of the calendar icon and date chips
            put(DoodleId.SUN_FACE, 0.93f, -0.025f, 0.15f, 0f, ink(Gold, SunOrange), sticker = false, t = 1.2f)
            put(DoodleId.CLOUD_PUFF, 0.66f, 0.085f, 0.15f, 0f, ink(Color.White, acc[3], 0.25f), sticker = false)
            // taped note card + three leaf sprigs in the gutters
            put(DoodleId.NOTE_CARD, 0.955f - 12f * dp / w, 0.37f, 0.17f, 6f, ink(Color.White, acc[1]))
            tape(0.955f - 12f * dp / w, 0.335f, -8f, acc[5], 1, 40f)
            put(DoodleId.LEAF_SPRIG, 0.03f, 0.22f, 0.17f, -12f, ink(LeafGreen, acc[2]), sticker = false)
            put(DoodleId.LEAF_SPRIG, 0.03f, 0.33f, 0.15f, 14f, ink(LeafGreen, acc[2]), sticker = false)
            put(DoodleId.LEAF_SPRIG, 0.035f, 0.57f, 0.15f, -10f, ink(LeafGreen, acc[2]), sticker = false)
            put(DoodleId.LEAF_SPRIG, 0.025f, 0.46f, 0.17f, 10f, ink(LeafGreen, acc[2]), sticker = false)
            put(DoodleId.LEAF_SPRIG, 0.035f, 0.69f, 0.17f, -6f, ink(LeafGreen, acc[2]), sticker = false)
            // multicolour confetti
            val pop = c.title.map { deep(it) }
            confetti(5, 4, 3, listOf(Glyph.HEART), pop, 12f..20f)
            confetti(17, 3, 3, listOf(Glyph.STAR), pop.reversed(), 12f..20f)
            confetti(29, 3, 2, listOf(Glyph.FLOWER), pop, 14f..22f)
            confetti(43, 8, 4, listOf(Glyph.SPARKLE), listOf(Gold, acc[4], acc[0]).map { deep(it) }, 9f..16f)
            // cozy bottom row
            put(DoodleId.CANDLE_JAR, 0.12f, 0.935f, 0.22f, -3f, ink(acc[0], Gold))
            put(DoodleId.CAMERA, 0.31f, 0.955f, 0.20f, 4f, ink(acc[3], acc[4]))
            put(DoodleId.MUG_HEART, 0.50f, 0.945f, 0.19f, -5f, ink(acc[5], acc[1]))
            put(DoodleId.PLANT_POT, 0.87f, 0.925f, 0.25f, 3f, ink(acc[0], LeafGreen))
        }
    }
}

// ---- matcha_tea: ruled paper, tea set bottom corners, bamboo edge, tea leaves, gingham strips ----
private val MatchaDeco = ThemeDecoration { size, density, dark ->
    val s = Scene(size, density, dark, "matcha_tea")
    DecorationFrame {
        with(s) {
            paperLayer(marginColor = acc[3])
            tape(0.10f, 0.03f, -12f, acc[0], 2)
            tape(0.995f, 0.40f, 22f, acc[2], 2, 64f)
            put(DoodleId.BAMBOO, 0.975f, 0.30f, 0.30f, 0f, ink(LeafGreen, acc[0]), sticker = false)
            put(DoodleId.BAMBOO, 0.975f, 0.56f, 0.30f, 0f, ink(LeafGreen, acc[0]), sticker = false)
            val greens = listOf(Color(0xFF9CC27A), Color(0xFF7FB38F), Color(0xFFB7D38F)).map { deep(it) }
            for ((i, p) in listOf(0.03f to 0.16f, 0.05f to 0.55f, 0.04f to 0.78f, 0.95f to 0.20f, 0.93f to 0.72f, 0.96f to 0.84f).withIndex()) {
                put(DoodleId.TEA_LEAF, p.first, p.second, 0.10f, (i * 47 % 80 - 40).toFloat(), ink(greens[i % greens.size]), sticker = false, maxDp = 34f)
            }
            doodleConfetti(7, 5, 3, listOf(DoodleId.TEA_LEAF), { ink(greens[it % greens.size]) }, 22f..34f)
            put(DoodleId.DANGO, 0.05f, 0.40f, 0.15f, -14f, ink(acc[3], acc[0]))
            confetti(61, 4, 2, listOf(Glyph.LEAF, Glyph.CIRCLE), greens, 10f..16f)
            confetti(73, 4, 2, listOf(Glyph.SPARKLE), listOf(acc[3], acc[1]).map { deep(it) }, 9f..15f)
            // tea set: teapot bottom-left, teacup + whisk bottom-right with steam
            put(DoodleId.TEAPOT, 0.15f, 0.93f, 0.26f, -4f, ink(acc[2], acc[0]))
            put(DoodleId.TEACUP_FACE, 0.76f, 0.945f, 0.23f, 3f, ink(Color.White, acc[0]))
            put(DoodleId.STEAM, 0.76f, 0.885f, 0.12f, 0f, ink(Color.White, acc[2], 0.8f), sticker = false, t = 0.5f)
            put(DoodleId.WHISK, 0.92f, 0.93f, 0.20f, 12f, ink(acc[1], acc[3]))
        }
    }
}

// ---- lavender_moon: star-dust paper, sleeping moon with halo, constellations, lavender, planet ----
private val ConstellationPts = listOf(
    // three compact 5-6 star groups in the gutters at ~0.15h, 0.45h, 0.70h
    0.05f to 0.13f, 0.10f to 0.17f, 0.07f to 0.22f, 0.13f to 0.25f, 0.04f to 0.28f,
    0.92f to 0.42f, 0.96f to 0.46f, 0.90f to 0.50f, 0.95f to 0.54f, 0.91f to 0.58f, 0.97f to 0.60f,
    0.04f to 0.67f, 0.09f to 0.71f, 0.05f to 0.75f, 0.11f to 0.78f, 0.06f to 0.82f,
)
private val ConstellationGroups = listOf(0..4, 5..10, 11..15)

private val LavenderDeco = ThemeDecoration { size, density, dark ->
    val s = Scene(size, density, dark, "lavender_moon")
    val dots = PathEffect.dashPathEffect(floatArrayOf(density.density * 3f, density.density * 5f))
    DecorationFrame {
        with(s) {
            paperLayer()
            // halo behind the moon (alpha 0.25)
            val moonAt = Offset(w * 0.80f, h * 0.10f)
            val halo = 90f * dp * 1.3f
            drawCircle(
                Brush.radialGradient(
                    listOf(acc[4].copy(alpha = 0.25f * c.decoAlpha), Color.Transparent),
                    center = moonAt, radius = halo,
                ),
                radius = halo, center = moonAt,
            )
            put(DoodleId.MOON_FACE, 0.80f, 0.10f, 0.30f, -8f, ink(acc[4], acc[0]), sticker = false, maxDp = 90f)
            // dotted constellations
            val lineCol = c.ink.copy(alpha = 0.30f * c.decoAlpha)
            for (g in ConstellationGroups) {
                for (i in g.first until g.last) {
                    val p0 = Offset(w * ConstellationPts[i].first, h * ConstellationPts[i].second)
                    val p1 = Offset(w * ConstellationPts[i + 1].first, h * ConstellationPts[i + 1].second)
                    drawLine(lineCol, p0, p1, dp * 1.2f, pathEffect = dots)
                }
            }
            for ((i, p) in ConstellationPts.withIndex()) {
                drawGlyph(Glyph.STAR, Offset(w * p.first, h * p.second), (9f + (i % 3) * 2f) * dp, deep(acc[4]).copy(alpha = 0.8f * c.decoAlpha))
            }
            val pop = listOf(acc[0], acc[1], acc[2], acc[4]).map { deep(it) }
            drawScatter(
                97, n(8), listOf(Glyph.SPARKLE), listOf(Gold, acc[4]).map { deep(it) }, whole, avoid, 0.5f * c.decoAlpha,
                9f * s.widthScale..14f * s.widthScale, s.density,
            )
            confetti(3, 8, 4, listOf(Glyph.STAR), pop, 9f..16f)
            confetti(19, 5, 3, listOf(Glyph.SPARKLE, Glyph.MOON), pop, 9f..15f)
            put(DoodleId.CLOUD_PUFF, 0.88f, 0.34f, 0.15f, 0f, ink(Color.White, acc[1], 0.7f), sticker = false)
            put(DoodleId.PLANET, 0.86f, 0.90f, 0.20f, 12f, ink(acc[2], acc[1]))
            put(DoodleId.LAVENDER, 0.09f, 0.93f, 0.24f, -6f, ink(acc[0], LeafGreen), sticker = false)
            put(DoodleId.LAVENDER, 0.22f, 0.955f, 0.20f, 8f, ink(acc[1], LeafGreen), sticker = false)
        }
    }
}

internal val WashiPlanner: AppTheme by lazy {
    AppTheme(
        id = "washi_planner",
        nameRes = R.string.theme_washi_planner_name,
        descriptionRes = R.string.theme_washi_planner_desc,
        light = paletteMode(WashiLight, dark = false),
        dark = paletteMode(WashiDark, dark = true),
        titleFont = checkNotNull(themeArt("washi_planner")).titleFont,
        headerStyle = HeaderStyle.PLAIN,
        decoration = WashiDeco,
    )
}

internal val SunnyJournal: AppTheme by lazy {
    AppTheme(
        id = "sunny_journal",
        nameRes = R.string.theme_sunny_journal_name,
        descriptionRes = R.string.theme_sunny_journal_desc,
        light = paletteMode(SunnyLight, dark = false),
        dark = paletteMode(SunnyDark, dark = true),
        titleFont = checkNotNull(themeArt("sunny_journal")).titleFont,
        headerStyle = HeaderStyle.PLAIN,
        decoration = SunnyDeco,
    )
}

internal val MatchaTea: AppTheme by lazy {
    AppTheme(
        id = "matcha_tea",
        nameRes = R.string.theme_matcha_tea_name,
        descriptionRes = R.string.theme_matcha_tea_desc,
        light = paletteMode(MatchaLight, dark = false),
        dark = paletteMode(MatchaDark, dark = true),
        titleFont = checkNotNull(themeArt("matcha_tea")).titleFont,
        headerStyle = HeaderStyle.PLAIN,
        decoration = MatchaDeco,
    )
}

internal val LavenderMoon: AppTheme by lazy {
    AppTheme(
        id = "lavender_moon",
        nameRes = R.string.theme_lavender_moon_name,
        descriptionRes = R.string.theme_lavender_moon_desc,
        light = paletteMode(LavenderLight, dark = false),
        dark = paletteMode(LavenderDark, dark = true),
        titleFont = checkNotNull(themeArt("lavender_moon")).titleFont,
        headerStyle = HeaderStyle.PLAIN,
        decoration = LavenderDeco,
    )
}
