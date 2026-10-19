package com.shibi.commentarii.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// Static themes: palettes from docs/plans/themes-plan.md; doodles are vector-only, fractions of size.

private val WashiLight = Palette(
    0xFFFFF9F4, 0xFF2E2633, 0xFFA0527A, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE6D9E6,
    listOf(0xFFF8C8D8, 0xFFD9CCF2, 0xFFC8EBD9, 0xFFFBEBB0, 0xFFC6E2F7), 0xFF2E2633,
    listOf(0xFFC2477A, 0xFF7E5BB5, 0xFF2E8A66, 0xFF9A6A0A, 0xFF3B7BB8),
)
private val WashiDark = Palette(
    0xFF221E26, 0xFFF1EAF2, 0xFFF2B6CF, 0xFF3A1A2A, 0xFF2C2731, 0xFF4A4250,
    listOf(0xFF7A4A5E, 0xFF5A4E7A, 0xFF3F6B58, 0xFF7A6A32, 0xFF3E5E7A), 0xFFFFF6FA,
    listOf(0xFFF7B9D0, 0xFFCDBDF5, 0xFFAEE3C8, 0xFFF6E3A0, 0xFFB5D8F5),
)
private val SunnyLight = Palette(
    0xFFFDF8EC, 0xFF33291F, 0xFFB5582E, 0xFFFFFFFF, 0xFFFFFDF6, 0xFFEADFC8,
    listOf(0xFFFFD6A5, 0xFFFDFFB6, 0xFFCAFFBF, 0xFFA0C4FF, 0xFFBDB2FF, 0xFFFFC6FF), 0xFF33291F,
    listOf(0xFFC4513A, 0xFF9C6F00, 0xFF3E8E4F, 0xFF3A6FB8, 0xFF7B5AB8),
)
private val SunnyDark = Palette(
    0xFF24201A, 0xFFF3EBDD, 0xFFF4B98E, 0xFF3D2410, 0xFF2E2921, 0xFF4B4336,
    listOf(0xFF8A5A3A, 0xFF6E6C30, 0xFF4A7A44, 0xFF3E5A86, 0xFF5E5290, 0xFF7E4A7E), 0xFFFFF8EE,
    listOf(0xFFFFC9A0, 0xFFF3E58A, 0xFFB6E8A8, 0xFFA8C8F5, 0xFFCBBEF5),
)
private val MatchaLight = Palette(
    0xFFF6F7EF, 0xFF26301F, 0xFF5C7A3A, 0xFFFFFFFF, 0xFFFFFFFA, 0xFFDCE2CC,
    listOf(0xFFD4E4B8, 0xFFEDE3C8, 0xFFC9DCC9), 0xFF26301F, emptyList(),
)
private val MatchaDark = Palette(
    0xFF1B2017, 0xFFE8EEDD, 0xFFB7D38F, 0xFF24361A, 0xFF242B1F, 0xFF3F4A36,
    listOf(0xFF4E6436, 0xFF6A5E40, 0xFF3E5A44), 0xFFF5FAEE, emptyList(),
)
private val LavenderLight = Palette(
    0xFFF5F2FA, 0xFF272235, 0xFF6A5AA8, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFDDD6EC,
    listOf(0xFFDCD3F2, 0xFFC9D6F2, 0xFFF2D3E4), 0xFF272235,
    listOf(0xFF6A5AA8, 0xFF3F5A9E),
)
private val LavenderDark = Palette(
    0xFF16141F, 0xFFECE8F6, 0xFFC3B6F2, 0xFF2A2058, 0xFF201D2C, 0xFF3D3850,
    listOf(0xFF4A3F78, 0xFF34486E, 0xFF6A4060), 0xFFF7F4FF,
    listOf(0xFFC3B6F2, 0xFFB5C8F5),
)

/** Every static painter shares this sizing: [u] is the base element size in px, [n] scales counts on wide screens. */
private class Metrics(size: Size, density: Density, dark: Boolean) {
    val w = size.width
    val h = size.height
    val dp = density.density
    private val minDim = minOf(w, h)
    val widthScale = (w / (412f * dp)).coerceIn(1f, 2f)
    val alpha = if (dark) 0.22f else 0.45f
    fun u(k: Float): Float = (minDim * k).coerceIn(16f * dp, 96f * dp)
    fun n(base: Int): Int = (base * widthScale).toInt().coerceAtLeast(1)
}

