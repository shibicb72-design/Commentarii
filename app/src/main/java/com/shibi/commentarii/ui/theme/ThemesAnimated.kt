package com.shibi.commentarii.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// Animated themes: palettes from docs/plans/themes-plan.md. Painters build paths once per size in build();
// the frame lambda only reads [phase] while drawing. Every motion term uses whole cycles per period so the loop is seamless.

private val CloudLight = Palette(
    0xFFF3F8FD, 0xFF1F2A36, 0xFF3F6E9E, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFD5E2EF,
    listOf(0xFFCFE4F7, 0xFFE3DAF5, 0xFFFDE2E4), 0xFF1F2A36,
    listOf(0xFF3F6E9E, 0xFF7A5BB0),
)
private val CloudDark = Palette(
    0xFF182230, 0xFFE7EEF6, 0xFFA9CBEE, 0xFF12304E, 0xFF202C3B, 0xFF3A4A5C,
    listOf(0xFF3A5A7A, 0xFF4E4670, 0xFF6E4A56), 0xFFF4F8FC,
    listOf(0xFFA9CBEE, 0xFFD2C2F2),
)
private val PetalLight = Palette(
    0xFFFFF7F8, 0xFF3A2328, 0xFFB04A63, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFF0D8DD,
    listOf(0xFFF9CFD8, 0xFFFBE3E8, 0xFFE9D5C9), 0xFF3A2328,
    listOf(0xFFB04A63, 0xFF9A5A3A),
)
private val PetalDark = Palette(
    0xFF231A1C, 0xFFF6E8EB, 0xFFF3B3C2, 0xFF4A1A28, 0xFF2D2225, 0xFF4E3C40,
    listOf(0xFF85505E, 0xFF6E4E55, 0xFF6A5446), 0xFFFFF4F6,
    listOf(0xFFF3B3C2, 0xFFF0CDB8),
)
private val HeartsLight = Palette(
    0xFFFBF7FF, 0xFF2D2438, 0xFF8656B0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE4DAF0,
    listOf(0xFFE6D6F7, 0xFFF8D3E6, 0xFFD3E9F7), 0xFF2D2438,
    listOf(0xFF8656B0, 0xFFC2477A),
)
private val HeartsDark = Palette(
    0xFF1F1A27, 0xFFEFE8F7, 0xFFCDB0EE, 0xFF34204E, 0xFF29222F, 0xFF463C52,
    listOf(0xFF5C4A7A, 0xFF7A4A64, 0xFF3E5E78), 0xFFFAF5FF,
    listOf(0xFFCDB0EE, 0xFFF7B9D0),
)

/** Sizing shared by the animated painters: [u] is a base element size in px, [n] scales counts on wide screens. */
private class AnimMetrics(size: Size, density: Density, val dark: Boolean) {
    val w = size.width
    val h = size.height
    val dp = density.density
    private val minDim = minOf(w, h)
    private val widthScale = (w / (412f * dp)).coerceIn(1f, 2f)
    fun u(k: Float): Float = (minDim * k).coerceIn(16f * dp, 96f * dp)
    fun n(base: Int): Int = (base * widthScale).toInt().coerceAtLeast(1)
}

private fun frac(x: Float): Float = x - floor(x)

/** Fixed pseudo-random value in [0,1) for element [i] and channel [k]; deterministic across runs. */
private fun seed(i: Int, k: Int): Float = frac((i + 1) * (0.6180339f + 0.137f * k) + 0.31f * k)

private fun twoPi(x: Float): Float = (2.0 * PI * x).toFloat()

private fun teardropPath(s: Float): Path = Path().apply {
    moveTo(0f, -s)
    cubicTo(s * 0.9f, -s * 0.5f, s * 0.7f, s * 0.7f, 0f, s)
    cubicTo(-s * 0.7f, s * 0.7f, -s * 0.9f, -s * 0.5f, 0f, -s)
    close()
}

private fun heartAtOrigin(s: Float): Path = Path().apply {
    moveTo(0f, s * 0.9f)
    cubicTo(-s * 1.6f, -s * 0.2f, -s * 0.7f, -s * 1.2f, 0f, -s * 0.4f)
    cubicTo(s * 0.7f, -s * 1.2f, s * 1.6f, -s * 0.2f, 0f, s * 0.9f)
    close()
}

