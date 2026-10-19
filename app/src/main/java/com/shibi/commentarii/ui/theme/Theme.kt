package com.shibi.commentarii.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.shibi.commentarii.data.DarkMode

/** Extra palette roles: each background is paired with the colour of the content drawn on it. */
@Immutable
class CommentariiColors(
    val tab: Color,
    val onTab: Color,
    val sage: Color,
    val onSage: Color,
    val card: Color,
    val onCard: Color,
)

private val LightExtras = CommentariiColors(
    tab = Color(0xFFE8B4B8),
    onTab = Color(0xFF2B2220),
    sage = Color(0xFFD8D3C4),
    onSage = Color(0xFF2B2220),
    card = Color(0xFFFFFDFB),
    onCard = Color(0xFF2B2220),
)

private val DarkExtras = CommentariiColors(
    tab = Color(0xFF8E5A5F),
    onTab = Color(0xFFFFF5F3),
    sage = Color(0xFF6E6A5E),
    onSage = Color(0xFFF3ECE9),
    card = Color(0xFF2A2423),
    onCard = Color(0xFFF3ECE9),
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF8E4A52),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF4D6D6),
    onPrimaryContainer = Color(0xFF2B2220),
    secondary = Color(0xFF5E5A4C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8D3C4),
    onSecondaryContainer = Color(0xFF2B2220),
    tertiary = Color(0xFF8E4A52),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE8B4B8),
    onTertiaryContainer = Color(0xFF2B2220),
    background = Color(0xFFFBF6F3),
    onBackground = Color(0xFF2B2220),
    surface = Color(0xFFFBF6F3),
    onSurface = Color(0xFF2B2220),
    surfaceVariant = Color(0xFFEFE7E2),
    onSurfaceVariant = Color(0xFF4A3F3C),
    surfaceTint = Color(0xFF8E4A52),
    inverseSurface = Color(0xFF2B2220),
    inverseOnSurface = Color(0xFFFBF6F3),
    inversePrimary = Color(0xFFE8B4B8),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD9CCC7),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF6F3),
    surfaceDim = Color(0xFFE6DDD8),
    surfaceContainerLowest = Color(0xFFFFFDFB),
    surfaceContainerLow = Color(0xFFF8F1ED),
    surfaceContainer = Color(0xFFF4ECE8),
    surfaceContainerHigh = Color(0xFFEFE6E2),
    surfaceContainerHighest = Color(0xFFE9DFDA),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE8B4B8),
    onPrimary = Color(0xFF3A1D21),
    primaryContainer = Color(0xFF8E5A5F),
    onPrimaryContainer = Color(0xFFFFF5F3),
    secondary = Color(0xFFD8D3C4),
    onSecondary = Color(0xFF2B2820),
    secondaryContainer = Color(0xFF6E6A5E),
    onSecondaryContainer = Color(0xFFF3ECE9),
    tertiary = Color(0xFFE8B4B8),
    onTertiary = Color(0xFF3A1D21),
    tertiaryContainer = Color(0xFF8E5A5F),
    onTertiaryContainer = Color(0xFFFFF5F3),
    background = Color(0xFF1E1A19),
    onBackground = Color(0xFFF3ECE9),
    surface = Color(0xFF1E1A19),
    onSurface = Color(0xFFF3ECE9),
    surfaceVariant = Color(0xFF3A3231),
    onSurfaceVariant = Color(0xFFD9CCC7),
    surfaceTint = Color(0xFFE8B4B8),
    inverseSurface = Color(0xFFF3ECE9),
    inverseOnSurface = Color(0xFF2B2220),
    inversePrimary = Color(0xFF8E4A52),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    outline = Color(0xFF9C8C88),
    outlineVariant = Color(0xFF4F4543),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B3433),
    surfaceDim = Color(0xFF1E1A19),
    surfaceContainerLowest = Color(0xFF1A1615),
    surfaceContainerLow = Color(0xFF231D1C),
    surfaceContainer = Color(0xFF2A2423),
    surfaceContainerHigh = Color(0xFF322B2A),
    surfaceContainerHighest = Color(0xFF3B3433),
)

private val LocalCommentariiColors = staticCompositionLocalOf { LightExtras }

/** Access to the extra palette roles, e.g. `AppColors.current.tab`. */
object AppColors {
    val current: CommentariiColors
        @Composable @ReadOnlyComposable get() = LocalCommentariiColors.current
}

@Composable
fun CommentariiTheme(darkMode: DarkMode, content: @Composable () -> Unit) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    CommentariiTheme(dark = dark, content = content)
}

@Composable
fun CommentariiTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCommentariiColors provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
