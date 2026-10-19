package com.shibi.commentarii.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * Page root: the same Surface every page used before, plus the theme's doodle layer behind [content].
 * Themes without a decoration (Rose & Cream) emit only the Surface.
 */
@Composable
fun ThemedPage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        if (LocalAppTheme.current.decoration == null) {
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
 * Background doodle layer; one per screen, never inside lazy items. The animated phase is read only in
 * the draw lambda, so frames cause no recomposition. Static when the theme, the user's per-theme toggle,
 * the system animation setting or the lifecycle say so.
 */
@Composable
fun ThemeBackground(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    val deco = theme.decoration ?: return
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateAsState()
    val systemOn = remember(state) { systemAnimationsEnabled() }
    val run = LocalThemeAnimate.current && theme.animated && theme.periodMillis > 0 &&
        systemOn && state.isAtLeast(Lifecycle.State.STARTED)
    val phase: State<Float> = if (run) {
        rememberInfiniteTransition("themeBg").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(theme.periodMillis, easing = LinearEasing)),
            label = "phase",
        )
    } else {
        remember(theme) { mutableFloatStateOf(theme.staticPhase) }
    }
    Spacer(
        modifier.drawWithCache {
            val frame = deco.build(size, this, dark)
            onDrawBehind { with(frame) { draw(phase.value) } }
        },
    )
}