private fun sparkleAtOrigin(r: Float): Path = Path().apply {
    val k = r * 0.22f
    moveTo(0f, -r)
    lineTo(k, -k)
    lineTo(r, 0f)
    lineTo(k, k)
    lineTo(0f, r)
    lineTo(-k, k)
    lineTo(-r, 0f)
    lineTo(-k, -k)
    close()
}

private class Cloud(val x0: Float, val y: Float, val s: Float, val speed: Int, val a: Float)

private val CloudDeco = ThemeDecoration { size, density, dark ->
    val m = AnimMetrics(size, density, dark)
    val cloudColor = if (dark) Color.White else Color(0xFFBBD7F0)
    val baseAlpha = if (dark) 0.10f else 0.5f
    val sunColor = if (dark) Color(0xFFF7E3A0) else Color(0xFFF7C948)
    val sunAlpha = if (dark) 0.20f else 0.55f
    // 3 depth rows (far = small/slow/faint, near = big/faster), 5 clouds, more on wide screens.
    val rows = listOf(Triple(0.20f, 1, 0.6f), Triple(0.52f, 1, 0.8f), Triple(0.80f, 2, 1.0f))
    val clouds = (0 until m.n(5).coerceAtMost(10)).map { i ->
        val (fy, speed, depth) = rows[i % rows.size]
        Cloud(seed(i, 0), fy + (seed(i, 1) - 0.5f) * 0.08f, m.u(0.07f) * (0.7f + depth * 0.6f), speed, depth)
    }
    val sunR = m.u(0.07f)
    DecorationFrame { phase ->
        for (c in clouds) {
            val cw = c.s * 2.4f
            val x = frac(c.x0 + phase * c.speed) * (m.w + cw) - cw * 0.5f
            val cy = m.h * c.y
            val col = cloudColor.copy(alpha = baseAlpha * c.a)
            drawCircle(col, c.s * 0.5f, Offset(x - c.s * 0.8f, cy + c.s * 0.1f))
            drawCircle(col, c.s * 0.7f, Offset(x - c.s * 0.25f, cy - c.s * 0.2f))
            drawCircle(col, c.s * 0.6f, Offset(x + c.s * 0.35f, cy))
            drawCircle(col, c.s * 0.45f, Offset(x + c.s * 0.85f, cy + c.s * 0.1f))
        }
        // Sleepy sun: bobs 4dp, rays sway 0..15 degrees (smooth, whole cycles).
        val cx = m.w * 0.82f
        val cy = m.h * 0.10f + sin(twoPi(phase * 3)) * 4f * m.dp
        val col = sunColor.copy(alpha = sunAlpha)
        drawCircle(col, sunR, Offset(cx, cy))
        val sway = (0.5f - 0.5f * cos(twoPi(phase * 3))) * 15f
        rotate(sway, Offset(cx, cy)) {
            for (r in 0 until 8) {
                val a = twoPi(r / 8f)
                drawLine(
                    col,
                    Offset(cx + cos(a) * sunR * 1.35f, cy + sin(a) * sunR * 1.35f),
                    Offset(cx + cos(a) * sunR * 1.8f, cy + sin(a) * sunR * 1.8f),
                    sunR * 0.12f,
                    StrokeCap.Round,
                )
            }
        }
        // Sleepy closed eyes.
        val eye = Color(0xFF7A5A10).copy(alpha = if (dark) 0.25f else 0.45f)
        drawLine(eye, Offset(cx - sunR * 0.5f, cy - sunR * 0.1f), Offset(cx - sunR * 0.2f, cy - sunR * 0.1f), sunR * 0.08f, StrokeCap.Round)
        drawLine(eye, Offset(cx + sunR * 0.2f, cy - sunR * 0.1f), Offset(cx + sunR * 0.5f, cy - sunR * 0.1f), sunR * 0.08f, StrokeCap.Round)
    }
}

private class Faller(val path: Path, val x0: Float, val delay: Float, val s: Float, val color: Color)

private fun DrawScope.drawFaller(f: Faller, x: Float, y: Float, angle: Float) {
    translate(x, y) { rotate(angle, Offset.Zero) { drawPath(f.path, f.color) } }
}