private fun heartPath(cx: Float, cy: Float, s: Float): Path = Path().apply {
    moveTo(cx, cy + s * 0.9f)
    cubicTo(cx - s * 1.6f, cy - s * 0.2f, cx - s * 0.7f, cy - s * 1.2f, cx, cy - s * 0.4f)
    cubicTo(cx + s * 0.7f, cy - s * 1.2f, cx + s * 1.6f, cy - s * 0.2f, cx, cy + s * 0.9f)
    close()
}

private fun starPath(cx: Float, cy: Float, r: Float, points: Int, inner: Float): Path = Path().apply {
    val total = points * 2
    for (i in 0 until total) {
        val rad = if (i % 2 == 0) r else r * inner
        val a = -PI / 2 + i * PI / points
        val x = cx + (cos(a) * rad).toFloat()
        val y = cy + (sin(a) * rad).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun DrawScope.leaf(cx: Float, cy: Float, len: Float, angle: Float, color: Color, rib: Boolean) {
    rotate(angle, Offset(cx, cy)) {
        val p = Path().apply {
            moveTo(cx - len / 2, cy)
            quadraticTo(cx, cy - len * 0.35f, cx + len / 2, cy)
            quadraticTo(cx, cy + len * 0.35f, cx - len / 2, cy)
            close()
        }
        drawPath(p, color)
        if (rib) drawLine(color.copy(alpha = color.alpha * 0.9f + 0.1f), Offset(cx - len / 2, cy), Offset(cx + len / 2, cy), len * 0.03f)
    }
}

private fun DrawScope.steam(x: Float, y: Float, hgt: Float, color: Color, stroke: Float) {
    val p = Path().apply {
        moveTo(x, y)
        cubicTo(x - hgt * 0.3f, y - hgt * 0.3f, x + hgt * 0.3f, y - hgt * 0.6f, x, y - hgt)
    }
    drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round))
}

private fun DrawScope.cloudPuff(cx: Float, cy: Float, s: Float, color: Color) {
    drawCircle(color, s * 0.5f, Offset(cx - s * 0.45f, cy))
    drawCircle(color, s * 0.65f, Offset(cx, cy - s * 0.2f))
    drawCircle(color, s * 0.5f, Offset(cx + s * 0.45f, cy))
}

private val Pink = Color(0xFFE88FB0)
private val Lilac = Color(0xFFB59CE0)
private val Mint = Color(0xFF7CCBA6)
private val Butter = Color(0xFFF2CF5E)
private val Sky = Color(0xFF7FB4E6)
private val Peach = Color(0xFFF2A476)

