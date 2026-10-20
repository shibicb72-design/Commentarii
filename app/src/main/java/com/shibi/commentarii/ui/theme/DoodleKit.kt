package com.shibi.commentarii.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp

/** Colours for one doodle: main pastel, second pastel, dark ink (lines/eyes), cheek blush, overall alpha. */
@Immutable
class DoodleInk(
    val fill: Color,
    val second: Color,
    val ink: Color,
    val blush: Color = Color(0xFFF7A1B5),
    val alpha: Float = 1f,
)

enum class DoodleId {
    BEAR_PEEK, STICKY_NOTE, PLANT_POT, CROWN, FLAG, BUBBLE_EXCLAIM, CALENDAR, CART, BAG_HEART,
    FORK_SPOON, BOBA, DROP_FACE, SMILEY, TULIP_JAR, CACTUS, PEN, SPEECH_HEART, MUG_HEART, CANDLE_JAR, CAMERA,
    NOTE_CARD, ENVELOPE, BOW,

    // part 2 (DoodlesSky.kt / DoodlesGarden.kt)
    SUN_FACE, CLOUD_FACE, CLOUD_PUFF, RAINBOW, RAINDROP, KITE, UMBRELLA,
    MOON_FACE, STAR_FACE, PLANET, SPARKLE_CLUSTER, HEART_FACE, BALLOON, BLOSSOM, BRANCH, PETAL, BUD, DAISY,
    LEAF_SPRIG, TEA_LEAF, TEACUP_FACE, WHISK, TEAPOT, DANGO, BAMBOO, LAVENDER, STEAM,
    MOCHI, ONIGIRI,
}

/** Draws a 100x100-unit doodle centred on [center], [size] px wide, rotated by [rotation] degrees. */
fun DrawScope.drawDoodle(
    id: DoodleId,
    center: Offset,
    size: Float,
    ink: DoodleInk,
    t: Float = 0f,
    rotation: Float = 0f,
    sticker: Boolean = false,
) {
    withTransform({
        translate(center.x - size / 2f, center.y - size / 2f)
        rotate(rotation, Offset(size / 2f, size / 2f))
        scale(size / 100f, size / 100f, Offset.Zero)
    }) {
        when (id) {
            DoodleId.BEAR_PEEK -> drawBearPeek(ink, sticker)
            DoodleId.STICKY_NOTE -> drawStickyNote(ink, sticker)
            DoodleId.PLANT_POT -> drawPlantPot(ink, sticker)
            DoodleId.CROWN -> drawCrown(ink, sticker)
            DoodleId.FLAG -> drawFlag(ink, sticker)
            DoodleId.BUBBLE_EXCLAIM -> drawBubbleExclaim(ink, sticker)
            DoodleId.CALENDAR -> drawCalendar(ink, sticker)
            DoodleId.CART -> drawCart(ink, sticker)
            DoodleId.BAG_HEART -> drawBagHeart(ink, sticker)
            DoodleId.FORK_SPOON -> drawForkSpoon(ink, sticker)
            DoodleId.BOBA -> drawBoba(ink, sticker)
            DoodleId.DROP_FACE -> drawDropFace(ink, sticker)
            DoodleId.SMILEY -> drawSmiley(ink, sticker)
            DoodleId.TULIP_JAR -> drawTulipJar(ink, sticker)
            DoodleId.CACTUS -> drawCactus(ink, sticker)
            DoodleId.PEN -> drawPen(ink, sticker)
            DoodleId.SPEECH_HEART -> drawSpeechHeart(ink, sticker)
            DoodleId.MUG_HEART -> drawMugHeart(ink, sticker)
            DoodleId.CANDLE_JAR -> drawCandleJar(ink, sticker)
            DoodleId.CAMERA -> drawCamera(ink, sticker)
            DoodleId.NOTE_CARD -> drawNoteCard(ink, sticker)
            DoodleId.ENVELOPE -> drawEnvelope(ink, sticker)
            DoodleId.BOW -> drawBow(ink, sticker)
            DoodleId.SUN_FACE -> drawSunFace(ink, sticker, t)
            DoodleId.CLOUD_FACE -> drawCloudFace(ink, sticker, t)
            DoodleId.CLOUD_PUFF -> drawCloudPuff(ink, sticker)
            DoodleId.RAINBOW -> drawRainbow(ink, sticker)
            DoodleId.RAINDROP -> drawRaindrop(ink, sticker)
            DoodleId.KITE -> drawKite(ink, sticker)
            DoodleId.UMBRELLA -> drawUmbrella(ink, sticker)
            DoodleId.MOON_FACE -> drawMoonFace(ink, sticker)
            DoodleId.STAR_FACE -> drawStarFace(ink, sticker)
            DoodleId.PLANET -> drawPlanet(ink, sticker)
            DoodleId.SPARKLE_CLUSTER -> drawSparkleCluster(ink, sticker)
            DoodleId.HEART_FACE -> drawHeartFace(ink, sticker)
            DoodleId.BALLOON -> drawBalloon(ink, sticker)
            DoodleId.BLOSSOM -> drawBlossom(ink, sticker)
            DoodleId.BRANCH -> drawBranch(ink, sticker)
            DoodleId.PETAL -> drawPetal(ink, sticker)
            DoodleId.BUD -> drawBud(ink, sticker)
            DoodleId.DAISY -> drawDaisy(ink, sticker)
            DoodleId.LEAF_SPRIG -> drawLeafSprig(ink, sticker)
            DoodleId.TEA_LEAF -> drawTeaLeaf(ink, sticker)
            DoodleId.TEACUP_FACE -> drawTeacupFace(ink, sticker)
            DoodleId.WHISK -> drawWhisk(ink, sticker)
            DoodleId.TEAPOT -> drawTeapot(ink, sticker)
            DoodleId.DANGO -> drawDango(ink, sticker)
            DoodleId.BAMBOO -> drawBamboo(ink, sticker)
            DoodleId.LAVENDER -> drawLavender(ink, sticker)
            DoodleId.STEAM -> drawSteam(ink, sticker, t)
            DoodleId.MOCHI -> drawMochi(ink, sticker)
            DoodleId.ONIGIRI -> drawOnigiri(ink, sticker)
        }
    }
}

