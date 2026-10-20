package com.shibi.commentarii.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp

/** Planner stickers in a 0..100 box. Paths are built once, in this lazy holder. */
private object PL {
    val bearEarL by lazy { path { oval(25, 24, 11, 11) } }
    val bearEarR by lazy { path { oval(75, 24, 11, 11) } }
    val bearEarInL by lazy { path { oval(25, 25, 6, 6) } }
    val bearEarInR by lazy { path { oval(75, 25, 6, 6) } }
    val bearHead by lazy { path { oval(50, 42, 35, 29) } }
    val bearMuzzle by lazy { path { oval(50, 50, 12, 8.5) } }
    val bearPawL by lazy { path { oval(31, 61, 8.5, 6) } }
    val bearPawR by lazy { path { oval(69, 61, 8.5, 6) } }
    val noteLow by lazy { path { rr(5, 60, 95, 98, 6) } }
    val noteHeart by lazy { path { heart(80, 84, 6) } }
    val bearMouth by lazy { path { m(50, 49); l(50, 52); m(45, 54); q(47.5, 56, 50, 52); q(52.5, 56, 55, 54) } }
    val bearNoteLines by lazy { path { m(14, 74); l(60, 74); m(14, 86); l(46, 86) } }

    val note by lazy {
        path {
            m(16, 12); l(84, 12); c(88, 12, 90, 14, 90, 18); l(90, 70); l(70, 90); l(16, 90)
            c(12, 90, 10, 88, 10, 84); l(10, 18); c(10, 14, 12, 12, 16, 12); close()
        }
    }
    val noteFold by lazy { path { m(90, 70); c(82, 71, 73, 72, 72, 74); c(71, 80, 71, 85, 70, 90); close() } }
    val noteHeartSm by lazy { path { heart(30, 76, 5.5) } }
    val noteLines by lazy {
        path { for (i in 0..2) { m(22, 36 + i * 14); l(if (i == 2) 52 else 76, 36 + i * 14) } }
    }

    val potBody by lazy { path { m(28, 62); l(72, 62); c(72, 80, 68, 92, 64, 94); l(36, 94); c(32, 92, 28, 80, 28, 62); close() } }
    val potRim by lazy { path { rr(22, 52, 78, 66, 6) } }
    val leafL by lazy { path { m(50, 54); c(30, 52, 16, 38, 18, 20); c(38, 22, 50, 36, 50, 54); close() } }
    val leafC by lazy { path { m(50, 54); c(38, 42, 38, 22, 50, 6); c(62, 22, 62, 42, 50, 54); close() } }
    val leafRib by lazy { path { m(50, 54); q(44, 34, 50, 14) } }

    val crown by lazy {
        path {
            m(14, 80); l(10, 32); c(16, 30, 26, 44, 32, 54); c(38, 44, 44, 26, 50, 18)
            c(56, 26, 62, 44, 68, 54); c(74, 44, 84, 30, 90, 32); l(86, 80); close()
        }
    }
    val crownBand by lazy { path { rr(13, 70, 87, 86, 6) } }

    val flagCloth by lazy {
        path { m(29, 14); c(45, 8, 58, 22, 84, 14); l(75, 30); l(84, 46); c(58, 54, 45, 40, 29, 48); close() }
    }
    val flagPole by lazy { path { rr(23, 12, 29, 92, 3) } }
    val flagBase by lazy { path { oval(26, 92, 15, 4.5) } }
    val flagStar by lazy { path { star(52, 31, 8) } }

    val bubble by lazy {
        union(
            path { rr(8, 12, 92, 68, 16) },
            path { m(26, 62); c(26, 74, 22, 84, 14, 90); c(30, 90, 44, 82, 52, 66); close() },
        )
    }
    val exBar by lazy { path { rr(45, 24, 55, 48, 5) } }
    val exDot by lazy { path { oval(50, 57, 5.2, 5.2) } }
    val exTicks by lazy { path { m(80, 22); l(85, 16); m(86, 32); l(92, 30); m(72, 18); l(74, 11) } }

