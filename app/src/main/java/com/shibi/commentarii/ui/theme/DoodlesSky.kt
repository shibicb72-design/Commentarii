package com.shibi.commentarii.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp

/** Sky doodles (100x100 unit box): sun, clouds, rainbow, rain, kite, umbrella, moon, star, planet, hearts. */
private object SK {
    val sunBody by lazy { path { oval(50, 50, 25, 25) } }
    val sunRay by lazy { path { m(50, 5); c(55.5, 10, 56, 17, 50, 21); c(44, 17, 44.5, 10, 50, 5); close() } }

    val cloud by lazy {
        path {
            m(20, 70); c(4, 70, 2, 49, 19, 47)
            c(18, 30, 42, 24, 50, 38)
            c(58, 24, 83, 30, 80, 49)
            c(97, 49, 98, 70, 82, 70)
            close()
        }
    }

    val dropBody by lazy {
        path {
            m(50, 7); c(50, 7, 21, 40, 21, 63); c(21, 79, 34, 91, 50, 91)
            c(66, 91, 79, 79, 79, 63); c(79, 40, 50, 7, 50, 7); close()
        }
    }

    val kiteBody by lazy {
        path {
            m(50, 5); c(58, 17, 72, 33, 77, 46); c(66, 57, 56, 66, 50, 75)
            c(44, 66, 34, 57, 23, 46); c(28, 33, 42, 17, 50, 5); close()
        }
    }
    val kiteLow by lazy { path { m(20, 46); l(80, 46); l(80, 80); l(20, 80); close() } }
    val kiteTail by lazy { path { m(50, 75); c(40, 83, 62, 87, 52, 95); c(47, 99, 44, 97, 46, 96) } }
    val kiteBowA by lazy { path { m(49, 82); c(41, 77, 40, 87, 49, 82); c(57, 77, 58, 87, 49, 82); close() } }
    val kiteBowB by lazy { path { m(53, 92); c(46, 87, 45, 97, 53, 92); c(60, 87, 61, 97, 53, 92); close() } }

    val umbrella by lazy {
        path {
            m(8, 50); c(8, 26, 28, 9, 50, 9); c(72, 9, 92, 26, 92, 50)
            c(89, 58, 74, 58, 71, 50); c(68, 58, 53, 58, 50, 50)
            c(47, 58, 32, 58, 29, 50); c(26, 58, 11, 58, 8, 50)
            close()
        }
    }
    val umbPanelA by lazy { path { m(50, 6); c(58, 20, 70, 40, 71, 58); l(50, 58); close() } }
    val umbPanelB by lazy { path { m(50, 6); c(42, 20, 30, 40, 29, 58); l(50, 58); close() } }
    val umbHandle by lazy { path { m(50, 52); l(50, 82); c(50, 93, 36, 93, 36, 85) } }
    val umbTip by lazy { path { m(50, 3); l(50, 10) } }

    val moon by lazy {
        Path.combine(
            PathOperation.Difference,
            path { oval(50, 52, 38, 38) },
            path { oval(68, 42, 32, 32) },
        )
    }
    val cap by lazy {
        path {
            m(18, 34); c(20, 17, 36, 7, 54, 11); c(62, 13, 68, 18, 71, 24)
            c(54, 20, 36, 24, 18, 34); close()
        }
    }
    val capBrim by lazy { path { rr(15, 30, 44, 38, 4) } }

    val starFace by lazy { path { star(50, 53, 46) } }

    val ringBand by lazy { path { oval(50, 50, 26, 26) } }

    val hearts by lazy { path { heart(50, 48, 42) } }
    val balloon by lazy { path { heart(50, 38, 36) } }
    val balloonKnot by lazy { path { m(50, 69); l(45, 77); l(55, 77); close() } }
    val balloonString by lazy { path { m(50, 77); c(40, 84, 60, 88, 50, 93); c(44, 97, 55, 99, 52, 99) } }
}

/** Four-point sparkle with concave Bezier sides, radius [r]. */
internal fun Path.spark(cx: Number, cy: Number, r: Number) {
    val x = cx.toFloat(); val y = cy.toFloat(); val k = r.toFloat()
    moveTo(x, y - k)
    cubicTo(x + .08f * k, y - .3f * k, x + .3f * k, y - .08f * k, x + k, y)
    cubicTo(x + .3f * k, y + .08f * k, x + .08f * k, y + .3f * k, x, y + k)
    cubicTo(x - .08f * k, y + .3f * k, x - .3f * k, y + .08f * k, x - k, y)
    cubicTo(x - .3f * k, y - .08f * k, x - .08f * k, y - .3f * k, x, y - k)
    close()
}