private val WashiDeco = ThemeDecoration { size, density, dark ->
    val m = Metrics(size, density, dark)
    DecorationFrame {
        val a = m.alpha
        // Top-corner washi strips.
        val sw = m.u(0.28f)
        val sh = sw * 0.22f
        rotate(-12f, Offset(m.w * 0.12f, m.h * 0.03f)) {
            drawRect(Pink.copy(alpha = a), Offset(m.w * 0.12f - sw / 2, m.h * 0.03f - sh / 2), Size(sw, sh))
        }
        rotate(14f, Offset(m.w * 0.9f, m.h * 0.04f)) {
            drawRect(Mint.copy(alpha = a), Offset(m.w * 0.9f - sw / 2, m.h * 0.04f - sh / 2), Size(sw, sh))
        }
        // Scattered hearts / stars / clouds.
        val hearts = listOf(0.08f to 0.22f, 0.93f to 0.30f, 0.12f to 0.52f, 0.88f to 0.60f, 0.5f to 0.94f, 0.2f to 0.78f)
        val cols = listOf(Pink, Lilac, Peach, Pink, Lilac, Peach)
        for (i in 0 until m.n(hearts.size).coerceAtMost(hearts.size * 2)) {
            val (fx, fy) = hearts[i % hearts.size]
            val ox = if (i >= hearts.size) 0.05f else 0f
            val s = m.u(0.03f + 0.008f * (i % 3))
            drawPath(heartPath(m.w * (fx + ox).coerceAtMost(0.97f), m.h * fy, s), cols[i % cols.size].copy(alpha = a))
        }
        val stars = listOf(0.06f to 0.38f, 0.95f to 0.46f, 0.82f to 0.84f, 0.3f to 0.12f)
        for (i in 0 until m.n(stars.size).coerceAtMost(stars.size * 2)) {
            val (fx, fy) = stars[i % stars.size]
            val ox = if (i >= stars.size) 0.04f else 0f
            drawPath(starPath(m.w * (fx + ox).coerceAtMost(0.97f), m.h * (fy + ox), m.u(0.035f), 5, 0.45f), Butter.copy(alpha = a))
        }
        cloudPuff(m.w * 0.78f, m.h * 0.14f, m.u(0.06f), Sky.copy(alpha = a * 0.8f))
        cloudPuff(m.w * 0.14f, m.h * 0.66f, m.u(0.05f), Sky.copy(alpha = a * 0.8f))
        // Bear face, bottom-right.
        val br = m.u(0.07f)
        val bc = Offset(m.w * 0.9f, m.h * 0.93f)
        val brown = Color(0xFFC9906A).copy(alpha = a + 0.1f)
        drawCircle(brown, br * 0.4f, bc + Offset(-br * 0.7f, -br * 0.7f))
        drawCircle(brown, br * 0.4f, bc + Offset(br * 0.7f, -br * 0.7f))
        drawCircle(brown, br, bc)
        val dot = Color(0xFF5A3A2A).copy(alpha = a + 0.2f)
        drawCircle(dot, br * 0.1f, bc + Offset(-br * 0.35f, -br * 0.15f))
        drawCircle(dot, br * 0.1f, bc + Offset(br * 0.35f, -br * 0.15f))
        drawCircle(dot, br * 0.14f, bc + Offset(0f, br * 0.2f))
        // Potted plant, bottom-left.
        val px = m.w * 0.08f
        val py = m.h * 0.94f
        val ps = m.u(0.06f)
        val pot = Path().apply {
            moveTo(px - ps * 0.8f, py - ps * 0.9f); lineTo(px + ps * 0.8f, py - ps * 0.9f)
            lineTo(px + ps * 0.55f, py); lineTo(px - ps * 0.55f, py); close()
        }
        drawPath(pot, Peach.copy(alpha = a + 0.1f))
        val leafC = Mint.copy(alpha = a + 0.15f)
        leaf(px, py - ps * 1.9f, ps * 1.6f, -90f, leafC, true)
        leaf(px - ps * 0.7f, py - ps * 1.5f, ps * 1.3f, -150f, leafC, true)
        leaf(px + ps * 0.7f, py - ps * 1.5f, ps * 1.3f, -30f, leafC, true)
    }
}

