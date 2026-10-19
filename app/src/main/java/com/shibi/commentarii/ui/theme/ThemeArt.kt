package com.shibi.commentarii.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shibi.commentarii.R
import kotlin.math.cos
import kotlin.math.sin

// Themes v2 art spec (docs/plans/themes2-plan.md, S1): bundled OFL fonts, per-theme art tables,
// Bezier glyphs, the multicolour hand-lettered title and the tab/swash/stamp section labels.

object ArtFonts {
    val PatrickHand = FontFamily(Font(R.font.patrick_hand))
    val Pacifico = FontFamily(Font(R.font.pacifico))
    val Sniglet = FontFamily(Font(R.font.sniglet_extrabold, FontWeight.ExtraBold))
    val Dancing = ScriptFont
}

enum class LabelStyle { ROSE, PILL, SWASH, STAMP }
enum class TitleColoring { PER_WORD, PER_LETTER, PER_LETTER_GRADIENT, PER_LETTER_OUTLINED }
enum class Glyph { DOT, CIRCLE, HEART, STAR, SPARKLE, FLOWER, LEAF, CLOUD, MOON, DIAMOND }
enum class Paper { NONE, DOT_GRID, LINED, SPECKLE, STARS }

/** [decoAlpha] scales doodles (1.0 light / 0.6 dark); paper textures use 0.35 light / 0.18 dark. */
@Immutable
class ArtColors(
    val accents: List<Color>,
    val ink: Color,
    val title: List<Color>,
    val outline: Color?,
    val paper: Color,
    val decoAlpha: Float,
    val cardEdge: Color? = null,
)

@Immutable
class ThemeArt(
    val titleFont: FontFamily,
    val titleColoring: TitleColoring,
    val titleTrail: Glyph?,
    val labelStyle: LabelStyle,
    val labelFont: FontFamily,
    val labelTrail: Glyph?,
    val bullets: List<Glyph>,
    val check: Glyph,
    val paper: Paper,
    val tape: Boolean,
    val cardSalt: Int,
    val light: ArtColors,
    val dark: ArtColors,
)

private fun rgb(v: Int) = Color(0xFF000000L or v.toLong())

@Suppress("LongParameterList")
private fun art(
    titleFont: FontFamily, coloring: TitleColoring, titleTrail: Glyph, label: LabelStyle, labelTrail: Glyph,
    bullets: List<Glyph>, check: Glyph, paper: Paper, tape: Boolean,
    accents: List<Int>, ink: Int, title: List<Int>, outline: Int?, paperHex: Int, darkBg: Int,
    edge: Int? = null,
): ThemeArt {
    val inkC = rgb(ink)
    val bgD = rgb(darkBg)
    return ThemeArt(
        titleFont, coloring, titleTrail, label, ArtFonts.PatrickHand, labelTrail, bullets, check, paper, tape, 0,
        light = ArtColors(accents.map(::rgb), inkC, title.map(::rgb), outline?.let(::rgb), rgb(paperHex), 1f, edge?.let(::rgb)),
        dark = ArtColors(
            accents = accents.map { lerp(rgb(it), bgD, 0.60f) },
            ink = lerp(Color.White, inkC, 0.06f),
            title = title.map { lerp(rgb(it), Color.White, 0.45f) },
            outline = outline?.let { lerp(Color.White, rgb(it), 0.06f) },
            paper = lerp(rgb(paperHex), bgD, 0.5f),
            decoAlpha = 0.6f,
            cardEdge = edge?.let { lerp(rgb(it), bgD, 0.55f) },
        ),
    )
}

