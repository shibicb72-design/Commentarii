package com.shibi.commentarii.testutil

import android.graphics.Bitmap
import android.os.Looper
import androidx.core.view.WindowCompat
import org.junit.Assert.assertTrue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.graphics.asAndroidBitmap
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.ui.theme.AppTheme
import com.shibi.commentarii.ui.theme.AppThemes
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * The real MainActivity opened on the Settings page's Theme card, for the theme items. Adds to [MainActivityTestBase]: the
 * per-theme "Off" flags are reset too (resetToDefaults leaves them), and helpers to find a theme row, read its swatch pixels,
 * and flip the emulated system dark mode (a +night qualifier plus a recreate, since MainActivity handles no config changes).
 */
abstract class ThemeFlowTestBase : MainActivityTestBase() {
    @Before
    fun resetStaticThemeIds() {
        runWithLooper { clearStaticThemeIds() }
    }

    @After
    fun restoreStaticThemeIds() {
        runWithLooper { clearStaticThemeIds() }
    }

    private suspend fun clearStaticThemeIds() {
        container.settings.staticThemeIds.first().forEach { container.settings.setThemeAnimated(it, true) }
    }

    protected val scrollBox = SemanticsMatcher("the settings page's scroll area") {
        SemanticsProperties.VerticalScrollAxisRange in it.config && SemanticsActions.ScrollBy in it.config
    }

    /** Text on the settings page itself, not in the bottom bar. */
    protected fun onPage(text: String) = hasText(text) and hasAnyAncestor(scrollBox)

    protected fun openSettings() {
        openTab(R.string.tab_settings)
        await { exists(onPage(str(R.string.settings_section_backup))) }
    }

    protected fun scrollToText(text: String) {
        rule.onNode(onPage(text)).performScrollTo()
        rule.waitForIdle()
    }

    protected val radioRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    protected fun themeName(theme: AppTheme) = str(theme.nameRes)

    protected fun themeDescription(theme: AppTheme) = str(theme.descriptionRes)

    /** The selectable row of [theme]: merged name and description, with the radio's selected state. */
    protected fun themeRow(theme: AppTheme) = hasText(themeName(theme)) and hasText(themeDescription(theme)) and radioRole and hasAnyAncestor(scrollBox)

    protected fun modeRow(mode: DarkMode) = hasText(str(modeLabel(mode))) and radioRole and hasAnyAncestor(scrollBox)

    protected fun modeLabel(mode: DarkMode) = when (mode) {
        DarkMode.SYSTEM -> R.string.settings_theme_system
        DarkMode.LIGHT -> R.string.settings_theme_light
        DarkMode.DARK -> R.string.settings_theme_dark
    }

    protected fun isSelected(matcher: SemanticsMatcher): Boolean =
        rule.onNode(matcher).fetchSemanticsNode().config[SemanticsProperties.Selected]

    protected fun selectedThemeNames(): List<String> =
        AppThemes.all.filter { exists(themeRow(it)) && isSelected(themeRow(it)) }.map { themeName(it) }

    /** Taps a row on the Settings page (scrolls to it first) and waits for the compose clock to settle. */
    protected fun tapRow(matcher: SemanticsMatcher) {
        rule.onNode(matcher).performScrollTo()
        rule.onNode(matcher).performClick()
        rule.waitForIdle()
    }

    /** The system's dark mode as the emulator sets it, then the activity recreated the way the system recreates it on a uiMode change. */
    protected fun setSystemNight(night: Boolean) {
        RuntimeEnvironment.setQualifiers(if (night) "+night" else "+notnight")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        val expected = if (night) android.content.res.Configuration.UI_MODE_NIGHT_YES else android.content.res.Configuration.UI_MODE_NIGHT_NO
        check(rule.activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == expected) {
            "the activity did not take the night qualifier"
        }
    }

    /** ARGB of a Compose colour, as a pixel of a rendered bitmap reads. */
    protected fun argb(color: Color): Int = color.toArgb()

    protected fun pump() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    protected val density get() = rule.activity.resources.displayMetrics.density

    protected fun scrollValue(): Float = rule.onNode(scrollBox).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    /** Distance of the node's top from the top of the page's content, the same whatever the scroll position. */
    protected fun contentY(node: SemanticsNode): Float {
        val viewport = rule.onNode(scrollBox).fetchSemanticsNode()
        return node.positionInRoot.y - viewport.positionInRoot.y + scrollValue()
    }

    /** The colour painted at the left edge of the page, in the middle of the window: nothing but the page's own background is there. */
    protected fun pageBackgroundPixel(bitmap: Bitmap = windowBitmap()): Int = bitmap.getPixel(1, bitmap.height / 2)

    /** The bottom bar's own fill (the scheme's surfaceContainer), which no page decoration is drawn over. */
    protected fun barBackgroundPixel(bitmap: Bitmap = windowBitmap()): Int {
        val top = boundsOf(tab(R.string.tab_settings)).top
        return bitmap.getPixel(1, top.toInt() + 3)
    }

    /** The three-button/gesture bars' icon colour as the app asked for it: true means dark icons (for a light background). */
    protected fun statusBarIconsAreDark(): Boolean =
        WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightStatusBars

    protected fun navigationBarIconsAreDark(): Boolean =
        WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView).isAppearanceLightNavigationBars

    /** A theme row's swatch as drawn: its paper colour and the three tab dots (read at points no other dot covers). */
    class Swatch(val paper: Int, val dots: List<Int>)

    /** Scrolls to the row and reads its swatch from the window's pixels. The swatch is 24dp, after the 48dp radio button (12dp padding, 24dp radio with its own 2dp padding, 12dp). */
    protected fun swatchOf(theme: AppTheme): Swatch {
        rule.onNode(themeRow(theme)).performScrollTo()
        rule.waitForIdle()
        val node = rule.onNode(themeRow(theme)).fetchSemanticsNode()
        val bitmap = windowBitmap()
        val d = density
        val left = node.positionInWindow.x + 48 * d
        val top = node.positionInWindow.y + node.size.height / 2f - 12 * d
        assertTrue("the swatch is on screen: top $top of ${bitmap.height}", top >= 0 && top + 24 * d <= bitmap.height)
        fun at(dx: Float, dy: Float) = bitmap.getPixel((left + dx * d).toInt(), (top + dy * d).toInt())
        return Swatch(paper = at(12f, 3f), dots = listOf(at(3f, 12f), at(8.5f, 12f), at(18f, 12f)))
    }

    /** What [swatchOf] should read for [theme] in the given mode. */
    protected fun expectedSwatch(theme: AppTheme, dark: Boolean): Swatch {
        val mode = if (dark) theme.dark else theme.light
        val tabs = mode.extras.tabs
        return Swatch(argb(mode.scheme.background), List(3) { argb(tabs[it % tabs.size]) })
    }

    protected fun hex(argb: Int) = "#%08X".format(argb)
}