    val calBody by lazy { path { rr(12, 18, 88, 92, 9) } }
    val calHead by lazy {
        path { m(12, 42); l(12, 27); c(12, 22, 15, 18, 21, 18); l(79, 18); c(85, 18, 88, 22, 88, 27); l(88, 42); close() }
    }
    val calRingL by lazy { path { rr(27, 9, 34, 26, 3.5) } }
    val calRingR by lazy { path { rr(66, 9, 73, 26, 3.5) } }
    val calHeart by lazy { path { heart(50, 68, 7) } }

    val cartBasket by lazy {
        path { m(26, 34); l(90, 34); c(90, 42, 84, 62, 79, 67); l(37, 67); c(32, 56, 28, 42, 26, 34); close() }
    }
    val cartHandle by lazy { path { m(8, 16); l(18, 16); c(21, 16, 23, 18, 24, 23); l(38, 67) } }
    val cartHeart by lazy { path { heart(58, 30, 9) } }
    val cartWheelL by lazy { path { oval(42, 81, 6.5, 6.5) } }
    val cartWheelR by lazy { path { oval(72, 81, 6.5, 6.5) } }

    val bag by lazy { path { rr(17, 34, 83, 93, 9) } }
    val bagBand by lazy { path { rr(17, 34, 83, 46, 5) } }
    val bagHandle by lazy { path { m(36, 38); c(36, 8, 64, 8, 64, 38) } }
    val bagHeart by lazy { path { heart(50, 66, 15) } }

    val forkHead by lazy {
        union(
            path {
                m(36, 10); l(36, 34); c(36, 42, 40, 46, 50, 46); c(60, 46, 64, 42, 64, 34); l(64, 10); l(58, 10)
                l(58, 30); l(53, 30); l(53, 10); l(47, 10); l(47, 30); l(42, 30); l(42, 10); close()
            },
            path { rr(45, 40, 55, 92, 5) },
        )
    }
    val spoon by lazy { union(path { oval(50, 24, 15, 20) }, path { rr(45, 38, 55, 92, 5) }) }

    val bobaCup by lazy {
        path { m(24, 34); l(76, 34); l(68, 90); c(67, 94, 64, 95, 60, 95); l(40, 95); c(36, 95, 33, 94, 32, 90); close() }
    }
    val bobaLid by lazy { path { m(20, 35); c(20, 16, 80, 16, 80, 35); close() } }
    val bobaRim by lazy { path { rr(17, 32, 83, 40, 4) } }
    val bobaStraw by lazy { path { m(56, 22); l(66, 3) } }
    val bobaPearls by lazy {
        path {
            for ((x, y) in listOf(35 to 87, 46 to 89, 57 to 88, 67 to 85, 40 to 79, 54 to 81, 62 to 76, 48 to 72)) oval(x, y, 4.2, 4.2)
        }
    }

    val drop by lazy {
        path { m(50, 5); c(50, 5, 21, 40, 21, 63); c(21, 81, 34, 95, 50, 95); c(66, 95, 79, 81, 79, 63); c(79, 40, 50, 5, 50, 5); close() }
    }
    val smiley by lazy { path { oval(50, 50, 41, 41) } }
    val smileySpark by lazy { path { m(88, 12); l(88, 22); m(83, 17); l(93, 17) } }

