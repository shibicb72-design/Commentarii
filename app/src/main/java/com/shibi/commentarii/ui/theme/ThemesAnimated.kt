package com.shibi.commentarii.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.shibi.commentarii.R
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

// Animated themes v2 (docs/plans/themes2-plan.md, S7): static layer (`decoration`) + animated `scene` per theme.
// Fields/paths/inks are built once per size in build(); the frame lambda is a pure function of timeSec and only
// reads the clock inside draw. Counts stay <= 40 particles per screen (see [particleCount]).

private fun tabsOf(c: ArtColors): List<Long> = c.accents.map { it.toArgb().toLong() and 0xFFFFFFFFL }

private fun artTabsDark(id: String): List<Long> = tabsOf(checkNotNull(themeArt(id)).dark)

private val CloudLight = Palette(
    0xFFF3F8FD, 0xFF1F2A36, 0xFF3F6E9E, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFD5E2EF,
    listOf(0xFFCFE5F8, 0xFFE3DAF6, 0xFFFDE1E6, 0xFFD8F1E4, 0xFFFFF1C7), 0xFF1F2A36, emptyList(),
)
private val CloudDark by lazy {
    Palette(
        0xFF182230, 0xFFE7EEF6, 0xFFA9CBEE, 0xFF12304E, 0xFF202C3B, 0xFF3A4A5C,
        artTabsDark("cloud_drift"), 0xFFF4F8FC, emptyList(),
    )
}
private val PetalLight = Palette(
    0xFFFFF6F7, 0xFF3A2328, 0xFFB04A63, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFF0D8DD,
    listOf(0xFFF9CFD8, 0xFFFBE3E8, 0xFFE9D5C9, 0xFFE5D9F2, 0xFFD9EBDD), 0xFF3A2328, emptyList(),
)
private val PetalDark by lazy {
    Palette(
        0xFF231A1C, 0xFFF6E8EB, 0xFFF3B3C2, 0xFF4A1A28, 0xFF2D2225, 0xFF4E3C40,
        artTabsDark("petal_fall"), 0xFFFFF4F6, emptyList(),
    )
}
private val HeartsLight = Palette(
    0xFFFBF6FF, 0xFF2D2438, 0xFF8656B0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFE4DAF0,
    listOf(0xFFE6D6F7, 0xFFF8D3E6, 0xFFD3E9F7, 0xFFFDE7C8, 0xFFD8F2E2), 0xFF2D2438, emptyList(),
)
private val HeartsDark by lazy {
    Palette(
        0xFF1F1A27, 0xFFEFE8F7, 0xFFCDB0EE, 0xFF34204E, 0xFF29222F, 0xFF463C52,
        artTabsDark("floating_hearts"), 0xFFFAF5FF, emptyList(),
    )
}

private fun frac(x: Float): Float = x - floor(x)

/** Fixed pseudo-random value in [0,1) for element [i] and channel [k]; deterministic across runs. */
private fun seed(i: Int, k: Int): Float = frac((i + 1) * (0.6180339f + 0.137f * k) + 0.31f * k)

private fun scaled(base: Int, ws: Float): Int = (base * ws).toInt().coerceAtLeast(1)

private fun widthScaleOf(w: Float, dp: Float): Float = (w / (400f * dp)).coerceIn(1f, 1.25f)

private fun cloudTotal(ws: Float) = scaled(4, ws) + scaled(3, ws) + scaled(2, ws) + scaled(8, ws) + 1 + 2
private fun petalTotal(ws: Float) = scaled(14, ws) + scaled(4, ws) + scaled(6, ws) + 1
private fun heartTotal(ws: Float) = scaled(12, ws) + scaled(8, ws) + scaled(8, ws) + 2

/** Largest animated element count over the three scenes at [widthScale]; always <= [MAX_PARTICLES]. */
internal fun particleCount(widthScale: Float): Int {
    val ws = widthScale.coerceIn(1f, 1.25f)
    return maxOf(cloudTotal(ws), petalTotal(ws), heartTotal(ws)).coerceAtMost(MAX_PARTICLES)
}

/** Shared sizing/colours for one build() pass. */
private class AScene(size: Size, val density: Density, val dark: Boolean, id: String) {
    val art = checkNotNull(themeArt(id))
    val c: ArtColors = if (dark) art.dark else art.light
    val acc: List<Color> = c.accents
    val w = size.width
    val h = size.height
    val dp = density.density
    val ws = widthScaleOf(w, dp)
    private val minDim = minOf(w, h)
    val paperAlpha = if (dark) 0.18f else 0.35f