private val PetalDeco = ThemeDecoration { size, density, dark ->
    val m = AnimMetrics(size, density, dark)
    val alpha = if (dark) 0.22f else 0.5f
    val cols = listOf(Color(0xFFF2A4B8), Color(0xFFF8C8D4), Color(0xFFE88FA8))
    val petals = (0 until m.n(14).coerceAtMost(28)).map { i ->
        val s = m.u(0.022f) * (0.7f + seed(i, 2) * 0.7f)
        Faller(teardropPath(s), seed(i, 0), seed(i, 1), s, cols[i % cols.size].copy(alpha = alpha))
    }
    val sway = 18f * m.dp
    DecorationFrame { phase ->
        for (p in petals) {
            val t = frac(phase + p.delay)
            val y = t * (m.h + p.s * 4f) - p.s * 2f
            val x = p.x0 * m.w + sin(twoPi(phase * 2 + p.delay)) * sway
            drawFaller(p, x, y, 360f * t)
        }
    }
}

private class Sparkle(val path: Path, val x: Float, val y: Float, val seed: Float)

private val HeartsDeco = ThemeDecoration { size, density, dark ->
    val m = AnimMetrics(size, density, dark)
    val alpha = if (dark) 0.22f else 0.5f
    val cols = listOf(Color(0xFFE88FB0), Color(0xFFB59CE0), Color(0xFFF2A4C8))
    val hearts = (0 until m.n(10).coerceAtMost(20)).map { i ->
        val s = m.u(0.018f) * (0.7f + seed(i, 2) * 0.8f)
        Faller(heartAtOrigin(s), seed(i, 0), seed(i, 1), s, cols[i % cols.size].copy(alpha = alpha))
    }
    val sparkles = (0 until m.n(12).coerceAtMost(24)).map { i ->
        Sparkle(sparkleAtOrigin(m.u(0.014f) * (0.8f + seed(i, 3) * 0.6f)), seed(i, 4) * m.w, seed(i, 5) * m.h, seed(i, 6))
    }
    val sparkleColor = if (dark) Color(0xFFFFF3C4) else Color(0xFFE0A93A)
    val sway = 10f * m.dp
    DecorationFrame { phase ->
        for (s in sparkles) {
            val a = (0.2f + 0.3f * (0.5f + 0.5f * sin(twoPi(phase * 4 + s.seed)))) * (if (dark) 0.6f else 1f)
            translate(s.x, s.y) { drawPath(s.path, sparkleColor.copy(alpha = a)) }
        }
        for (hh in hearts) {
            val t = frac(phase + hh.delay)
            val y = m.h + hh.s * 2f - t * (m.h + hh.s * 4f)
            val x = hh.x0 * m.w + sin(twoPi(phase + hh.delay)) * sway
            drawFaller(hh, x, y, sin(twoPi(phase * 2 + hh.delay)) * 12f)
        }
    }
}

internal val CloudDrift: AppTheme by lazy {
    AppTheme(
        id = "cloud_drift",
        nameRes = R.string.theme_cloud_drift_name,
        descriptionRes = R.string.theme_cloud_drift_desc,
        light = paletteMode(CloudLight, dark = false),
        dark = paletteMode(CloudDark, dark = true),
        tabShape = RoundedCornerShape(18.dp, 18.dp, 0.dp, 0.dp),
        cardShape = RoundedCornerShape(0.dp, 22.dp, 22.dp, 22.dp),
        decoration = CloudDeco,
        animated = true, periodMillis = 60000, staticPhase = 0.3f,
    )
}

internal val PetalFall: AppTheme by lazy {
    AppTheme(
        id = "petal_fall",
        nameRes = R.string.theme_petal_fall_name,
        descriptionRes = R.string.theme_petal_fall_desc,
        light = paletteMode(PetalLight, dark = false),
        dark = paletteMode(PetalDark, dark = true),
        tabShape = RoundedCornerShape(18.dp, 18.dp, 0.dp, 0.dp),
        cardShape = RoundedCornerShape(0.dp, 22.dp, 22.dp, 22.dp),
        decoration = PetalDeco,
        animated = true, periodMillis = 24000, staticPhase = 0.4f,
    )
}

internal val FloatingHearts: AppTheme by lazy {
    AppTheme(
        id = "floating_hearts",
        nameRes = R.string.theme_floating_hearts_name,
        descriptionRes = R.string.theme_floating_hearts_desc,
        light = paletteMode(HeartsLight, dark = false),
        dark = paletteMode(HeartsDark, dark = true),
        tabShape = RoundedCornerShape(18.dp, 18.dp, 0.dp, 0.dp),
        cardShape = RoundedCornerShape(0.dp, 22.dp, 22.dp, 22.dp),
        decoration = HeartsDeco,
        animated = true, periodMillis = 20000, staticPhase = 0.25f,
    )
}