    val jar by lazy {
        path { m(27, 52); l(27, 86); c(27, 92, 31, 95, 38, 95); l(62, 95); c(69, 95, 73, 92, 73, 86); l(73, 52); close() }
    }
    val jarNeck by lazy { path { rr(29, 44, 71, 54, 3.5) } }
    val jarStripe by lazy { path { rr(33, 60, 38, 86, 2.5) } }
    val tulipStems by lazy { path { m(50, 66); l(50, 34); m(42, 66); c(40, 56, 36, 46, 34, 38); m(58, 66); c(60, 56, 64, 46, 66, 38) } }
    private fun tulip(cx: Float, cy: Float) = path {
        m(cx - 9, cy - 8); l(cx - 9, cy + 2); c(cx - 9, cy + 14, cx + 9, cy + 14, cx + 9, cy + 2)
        l(cx + 9, cy - 8); l(cx + 4.5, cy - 3); l(cx, cy - 10); l(cx - 4.5, cy - 3); close()
    }
    val tulipL by lazy { tulip(33f, 30f) }
    val tulipC by lazy { tulip(50f, 20f) }
    val tulipR by lazy { tulip(67f, 30f) }

    val cactus by lazy {
        union(
            union(path { rr(35, 14, 65, 78, 15) }, path { rr(14, 38, 28, 62, 7) }),
            union(
                union(path { rr(14, 52, 42, 64, 6) }, path { rr(72, 30, 86, 52, 7) }),
                path { rr(58, 42, 86, 54, 6) },
            ),
        )
    }
    val cactusPot by lazy { path { m(30, 74); l(70, 74); c(70, 86, 66, 94, 62, 95); l(38, 95); c(34, 94, 30, 86, 30, 74); close() } }
    val cactusRim by lazy { path { rr(26, 68, 74, 78, 5) } }
    val cactusSpines by lazy { path { for ((x, y) in listOf(42 to 28, 58 to 36, 42 to 60, 58 to 62, 20 to 46, 79 to 38)) { m(x - 2, y); l(x + 2, y - 1.5) } } }
    val cactusFlower by lazy { path { for (i in 0 until 5) { val a = i * 1.2566f; oval(50 + 6 * Math.cos(a.toDouble()).toFloat(), 12 + 6 * Math.sin(a.toDouble()).toFloat(), 3.6, 3.6) } } }

    val penBody by lazy { path { rr(41, 8, 59, 72, 6) } }
    val penCap by lazy { path { rr(41, 8, 59, 24, 6) } }
    val penTip by lazy { path { m(41, 72); l(59, 72); c(57, 82, 54, 88, 50, 94); c(46, 88, 43, 82, 41, 72); close() } }
    val penNib by lazy { path { m(47.5, 86); c(49, 91, 51, 91, 52.5, 86); c(51.5, 84, 48.5, 84, 47.5, 86); close() } }
    val penClip by lazy { path { rr(60, 10, 64, 40, 2) } }
    val penHeart by lazy { path { heart(50, 50, 5.5) } }

    val speech by lazy {
        union(
            path { oval(50, 44, 42, 33) },
            path { m(28, 64); c(28, 76, 24, 84, 16, 90); c(34, 90, 46, 82, 54, 72); close() },
        )
    }
    val speechHeart by lazy { path { heart(50, 43, 17) } }
}

private val brown get() = Color(0xFF8A6A5A)

internal fun DrawScope.drawBearPeek(ink: DoodleInk, sticker: Boolean) {
    val body = lerp(ink.fill, brown, .45f)
    val light = lerp(body, Color.White, .55f)
    diecut(ink, sticker, PL.bearHead, PL.bearEarL, PL.bearEarR, PL.noteLow)
    lay(PL.bearEarL, ink, body); lay(PL.bearEarR, ink, body)
    lay(PL.bearEarInL, ink, ink.blush, false); lay(PL.bearEarInR, ink, ink.blush, false)
    lay(PL.bearHead, ink, body)
    lay(PL.bearMuzzle, ink, light, false)
    drawFace(50f, 36f, 30f, ink, mouth = false)
    drawOval(ink.a(ink.ink), Offset(46.8f, 44.6f), androidx.compose.ui.geometry.Size(6.4f, 4.6f))
    line(PL.bearMouth, ink, ink.ink, 1.6f)
    lay(PL.noteLow, ink, ink.second)
    lay(PL.noteHeart, ink, ink.blush, false)
    line(PL.bearNoteLines, ink, lerp(ink.second, ink.ink, .3f), 2f)
    lay(PL.bearPawL, ink, body, false); lay(PL.bearPawR, ink, body, false)
}

