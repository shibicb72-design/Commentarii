package com.shibi.commentarii.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticlesTest {
    private val size = Size(800f, 1600f)
    private val system = ParticleSystem(
        count = 60, seed = 7, speedDp = 10f..30f, direction = 1, swayDp = 8f, swayHz = 0.2f,
        spinDegPerSec = -40f..40f, flip = true, fadeInFrac = 0.1f, fadeOutFrac = 0.2f,
        sizeDp = 8f..16f, alpha = 0.8f,
    )

    @Test fun capsCount() {
        assertEquals(MAX_PARTICLES, ParticleField(system, size, Density(2f)).count)
    }

    @Test fun sceneParticleCountWithinCap() {
        assertTrue(particleCount(1f) <= MAX_PARTICLES)
        assertTrue(particleCount(1.25f) <= MAX_PARTICLES)
    }

    @Test fun deterministicForSameTime() {
        val a = ParticleField(system, size, Density(2f))
        val b = ParticleField(system, size, Density(2f))
        for (i in 0 until a.count) {
            assertEquals(a.progress(i, 12.5f), b.progress(i, 12.5f), 0f)
            assertEquals(a.y(i, 12.5f, a.progress(i, 12.5f)), b.y(i, 12.5f, b.progress(i, 12.5f)), 0f)
        }
    }

    @Test fun alphaInRangeAndYWrapped() {
        for (dir in intArrayOf(1, -1, 0)) {
            val f = ParticleField(
                ParticleSystem(20, 3, 10f..30f, dir, 8f, 0.2f, -40f..40f, false, 0.1f, 0.2f, 8f..16f, 1f),
                size, Density(2f),
            )
            for (t in floatArrayOf(0f, 1f, 37.3f, 900f, 12345f)) {
                for (i in 0 until f.count) {
                    val p = f.progress(i, t)
                    assertTrue(p in 0f..1f)
                    assertTrue(f.alphaAt(p) in 0f..1f)
                    val y = f.y(i, t, p)
                    assertTrue(y >= -size.height && y <= size.height * 2f)
                }
            }
        }
    }
}