private val SunnyDeco = ThemeDecoration { size, density, dark ->
    val m = Metrics(size, density, dark)
    DecorationFrame {
        val a = m.alpha
        // Smiling sun, top-right.
        val sr = m.u(0.05f)
        val sc = Offset(m.w * 0.86f, m.h * 0.07f)
        val sun = Butter.copy(alpha = a + 0.15f)
        for (i in 0 until 10) {
            val ang = i * 2 * PI / 10
            val d = Offset(cos(ang).toFloat(), sin(ang).toFloat())
            drawLine(sun, sc + d * (sr * 1.3f), sc + d * (sr * 1.75f), sr * 0.14f, StrokeCap.Round)
        }
        drawCircle(sun, sr, sc)
        val face = Color(0xFF8A5A20).copy(alpha = a + 0.3f)
        drawCircle(face, sr * 0.09f, sc + Offset(-sr * 0.35f, -sr * 0.2f))
        drawCircle(face, sr * 0.09f, sc + Offset(sr * 0.35f, -sr * 0.2f))
        drawArc(face, 20f, 140f, false, sc + Offset(-sr * 0.45f, -sr * 0.35f), Size(sr * 0.9f, sr * 0.9f), style = Stroke(sr * 0.08f, cap = StrokeCap.Round))
        cloudPuff(m.w * 0.62f, m.h * 0.1f, m.u(0.05f), Sky.copy(alpha = a * 0.8f))
        // Leaf sprigs along the left edge.
        val leafC = Color(0xFF6DB96F).copy(alpha = a + 0.1f)
        val ls = m.u(0.07f)
        for (i in 0 until m.n(4)) {
            val fy = 0.2f + 0.2f * (i % 4) + 0.03f * (i / 4)
            leaf(m.w * 0.03f, m.h * fy, ls, -35f, leafC, true)
            leaf(m.w * 0.03f, m.h * fy + ls * 0.4f, ls * 0.8f, 35f, leafC, true)
        }
        // Candle, right edge.
        val cx = m.w * 0.94f
        val cb = m.h * 0.55f
        val cw = m.u(0.025f)
        val ch = cw * 3f
        drawRoundRect(Color(0xFFFFF0C8).copy(alpha = a + 0.2f), Offset(cx - cw / 2, cb - ch), Size(cw, ch), androidx.compose.ui.geometry.CornerRadius(cw * 0.2f))
        val flame = Path().apply {
            moveTo(cx, cb - ch - cw * 1.4f)
            quadraticTo(cx + cw * 0.7f, cb - ch - cw * 0.5f, cx, cb - ch - cw * 0.1f)
            quadraticTo(cx - cw * 0.7f, cb - ch - cw * 0.5f, cx, cb - ch - cw * 1.4f)
            close()
        }
        drawPath(flame, Peach.copy(alpha = a + 0.3f))
        // Mug with steam, bottom-left.
        val mx = m.w * 0.1f
        val my = m.h * 0.93f
        val ms = m.u(0.06f)
        val mugC = Color(0xFFD98A6A).copy(alpha = a + 0.1f)
        drawRoundRect(mugC, Offset(mx - ms * 0.6f, my - ms * 1.1f), Size(ms * 1.2f, ms * 1.1f), androidx.compose.ui.geometry.CornerRadius(ms * 0.2f))
        drawArc(mugC, -90f, 180f, false, Offset(mx + ms * 0.4f, my - ms * 0.9f), Size(ms * 0.7f, ms * 0.7f), style = Stroke(ms * 0.14f))
        val st = Color(0xFF8A7A66).copy(alpha = a)
        steam(mx - ms * 0.2f, my - ms * 1.2f, ms * 0.9f, st, ms * 0.07f)
        steam(mx + ms * 0.2f, my - ms * 1.2f, ms * 0.9f, st, ms * 0.07f)
        // Hearts.
        val hs = listOf(0.45f to 0.2f, 0.9f to 0.38f, 0.15f to 0.45f, 0.8f to 0.82f)
        for (i in 0 until m.n(hs.size).coerceAtMost(hs.size * 2)) {
            val (fx, fy) = hs[i % hs.size]
            val o = if (i >= hs.size) 0.06f else 0f
            drawPath(heartPath(m.w * (fx - o), m.h * (fy + o), m.u(0.025f)), Pink.copy(alpha = a))
        }
    }
}

private val MatchaDeco = ThemeDecoration { size, density, dark ->
    val m = Metrics(size, density, dark)
    DecorationFrame {
        val a = m.alpha
        val leafC = Color(0xFF7FA650).copy(alpha = a)
        val spots = listOf(
            Triple(0.06f, 0.14f, -40f), Triple(0.93f, 0.2f, 30f), Triple(0.08f, 0.5f, 55f),
            Triple(0.92f, 0.58f, -50f), Triple(0.15f, 0.82f, 20f), Triple(0.85f, 0.9f, -25f),
        )
        for (i in 0 until m.n(spots.size).coerceAtMost(spots.size * 2)) {
            val (fx, fy, ang) = spots[i % spots.size]
            val o = if (i >= spots.size) 0.07f else 0f
            leaf(m.w * (fx + o).coerceAtMost(0.97f), m.h * (fy + o), m.u(0.07f), ang, leafC, true)
        }
        // Cup outline + steam curls, top-right.
        val cx = m.w * 0.84f
        val cy = m.h * 0.12f
        val cs = m.u(0.05f)
        val line = Color(0xFF6B8A48).copy(alpha = a + 0.15f)
        val cup = Path().apply {
            moveTo(cx - cs, cy - cs * 0.6f); lineTo(cx + cs, cy - cs * 0.6f)
            quadraticTo(cx + cs, cy + cs * 0.9f, cx, cy + cs * 0.9f)
            quadraticTo(cx - cs, cy + cs * 0.9f, cx - cs, cy - cs * 0.6f)
        }
        drawPath(cup, line, style = Stroke(cs * 0.1f, cap = StrokeCap.Round))
        drawArc(line, -90f, 180f, false, Offset(cx + cs * 0.75f, cy - cs * 0.4f), Size(cs * 0.7f, cs * 0.7f), style = Stroke(cs * 0.1f))
        steam(cx - cs * 0.4f, cy - cs * 0.8f, cs * 0.9f, line, cs * 0.07f)
        steam(cx + cs * 0.1f, cy - cs * 0.8f, cs * 1.1f, line, cs * 0.07f)
        steam(cx + cs * 0.6f, cy - cs * 0.8f, cs * 0.9f, line, cs * 0.07f)
    }
}