    fun sz(k: Float): Float = (minDim * k * ws).coerceIn(16f * dp, 110f * dp)
    fun ink(fill: Color, second: Color = fill, a: Float = 1f) = DoodleInk(fill, second, c.ink, alpha = a * c.decoAlpha)
    fun deep(col: Color): Color = lerp(col, c.ink, 0.12f)

    fun DrawScope.put(id: DoodleId, fx: Float, fy: Float, k: Float, rot: Float, ink: DoodleInk, sticker: Boolean = true, t: Float = 0f) =
        drawDoodle(id, Offset(w * fx, h * fy), sz(k), ink, t, rot, sticker)
}

/** Six pre-built alpha steps of one doodle colour so faded particles never allocate per frame. */
private class InkRamp(fill: Color, second: Color, ink: Color, private val maxAlpha: Float) {
    private val steps = Array(STEPS + 1) { DoodleInk(fill, second, ink, alpha = maxAlpha * it / STEPS) }
    fun at(alpha: Float): DoodleInk = steps[(alpha / maxAlpha * STEPS).coerceIn(0f, STEPS.toFloat()).roundToInt()]

    private companion object {
        const val STEPS = 6
    }
}

private fun DrawScope.twinkleSparkle(c: Offset, size: Float, color: Color, t: Float, s: Float, maxAlpha: Float) {
    val tw = twinkle(t, s)
    drawGlyph(Glyph.SPARKLE, c, size * (0.7f + 0.3f * tw), color.copy(alpha = maxAlpha * tw))
}

/** Sparkle spots mostly in the side gutters (off the text column); every third one drifts anywhere, fainter. */
private fun spots(n: Int, base: Int, w: Float, h: Float): List<Offset> = List(n) { i ->
    val k = base + i
    val fx = when (i % 3) {
        0 -> 0.01f + seed(k, 0) * 0.08f
        1 -> 0.91f + seed(k, 0) * 0.08f
        else -> 0.05f + seed(k, 0) * 0.9f
    }
    Offset(w * fx, h * (0.03f + seed(k, 1) * 0.94f))
}

private val BirdPath: Path by lazy {
    path {
        m(-1, 0)
        q(-0.5, -0.8, 0, 0)
        q(0.5, -0.8, 1, 0)
    }
}

// ---- cloud_drift ----
private val CloudDeco = ThemeDecoration { size, density, dark ->
    val s = AScene(size, density, dark, "cloud_drift")
    val top = if (dark) Color(0xFF1C3048) else Color(0xFFDCEEFC)
    val sky = Brush.verticalGradient(listOf(top, Color(if (dark) 0xFF182230 else 0xFFF3F8FD)), 0f, size.height * 0.75f)
    DecorationFrame {
        with(s) {
            drawRect(sky)
            put(DoodleId.RAINBOW, 0.14f, 0.30f, 0.30f, 0f, ink(Color.White, acc[0], 0.20f), sticker = false)
            // sky always visible: 4 far + 2 near puffs along the top quarter
            // alpha <= 0.25 so the puffs stay strictly behind the header/chips and never hurt text contrast
            val far = ink(if (dark) lerp(c.paper, Color.White, 0.35f) else Color.White, acc[0], 0.2f)
            val near = ink(if (dark) lerp(c.paper, Color.White, 0.35f) else Color.White, acc[0], 0.25f)
            // fully visible (inset from the edges) and confined to the header band, clear of the cards below
            for (i in 0 until 4) put(DoodleId.CLOUD_PUFF, 0.16f + 0.22f * i, 0.035f + 0.05f * (i % 2), 0.20f, 0f, far, sticker = false)
            put(DoodleId.CLOUD_PUFF, 0.34f, 0.115f, 0.26f, 0f, near, sticker = false)
            put(DoodleId.CLOUD_PUFF, 0.66f, 0.13f, 0.24f, 0f, near, sticker = false)
        }
    }
}

private class CloudSpec(val y: Float, val x0: Float, val size: Float, val speedPx: Float, val seed: Float, val face: Boolean, val ink: DoodleInk)