internal fun DrawScope.drawStickyNote(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.note)
    lay(PL.note, ink)
    drawPath(PL.noteFold, ink.a(lerp(ink.fill, ink.ink, .22f)))
    line(PL.noteLines, ink, ink.ink, 2f)
    lay(PL.noteHeartSm, ink, ink.blush, false)
    drawWashiTape(Offset(50f, 12f), 36f, 13f, -4f, ink.second, 1, ink.ink)
    drawFace(66f, 74f, 14f, ink)
}

internal fun DrawScope.drawPlantPot(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.potBody, PL.leafC, PL.leafL)
    val green = LeafGreen
    lay(PL.leafL, ink, lerp(green, ink.ink, .06f))
    withTransform({ scale(-1f, 1f, Offset(50f, 50f)) }) { lay(PL.leafL, ink, green) }
    lay(PL.leafC, ink, lerp(green, ink.second, .25f))
    line(PL.leafRib, ink, green, 1.6f)
    lay(PL.potBody, ink)
    lay(PL.potRim, ink, lerp(ink.fill, Color.White, .35f))
    drawFace(50f, 79f, 26f, ink)
}

internal fun DrawScope.drawCrown(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.crown)
    lay(PL.crown, ink, Color(0xFFFFD86B))
    lay(PL.crownBand, ink, ink.fill, false)
    for ((x, y) in listOf(10f to 32f, 50f to 18f, 90f to 32f)) drawCircle(ink.a(ink.second), 4.4f, Offset(x, y))
    for (x in floatArrayOf(28f, 72f)) drawCircle(ink.a(Color.White), 2.2f, Offset(x, 78f))
    drawFace(50f, 58f, 22f, ink)
}

internal fun DrawScope.drawFlag(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.flagCloth, PL.flagPole)
    lay(PL.flagBase, ink, lerp(ink.ink, Color.White, .6f), false)
    lay(PL.flagPole, ink, lerp(ink.ink, Color.White, .55f), false)
    drawCircle(ink.a(ink.second), 4.4f, Offset(26f, 10f))
    lay(PL.flagCloth, ink)
    drawPath(PL.flagStar, ink.a(Color.White))
    drawFace(60f, 40f, 12f, ink)
}

internal fun DrawScope.drawBubbleExclaim(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.bubble)
    lay(PL.bubble, ink)
    drawPath(PL.exBar, ink.a(Color.White)); drawPath(PL.exDot, ink.a(Color.White))
    line(PL.exTicks, ink, ink.ink, 2.4f)
}

internal fun DrawScope.drawCalendar(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.calBody)
    lay(PL.calBody, ink, lerp(ink.second, Color.White, .65f), false)
    lay(PL.calHead, ink, ink.fill, false)
    for (y in floatArrayOf(54f, 68f, 80f)) for (x in floatArrayOf(28f, 50f, 72f)) {
        if (!(x == 50f && y == 68f)) drawCircle(ink.a(ink.ink.copy(alpha = .3f)), 2.4f, Offset(x, y))
    }
    drawPath(PL.calHeart, ink.a(ink.fill))
    lay(PL.calRingL, ink, lerp(ink.ink, Color.White, .5f), false)
    lay(PL.calRingR, ink, lerp(ink.ink, Color.White, .5f), false)
    drawFace(50f, 31f, 14f, ink)
}

internal fun DrawScope.drawCart(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.cartBasket)
    lay(PL.cartHeart, ink, ink.second, false)
    line(PL.cartHandle, ink, ink.ink, 3.4f)
    lay(PL.cartBasket, ink)
    lay(PL.cartWheelL, ink, ink.ink, false); lay(PL.cartWheelR, ink, ink.ink, false)
    drawFace(58f, 51f, 20f, ink)
}

