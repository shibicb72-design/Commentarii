package com.shibi.commentarii.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp

/** Cozy-journal stickers in a 0..100 box. Paths are built once, in this lazy holder. */
private object CZ {
    val mug by lazy {
        path { m(16, 30); l(16, 76); c(16, 88, 24, 95, 35, 95); l(53, 95); c(64, 95, 72, 88, 72, 76); l(72, 30); close() }
    }
    val mugHandle by lazy { path { m(72, 42); c(94, 38, 96, 72, 70, 72) } }
    val mugTop by lazy { path { oval(44, 30, 28, 6) } }
    val mugHeart by lazy { path { heart(44, 64, 13) } }
    val mugSteam by lazy { path { m(33, 20); c(28, 14, 38, 10, 33, 3); m(50, 20); c(45, 14, 55, 10, 50, 3) } }

    val jar by lazy { path { rr(18, 42, 82, 95, 12) } }
    val jarWax by lazy { path { rr(23, 54, 77, 91, 8) } }
    val jarLid by lazy { path { rr(16, 33, 84, 45, 6) } }
    val jarLabel by lazy { path { oval(50, 72, 20, 14) } }
    val jarHeart by lazy { path { heart(50, 72, 9) } }
    val flame by lazy { path { m(50, 4); c(61, 15, 63, 24, 50, 31); c(37, 24, 39, 15, 50, 4); close() } }
    val flameIn by lazy { path { m(50, 14); c(55, 20, 56, 25, 50, 28); c(44, 25, 45, 20, 50, 14); close() } }
    val wick by lazy { path { m(50, 30); l(50, 35) } }

    val camBody by lazy { union(path { rr(9, 32, 91, 88, 12) }, path { rr(54, 22, 80, 40, 6) }) }
    val camLensOuter by lazy { path { oval(50, 60, 24, 24) } }
    val camLensMid by lazy { path { oval(50, 60, 18, 18) } }
    val camLensIn by lazy { path { oval(50, 60, 10, 10) } }
    val camFlash by lazy { path { rr(15, 38, 31, 47, 3.5) } }
    val camBtn by lazy { path { rr(20, 24, 34, 33, 3.5) } }
    val camShine by lazy { path { oval(45, 55, 4, 3) } }

    val card by lazy { path { rr(8, 16, 92, 90, 7) } }
    val cardLines by lazy { path { for (i in 0..3) { m(20, 40 + i * 12); l(if (i == 3) 52 else 78, 40 + i * 12) } } }
    val cardHeart by lazy { path { heart(78, 30, 7) } }

    val env by lazy { path { rr(8, 28, 92, 86, 9) } }
    val envFlap by lazy {
        path { m(12, 30); l(88, 30); c(91, 30, 92, 32, 90, 34); l(53, 62); c(51, 63.5, 49, 63.5, 47, 62); l(10, 34); c(8, 32, 9, 30, 12, 30); close() }
    }
    val envFolds by lazy { path { m(12, 84); l(42, 56); m(88, 84); l(58, 56) } }
    val envHeart by lazy { path { heart(50, 18, 11) } }
    val envSeal by lazy { path { heart(50, 59, 6) } }

    val bowLoop by lazy { path { m(46, 50); c(32, 22, 6, 26, 7, 48); c(8, 70, 30, 76, 46, 52); close() } }
    val bowCrease by lazy { path { m(40, 50); c(30, 40, 22, 42, 16, 48) } }
    val bowTail by lazy { path { m(46, 56); l(30, 92); l(42, 87); l(50, 95); l(53, 60); close() } }
    val bowKnot by lazy { path { oval(50, 51, 8.5, 9.5) } }
}

