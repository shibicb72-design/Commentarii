package com.shibi.commentarii.testutil

import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsNode
import kotlin.math.pow

/** WCAG 2.x relative luminance of an ARGB int (alpha ignored). */
fun luminanceOf(argb: Int): Double {
    fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    val r = channel((argb shr 16) and 0xFF)
    val g = channel((argb shr 8) and 0xFF)
    val b = channel(argb and 0xFF)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG contrast ratio between two ARGB ints, from 1.0 to 21.0. */
fun contrastRatio(a: Int, b: Int): Double {
    val la = luminanceOf(a)
    val lb = luminanceOf(b)
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/** What a region of a rendered window looks like: the colour most of it is painted in, and the colour furthest from that. */
class RegionInk(val background: Int, val ink: Int) {
    val ratio: Double get() = contrastRatio(background, ink)
    val inkIsLighter: Boolean get() = luminanceOf(ink) > luminanceOf(background)
    override fun toString() = "bg #%08X ink #%08X ratio %.2f".format(background, ink, ratio)
}

/**
 * Reads the pixels under a window region: the most common colour (other than [excluding], the text's own declared colour, which a fat glyph can out-number the paper with) is the paper behind the text, and the colour with the
 * highest contrast against it is the ink at full coverage. Text this small and thin can still be anti-aliased below its true
 * colour, so this never over-states contrast.
 */
fun inkIn(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int, excluding: Int? = null): RegionInk {
    val l = left.coerceIn(0, bitmap.width)
    val r = right.coerceIn(0, bitmap.width)
    val t = top.coerceIn(0, bitmap.height)
    val b = bottom.coerceIn(0, bitmap.height)
    val counts = HashMap<Int, Int>()
    for (y in t until b) for (x in l until r) counts.merge(bitmap.getPixel(x, y), 1, Int::plus)
    require(counts.isNotEmpty()) { "no pixels in $left,$top,$right,$bottom of ${bitmap.width}x${bitmap.height}" }
    val background = (counts.filterKeys { it != excluding }.takeIf { it.isNotEmpty() } ?: counts).maxBy { it.value }.key
    val ink = counts.keys.maxBy { contrastRatio(background, it) }
    return RegionInk(background, ink)
}

fun inkOf(bitmap: Bitmap, node: SemanticsNode, excluding: Int? = null): RegionInk {
    val p = node.positionInWindow
    return inkIn(bitmap, p.x.toInt(), p.y.toInt(), (p.x + node.size.width).toInt(), (p.y + node.size.height).toInt(), excluding)
}
