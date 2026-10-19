package com.shibi.commentarii.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.isActive

/**
 * Page root: the same Surface every page used before, plus the theme's doodle layer behind [content].
 * Themes without a decoration or scene (Rose & Cream) emit only the Surface.
 */
@Composable
fun ThemedPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        val theme = LocalAppTheme.current
        if (theme.decoration == null && theme.scene == null) {
            content()
        } else {
            Box(Modifier.fillMaxSize()) {
                ThemeBackground(Modifier.fillMaxSize())
                content()
            }
        }
    }
}

/**
 * Background layers; one per screen, never inside lazy items. A single withFrameNanos loop writes [time];
 * it is read only inside draw lambdas, so frames cause no recomposition. The loop runs only when the theme,
 * the user's per-theme toggle, the system animation setting and the lifecycle all allow it; pausing keeps
 * the last frame and resuming continues from it.
 */
@Composable
fun ThemeBackground(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val deco = theme.decoration
    val scene = theme.scene
    if (deco == null && scene == null) return
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateAsState()
    val systemOn = remember(state) { systemAnimationsEnabled() }
    val enabled = LocalThemeAnimate.current && theme.animated && systemOn
    val run = enabled && state.isAtLeast(Lifecycle.State.STARTED)
    val time = remember(theme) { mutableFloatStateOf(theme.staticTimeSec) }
    // Off toggle / system remove-animations: snap to the designed static frame (lifecycle pauses keep the last frame).
    LaunchedEffect(theme, enabled) { if (!enabled) time.floatValue = theme.staticTimeSec }
    if (run) {
        LaunchedEffect(theme, run) {
            val base = time.floatValue
            val start = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { time.floatValue = base + (it - start) / 1e9f }
            }
        }
    }
    // Layer 1: paper + v1 phase decoration (phase is derived from time until the S7 scenes replace it).
    if (deco != null) {
        Spacer(
            modifier.drawWithCache {
                val frame = deco.build(size, this, dark)
                onDrawBehind {
                    val phase = if (theme.periodMillis > 0 && enabled) {
                        val c = time.floatValue * 1000f / theme.periodMillis
                        c - kotlin.math.floor(c)
                    } else {
                        theme.staticPhase
                    }
                    with(frame) { draw(phase) }
                }
            },
        )
    }
    // Layer 2: particle/parallax scene, only for themes that define one.
    if (scene != null) {
        Spacer(
            modifier.drawWithCache {
                val frame = scene.build(size, this, dark)
                onDrawBehind { with(frame) { draw(time.floatValue) } }
            },
        )
    }
}