private val SunYellow = Color(0xFFFFD95E)
private val RainbowBlue = Color(0xFFB9D8F8)

internal fun DrawScope.drawSunFace(ink: DoodleInk, sticker: Boolean, t: Float = 0f) {
    val warm = lerp(SunYellow, Color(0xFFFF9E3D), .4f)
    val rayFill = ink.a(warm)
    rotate(t * 12f, Offset(50f, 50f)) {
        for (i in 0 until 12) {
            rotate(i * 30f, Offset(50f, 50f)) {
                // larger rays: stretched 1.35x outward from the sun centre
                withTransform({ scale(1.35f, 1.35f, Offset(50f, 50f)) }) {
                    drawPath(SK.sunRay, rayFill)
                    drawPath(SK.sunRay, ink.lineOf(warm), style = Stroke(1.4f, cap = StrokeCap.Round))
                }
            }
        }
    }
    diecut(ink, sticker, SK.sunBody)
    lay(SK.sunBody, ink, SunYellow)
    drawFace(50f, 53f, 27f, ink)
}

internal fun DrawScope.drawCloudPuff(ink: DoodleInk, sticker: Boolean) {
    val underside = Color(0xFFA9CBEA)
    diecut(ink, sticker, SK.cloud)
    lay(SK.cloud, ink, Color.White)
    clipPath(SK.cloud) {
        // soft blue underside in two bands, then a subtle top highlight
        drawRect(ink.a(lerp(Color.White, underside, .55f)), Offset(0f, 58f), Size(100f, 16f))
        drawRect(ink.a(underside), Offset(0f, 65f), Size(100f, 9f))
        drawOval(ink.a(Color.White), Offset(26f, 32f), Size(34f, 12f))
    }
    drawPath(SK.cloud, ink.lineOf(lerp(ink.fill, underside, .5f)), style = Stroke(2.2f, cap = StrokeCap.Round))
}

internal fun DrawScope.drawCloudFace(ink: DoodleInk, sticker: Boolean, t: Float = 0f) {
    drawCloudPuff(ink, sticker)
    val blink = (t % 5f) < .16f
    drawFace(50f, 56f, 22f, ink, sleepy = blink)
}

internal fun DrawScope.drawRainbow(ink: DoodleInk, sticker: Boolean) {
    val bands = arrayOf(ink.fill, SunYellow, LeafGreen, RainbowBlue)
    for (i in bands.indices) {
        val r = 40f - i * 8f
        drawArc(
            ink.a(Color.White), 180f, 180f, false, Offset(50f - r, 74f - r), Size(r * 2f, r * 2f),
            style = Stroke(9f, cap = StrokeCap.Round),
        )
    }
    for (i in bands.indices) {
        val r = 40f - i * 8f
        drawArc(
            ink.a(bands[i]), 180f, 180f, false, Offset(50f - r, 74f - r), Size(r * 2f, r * 2f),
            style = Stroke(7f, cap = StrokeCap.Round),
        )
        drawArc(
            Color.White.copy(alpha = .45f * ink.alpha), 200f, 70f, false, Offset(50f - r, 74f - r),
            Size(r * 2f, r * 2f), style = Stroke(1.8f, cap = StrokeCap.Round),
        )
    }
    for (x in floatArrayOf(2f, 62f)) {
        withTransform({ translate(x, 60f); scale(.34f, .34f, Offset.Zero) }) { drawCloudPuff(ink, sticker) }
    }
}

internal fun DrawScope.drawRaindrop(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, SK.dropBody)
    lay(SK.dropBody, ink)
    drawFace(50f, 66f, 28f, ink)
}

internal fun DrawScope.drawKite(ink: DoodleInk, sticker: Boolean) {
    line(SK.kiteTail, ink, ink.ink, 2f)
    lay(SK.kiteBowA, ink, ink.second, highlight = false)
    lay(SK.kiteBowB, ink, ink.fill, highlight = false)
    diecut(ink, sticker, SK.kiteBody)
    lay(SK.kiteBody, ink)
    clipPath(SK.kiteBody) { drawPath(SK.kiteLow, ink.a(ink.second)) }
    drawPath(SK.kiteBody, ink.lineOf(ink.fill), style = Stroke(2.2f, cap = StrokeCap.Round))
    drawFace(50f, 40f, 20f, ink)
}