private val CloudScene = ThemeScene { size, density, dark ->
    val s = AScene(size, density, dark, "cloud_drift")
    val dp = s.dp
    val tone = if (dark) lerp(s.c.paper, Color.White, 0.35f) else Color.White
    // layer: count, base y band, size k, speed dp/s, alpha, sleepy face
    class Layer(val n: Int, val y0: Float, val y1: Float, val k: Float, val speed: Float, val a: Float, val face: Boolean)
    val layers = listOf(
        // three parallax depths (far small/slow/faint -> near large/fast/opaque with a face), all in the sky band above the cards
        // alpha <= 0.25: clouds cross the header/chip band, so they must stay faint and behind content
        Layer(scaled(4, s.ws), 0.02f, 0.10f, 0.18f, 5f, 0.20f, false),
        Layer(scaled(3, s.ws), 0.08f, 0.17f, 0.24f, 10f, 0.22f, false),
        Layer(scaled(2, s.ws), 0.13f, 0.22f, 0.30f, 17f, 0.25f, true),
    )
    var idx = 0
    val clouds = ArrayList<CloudSpec>()
    for (l in layers) {
        val ink = s.ink(tone, s.acc[0], l.a)
        for (j in 0 until l.n) {
            clouds += CloudSpec(
                l.y0 + (l.y1 - l.y0) * ((j + 0.5f) / l.n), seed(idx, 0), s.sz(l.k), l.speed * dp, seed(idx, 3) * 6.28f, l.face, ink,
            )
            idx++
        }
    }
    val sunInk = s.ink(Color(0xFFF7C948), Color(0xFFFFE08A), 0.9f)
    val sparkleSpots = spots(scaled(8, s.ws), 40, s.w, s.h)
    val sparkleColor = if (dark) Color(0xFFFFF3C4) else Color(0xFFE0A93A)
    val sparkleSize = 12f * dp
    val sparkleAlpha = 0.6f * s.c.decoAlpha
    val birdColor = s.c.ink.copy(alpha = 0.45f * s.c.decoAlpha)
    val birdStroke = Stroke(1.6f / 8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val birdSize = 8f * dp
    val sunSize = 104f * dp

    SceneFrame { t ->
        with(s) {
            // sun with rotating rays, 4dp bob
            drawDoodle(
                DoodleId.SUN_FACE, Offset(w * 0.82f, h * 0.075f + sin(t * 1.1f) * 4f * dp), sunSize, sunInk, t, 0f, false,
            )
            for ((i, p) in sparkleSpots.withIndex()) twinkleSparkle(p, sparkleSize, sparkleColor, t, i * 1.9f, sparkleAlpha)
            // two tiny birds glide a sine path every 14s
            for (b in 0 until 2) {
                val u = frac((t + b * 7f) / 14f)
                val x = -20f * dp + u * (w + 40f * dp)
                val y = h * (0.17f + 0.13f * b) + sin(u * 12.566f) * 14f * dp
                val flap = 0.35f + 0.65f * sin(t * 6f + b)
                withTransform({
                    translate(x, y)
                    scale(birdSize, birdSize * flap, Offset.Zero)
                }) { drawPath(BirdPath, birdColor, style = birdStroke) }
            }
            // parallax clouds: far -> near, wrapping horizontally with a +-3dp bob
            for (cl in clouds) {
                val span = w + cl.size * 3.2f
                val x = ((cl.x0 * span + cl.speedPx * t) % span) - cl.size * 1.6f
                val y = h * cl.y + sin(t * 0.6f + cl.seed) * 3f * dp
                drawDoodle(if (cl.face) DoodleId.CLOUD_FACE else DoodleId.CLOUD_PUFF, Offset(x, y), cl.size, cl.ink, t, 0f, false)
            }
        }
    }
}

// ---- petal_fall ----
private val PetalDeco = ThemeDecoration { size, density, dark ->
    val s = AScene(size, density, dark, "petal_fall")
    val paper = PaperCache(size, density, s.art.paper, "petal_fall".hashCode())
    DecorationFrame {
        with(s) {
            drawPaper(paper, c.paper, paperAlpha, c.ink)
            val pink = Color(0xFFF4A9BC)
            val white = Color(0xFFFFF4F7)
            put(DoodleId.BLOSSOM, 0.82f, 0.93f, 0.24f, 12f, ink(pink, acc[1]))
            put(DoodleId.BLOSSOM, 0.94f, 0.85f, 0.17f, -14f, ink(white, acc[1]))
            put(DoodleId.BLOSSOM, 0.66f, 0.965f, 0.15f, 28f, ink(acc[1], acc[0]))
            put(DoodleId.BLOSSOM, 0.10f, 0.94f, 0.20f, -20f, ink(white, acc[1]))
            put(DoodleId.BLOSSOM, 0.22f, 0.975f, 0.13f, 15f, ink(pink, acc[1]))
            // blossom branches: one curving in from the top-right behind the header, one beside the Schedule card (low alpha)
            put(DoodleId.BRANCH, 0.86f, 0.07f, 0.34f, -25f, ink(Color(0xFFF4B6C6), Color(0xFFB9D8B8), 0.30f), sticker = false)
            put(DoodleId.BRANCH, 0.07f, 0.38f, 0.26f, 160f, ink(Color(0xFFF4B6C6), Color(0xFFB9D8B8), 0.26f), sticker = false)
            put(DoodleId.BLOSSOM, 0.93f, 0.30f, 0.12f, 30f, ink(pink, acc[1], 0.6f), sticker = false)
            put(DoodleId.BLOSSOM, 0.06f, 0.55f, 0.12f, -25f, ink(white, acc[1], 0.6f), sticker = false)
            // 9 static petals spread over 0-0.9h at mixed angles, alternating flip-shade
            val petalCols = listOf(Color(0xFFF2A4B8), Color(0xFFF8C8D4), Color(0xFFE88FA8))
            for (i in 0 until 9) {
                val col = petalCols[i % 3]
                val ci = if (i % 2 == 0) col else lerp(col, c.ink, 0.25f)
                val fx = seed(i, 0)
                val gx = if (i % 2 == 0) 0.02f + 0.12f * fx else 0.86f + 0.12f * fx
                put(DoodleId.PETAL, gx, 0.04f + 0.86f * i / 8f, 0.05f, (seed(i, 2) - 0.5f) * 160f, ink(ci, acc[1], 0.7f), sticker = false)
            }
        }
    }
}

private val PetalScene = ThemeScene { size, density, dark ->
    val s = AScene(size, density, dark, "petal_fall")
    val petals = ParticleField(
        ParticleSystem(scaled(14, s.ws), 7, 26f..44f, 1, 22f, 0.9f / 6.2832f, 40f..90f, true, 0.06f, 0.12f, 24f..36f, 0.9f * s.c.decoAlpha),
        size, density, gutter = 0.2f,
    )
    val blossoms =ParticleField(
        ParticleSystem(scaled(4, s.ws), 9, 16f..27f, 1, 12f, 0.1f, 25f..25f, false, 0.06f, 0.12f, 24f..34f, 0.85f * s.c.decoAlpha),
        size, density,
    )
    val petalRamps = listOf(Color(0xFFF2A4B8), Color(0xFFF8C8D4), Color(0xFFE88FA8)).map {
        InkRamp(it, s.acc[1], s.c.ink, 0.9f * s.c.decoAlpha) to InkRamp(lerp(it, s.c.ink, 0.25f), s.acc[1], s.c.ink, 0.9f * s.c.decoAlpha)
    }
    val blossomRamp = InkRamp(Color(0xFFF4A9BC), s.acc[1], s.c.ink, 0.85f * s.c.decoAlpha)
    val sparkleSpots = spots(scaled(6, s.ws), 70, s.w, s.h)
    val sparkleColor = if (dark) Color(0xFFFFE9EE) else Color(0xFFE08AA0)
    val sparkleSize = 11f * s.dp
    val branchInk = s.ink(Color(0xFFF4B6C6), Color(0xFFB9D8B8), 0.28f)
    val branchSize = 84f * s.dp

    SceneFrame { t ->
        with(s) {
            drawDoodle(DoodleId.BRANCH, Offset(44f * dp, 38f * dp), branchSize, branchInk, t, 2.5f * sin(t * 0.5f), false)
            for ((i, p) in sparkleSpots.withIndex()) twinkleSparkle(p, sparkleSize, sparkleColor, t, i * 2.3f, 0.6f * c.decoAlpha)
            drawParticles(blossoms, t) { _, sz, a ->
                drawDoodle(DoodleId.BLOSSOM, Offset.Zero, sz, blossomRamp.at(a), t, 0f, false)
            }
            drawParticles(petals, t) { i, sz, a ->
                val shade = blendFlip(petals.flipScale(i, t))
                val ramp = petalRamps[i % petalRamps.size]
                drawDoodle(DoodleId.PETAL, Offset.Zero, sz, (if (shade) ramp.second else ramp.first).at(a), t, 0f, false)
            }
        }
    }
}

/** True while a flipping petal shows its back (narrow scaleX): swap to the shaded colour. */
private fun blendFlip(scaleX: Float): Boolean = kotlin.math.abs(scaleX) < 0.45f

// ---- floating_hearts ----
private val HeartsDeco = ThemeDecoration { size, density, dark ->
    val s = AScene(size, density, dark, "floating_hearts")
    val paper = PaperCache(size, density, s.art.paper, "floating_hearts".hashCode(), 20f)
    DecorationFrame {
        with(s) {
            drawPaper(paper, c.paper, paperAlpha, c.ink)
            put(DoodleId.ENVELOPE, 0.17f, 0.93f, 0.32f, -6f, ink(acc[1], acc[0]))
            // side-gutter art at ~30% / 55% height: heart balloon and a small love-letter stack
            put(DoodleId.BALLOON, 0.94f, 0.32f, 0.16f, 8f, ink(acc[1], Color(0xFFF08FB0), 0.75f), sticker = false)
            put(DoodleId.ENVELOPE, 0.07f, 0.56f, 0.15f, 9f, ink(acc[0], acc[2], 0.7f))
            put(DoodleId.ENVELOPE, 0.09f, 0.545f, 0.15f, -7f, ink(acc[2], acc[1], 0.7f))
            // 4 bokeh orbs + 11 pre-seeded hearts with highlight across 0.1-0.9h
            val orbCols = listOf(acc[1], acc[0], acc[2], acc[3])
            for (i in 0 until 4) {
                val r = (50f + 30f * seed(i, 2)) * dp
                val col = orbCols[i]
                val o = Offset(w * (0.1f + 0.8f * seed(i, 0)), h * (0.15f + 0.7f * seed(i, 1)))
                drawCircle(Brush.radialGradient(listOf(col.copy(alpha = 0.18f), col.copy(alpha = 0f)), o, r), r, o)
            }
            val hc = listOf(Color(0xFFE88FB0), Color(0xFFB59CE0), Color(0xFFF2A4C8)).map { deep(it) }
            for (i in 0 until 11) {
                val sz = (18f + 16f * seed(i, 5)) * dp
                val gf = 0.02f + 0.12f * seed(i, 6)
                val p = Offset(w * (if (i % 2 == 0) gf else 1f - gf), h * (0.1f + 0.8f * i / 10f))
                drawGlyph(Glyph.HEART, p, sz, hc[i % 3].copy(alpha = 0.55f * c.decoAlpha))
                drawOval(Color.White.copy(alpha = 0.35f), Offset(p.x - sz * 0.30f, p.y - sz * 0.30f), Size(sz * 0.24f, sz * 0.15f))
            }
        }
    }
}

private class Orb(val x: Float, val y: Float, val r: Float, val brush: Brush, val seed: Float)

private val HeartsScene = ThemeScene { size, density, dark ->
    val s = AScene(size, density, dark, "floating_hearts")
    val dp = s.dp
    val hearts = ParticleField(
        ParticleSystem(scaled(12, s.ws), 3, 18f..34f, -1, 14f, 1.4f / 6.2832f, 0f..0f, false, 0.10f, 0.25f, 14f..26f, 0.75f * s.c.decoAlpha),
        size, density, gutter = 0.18f,
    )
    // second layer: small, faster hearts with a wider wobble, spread over the whole width
    val tiny = ParticleField(
        ParticleSystem(scaled(8, s.ws), 11, 30f..52f, -1, 20f, 2.0f / 6.2832f, 0f..0f, false, 0.10f, 0.30f, 8f..13f, 0.6f * s.c.decoAlpha),
        size, density,
    )
    val heartCols = listOf(Color(0xFFE88FB0), Color(0xFFB59CE0), Color(0xFFF2A4C8)).map { s.deep(it) }
    val sparkleSpots = spots(scaled(8, s.ws), 90, s.w, s.h) + List(scaled(2, s.ws)) {
        Offset(s.w * (0.3f + 0.4f * seed(it, 7)), s.h * (0.2f + 0.6f * seed(it, 8)))
    }
    val sparkleColor = if (dark) Color(0xFFFFF3C4) else Color(0xFFE0A93A)
    val orbColors = listOf(s.acc[1], s.acc[0], s.acc[2], s.acc[3])
    val orbs = List(scaled(4, s.ws)) { i ->
        val r = (50f + 30f * seed(i, 2)) * dp
        val col = orbColors[i % orbColors.size]
        Orb(
            s.w * (0.1f + 0.8f * seed(i, 0)), s.h * (0.15f + 0.7f * seed(i, 1)), r,
            Brush.radialGradient(listOf(col.copy(alpha = 0.18f), col.copy(alpha = 0f)), Offset.Zero, r), seed(i, 4) * 6.28f,
        )
    }
    val balloonInkA = s.ink(s.acc[1], Color(0xFFF08FB0), 0.9f)
    val balloonInkB = s.ink(s.acc[2], s.acc[0], 0.9f)
    val balloonSize = s.sz(0.20f)

    SceneFrame { t ->
        with(s) {
            for (o in orbs) {
                withTransform({ translate(o.x + sin(t * 0.3f + o.seed) * 14f * dp, o.y + sin(t * 0.23f + o.seed) * 10f * dp) }) {
                    drawCircle(o.brush, o.r, Offset.Zero)
                }
            }
            for ((i, p) in sparkleSpots.withIndex()) twinkleSparkle(p, 11f * dp, sparkleColor, t, i * 1.7f, 0.6f * c.decoAlpha)
            drawDoodle(DoodleId.BALLOON, Offset(w * 0.92f, h * 0.18f), balloonSize, balloonInkA, t, 3f * sin(t * 0.8f), false)
            drawDoodle(DoodleId.BALLOON, Offset(w * 0.06f, h * 0.42f), balloonSize * 0.85f, balloonInkB, t, 3f * sin(t * 0.8f + 2f), false)
            for (layer in 0 until 2) for (i in 0 until (if (layer == 0) hearts.count else tiny.count)) {
                val f = if (layer == 0) hearts else tiny
                val p = f.progress(i, t)
                val a = f.alphaAt(p)
                if (a <= 0.01f) continue
                val sd = f.seedOf(i)
                val sz = f.sizeOf(i)
                val pulse = 1f + 0.06f * sin(t * 3f + sd)
                withTransform({
                    translate(f.x(i, t, p), f.y(i, t, p))
                    rotate(10f * sin(t * 1.4f + sd), Offset.Zero)
                    scale(pulse, pulse, Offset.Zero)
                }) {
                    drawGlyph(Glyph.HEART, Offset.Zero, sz, heartCols[i % heartCols.size].copy(alpha = a))
                    drawOval(Color.White.copy(alpha = a * 0.55f), Offset(-sz * 0.30f, -sz * 0.30f), Size(sz * 0.24f, sz * 0.15f))
                }
            }
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
        titleFont = checkNotNull(themeArt("cloud_drift")).titleFont,
        headerStyle = HeaderStyle.PLAIN,
        decoration = CloudDeco,
        animated = true, periodMillis = 0, scene = CloudScene, staticTimeSec = 7.5f,
    )
}

internal val PetalFall: AppTheme by lazy {
    AppTheme(
        id = "petal_fall",
        nameRes = R.string.theme_petal_fall_name,
        descriptionRes = R.string.theme_petal_fall_desc,
        light = paletteMode(PetalLight, dark = false),
        dark = paletteMode(PetalDark, dark = true),
        titleFont = checkNotNull(themeArt("petal_fall")).titleFont,
        headerStyle = HeaderStyle.PLAIN,
        decoration = PetalDeco,
        animated = true, periodMillis = 0, scene = PetalScene, staticTimeSec = 4.2f,
    )
}

internal val FloatingHearts: AppTheme by lazy {
    AppTheme(
        id = "floating_hearts",
        nameRes = R.string.theme_floating_hearts_name,
        descriptionRes = R.string.theme_floating_hearts_desc,
        light = paletteMode(HeartsLight, dark = false),
        dark = paletteMode(HeartsDark, dark = true),
        titleFont = checkNotNull(themeArt("floating_hearts")).titleFont,
        headerStyle = HeaderStyle.PLAIN,
        decoration = HeartsDeco,
        animated = true, periodMillis = 0, scene = HeartsScene, staticTimeSec = 3.0f,
    )
}
