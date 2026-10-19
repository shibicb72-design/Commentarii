package com.shibi.commentarii.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin

/** Garden and tea doodles (100x100 unit box): blossoms, leaves, tea things, lavender, steam. */
private object GD {
    val blossomPetal by lazy {
        path { m(50, 50); c(31, 40, 31, 14, 43, 8); l(50, 14); l(57, 8); c(69, 14, 69, 40, 50, 50); close() }
    }
    val blossomHalo by lazy { path { oval(50, 50, 40, 40) } }
    val blossomDots by lazy {
        path {
            for (i in 0 until 5) {
                val a = (i * 72.0 - 90.0) * PI / 180.0
                oval(50 + 4.6 * Math.cos(a), 50 + 4.6 * Math.sin(a), 1.1, 1.1)
            }
        }
    }

    val twig by lazy {
        path {
            m(3, 90); c(28, 76, 48, 64, 68, 44); c(78, 34, 90, 26, 97, 12)
            m(50, 63); c(54, 50, 58, 42, 60, 32)
            m(24, 79); c(20, 72, 22, 66, 28, 60)
        }
    }
    val budA by lazy { path { oval(84, 26, 3.6, 5.2) } }
    val budB by lazy { path { oval(61, 29, 3.4, 5) } }

    val petal by lazy {
        path {
            m(50, 93); c(30, 77, 21, 45, 37, 21); c(41, 14, 46, 12, 50, 17)
            c(54, 12, 59, 14, 63, 21); c(79, 45, 70, 77, 50, 93); close()
        }
    }
    val petalVein by lazy { path { m(50, 86); c(50, 66, 50, 48, 50, 30) } }

    val budBody by lazy { path { m(50, 12); c(67, 24, 70, 46, 50, 62); c(30, 46, 33, 24, 50, 12); close() } }
    val budSepalL by lazy { path { m(50, 64); c(36, 64, 30, 54, 30, 46); c(40, 50, 47, 55, 50, 64); close() } }
    val budSepalR by lazy { path { m(50, 64); c(64, 64, 70, 54, 70, 46); c(60, 50, 53, 55, 50, 64); close() } }
    val budStem by lazy { path { m(50, 64); c(50, 78, 48, 86, 52, 97) } }

    val daisyPetal by lazy { path { m(50, 50); c(43, 40, 43, 16, 50, 7); c(57, 16, 57, 40, 50, 50); close() } }
    val daisyShadow by lazy { path { oval(50, 50, 42, 42) } }
    val daisyCore by lazy { path { oval(50, 50, 11, 11) } }

    val sprigStem by lazy { path { m(50, 97); c(47, 70, 53, 40, 50, 12) } }
    val leaf by lazy { path { m(0, 0); c(6, -13, 21, -15, 31, -8); c(24, 5, 10, 9, 0, 0); close() } }
    val leafRib by lazy { path { m(1, 0); c(10, -2, 18, -4, 27, -7) } }

    val teaLeaf by lazy { path { m(50, 93); c(18, 76, 14, 34, 50, 7); c(86, 34, 82, 76, 50, 93); close() } }
    val teaVeins by lazy {
        path {
            m(50, 88); c(50, 62, 50, 36, 50, 14)
            m(50, 72); c(42, 68, 36, 62, 31, 55); m(50, 72); c(58, 68, 64, 62, 69, 55)
            m(50, 54); c(43, 50, 38, 44, 35, 38); m(50, 54); c(57, 50, 62, 44, 65, 38)
            m(50, 38); c(46, 34, 44, 30, 43, 26); m(50, 38); c(54, 34, 56, 30, 57, 26)
        }
    }

    val cup by lazy {
        path { m(14, 41); l(86, 41); c(86, 77, 72, 91, 50, 91); c(28, 91, 14, 77, 14, 41); close() }
    }
    val cupRim by lazy { path { oval(50, 41, 36, 8) } }
    val cupMatcha by lazy { path { oval(50, 42, 31, 5.4) } }
    val cupHandle by lazy { path { m(85, 49); c(101, 46, 101, 72, 79, 74) } }
    val saucer by lazy { path { oval(50, 93, 42, 5) } }

