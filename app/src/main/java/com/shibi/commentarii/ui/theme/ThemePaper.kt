package com.shibi.commentarii.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.Density
import kotlin.random.Random

// Themes v2 S5: paper textures, page border and seeded scatter. Everything is precomputed once per
// page size (PaperCache / lazily built paths) so the draw lambdas only issue batched primitives.

/**
 * Pre-computed texture geometry for one page size. [pitchDp] is the grid/line pitch for DOT_GRID and LINED.
 * Points are batched into a few lists so each paper costs 1-4 drawPoints/drawPath calls.
 */
class PaperCache(size: Size, density: Density, val paper: Paper, seed: Int, pitchDp: Float = 0f) {
    private val dp = density.density
    val dots: List<Offset>
    val lineEnds: List<Offset>
    val specksSmall: List<Offset>
    val specksBig: List<Offset>
    val stars: Path = Path()
    val marginX: Float = 24f * dp
    val dotStroke: Float = 1.2f * dp
    val lineStroke: Float = 0.8f * dp
    val marginStroke: Float = 1f * dp

    init {
        val w = size.width
        val h = size.height
        val dotList = ArrayList<Offset>()
        val lineList = ArrayList<Offset>()
        val small = ArrayList<Offset>()
        val big = ArrayList<Offset>()
        when (paper) {
            Paper.DOT_GRID -> {
                val pitch = (if (pitchDp > 0f) pitchDp else 16f) * dp
                var y = pitch / 2f
                while (y < h) {
                    var x = pitch / 2f
                    while (x < w) {
                        dotList.add(Offset(x, y))
                        x += pitch
                    }
                    y += pitch
                }
            }
            Paper.LINED -> {
                val pitch = (if (pitchDp > 0f) pitchDp else 28f) * dp
                var y = pitch
                while (y < h) {
                    lineList.add(Offset(0f, y))
                    lineList.add(Offset(w, y))
                    y += pitch
                }
            }
            Paper.SPECKLE -> {
                val r = Random(seed)
                repeat(220) {
                    val p = Offset(r.nextFloat() * w, r.nextFloat() * h)
                    if (r.nextFloat() < 0.65f) small.add(p) else big.add(p)
                }
            }
            Paper.STARS -> {
                val r = Random(seed)
                repeat(180) { i ->
                    val p = Offset(r.nextFloat() * w, r.nextFloat() * h)
                    if (i % 3 == 0) {
                        small.add(p)
                    } else {
                        addTwinkle(stars, p.x, p.y, (2f + r.nextFloat() * 2.2f) * dp)
                    }
                }
            }
            Paper.NONE -> Unit
        }
        dots = dotList
        lineEnds = lineList
        specksSmall = small
        specksBig = big
    }

    /** Tiny 4-point star with concave Bezier sides, appended to [into]. */
    private fun addTwinkle(into: Path, cx: Float, cy: Float, r: Float) {
        val k = r * 0.22f
        into.moveTo(cx, cy - r)
        into.cubicTo(cx + k * 0.3f, cy - k, cx + k, cy - k * 0.3f, cx + r, cy)
        into.cubicTo(cx + k, cy + k * 0.3f, cx + k * 0.3f, cy + k, cx, cy + r)
        into.cubicTo(cx - k * 0.3f, cy + k, cx - k, cy + k * 0.3f, cx - r, cy)
        into.cubicTo(cx - k, cy - k * 0.3f, cx - k * 0.3f, cy - k, cx, cy - r)
        into.close()
    }
}

