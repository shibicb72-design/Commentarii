package com.shibi.commentarii.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** One frame painter of an animated scene; [timeSec] is the only moving input and is read only while drawing. */
fun interface SceneFrame { fun DrawScope.draw(timeSec: Float) }

/** Builds the [SceneFrame] for a page size so positions stay size-relative and paths are built once. */
fun interface ThemeScene { fun build(size: Size, density: Density, dark: Boolean): SceneFrame }

/** Hard cap of particles per screen across all systems of a scene. */
const val MAX_PARTICLES = 40

/**
 * Declarative particle system. [direction]: +1 falls down, -1 rises, 0 drifts horizontally and wraps.
 * Speeds, sway amplitude and size are in dp; [fadeInFrac]/[fadeOutFrac] are fractions of the travel path.
 */
@Immutable
class ParticleSystem(
    val count: Int,
    val seed: Int,
    val speedDp: ClosedFloatingPointRange<Float>,
    val direction: Int,
    val swayDp: Float,
    val swayHz: Float,
    val spinDegPerSec: ClosedFloatingPointRange<Float>,
    val flip: Boolean,
    val fadeInFrac: Float,
    val fadeOutFrac: Float,
    val sizeDp: ClosedFloatingPointRange<Float>,
    val alpha: Float,
)

/** Per-particle constants, generated once per size; positions are pure functions of time. */
class ParticleField(private val system: ParticleSystem, val size: Size, density: Density, gutter: Float = 0f) {
    val count: Int = system.count.coerceIn(0, MAX_PARTICLES)
    private val dp = density.density
    private val x0 = FloatArray(count)
    private val y0 = FloatArray(count)
    private val speed = FloatArray(count)
    private val phase = FloatArray(count)
    private val spin = FloatArray(count)
    private val sizePx = FloatArray(count)

    init {
        var s = system.seed * 2654435761L.toInt() + 12345
        fun next(): Float {
            s = s * 1664525 + 1013904223
            return ((s ushr 8) and 0xFFFFFF) / 16777216f
        }
        for (i in 0 until count) {
            x0[i] = next() * size.width
            if (gutter > 0f && system.direction != 0) {
                // fold x into the left/right gutters (fraction of width each) so content stays clear
                val f = x0[i] / size.width
                x0[i] = size.width * if (f < 0.5f) f * 2f * gutter else 1f - gutter + (f - 0.5f) * 2f * gutter
            }
            y0[i] = next() * (size.height + 2f * margin(i))
            speed[i] = lerpF(system.speedDp.start, system.speedDp.endInclusive, next()) * dp
            phase[i] = next() * (2f * PI.toFloat())
            spin[i] = lerpF(system.spinDegPerSec.start, system.spinDegPerSec.endInclusive, next())
            sizePx[i] = lerpF(system.sizeDp.start, system.sizeDp.endInclusive, next()) * dp
        }
    }

    private fun margin(i: Int) = system.sizeDp.endInclusive * dp

    /** Travel progress in [0,1) of particle [i] at [t]; also the fade parameter. */
    fun progress(i: Int, t: Float): Float {
        val span = if (system.direction == 0) size.width + 2f * margin(i) else size.height + 2f * margin(i)
        val start = if (system.direction == 0) x0[i] else y0[i]
        val travel = start + speed[i] * t
        val w = travel / span
        return w - floor(w)
    }

    fun alphaAt(p: Float): Float {
        val a = system.alpha
        val fi = system.fadeInFrac
        val fo = system.fadeOutFrac
        var f = 1f
        if (fi > 0f && p < fi) f = p / fi
        if (fo > 0f && p > 1f - fo) f = min(f, (1f - p) / fo)
        return (a * f).coerceIn(0f, 1f)
    }

    internal fun x(i: Int, t: Float, p: Float): Float {
        val m = margin(i)
        val sway = system.swayDp * dp * sin(2f * PI.toFloat() * system.swayHz * t + phase[i])
        return if (system.direction == 0) -m + p * (size.width + 2f * m) else x0[i] + sway
    }

    internal fun y(i: Int, t: Float, p: Float): Float {
        val m = margin(i)
        val span = size.height + 2f * m
        val sway = system.swayDp * dp * sin(2f * PI.toFloat() * system.swayHz * t + phase[i])
        return when (system.direction) {
            0 -> y0[i] % max(size.height, 1f) + sway
            1 -> -m + p * span
            else -> size.height + m - p * span
        }
    }

    internal fun angle(i: Int, t: Float) = phase[i] * 57.29578f * 0.25f + spin[i] * t
    internal fun flipScale(i: Int, t: Float): Float =
        if (system.flip) cos(2f * PI.toFloat() * 0.35f * t + phase[i]).let { if (abs(it) < 0.15f) 0.15f else it } else 1f
    internal fun sizeOf(i: Int) = sizePx[i]
    internal fun seedOf(i: Int) = phase[i]
}

/**
 * Draws every particle at time [t]. [draw] paints a unit-centred particle around the origin with extent
 * [size] px and the already-faded alpha. One withTransform per particle; no allocation.
 */
fun DrawScope.drawParticles(
    field: ParticleField,
    t: Float,
    draw: DrawScope.(i: Int, size: Float, alpha: Float) -> Unit,
) {
    for (i in 0 until field.count) {
        val p = field.progress(i, t)
        val a = field.alphaAt(p)
        if (a <= 0.01f) continue
        val x = field.x(i, t, p)
        val y = field.y(i, t, p)
        val sz = field.sizeOf(i)
        withTransform({
            translate(x, y)
            rotate(field.angle(i, t), pivot = androidx.compose.ui.geometry.Offset.Zero)
            scale(field.flipScale(i, t), 1f, pivot = androidx.compose.ui.geometry.Offset.Zero)
        }) { draw(i, sz, a) }
    }
}

/** Twinkle brightness in [0.3,1]. */
fun twinkle(t: Float, seed: Float): Float = 0.3f + 0.7f * sin(t * 1.7f + seed).let { it * it }

private fun lerpF(a: Float, b: Float, f: Float) = a + (b - a) * f