    val whiskHandle by lazy { path { rr(41, 5, 59, 46, 7) } }
    val whiskBulb by lazy {
        path { m(43, 45); c(22, 56, 20, 80, 31, 94); c(40, 99, 60, 99, 69, 94); c(80, 80, 78, 56, 57, 45); close() }
    }
    val whiskBand by lazy { path { rr(40, 42, 60, 48, 3) } }
    val whiskTines by lazy {
        path {
            for (x in floatArrayOf(33f, 41f, 50f, 59f, 67f)) {
                m(50, 48); q((50 + x) / 2f - 4f * (x - 50f) / 20f, 70, x, 95)
            }
        }
    }

    val potBody by lazy { path { oval(46, 62, 32, 27) } }
    val potLid by lazy { path { oval(46, 36, 15, 6) } }
    val potKnob by lazy { path { oval(46, 28, 4.5, 4.5) } }
    val potSpout by lazy {
        path { m(74, 56); c(84, 56, 88, 46, 94, 37); c(98, 35, 99, 42, 97, 46); c(94, 58, 88, 71, 72, 73); close() }
    }
    val potHandle by lazy { path { m(19, 52); c(-1, 50, -1, 78, 21, 77) } }
    val potBase by lazy { path { rr(26, 84, 66, 91, 3) } }

    val mochi by lazy { path { m(12, 78); c(8, 52, 24, 30, 50, 30); c(76, 30, 92, 52, 88, 78); c(86, 90, 14, 90, 12, 78); close() } }
    val mochiLeaf by lazy { path { m(50, 32); c(44, 20, 56, 10, 66, 12); c(66, 24, 58, 32, 50, 32); close() } }
    val onigiri by lazy { path { m(50, 10); c(60, 10, 90, 70, 86, 82); c(84, 90, 16, 90, 14, 82); c(10, 70, 40, 10, 50, 10); close() } }
    val nori by lazy { path { m(30, 70); l(70, 70); c(72, 80, 74, 86, 72, 88); l(28, 88); c(26, 86, 28, 80, 30, 70); close() } }
    val stick by lazy { path { m(50, 3); l(50, 97) } }
    val ball1 by lazy { path { oval(50, 23, 18, 18) } }
    val ball2 by lazy { path { oval(50, 52, 18, 18) } }
    val ball3 by lazy { path { oval(50, 81, 18, 18) } }

    val stalkA by lazy { path { rr(33, 3, 47, 31, 6); rr(33, 33, 47, 62, 6); rr(33, 64, 47, 97, 6) } }
    val stalkB by lazy { path { rr(58, 14, 70, 44, 5); rr(58, 46, 70, 74, 5); rr(58, 76, 70, 98, 5) } }
    val nodes by lazy {
        path {
            rr(31, 29, 49, 35, 3); rr(31, 60, 49, 66, 3)
            rr(56, 42, 72, 48, 3); rr(56, 72, 72, 78, 3)
        }
    }

    val lavStems by lazy {
        path {
            m(50, 98); c(48, 72, 50, 48, 50, 16)
            m(48, 98); c(40, 76, 32, 58, 27, 28)
            m(52, 98); c(60, 76, 68, 58, 73, 28)
        }
    }
    val lavBuds by lazy {
        path {
            val xs = floatArrayOf(
                50f, 47f, 53f, 47.5f, 52.5f, 48f, 52f, 49f,
                27f, 25f, 29.5f, 26f, 30f, 28f, 32f, 31f,
                73f, 75f, 70.5f, 74f, 70f, 72f, 68f, 69f,
            )
            val ys = floatArrayOf(
                14f, 23f, 23f, 32f, 32f, 41f, 41f, 50f,
                26f, 34f, 34f, 42f, 42f, 50f, 50f, 58f,
                26f, 34f, 34f, 42f, 42f, 50f, 50f, 58f,
            )
            for (i in xs.indices) oval(xs[i], ys[i], 3.8, 5.6)
        }
    }
    val lavBow by lazy {
        path { m(50, 85); c(36, 76, 34, 94, 50, 87); c(66, 94, 64, 76, 50, 85); close() }
    }

    val steam by lazy {
        path { m(0, 90); c(-9, 80, 9, 70, 0, 60); c(-9, 50, 9, 40, 0, 30) }
    }
}

private val Yellow = Color(0xFFFFE08A)
private val Wood = Color(0xFFD9B98A)
private val Matcha = Color(0xFF9CC77A)