/** Draws the paper texture; [marginColor] only matters for LINED (the vertical margin rule). */
fun DrawScope.drawPaper(cache: PaperCache, color: Color, alpha: Float, marginColor: Color = color) {
    val c = color.copy(alpha = color.alpha * alpha)
    when (cache.paper) {
        Paper.DOT_GRID -> drawPoints(cache.dots, PointMode.Points, c, cache.dotStroke, StrokeCap.Round)
        Paper.LINED -> {
            val la = maxOf(alpha, 0.45f)
            drawPoints(cache.lineEnds, PointMode.Lines, color.copy(alpha = color.alpha * la), cache.lineStroke, StrokeCap.Butt)
            val m = marginColor.copy(alpha = marginColor.alpha * la * 1.4f)
            drawLine(m, Offset(cache.marginX, 0f), Offset(cache.marginX, size.height), cache.marginStroke)
        }
        Paper.SPECKLE -> {
            val dp = cache.dotStroke / 1.2f
            drawPoints(cache.specksSmall, PointMode.Points, c, 0.8f * dp, StrokeCap.Round)
            drawPoints(cache.specksBig, PointMode.Points, c, 1.8f * dp, StrokeCap.Round)
        }
        Paper.STARS -> {
            val dp = cache.dotStroke / 1.2f
            drawPath(cache.stars, c)
            drawPoints(cache.specksSmall, PointMode.Points, c, 1.1f * dp, StrokeCap.Round)
        }
        Paper.NONE -> Unit
    }
}

/** Dashed rounded rectangle inset 6dp (7/5dp dashes, 1.5dp stroke), e.g. the planner's page border. */
fun DrawScope.drawPageBorder(color: Color, density: Density) {
    val dp = density.density
    val inset = 6f * dp
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2f, size.height - inset * 2f),
        cornerRadius = CornerRadius(18f * dp),
        style = Stroke(
            width = 1.5f * dp,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * dp, 5f * dp)),
        ),
    )
}

private fun scatterPoint(r: Random, area: Rect, avoid: Rect?, pad: Float): Offset? {
    repeat(12) {
        val p = Offset(
            area.left + pad + r.nextFloat() * (area.width - pad * 2f).coerceAtLeast(1f),
            area.top + pad + r.nextFloat() * (area.height - pad * 2f).coerceAtLeast(1f),
        )
        if (avoid == null || !avoid.contains(p)) return p
    }
    return null
}

/**
 * Seeded scatter of [count] glyphs (rotated up to +-25deg) inside [area], skipping [avoid] so nothing
 * sits under body text. Sizes are drawn from [sizeDp]; colours cycle through [colors].
 */
fun DrawScope.drawScatter(
    seed: Int,
    count: Int,
    glyphs: List<Glyph>,
    colors: List<Color>,
    area: Rect,
    avoid: Rect?,
    alpha: Float,
    sizeDp: ClosedFloatingPointRange<Float>,
    density: Density,
) {
    if (glyphs.isEmpty() || colors.isEmpty()) return
    val r = Random(seed)
    val dp = density.density
    repeat(count) { i ->
        val s = (sizeDp.start + r.nextFloat() * (sizeDp.endInclusive - sizeDp.start)) * dp
        val p = scatterPoint(r, area, avoid, s / 2f)
        val rot = (r.nextFloat() * 2f - 1f) * 25f
        if (p != null) {
            val col = colors[i % colors.size]
            rotate(rot, p) {
                drawGlyph(glyphs[i % glyphs.size], p, s, col.copy(alpha = col.alpha * alpha))
            }
        }
    }
}

/** Same as [drawScatter] but with full doodles (daisies, leaves, ...); [inkFor] picks the ink per index. */
fun DrawScope.drawScatterDoodles(
    seed: Int,
    count: Int,
    ids: List<DoodleId>,
    inkFor: (Int) -> DoodleInk,
    area: Rect,
    avoid: Rect?,
    sizeDp: ClosedFloatingPointRange<Float>,
    density: Density,
) {
    if (ids.isEmpty()) return
    val r = Random(seed)
    val dp = density.density
    repeat(count) { i ->
        val s = (sizeDp.start + r.nextFloat() * (sizeDp.endInclusive - sizeDp.start)) * dp
        val p = scatterPoint(r, area, avoid, s / 2f)
        val rot = (r.nextFloat() * 2f - 1f) * 25f
        if (p != null) drawDoodle(ids[i % ids.size], p, s, inkFor(i), 0f, rot, sticker = false)
    }
}