private val LavenderDeco = ThemeDecoration { size, density, dark ->
    val m = Metrics(size, density, dark)
    DecorationFrame {
        val a = m.alpha
        val ink = if (dark) Color(0xFFD9CFFA) else Color(0xFF8E7FD0)
        // Crescent = big circle minus offset circle.
        val mr = m.u(0.06f)
        val mc = Offset(m.w * 0.84f, m.h * 0.09f)
        val moon = Path.combine(
            androidx.compose.ui.graphics.PathOperation.Difference,
            Path().apply { addOval(androidx.compose.ui.geometry.Rect(mc, mr)) },
            Path().apply { addOval(androidx.compose.ui.geometry.Rect(mc + Offset(mr * 0.45f, -mr * 0.2f), mr * 0.85f)) },
        )
        drawPath(moon, Color(0xFFF2CF5E).copy(alpha = a + 0.2f))
        // Stars joined by dotted constellation lines.
        val pts = listOf(0.1f to 0.18f, 0.2f to 0.3f, 0.12f to 0.44f, 0.9f to 0.5f, 0.8f to 0.66f, 0.93f to 0.78f, 0.25f to 0.9f, 0.4f to 0.84f)
        val dots = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(m.dp * 3f, m.dp * 5f))
        val groups = listOf(0..2, 3..5, 6..7)
        for (g in groups) {
            for (i in g.first until g.last) {
                val p0 = Offset(m.w * pts[i].first, m.h * pts[i].second)
                val p1 = Offset(m.w * pts[i + 1].first, m.h * pts[i + 1].second)
                drawLine(ink.copy(alpha = a * 0.8f), p0, p1, m.dp * 1.2f, pathEffect = dots)
            }
        }
        for (i in 0 until m.n(pts.size).coerceAtMost(pts.size * 2)) {
            val (fx, fy) = pts[i % pts.size]
            val o = if (i >= pts.size) 0.05f else 0f
            drawPath(starPath(m.w * (fx + o).coerceAtMost(0.97f), m.h * (fy + o), m.u(0.022f), 4, 0.35f), ink.copy(alpha = a + 0.1f))
        }
    }
}

private val SerifItalic = FontFamily.Serif

internal val WashiPlanner: AppTheme by lazy {
    AppTheme(
        id = "washi_planner",
        nameRes = R.string.theme_washi_planner_name,
        descriptionRes = R.string.theme_washi_planner_desc,
        light = paletteMode(WashiLight, dark = false),
        dark = paletteMode(WashiDark, dark = true),
        tabShape = RoundedCornerShape(12.dp, 12.dp, 0.dp, 0.dp),
        cardShape = RoundedCornerShape(0.dp, 20.dp, 20.dp, 20.dp),
        headerStyle = HeaderStyle.WASHI,
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
        tabShape = RoundedCornerShape(6.dp),
        cardShape = RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp),
        headerStyle = HeaderStyle.HIGHLIGHTER,
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
        titleFont = SerifItalic,
        tabShape = RoundedCornerShape(4.dp, 4.dp, 0.dp, 0.dp),
        cardShape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 8.dp),
        headerStyle = HeaderStyle.RIBBON,
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
        headerStyle = HeaderStyle.RIBBON,
        decoration = LavenderDeco,
    )
}