private val arts: Map<String, ThemeArt> by lazy {
    mapOf(
        "washi_planner" to art(
            ArtFonts.Sniglet, TitleColoring.PER_WORD, Glyph.SPARKLE, LabelStyle.PILL, Glyph.HEART,
            listOf(Glyph.CIRCLE, Glyph.HEART, Glyph.STAR), Glyph.HEART, Paper.DOT_GRID, true,
            listOf(0xF9C9D6, 0xDCCFF5, 0xC6EBDA, 0xFCEBA8, 0xC7E3F8), 0x2E2633,
            listOf(0x2E2633, 0xE06C66, 0x2E9C7A), null, 0xE4DEEA, 0x221E26,
        ),
        "sunny_journal" to art(
            ArtFonts.Sniglet, TitleColoring.PER_LETTER_OUTLINED, Glyph.HEART, LabelStyle.SWASH, Glyph.HEART,
            listOf(Glyph.HEART, Glyph.STAR, Glyph.FLOWER), Glyph.FLOWER, Paper.SPECKLE, true,
            listOf(0xFFD3B0, 0xFFF0A0, 0xCDEFC0, 0xBFD9FF, 0xDCCBFF, 0xFFC9DE), 0x33291F,
            listOf(0xF59AB5, 0xF7CF4A, 0x8FD48A, 0x8EB8F5, 0xB79CF2), 0x33291F, 0xC9B89A, 0x24201A, edge = 0xD9C9A8,
        ),
        "cloud_drift" to art(
            ArtFonts.Pacifico, TitleColoring.PER_LETTER, Glyph.SPARKLE, LabelStyle.PILL, Glyph.SPARKLE,
            listOf(Glyph.CLOUD), Glyph.CLOUD, Paper.NONE, false,
            listOf(0xCFE5F8, 0xE3DAF6, 0xFDE1E6, 0xD8F1E4, 0xFFF1C7), 0x1F2A36,
            listOf(0x3F6E9E, 0x7A5BB0, 0xB0527A), null, 0xDCEEFC, 0x141C26,
        ),
        "petal_fall" to art(
            ArtFonts.Pacifico, TitleColoring.PER_WORD, Glyph.FLOWER, LabelStyle.SWASH, Glyph.FLOWER,
            listOf(Glyph.FLOWER), Glyph.FLOWER, Paper.SPECKLE, false,
            listOf(0xF9CFD8, 0xFBE3E8, 0xE9D5C9, 0xE5D9F2, 0xD9EBDD), 0x3A2328,
            listOf(0xB04A63, 0xA0603F), null, 0xF2D6DC, 0x241A1D,
        ),
        "floating_hearts" to art(
            ArtFonts.Pacifico, TitleColoring.PER_LETTER, Glyph.HEART, LabelStyle.PILL, Glyph.HEART,
            listOf(Glyph.HEART), Glyph.HEART, Paper.DOT_GRID, false,
            listOf(0xE6D6F7, 0xF8D3E6, 0xD3E9F7, 0xFDE7C8, 0xD8F2E2), 0x2D2438,
            listOf(0x8656B0, 0xC2477A, 0xA04A9E), null, 0xEADFF5, 0x1D1826,
        ),
        "matcha_tea" to art(
            ArtFonts.Sniglet, TitleColoring.PER_WORD, Glyph.LEAF, LabelStyle.STAMP, Glyph.LEAF,
            listOf(Glyph.LEAF), Glyph.CIRCLE, Paper.LINED, true,
            listOf(0xD4E4B8, 0xEDE3C8, 0xC9DCC9, 0xF2D6C9, 0xE8E0F0), 0x26301F,
            listOf(0x4F6B2E, 0x8A5A3A), null, 0xDDE3CC, 0x1B2017,
        ),
        "lavender_moon" to art(
            ArtFonts.Pacifico, TitleColoring.PER_LETTER_GRADIENT, Glyph.STAR, LabelStyle.STAMP, Glyph.STAR,
            listOf(Glyph.STAR), Glyph.STAR, Paper.STARS, false,
            listOf(0xDCD3F2, 0xC9D6F2, 0xF2D3E4, 0xD6EEF0, 0xF6E7C1), 0x272235,
            listOf(0x6A5AA8, 0x3F5A9E), null, 0xC9BEE8, 0x16141F,
        ),
    )
}

/** Art for a theme id; null for rose_cream (and unknown ids), which keeps its original look. */
fun themeArt(id: String): ThemeArt? = arts[id]

/** Art + colours for the active theme and light/dark mode, or null on Rose & Cream. */
@Composable
fun currentArt(): Pair<ThemeArt, ArtColors>? {
    val id = LocalAppTheme.current.id
    val art = remember(id) { themeArt(id) } ?: return null
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(art, dark) { art to (if (dark) art.dark else art.light) }
}

fun accentDeep(accent: Color, ink: Color): Color = lerp(accent, ink, 0.45f)

// ---------------------------------------------------------------------------------------------
// Glyphs: 100-unit Bezier paths, built once.

private fun roundedPoly(pts: List<Offset>): Path = Path().apply {
    val n = pts.size
    fun mid(a: Offset, b: Offset) = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    fun toward(m: Offset, v: Offset) = Offset(m.x + (v.x - m.x) * 0.667f, m.y + (v.y - m.y) * 0.667f)
    val start = mid(pts[n - 1], pts[0])
    moveTo(start.x, start.y)
    for (i in 0 until n) {
        val v = pts[i]
        val a = mid(pts[(i + n - 1) % n], v)
        val b = mid(v, pts[(i + 1) % n])
        val c1 = toward(a, v)
        val c2 = toward(b, v)
        cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
    }
    close()
}