// ---- path builders (unit space; Number args keep the art tables short) ----

internal fun path(b: Path.() -> Unit): Path = Path().apply(b)
internal fun Path.m(x: Number, y: Number) = moveTo(x.toFloat(), y.toFloat())
internal fun Path.l(x: Number, y: Number) = lineTo(x.toFloat(), y.toFloat())
internal fun Path.c(x1: Number, y1: Number, x2: Number, y2: Number, x: Number, y: Number) =
    cubicTo(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), x.toFloat(), y.toFloat())
internal fun Path.q(x1: Number, y1: Number, x: Number, y: Number) =
    quadraticTo(x1.toFloat(), y1.toFloat(), x.toFloat(), y.toFloat())
internal fun Path.oval(cx: Number, cy: Number, rx: Number, ry: Number) {
    val x = cx.toFloat(); val y = cy.toFloat(); val a = rx.toFloat(); val b = ry.toFloat()
    addOval(Rect(x - a, y - b, x + a, y + b))
}
internal fun Path.rr(l: Number, t: Number, r: Number, b: Number, rad: Number) {
    val k = rad.toFloat()
    addRoundRect(RoundRect(l.toFloat(), t.toFloat(), r.toFloat(), b.toFloat(), k, k))
}
/** Bezier heart of half-width ~1.1s centred on (cx, cy). */
internal fun Path.heart(cx: Number, cy: Number, s: Number) {
    val x = cx.toFloat(); val y = cy.toFloat(); val k = s.toFloat()
    moveTo(x, y + .9f * k)
    cubicTo(x - 1.3f * k, y + .1f * k, x - .9f * k, y - .95f * k, x, y - .4f * k)
    cubicTo(x + .9f * k, y - .95f * k, x + 1.3f * k, y + .1f * k, x, y + .9f * k)
    close()
}
/** Soft 5-point star (round joins make it friendly). */
internal fun Path.star(cx: Number, cy: Number, r: Number) {
    val x = cx.toFloat(); val y = cy.toFloat(); val o = r.toFloat(); val i = o * .48f
    for (n in 0 until 10) {
        val a = (-90.0 + n * 36.0) * Math.PI / 180.0
        val rad = if (n % 2 == 0) o else i
        val px = x + rad * Math.cos(a).toFloat(); val py = y + rad * Math.sin(a).toFloat()
        if (n == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}
internal fun union(a: Path, b: Path): Path = Path.combine(PathOperation.Union, a, b)

private val DieCut = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val LineStroke = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
internal val LeafGreen = Color(0xFF9ED8A8)
internal val Cream = Color(0xFFFFFAF0)

internal fun DoodleInk.a(c: Color): Color = c.copy(alpha = c.alpha * alpha)
internal fun DoodleInk.lineOf(base: Color): Color = a(lerp(base, ink, .55f))

/** White die-cut outline under the given silhouettes (sticker look). */
internal fun DrawScope.diecut(ink: DoodleInk, sticker: Boolean, vararg paths: Path) {
    if (!sticker) return
    for (p in paths) drawPath(p, Color.White.copy(alpha = ink.alpha), style = DieCut)
}

/** Layers: shadow, optional die-cut, fill, lower-right shade, upper-left highlight, outline. */
internal fun DrawScope.drawLayered(
    silhouette: Path,
    ink: DoodleInk,
    sticker: Boolean,
    fill: Color = ink.fill,
    highlight: Boolean = true,
) {
    withTransform({ translate(2f, 3f) }) { drawPath(silhouette, ink.ink.copy(alpha = .10f * ink.alpha)) }
    if (sticker) drawPath(silhouette, Color.White.copy(alpha = ink.alpha), style = DieCut)
    drawPath(silhouette, ink.a(fill))
    val b = silhouette.getBounds()
    val c = b.center
    clipPath(silhouette) {
        rotate(-45f, c) {
            drawRect(
                ink.a(lerp(fill, ink.ink, .14f)),
                Offset(b.left - b.width, c.y + b.height * .22f),
                Size(b.width * 3f, b.height * 2f),
            )
        }
        if (highlight) {
            rotate(-30f, Offset(b.left + b.width * .3f, b.top + b.height * .2f)) {
                drawOval(
                    Color.White.copy(alpha = .65f * ink.alpha),
                    Offset(b.left + b.width * .16f, b.top + b.height * .14f),
                    Size(b.width * .2f, b.height * .09f),
                )
            }
        }
    }
    drawPath(silhouette, ink.lineOf(fill), style = LineStroke)
}

internal fun DrawScope.lay(p: Path, ink: DoodleInk, fill: Color = ink.fill, highlight: Boolean = true) =
    drawLayered(p, ink, false, fill, highlight)

/** Round-capped stroke only (stems, lines, handles). */
internal fun DrawScope.line(p: Path, ink: DoodleInk, color: Color, w: Float = 2.2f) =
    drawPath(p, ink.a(color), style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))

/** Cute face: oval eyes with catchlights, smile, blush; [s] is the face width in unit px. */
internal fun DrawScope.drawFace(
    cx: Float, cy: Float, s: Float, ink: DoodleInk, sleepy: Boolean = false, mouth: Boolean = true,
) {
    val k = s / 30f
    val dark = ink.a(ink.ink)
    if (sleepy) {
        for (dx in floatArrayOf(-8f, 8f)) {
            drawArc(
                dark, 20f, 140f, false, Offset(cx + dx * k - 3.4f * k, cy - 4.9f * k),
                Size(6.8f * k, 6.8f * k), style = Stroke(1.8f * k, cap = StrokeCap.Round),
            )
        }
    } else {
        for (dx in floatArrayOf(-8f, 8f)) {
            drawOval(dark, Offset(cx + dx * k - 3.4f * k, cy - 4.2f * k), Size(6.8f * k, 8.4f * k))
            drawCircle(Color.White.copy(alpha = ink.alpha), 1.1f * k, Offset(cx + dx * k - 1f * k, cy - 2.4f * k))
        }
    }
    if (mouth) {
        drawArc(
            dark, 30f, 120f, false, Offset(cx - 4.5f * k, cy - 2f * k),
            Size(9f * k, 9f * k), style = Stroke(2f * k, cap = StrokeCap.Round),
        )
    }
    for (dx in floatArrayOf(-13.5f, 13.5f)) {
        drawOval(
            ink.blush.copy(alpha = .55f * ink.alpha),
            Offset(cx + dx * k - 4f * k, cy + 3f * k), Size(8f * k, 5f * k),
        )
    }
}

// ---- washi tape ----

private val tapeLeft by lazy {
    path { m(0, -10); l(-3, -7.5); l(0, -5); l(-3, -2.5); l(0, 0); l(-3, 2.5); l(0, 5); l(-3, 7.5); l(0, 10); close() }
}
private val tapeRight by lazy {
    path { m(0, -10); l(3, -7.5); l(0, -5); l(3, -2.5); l(0, 0); l(3, 2.5); l(0, 5); l(3, 7.5); l(0, 10); close() }
}

/** Translucent washi strip with torn zigzag ends; pattern 0 stripes, 1 dots, 2 gingham. */
fun DrawScope.drawWashiTape(
    center: Offset, length: Float, thickness: Float, angle: Float, color: Color, pattern: Int, ink: Color,
) {
    val s = thickness / 20f
    val body = color.copy(alpha = color.alpha * .82f)
    val mark = ink.copy(alpha = .14f)
    withTransform({
        translate(center.x, center.y)
        rotate(angle, Offset.Zero)
        scale(s, s, Offset.Zero)
    }) {
        val len = length / s
        val x0 = -len / 2f
        drawRect(body, Offset(x0, -10f), Size(len, 20f))
        withTransform({ translate(x0, 0f) }) { drawPath(tapeLeft, body) }
        withTransform({ translate(x0 + len, 0f) }) { drawPath(tapeRight, body) }
        clipRect(x0, -10f, x0 + len, 10f) {
            when (pattern) {
                0 -> {
                    var x = x0 - 20f
                    while (x < x0 + len) {
                        drawLine(mark, Offset(x, 12f), Offset(x + 24f, -12f), 3.5f)
                        x += 11f
                    }
                }
                1 -> {
                    var x = x0 + 5f
                    var row = 0
                    while (x < x0 + len) {
                        drawCircle(mark, 1.9f, Offset(x, if (row % 2 == 0) -4.5f else 4.5f))
                        x += 7f
                        row++
                    }
                }
                else -> {
                    var x = x0 + 4f
                    while (x < x0 + len) { drawRect(mark.copy(alpha = .09f), Offset(x, -10f), Size(5f, 20f)); x += 12f }
                    for (y in floatArrayOf(-7f, 2f)) drawRect(mark.copy(alpha = .09f), Offset(x0, y), Size(len, 5f))
                }
            }
        }
    }
}
