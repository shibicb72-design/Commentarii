package com.shibi.commentarii.ui.theme

import android.animation.ValueAnimator
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DEFAULT_THEME_ID
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
    /** Day-square tints for moods 1 (very sad) to 5 (very happy); [onMood] is the colour of the number drawn on each. */
    val mood: List<Color>,
    val onMood: List<Color>,
    /** Section tab colours; a section picks one by a stable hash of its title. All [onTab]-paired. */
    val tabs: List<Color> = listOf(tab),
)

private val LightExtras = CommentariiColors(
    tab = Color(0xFFE8B4B8),
    onTab = Color(0xFF2B2220),
    sage = Color(0xFFD8D3C4),
    onSage = Color(0xFF2B2220),
    card = Color(0xFFFFFDFB),
    onCard = Color(0xFF2B2220),
    mood = listOf(Color(0xFFE8A7A7), Color(0xFFF2CDB5), Color(0xFFE2DCEC), Color(0xFFDCE6C4), Color(0xFFBFDDBE)),
    onMood = List(5) { Color(0xFF2B2220) },
)

private val DarkExtras = CommentariiColors(
    tab = Color(0xFF8E5A5F),
    onTab = Color(0xFFFFF5F3),
    sage = Color(0xFF6E6A5E),
    onSage = Color(0xFFF3ECE9),
    card = Color(0xFF2A2423),
    onCard = Color(0xFFF3ECE9),
    mood = listOf(Color(0xFF8C3A3A), Color(0xFF7A5236), Color(0xFF4A4560), Color(0xFF5C6B3A), Color(0xFF3F6B47)),
    onMood = List(5) { Color(0xFFFFF5F3) },
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
fun CommentariiTheme(
    dark: Boolean,
    theme: AppTheme = AppThemes.Default,
    animate: Boolean = false,
    content: @Composable () -> Unit,
) {
    val mode = if (dark) theme.dark else theme.light
    val typography = remember(theme) {
        if (theme === AppThemes.Default) AppTypography else themeTypography(theme.titleFont, mode.titleBrush)
    }
    CompositionLocalProvider(
        LocalAppTheme provides theme,
        LocalThemeAnimate provides animate,
        LocalCommentariiColors provides mode.extras,
    ) {
        MaterialTheme(colorScheme = mode.scheme, typography = typography, content = content)
    }
}

enum class HeaderStyle { PLAIN, WASHI, HIGHLIGHTER, RIBBON }

/** One frame of a theme's background doodles; [phase] is in [0,1) and is the only moving input. */
fun interface DecorationFrame { fun DrawScope.draw(phase: Float) }

/** Builds the frame painter for a given page size, so positions stay size-relative. */
fun interface ThemeDecoration { fun build(size: Size, density: Density, dark: Boolean): DecorationFrame }

@Immutable
class ThemeMode(val scheme: ColorScheme, val extras: CommentariiColors, val titleBrush: Brush?)

@Immutable
class AppTheme(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descriptionRes: Int,
    val light: ThemeMode,
    val dark: ThemeMode,
    val titleFont: FontFamily = ScriptFont,
    val tabShape: Shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    val cardShape: Shape = RoundedCornerShape(0.dp, 16.dp, 16.dp, 16.dp),
    val headerStyle: HeaderStyle = HeaderStyle.PLAIN,
    val decoration: ThemeDecoration? = null,
    val animated: Boolean = false,
    val periodMillis: Int = 0,
    val staticPhase: Float = 0f,
    val scene: ThemeScene? = null,
    val staticTimeSec: Float = 0f,
)

val LocalAppTheme = staticCompositionLocalOf { AppThemes.Default }

/** User-level animation intent only; callers must also check [systemAnimationsEnabled] and the lifecycle. */
val LocalThemeAnimate = staticCompositionLocalOf { false }

object AppThemes {
    const val DEFAULT_ID = DEFAULT_THEME_ID

    val Default: AppTheme by lazy {
        AppTheme(
            id = DEFAULT_ID,
            nameRes = R.string.theme_rose_cream_name,
            descriptionRes = R.string.theme_rose_cream_desc,
            light = ThemeMode(LightScheme, LightExtras, null),
            dark = ThemeMode(DarkScheme, DarkExtras, null),
        )
    }

    val all: List<AppTheme> by lazy { listOf(Default, WashiPlanner, SunnyJournal, CloudDrift, PetalFall, FloatingHearts, MatchaTea, LavenderMoon) }

    fun byId(id: String?): AppTheme = all.firstOrNull { it.id == id } ?: Default
}

fun systemAnimationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()

/** Same text styles as [AppTypography] but with [font] for titles; [titleBrush] colours display* only. */
fun themeTypography(font: FontFamily, titleBrush: Brush?): Typography {
    val base = Typography()
    fun TextStyle.title(withBrush: Boolean = false): TextStyle {
        val styled = copy(fontFamily = font)
        return if (withBrush && titleBrush != null) styled.copy(brush = titleBrush) else styled
    }
    return Typography(
        displayLarge = base.displayLarge.title(true),
        displayMedium = base.displayMedium.title(true),
        displaySmall = base.displaySmall.title(true),
        headlineLarge = base.headlineLarge.title(),
        headlineMedium = base.headlineMedium.title(),
        headlineSmall = base.headlineSmall.title(),
        titleLarge = base.titleLarge.title(),
    )
}

data class Palette(
    val bg: Long,
    val onBg: Long,
    val primary: Long,
    val onPrimary: Long,
    val card: Long,
    val outlineVariant: Long,
    val tabs: List<Long>,
    val onTab: Long,
    val title: List<Long>,
)

/** Builds a full [ThemeMode] from a [Palette]; error and mood roles stay the Rose & Cream values. */
fun paletteMode(p: Palette, dark: Boolean): ThemeMode {
    val bg = Color(p.bg)
    val onBg = Color(p.onBg)
    val primary = Color(p.primary)
    val onPrimary = Color(p.onPrimary)
    val card = Color(p.card)
    val onTab = Color(p.onTab)
    val tabs = p.tabs.map { Color(it) }
    val base = if (dark) DarkScheme else LightScheme
    val baseExtras = if (dark) DarkExtras else LightExtras
    fun mix(f: Float) = lerp(bg, onBg, f)
    val onSurfaceVariant = lerp(onBg, bg, 0.25f)
    val scheme = base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = tabs[0],
        onPrimaryContainer = onTab,
        secondary = onSurfaceVariant,
        secondaryContainer = tabs[1 % tabs.size],
        onSecondaryContainer = onTab,
        tertiary = primary,
        onTertiary = onPrimary,
        tertiaryContainer = tabs[2 % tabs.size],
        onTertiaryContainer = onTab,
        background = bg,
        onBackground = onBg,
        surface = bg,
        onSurface = onBg,
        surfaceVariant = mix(0.07f),
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = onBg,
        inverseOnSurface = bg,
        inversePrimary = primary,
        outline = lerp(onBg, bg, 0.45f),
        outlineVariant = Color(p.outlineVariant),
        surfaceBright = bg,
        surfaceDim = bg,
        surfaceContainerLowest = card,
        surfaceContainerLow = mix(0.03f),
        surfaceContainer = mix(0.05f),
        surfaceContainerHigh = mix(0.08f),
        surfaceContainerHighest = mix(0.11f),
    )
    val extras = CommentariiColors(
        tab = tabs[0],
        onTab = onTab,
        sage = tabs[1 % tabs.size],
        onSage = onTab,
        card = card,
        onCard = onBg,
        mood = baseExtras.mood,
        onMood = baseExtras.onMood,
        tabs = tabs,
    )
    val brush = if (p.title.size >= 2) Brush.horizontalGradient(p.title.map { Color(it) }) else null
    return ThemeMode(scheme, extras, brush)
}