private fun circlePath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    val k = 0.5523f * r
    moveTo(cx + r, cy)
    cubicTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
    cubicTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
    cubicTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
    cubicTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
    close()
}

private val glyphPaths: Map<Glyph, Path> by lazy {
    mapOf(
        Glyph.DOT to circlePath(50f, 50f, 24f),
        Glyph.CIRCLE to circlePath(50f, 50f, 42f),
        Glyph.HEART to Path().apply {
            moveTo(50f, 90f)
            cubicTo(8f, 62f, 2f, 30f, 25f, 17f)
            cubicTo(40f, 9f, 50f, 19f, 50f, 30f)
            cubicTo(50f, 19f, 60f, 9f, 75f, 17f)
            cubicTo(98f, 30f, 92f, 62f, 50f, 90f)
            close()
        },
        Glyph.STAR to roundedPoly(
            List(10) { i ->
                val r = if (i % 2 == 0) 47f else 23f
                val a = Math.toRadians(-90.0 + i * 36.0)
                Offset(50f + r * cos(a).toFloat(), 53f + r * sin(a).toFloat())
            },
        ),
        Glyph.DIAMOND to roundedPoly(listOf(Offset(50f, 6f), Offset(90f, 50f), Offset(50f, 94f), Offset(10f, 50f))),
        Glyph.SPARKLE to Path().apply {
            moveTo(50f, 4f)
            cubicTo(52f, 34f, 66f, 48f, 96f, 50f)
            cubicTo(66f, 52f, 52f, 66f, 50f, 96f)
            cubicTo(48f, 66f, 34f, 52f, 4f, 50f)
            cubicTo(34f, 48f, 48f, 34f, 50f, 4f)
            close()
        },
        Glyph.FLOWER to Path().apply {
            for (k in 0 until 5) {
                val a = Math.toRadians(-90.0 + k * 72.0)
                val dx = cos(a).toFloat()
                val dy = sin(a).toFloat()
                val nx = -dy
                val ny = dx
                fun p(along: Float, side: Float) = Offset(50f + dx * along + nx * side, 50f + dy * along + ny * side)
                val tip = p(46f, 0f)
                val a1 = p(12f, 24f)
                val a2 = p(38f, 24f)
                val b1 = p(38f, -24f)
                val b2 = p(12f, -24f)
                moveTo(50f, 50f)
                cubicTo(a1.x, a1.y, a2.x, a2.y, tip.x, tip.y)
                cubicTo(b1.x, b1.y, b2.x, b2.y, 50f, 50f)
                close()
            }
        },
        Glyph.LEAF to Path().apply {
            moveTo(10f, 90f)
            cubicTo(6f, 42f, 38f, 8f, 92f, 8f)
            cubicTo(94f, 62f, 58f, 94f, 10f, 90f)
            close()
        },
        Glyph.CLOUD to Path().apply {
            moveTo(24f, 78f)
            cubicTo(4f, 78f, 2f, 52f, 22f, 50f)
            cubicTo(20f, 26f, 48f, 18f, 58f, 34f)
            cubicTo(70f, 20f, 96f, 32f, 88f, 54f)
            cubicTo(106f, 58f, 98f, 78f, 80f, 78f)
            close()
        },
        Glyph.MOON to Path().apply {
            moveTo(66f, 8f)
            cubicTo(28f, 12f, 8f, 42f, 16f, 68f)
            cubicTo(26f, 96f, 62f, 98f, 88f, 76f)
            cubicTo(52f, 82f, 36f, 52f, 50f, 28f)
            cubicTo(54f, 20f, 60f, 13f, 66f, 8f)
            close()
        },
    )
}

/** Soft highlight blob centre per glyph (null = none); FLOWER and LEAF are special-cased in [drawGlyph]. */
private fun highlightAt(g: Glyph): Offset? = when (g) {
    Glyph.HEART -> Offset(30f, 34f)
    Glyph.STAR -> Offset(40f, 38f)
    Glyph.CIRCLE -> Offset(33f, 32f)
    Glyph.CLOUD -> Offset(32f, 54f)
    else -> null
}

private val white55 = Color.White.copy(alpha = 0.55f)