internal fun DrawScope.drawUmbrella(ink: DoodleInk, sticker: Boolean) {
    line(SK.umbHandle, ink, ink.ink, 2.8f)
    diecut(ink, sticker, SK.umbrella)
    lay(SK.umbrella, ink)
    clipPath(SK.umbrella) {
        drawPath(SK.umbPanelA, ink.a(ink.second))
        drawPath(SK.umbPanelB, ink.a(ink.second))
    }
    drawPath(SK.umbrella, ink.lineOf(ink.fill), style = Stroke(2.2f, cap = StrokeCap.Round))
    line(SK.umbTip, ink, ink.ink, 2.4f)
}

internal fun DrawScope.drawMoonFace(ink: DoodleInk, sticker: Boolean, sleepy: Boolean = true) {
    diecut(ink, sticker, SK.moon)
    lay(SK.moon, ink, SunYellow.copy(alpha = 1f).let { lerp(ink.fill, it, .5f) })
    drawFace(24f, 56f, 18f, ink, sleepy = sleepy)
    lay(SK.cap, ink, ink.second, highlight = false)
    lay(SK.capBrim, ink, Color.White, highlight = false)
    drawCircle(ink.a(Color.White), 4.2f, Offset(71f, 24f))
    drawCircle(ink.lineOf(ink.second), 4.2f, Offset(71f, 24f), style = Stroke(1.6f))
}

internal fun DrawScope.drawStarFace(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, SK.starFace)
    lay(SK.starFace, ink, lerp(ink.fill, SunYellow, .5f))
    drawFace(50f, 58f, 22f, ink)
}

internal fun DrawScope.drawPlanet(ink: DoodleInk, sticker: Boolean) {
    val ring = Stroke(7f, cap = StrokeCap.Round)
    rotate(-22f, Offset(50f, 50f)) {
        drawArc(ink.a(ink.second), 180f, 180f, false, Offset(6f, 36f), Size(88f, 28f), style = ring)
    }
    diecut(ink, sticker, SK.ringBand)
    lay(SK.ringBand, ink)
    clipPath(SK.ringBand) {
        rotate(-22f, Offset(50f, 50f)) {
            drawRect(ink.a(ink.second).copy(alpha = .55f * ink.alpha), Offset(20f, 40f), Size(60f, 6f))
            drawRect(ink.a(ink.second).copy(alpha = .55f * ink.alpha), Offset(20f, 54f), Size(60f, 4f))
        }
    }
    drawCircle(ink.a(Color.White).copy(alpha = .55f * ink.alpha), 2.2f, Offset(40f, 62f))
    drawCircle(ink.a(Color.White).copy(alpha = .55f * ink.alpha), 1.6f, Offset(60f, 36f))
    rotate(-22f, Offset(50f, 50f)) {
        drawArc(ink.a(ink.second), 0f, 180f, false, Offset(6f, 36f), Size(88f, 28f), style = ring)
        drawArc(
            Color.White.copy(alpha = .5f * ink.alpha), 30f, 60f, false, Offset(6f, 36f), Size(88f, 28f),
            style = Stroke(1.8f, cap = StrokeCap.Round),
        )
    }
}

private val sparkBig by lazy { path { spark(44, 54, 34) } }
private val sparkMid by lazy { path { spark(78, 22, 17) } }
private val sparkSmall by lazy { path { spark(80, 80, 13) } }
private val sparkTiny by lazy { path { spark(18, 18, 8) } }

internal fun DrawScope.drawSparkleCluster(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, sparkBig, sparkMid, sparkSmall)
    lay(sparkBig, ink)
    lay(sparkMid, ink, ink.second)
    lay(sparkSmall, ink, ink.fill)
    lay(sparkTiny, ink, ink.second, highlight = false)
}

internal fun DrawScope.drawHeartFace(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, SK.hearts)
    lay(SK.hearts, ink)
    drawFace(50f, 50f, 30f, ink)
}

internal fun DrawScope.drawBalloon(ink: DoodleInk, sticker: Boolean) {
    line(SK.balloonString, ink, ink.ink, 1.8f)
    lay(SK.balloonKnot, ink, ink.fill, highlight = false)
    diecut(ink, sticker, SK.balloon)
    lay(SK.balloon, ink)
}