internal fun DrawScope.drawBagHeart(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.bag)
    line(PL.bagHandle, ink, ink.ink, 3.6f)
    lay(PL.bag, ink)
    lay(PL.bagBand, ink, ink.second, false)
    lay(PL.bagHeart, ink, Color.White, false)
}

internal fun DrawScope.drawForkSpoon(ink: DoodleInk, sticker: Boolean) {
    withTransform({ translate(-18f, 0f); rotate(-12f, Offset(50f, 50f)) }) {
        diecut(ink, sticker, PL.forkHead); lay(PL.forkHead, ink)
    }
    withTransform({ translate(18f, 0f); rotate(12f, Offset(50f, 50f)) }) {
        diecut(ink, sticker, PL.spoon); lay(PL.spoon, ink, ink.second)
        drawFace(50f, 24f, 16f, ink)
    }
}

internal fun DrawScope.drawBoba(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.bobaCup, PL.bobaLid)
    line(PL.bobaStraw, ink, ink.second, 5.5f)
    lay(PL.bobaCup, ink)
    lay(PL.bobaPearls, ink, lerp(ink.ink, Color.White, .12f), false)
    lay(PL.bobaLid, ink, lerp(ink.second, Color.White, .55f), false)
    lay(PL.bobaRim, ink, ink.second, false)
    drawFace(50f, 58f, 26f, ink)
}

internal fun DrawScope.drawDropFace(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.drop)
    lay(PL.drop, ink)
    drawFace(50f, 66f, 30f, ink)
}

internal fun DrawScope.drawSmiley(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.smiley)
    lay(PL.smiley, ink, Color(0xFFFFE27A))
    line(PL.smileySpark, ink, ink.ink, 2.2f)
    drawFace(50f, 50f, 42f, ink)
}

internal fun DrawScope.drawTulipJar(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.jar, PL.tulipC)
    line(PL.tulipStems, ink, LeafGreen, 3.2f)
    lay(PL.tulipL, ink, ink.second, false); lay(PL.tulipR, ink, ink.second, false)
    lay(PL.tulipC, ink, ink.fill, false)
    lay(PL.jar, ink, lerp(Color.White, ink.second, .3f).copy(alpha = .78f), false)
    lay(PL.jarNeck, ink, lerp(Color.White, ink.second, .5f), false)
    drawPath(PL.jarStripe, ink.a(Color.White.copy(alpha = .6f)))
    drawFace(53f, 78f, 20f, ink)
}

internal fun DrawScope.drawCactus(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.cactus, PL.cactusPot)
    lay(PL.cactus, ink, LeafGreen)
    line(PL.cactusSpines, ink, ink.ink, 1.4f)
    lay(PL.cactusFlower, ink, ink.fill, false)
    drawCircle(ink.a(Color(0xFFFFD86B)), 2.8f, Offset(50f, 12f))
    lay(PL.cactusPot, ink, ink.second)
    lay(PL.cactusRim, ink, lerp(ink.second, Color.White, .35f), false)
    drawFace(50f, 48f, 22f, ink)
}

internal fun DrawScope.drawPen(ink: DoodleInk, sticker: Boolean) {
    rotate(38f, Offset(50f, 50f)) {
        diecut(ink, sticker, PL.penBody, PL.penTip)
        lay(PL.penClip, ink, ink.ink, false)
        lay(PL.penBody, ink)
        lay(PL.penCap, ink, ink.second, false)
        lay(PL.penTip, ink, Color(0xFFF6E3C8), false)
        drawPath(PL.penNib, ink.a(ink.ink))
        drawPath(PL.penHeart, ink.a(Color.White))
    }
}

internal fun DrawScope.drawSpeechHeart(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, PL.speech)
    lay(PL.speech, ink, lerp(ink.second, Color.White, .35f))
    lay(PL.speechHeart, ink, ink.fill, false)
}