internal fun DrawScope.drawBlossom(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, GD.blossomHalo)
    for (i in 0 until 5) {
        rotate(i * 72f, Offset(50f, 50f)) { lay(GD.blossomPetal, ink, ink.fill, highlight = i == 0) }
    }
    drawCircle(ink.a(Yellow), 8.5f, Offset(50f, 50f))
    drawCircle(ink.lineOf(Yellow), 8.5f, Offset(50f, 50f), style = Stroke(1.6f))
    drawPath(GD.blossomDots, ink.a(ink.blush))
}

private fun DrawScope.miniBlossom(ink: DoodleInk, cx: Float, cy: Float, k: Float) {
    withTransform({ translate(cx - 50f * k, cy - 50f * k); scale(k, k, Offset.Zero) }) { drawBlossom(ink, false) }
}

internal fun DrawScope.drawBranch(ink: DoodleInk, sticker: Boolean) {
    val bark = lerp(Color(0xFFB08968), ink.ink, .2f)
    line(GD.twig, ink, bark, 3.4f)
    val bud = lerp(ink.fill, ink.ink, .12f)
    lay(GD.budA, ink, bud, highlight = false)
    lay(GD.budB, ink, bud, highlight = false)
    miniBlossom(ink, 30f, 62f, .3f)
    miniBlossom(ink, 63f, 46f, .32f)
    miniBlossom(ink, 88f, 18f, .28f)
}

internal fun DrawScope.drawPetal(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, GD.petal)
    lay(GD.petal, ink)
    line(GD.petalVein, ink, lerp(ink.fill, ink.ink, .3f), 1.4f)
}

internal fun DrawScope.drawBud(ink: DoodleInk, sticker: Boolean) {
    line(GD.budStem, ink, LeafGreen, 3f)
    lay(GD.budSepalL, ink, LeafGreen, highlight = false)
    lay(GD.budSepalR, ink, LeafGreen, highlight = false)
    diecut(ink, sticker, GD.budBody)
    lay(GD.budBody, ink)
}

internal fun DrawScope.drawDaisy(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, GD.daisyShadow)
    val petal = ink.a(Color.White)
    val edge = ink.lineOf(Color.White)
    for (i in 0 until 10) {
        rotate(i * 36f, Offset(50f, 50f)) {
            drawPath(GD.daisyPetal, petal)
            drawPath(GD.daisyPetal, edge, style = Stroke(1.6f, cap = StrokeCap.Round))
        }
    }
    lay(GD.daisyCore, ink, Yellow)
}

internal fun DrawScope.drawLeafSprig(ink: DoodleInk, sticker: Boolean) {
    line(GD.sprigStem, ink, lerp(LeafGreen, ink.ink, .3f), 2.6f)
    val x = floatArrayOf(50f, 50f, 50f, 50f, 50f)
    val y = floatArrayOf(80f, 64f, 48f, 34f, 18f)
    val rot = floatArrayOf(-40f, -140f, -42f, -138f, -90f)
    val sc = floatArrayOf(1f, 1f, .9f, .85f, .8f)
    for (i in 0 until 5) {
        withTransform({
            translate(x[i], y[i]); rotate(rot[i], Offset.Zero); scale(sc[i], sc[i], Offset.Zero)
        }) {
            lay(GD.leaf, ink, if (i % 2 == 0) ink.fill else lerp(ink.fill, ink.second, .4f))
            drawPath(GD.leafRib, ink.lineOf(ink.fill), style = Stroke(1.5f, cap = StrokeCap.Round))
        }
    }
}

internal fun DrawScope.drawTeaLeaf(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, GD.teaLeaf)
    lay(GD.teaLeaf, ink)
    drawPath(GD.teaVeins, ink.lineOf(ink.fill), style = Stroke(1.5f, cap = StrokeCap.Round))
}

internal fun DrawScope.drawTeacupFace(ink: DoodleInk, sticker: Boolean) {
    lay(GD.saucer, ink, Color.White, highlight = false)
    line(GD.cupHandle, ink, ink.lineOf(ink.fill), 6f)
    line(GD.cupHandle, ink, ink.fill, 3.2f)
    diecut(ink, sticker, GD.cup)
    lay(GD.cup, ink)
    lay(GD.cupRim, ink, Cream, highlight = false)
    drawPath(GD.cupMatcha, ink.a(Matcha))
    drawFace(50f, 68f, 26f, ink)
}