fun DrawScope.drawGlyph(g: Glyph, center: Offset, size: Float, color: Color, filled: Boolean = true) {
    val path = glyphPaths[g] ?: return
    val s = size / 100f
    withTransform({
        translate(center.x - size / 2f, center.y - size / 2f)
        scale(s, s, Offset.Zero)
    }) {
        if (!filled) {
            drawPath(path, color, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            return@withTransform
        }
        drawPath(path, color)
        when (g) {
            Glyph.FLOWER -> drawPath(circlePath(50f, 50f, 11f), Color.White.copy(alpha = 0.8f))
            Glyph.LEAF -> drawLine(white55, Offset(14f, 86f), Offset(72f, 28f), strokeWidth = 4f, cap = StrokeCap.Round)
            else -> highlightAt(g)?.let { at ->
                rotate(-35f, pivot = at) {
                    drawOval(white55, Offset(at.x - 10f, at.y - 5f), Size(20f, 10f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Hand-lettered title.

private fun buildTitle(text: String, mode: TitleColoring, cols: List<Color>): AnnotatedString = buildAnnotatedString {
    var wordIdx = -1
    var inWord = false
    var letterIdx = 0
    val letters = text.count { it.isLetterOrDigit() }
    for (ch in text) {
        if (ch.isWhitespace()) {
            inWord = false
            append(ch)
            continue
        }
        if (!inWord) {
            inWord = true
            wordIdx++
        }
        val idx = if (ch.isLetterOrDigit()) letterIdx else maxOf(letterIdx - 1, 0)
        val col = when (mode) {
            TitleColoring.PER_WORD -> cols[wordIdx % cols.size]
            TitleColoring.PER_LETTER, TitleColoring.PER_LETTER_OUTLINED -> cols[idx % cols.size]
            TitleColoring.PER_LETTER_GRADIENT ->
                lerp(cols.first(), cols.last(), if (letters > 1) idx.toFloat() / (letters - 1) else 0f)
        }
        if (ch.isLetterOrDigit()) letterIdx++
        withStyle(SpanStyle(color = col)) { append(ch) }
    }
}

/** Page title. On Rose & Cream this is exactly `Text(text, style, modifier)`. */
@Composable
fun ThemedTitle(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    val pair = currentArt()
    if (pair == null) {
        Text(text, style = style, modifier = modifier)
        return
    }
    val (art, c) = pair
    val annotated = remember(text, art, c) {
        // Keep the last two words together ("Oct 18") so a day number never wraps alone.
        val cut = text.lastIndexOf(' ')
        val glued = if (cut > 0) text.replaceRange(cut, cut + 1, " ") else text
        buildTitle(glued, art.titleColoring, c.title)
    }
    // Auto-shrink so the title stays on one line (min 28sp); resets when the text changes.
    var shrink by remember(text) { mutableStateOf(1f) }
    val baseSp = if (style.fontSize.isSpecified) style.fontSize.value else 36f
    val textStyle = style.copy(
        fontFamily = art.titleFont, brush = null, lineHeight = TextUnit.Unspecified,
        fontSize = (baseSp * shrink).sp,
    )
    val minShrink = minOf(1f, 28f / baseSp)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val onLayout: (TextLayoutResult) -> Unit = {
        layout = it
        if ((it.lineCount > 1 || it.didOverflowWidth) && shrink > minShrink) {
            shrink = maxOf(shrink * 0.92f, minShrink)
        }
    }
    val density = LocalDensity.current
    val trail = art.titleTrail
    val trailColor = c.title.getOrElse(1) { c.title.first() }
    val decorated = modifier.drawWithContent {
        drawContent()
        val l = layout
        if (trail != null && l != null && l.lineCount > 0) {
            val last = l.lineCount - 1
            val gs = (l.getLineBottom(last) - l.getLineTop(last)) * 0.55f
            val cx = l.getLineRight(last) + with(density) { 8.dp.toPx() } + gs / 2f
            if (cx + gs / 2f <= size.width) {
                val cy = (l.getLineTop(last) + l.getLineBottom(last)) / 2f
                drawGlyph(trail, Offset(cx, cy), gs, trailColor)
            }
        }
    }
    val outline = c.outline
    if (art.titleColoring == TitleColoring.PER_LETTER_OUTLINED && outline != null) {
        val strokePx = with(density) { 3.2.dp.toPx() } // half hides under the fill: 1.6dp visible
        Box(decorated) {
            Text(
                text,
                style = textStyle.copy(
                    color = outline,
                    drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
                ),
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(annotated, style = textStyle, maxLines = 1, softWrap = false, onTextLayout = onLayout)
        }
    } else {
        Text(annotated, style = textStyle, modifier = decorated, maxLines = 1, softWrap = false, onTextLayout = onLayout)
    }
}

// ---------------------------------------------------------------------------------------------
// Section labels.

private fun swashPath(w: Float, h: Float): Path = Path().apply {
    moveTo(0f, h * 0.62f)
    cubicTo(w * 0.01f, h * 0.20f, w * 0.05f, h * 0.04f, w * 0.14f, h * 0.10f)
    cubicTo(w * 0.40f, h * 0.00f, w * 0.70f, h * 0.10f, w * 0.93f, h * 0.04f)
    cubicTo(w * 0.99f, h * 0.08f, w * 0.995f, h * 0.30f, w * 0.985f, h * 0.58f)
    cubicTo(w * 0.99f, h * 0.85f, w * 0.95f, h * 0.98f, w * 0.86f, h * 0.94f)
    cubicTo(w * 0.60f, h * 1.00f, w * 0.30f, h * 0.92f, w * 0.12f, h * 0.97f)
    cubicTo(w * 0.04f, h * 0.98f, 0f, h * 0.85f, 0f, h * 0.62f)
    close()
}

/** Section label: tab pill, highlighter swash or stitched stamp. Plain text when no art is active. */
@Composable
fun ThemedSectionLabel(title: String, accent: Color, modifier: Modifier = Modifier) {
    val pair = currentArt()
    if (pair == null) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        return
    }
    val (art, c) = pair
    val deep = accentDeep(accent, c.ink)
    val swash = art.labelStyle == LabelStyle.SWASH
    val textStyle = MaterialTheme.typography.titleLarge.copy(
        fontFamily = art.labelFont,
        fontSize = if (swash) 15.sp else 14.sp,
        letterSpacing = if (swash) 0.12.em else 0.14.em,
        color = c.ink,
    )
    val darkMode = c.ink.luminance() > 0.5f
    val trail = art.labelTrail ?: Glyph.HEART
    val capsule = RoundedCornerShape(50)
    val shell = when (art.labelStyle) {
        LabelStyle.PILL, LabelStyle.ROSE -> modifier
            .zIndex(1f)
            .offset(12.dp, 7.dp)
            .clip(capsule)
            .background(accent)
            .border(1.dp, lerp(accent, c.ink, 0.25f), capsule)
        LabelStyle.STAMP -> modifier
            .zIndex(1f)
            .offset(12.dp, 7.dp)
            .clip(capsule)
            .background(if (darkMode) accent else lerp(accent, Color.White, 0.2f))
            .drawBehind {
                val sw = 1.5.dp.toPx()
                drawRoundRect(
                    lerp(deep, c.ink, 0.3f),
                    topLeft = Offset(sw / 2f, sw / 2f),
                    size = Size(size.width - sw, size.height - sw),
                    cornerRadius = CornerRadius(size.height / 2f),
                    style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                )
            }
        LabelStyle.SWASH -> modifier.drawWithCache {
            val ext = 6.dp.toPx()
            val grain = 3.dp.toPx()
            val main = swashPath(size.width + 2 * ext, size.height)
            val fine = swashPath(size.width + 2 * ext - 2 * grain, size.height - 2 * grain)
            val fullW = size.width + 2 * ext
            val tails = Path().apply {
                // three tapered bristle tails fraying off the right end
                for (i in 0..2) {
                    val y = size.height * (0.28f + 0.22f * i)
                    val len = (5f + 3f * (2 - i)) * density
                    moveTo(fullW - 2f * density, y - 1.6f * density)
                    cubicTo(fullW + len * 0.4f, y - 1.2f * density, fullW + len * 0.8f, y - 0.4f * density, fullW + len, y)
                    cubicTo(fullW + len * 0.8f, y + 0.5f * density, fullW + len * 0.4f, y + 1.2f * density, fullW - 2f * density, y + 1.6f * density)
                    close()
                }
            }
            onDrawBehind {
                withTransform({
                    translate(-ext, 0f)
                    rotate(-3f, Offset(fullW / 2f, size.height / 2f))
                }) {
                    drawPath(main, accent.copy(alpha = 0.80f))
                    drawPath(tails, accent.copy(alpha = 0.70f))
                    withTransform({ translate(grain, grain) }) { drawPath(fine, accent.copy(alpha = 0.45f)) }
                }
            }
        }
    }
    Row(shell.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(),
            style = textStyle,
            modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(title) },
        )
        Spacer(Modifier.size(6.dp))
        Spacer(
            Modifier
                .size(11.dp)
                .drawBehind { drawGlyph(trail, Offset(size.width / 2f, size.height / 2f), size.width, deep) },
        )
    }
}