internal fun DrawScope.drawMugHeart(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, CZ.mug, CZ.mugHandle)
    line(CZ.mugSteam, ink, ink.ink.copy(alpha = .35f), 2.4f)
    line(CZ.mugHandle, ink, lerp(ink.fill, ink.ink, .3f), 9f)
    line(CZ.mugHandle, ink, ink.fill, 5.6f)
    lay(CZ.mug, ink)
    lay(CZ.mugTop, ink, lerp(ink.second, ink.ink, .3f), false)
    lay(CZ.mugHeart, ink, Color.White, false)
    drawFace(44f, 52f, 20f, ink, mouth = false)
}

internal fun DrawScope.drawCandleJar(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, CZ.jar, CZ.flame)
    drawCircle(ink.a(Color(0xFFFFE9A8).copy(alpha = .35f)), 17f, Offset(50f, 18f))
    line(CZ.wick, ink, ink.ink, 2f)
    lay(CZ.flame, ink, Color(0xFFFFC857), false)
    drawPath(CZ.flameIn, ink.a(Color(0xFFFF8F5A)))
    lay(CZ.jar, ink, lerp(ink.fill, Color.White, .3f))
    lay(CZ.jarWax, ink, Cream, false)
    lay(CZ.jarLid, ink, ink.second, false)
    lay(CZ.jarLabel, ink, ink.fill, false)
    drawPath(CZ.jarHeart, ink.a(Color.White))
}

internal fun DrawScope.drawCamera(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, CZ.camBody)
    lay(CZ.camBody, ink)
    lay(CZ.camBtn, ink, ink.second, false)
    lay(CZ.camFlash, ink, Color.White, false)
    lay(CZ.camLensOuter, ink, lerp(ink.ink, Color.White, .2f), false)
    lay(CZ.camLensMid, ink, lerp(ink.second, ink.ink, .25f), false)
    lay(CZ.camLensIn, ink, ink.ink, false)
    drawPath(CZ.camShine, ink.a(Color.White.copy(alpha = .8f)))
    drawOval(ink.blush.copy(alpha = .55f * ink.alpha), Offset(12f, 66f), androidx.compose.ui.geometry.Size(10f, 6f))
    drawOval(ink.blush.copy(alpha = .55f * ink.alpha), Offset(78f, 66f), androidx.compose.ui.geometry.Size(10f, 6f))
}

internal fun DrawScope.drawNoteCard(ink: DoodleInk, sticker: Boolean) {
    withTransform({ rotate(-3f, Offset(50f, 50f)) }) {
        diecut(ink, sticker, CZ.card)
        lay(CZ.card, ink, lerp(ink.fill, Color.White, .45f), false)
        line(CZ.cardLines, ink, ink.second, 2.2f)
        lay(CZ.cardHeart, ink, ink.fill, false)
        drawWashiTape(Offset(50f, 16f), 38f, 13f, -4f, ink.second, 0, ink.ink)
    }
}

internal fun DrawScope.drawEnvelope(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, CZ.env, CZ.envHeart)
    lay(CZ.envHeart, ink, ink.second, false)
    lay(CZ.env, ink, lerp(ink.fill, Color.White, .3f))
    line(CZ.envFolds, ink, lerp(ink.fill, ink.ink, .3f), 1.8f)
    lay(CZ.envFlap, ink, ink.fill, false)
    drawPath(CZ.envSeal, ink.a(Color.White))
}

internal fun DrawScope.drawBow(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, CZ.bowLoop, CZ.bowTail)
    lay(CZ.bowTail, ink, lerp(ink.fill, ink.ink, .06f), false)
    withTransform({ scale(-1f, 1f, Offset(50f, 50f)) }) { lay(CZ.bowTail, ink, lerp(ink.fill, ink.ink, .06f), false) }
    lay(CZ.bowLoop, ink)
    withTransform({ scale(-1f, 1f, Offset(50f, 50f)) }) { lay(CZ.bowLoop, ink) }
    line(CZ.bowCrease, ink, lerp(ink.fill, ink.ink, .3f), 1.8f)
    lay(CZ.bowKnot, ink, ink.second, false)
}