internal fun DrawScope.drawWhisk(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, GD.whiskHandle, GD.whiskBulb)
    lay(GD.whiskHandle, ink, Wood)
    lay(GD.whiskBulb, ink, Cream, highlight = false)
    drawPath(GD.whiskTines, ink.lineOf(Cream).copy(alpha = .55f * ink.alpha), style = Stroke(1.3f, cap = StrokeCap.Round))
    lay(GD.whiskBand, ink, ink.second, highlight = false)
}

internal fun DrawScope.drawTeapot(ink: DoodleInk, sticker: Boolean) {
    line(GD.potHandle, ink, ink.lineOf(ink.fill), 7f)
    line(GD.potHandle, ink, ink.fill, 4f)
    lay(GD.potSpout, ink, ink.fill, highlight = false)
    lay(GD.potBase, ink, ink.second, highlight = false)
    diecut(ink, sticker, GD.potBody)
    lay(GD.potBody, ink)
    lay(GD.potLid, ink, ink.second, highlight = false)
    lay(GD.potKnob, ink, ink.second, highlight = false)
    drawFace(46f, 62f, 28f, ink)
}

internal fun DrawScope.drawDango(ink: DoodleInk, sticker: Boolean) {
    line(GD.stick, ink, Wood, 3.4f)
    diecut(ink, sticker, GD.ball1, GD.ball2, GD.ball3)
    lay(GD.ball3, ink, ink.second)
    drawFace(50f, 83f, 20f, ink)
    lay(GD.ball2, ink, Cream)
    drawFace(50f, 54f, 20f, ink)
    lay(GD.ball1, ink, ink.fill)
    drawFace(50f, 25f, 20f, ink)
}

internal fun DrawScope.drawMochi(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, GD.mochi)
    lay(GD.mochi, ink, Color.White)
    lay(GD.mochiLeaf, ink, LeafGreen, highlight = false)
    drawFace(50f, 60f, 28f, ink)
}

internal fun DrawScope.drawOnigiri(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, GD.onigiri)
    lay(GD.onigiri, ink, Color.White)
    lay(GD.nori, ink, lerp(ink.ink, Matcha, .25f), highlight = false)
    drawFace(50f, 48f, 26f, ink)
}

internal fun DrawScope.drawBamboo(ink: DoodleInk, sticker: Boolean) {
    diecut(ink, sticker, GD.stalkA, GD.stalkB)
    lay(GD.stalkB, ink, lerp(ink.fill, ink.second, .5f), highlight = false)
    lay(GD.stalkA, ink, ink.fill, highlight = false)
    drawPath(GD.nodes, ink.a(lerp(ink.fill, ink.ink, .22f)))
    val hi = Color.White.copy(alpha = .5f * ink.alpha)
    drawLine(hi, Offset(36f, 9f), Offset(36f, 26f), 2f, StrokeCap.Round)
    drawLine(hi, Offset(36f, 40f), Offset(36f, 57f), 2f, StrokeCap.Round)
    drawLine(hi, Offset(61f, 22f), Offset(61f, 40f), 2f, StrokeCap.Round)
    withTransform({ translate(49f, 32f); rotate(-30f, Offset.Zero); scale(.8f, .8f, Offset.Zero) }) {
        lay(GD.leaf, ink, LeafGreen, highlight = false)
    }
    withTransform({ translate(56f, 60f); rotate(-150f, Offset.Zero); scale(.7f, .7f, Offset.Zero) }) {
        lay(GD.leaf, ink, LeafGreen, highlight = false)
    }
}

internal fun DrawScope.drawLavender(ink: DoodleInk, sticker: Boolean) {
    line(GD.lavStems, ink, lerp(LeafGreen, ink.ink, .3f), 2.2f)
    lay(GD.lavBuds, ink, ink.fill, highlight = false)
    lay(GD.lavBow, ink, ink.second, highlight = false)
}

/** Three drifting S-curves; phase comes from [t] so they rise and fade in turn (max alpha stays faint). */
internal fun DrawScope.drawSteam(ink: DoodleInk, sticker: Boolean, t: Float = 0f) {
    val xs = floatArrayOf(34f, 50f, 66f)
    for (i in 0 until 3) {
        val p = (t * .35f + i / 3f) % 1f
        val a = sin(p * PI.toFloat()) * .22f * ink.alpha
        withTransform({ translate(xs[i], -p * 14f) }) {
            drawPath(GD.steam, ink.ink.copy(alpha = a), style = Stroke(4f, cap = StrokeCap.Round))
        }
    }
}
