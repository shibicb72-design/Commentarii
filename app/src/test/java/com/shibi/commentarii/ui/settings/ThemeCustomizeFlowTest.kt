package com.shibi.commentarii.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.testutil.ThemeFlowTestBase
import com.shibi.commentarii.testutil.runWithLooper
import com.shibi.commentarii.testutil.waitUntilWithLooper
import com.shibi.commentarii.ui.daily.DailyActions
import com.shibi.commentarii.ui.daily.DailyPageContent
import com.shibi.commentarii.ui.daily.sampleDailyUiState
import com.shibi.commentarii.ui.theme.AppTheme
import com.shibi.commentarii.ui.theme.AppThemes
import com.shibi.commentarii.ui.theme.CommentariiTheme
import com.shibi.commentarii.ui.theme.ThemeOverride
import com.shibi.commentarii.ui.theme.ThemeOverrideCodec
import com.shibi.commentarii.ui.theme.customized
import java.time.Duration
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The per-theme Customize page on the real MainActivity and container. Colours are read from the window's pixels, on themes with
 * no decoration whenever the reading must be exactly the page's paper. Tests keep to one or two writes on top of the base class's
 * resets: repeated writes to one store trip a Windows host IOException.
 */
class ThemeCustomizeFlowTest : ThemeFlowTestBase() {
    private val rose = AppThemes.Default
    private val washi = AppThemes.byId("washi_planner")
    private val petal = AppThemes.byId("petal_fall")

    private val tag get() = str(R.string.theme_customize_tag)
    private val resetButton get() = hasText(str(R.string.theme_customize_reset))
    private val animatedLabel get() = str(R.string.settings_theme_animated)
    private val offLabel get() = str(R.string.settings_theme_static)
    private val lightLabel get() = str(R.string.settings_theme_light)
    private val darkLabel get() = str(R.string.settings_theme_dark)
    private val settingsPage get() = onPage(str(R.string.settings_section_backup))

    private val editedBg = 0xFFDDEEFFL
    private val editedHex = "#FFDDEEFF"

    private fun lightBgOverride() = ThemeOverrideCodec.encode(ThemeOverride.Empty.withColor("l.bg", editedBg))

    private fun customizeButton(theme: AppTheme) = hasContentDescription(str(R.string.theme_customize_open_desc, themeName(theme)))

    private fun preview(theme: AppTheme) = hasContentDescription(str(R.string.theme_customize_preview_desc, themeName(theme)))

    private fun inDialog(text: String) = hasText(text) and hasAnyAncestor(isDialog())

    private fun dialogOpen() = exists(hasAnyAncestor(isDialog()))

    /** A Light/Dark or Animated/Off choice, which has the selected state a plain label does not. */
    private fun segment(label: String) = hasText(label) and radioRole and hasAnyAncestor(scrollBox)

    private fun overrides() = runWithLooper { container.settings.themeOverrides.first() }

    private fun staticIds() = runWithLooper { container.settings.staticThemeIds.first() }

    private fun storedTheme() = runWithLooper { container.settings.themeId.first() }

    private fun storedDarkMode() = runWithLooper { container.settings.darkMode.first() }

    private fun openCustomize(theme: AppTheme) {
        rule.onNode(customizeButton(theme)).performScrollTo()
        rule.onNode(customizeButton(theme)).performClick()
        await { exists(preview(theme)) }
    }

    private fun tapBackArrow() {
        rule.onNode(hasContentDescription(str(R.string.ed_back))).performClick()
        await { exists(settingsPage) && count(hasContentDescription(str(R.string.ed_back))) == 0 }
    }

    private fun pressSystemBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun previewPixelHex(theme: AppTheme): String {
        val box = boundsOf(preview(theme))
        // Past the 1dp border, inside the page's 16dp content padding, in the middle of the height: only the paper is there.
        return hex(windowBitmap().getPixel((box.left + 6 * density).toInt(), box.center.y.toInt()))
    }

    private fun stockLightBg(theme: AppTheme) = hex(argb(theme.light.scheme.background))

    private fun stockDarkBg(theme: AppTheme) = hex(argb(theme.dark.scheme.background))

    private fun rowY(theme: AppTheme) = contentY(rule.onNode(themeRow(theme)).fetchSemanticsNode())

    /** The page has one "Customized" tag, and it sits between [theme]'s row and the next theme's. */
    private fun assertOnlyTagIsUnder(theme: AppTheme) {
        assertEquals("exactly one row is tagged", 1, count(onPage(tag)))
        val y = contentY(rule.onNode(onPage(tag)).fetchSemanticsNode())
        assertTrue("the tag is below ${theme.id}'s row", y > rowY(theme))
        val next = AppThemes.all.getOrNull(AppThemes.all.indexOf(theme) + 1)
        if (next != null) assertTrue("the tag is above ${next.id}'s row", y < rowY(next))
    }

    /** Sanity for every pixel assertion on the edit: the resolver takes this colour, and stock is something else. */
    private fun assertEditIsVisibleOn(theme: AppTheme) {
        val o = ThemeOverrideCodec.decode(lightBgOverride())
        assertEquals(editedHex, hex(argb(theme.customized(o).light.scheme.background)))
        assertNotEquals(editedHex, stockLightBg(theme))
    }

    // ---- a ----

    @Test
    fun a_customizeOpensTheThemesPageAndBackReturnsToSettingsWithTheSettingsTabSelected() {
        openSettings()
        rule.onNode(customizeButton(washi)).performScrollTo()
        rule.onNode(customizeButton(washi)).performClick()
        await { exists(preview(washi)) }

        rule.onNode(hasText(themeName(washi))).assertIsDisplayed()
        assertFalse("Settings has left the screen", exists(settingsPage))
        assertTrue("the Settings tab stays selected on the page", tabSelected(R.string.tab_settings))
        assertEquals("opening a theme's page does not select it", rose.id, storedTheme())

        tapBackArrow()
        assertTrue(tabSelected(R.string.tab_settings))
        assertEquals(rose.id, storedTheme())

        openCustomize(washi)
        pressSystemBack()
        await { exists(settingsPage) && !exists(preview(washi)) }
        assertTrue(tabSelected(R.string.tab_settings))
    }

    // ---- b ----

    @Test
    fun b_everyThemeHasACustomizeButtonAndItsRadioRowIsUnchanged() {
        openSettings()
        assertEquals("one Customize per theme", AppThemes.all.size, count(onPage(str(R.string.theme_customize_open))))
        AppThemes.all.forEach { theme ->
            rule.onNode(customizeButton(theme)).performScrollTo()
            rule.onNode(customizeButton(theme)).assertIsDisplayed().assertIsEnabled()
            val button = rule.onNode(customizeButton(theme)).fetchSemanticsNode()
            assertEquals("${theme.id}: a button, not a second radio", Role.Button, button.config[SemanticsProperties.Role])
            assertTrue("${theme.id}: clickable", SemanticsActions.OnClick in button.config)
            assertFalse("${theme.id}: holds no selection", SemanticsProperties.Selected in button.config)
            assertEquals("${theme.id}: visible label", listOf(str(R.string.theme_customize_open)), button.config[SemanticsProperties.Text].map { it.text })

            val row = rule.onNode(themeRow(theme)).fetchSemanticsNode()
            assertEquals(listOf(themeName(theme), themeDescription(theme)), row.config[SemanticsProperties.Text].map { it.text })
            assertEquals(Role.RadioButton, row.config[SemanticsProperties.Role])
            assertTrue(SemanticsActions.OnClick in row.config)
            assertFalse("${theme.id}: the row did not absorb the button's description", SemanticsProperties.ContentDescription in row.config)
        }
        assertEquals(listOf(themeName(rose)), selectedThemeNames())
        assertEquals("nothing is tagged on stock themes", 0, count(onPage(tag)))
    }

    /** The button draws 40dp and reserves 48dp: a tap 3dp above what it draws still opens the page, and does not select the row above. */
    @Test
    fun b_theCustomizeButtonHasA48dpTouchTarget() {
        openSettings()
        rule.onNode(customizeButton(washi)).performScrollTo()
        val button = rule.onNode(customizeButton(washi)).fetchSemanticsNode()
        assertEquals("draws 40dp", 40 * density, button.size.height.toFloat(), 1f)
        rule.onNode(customizeButton(washi)).performTouchInput { click(Offset(centerX, -3 * density)) }
        await { exists(preview(washi)) }
        assertEquals(rose.id, storedTheme())
    }

    // ---- c ----

    @Test
    fun c_aSavedOverrideOnTheActiveThemeIsTaggedOnItsRowOnlyAppliedToDailyAndPreviewed() {
        assertEditIsVisibleOn(rose)
        await { tabSelected(R.string.tab_today) && hex(pageBackgroundPixel()) == stockLightBg(rose) }

        runWithLooper { container.settings.setThemeOverride(rose.id, lightBgOverride()) }
        await { hex(pageBackgroundPixel()) == editedHex }

        openSettings()
        await { exists(onPage(tag)) }
        assertOnlyTagIsUnder(rose)
        assertEquals("the swatch reads the edit", editedHex, hex(swatchOf(rose).paper))
        assertEquals("another theme's swatch is stock", stockLightBg(washi), hex(swatchOf(washi).paper))

        openCustomize(rose)
        rule.onNode(preview(rose)).assertIsDisplayed()
        assertEquals("the preview shows the edit", editedHex, previewPixelHex(rose))
    }

    @Test
    fun c_thePreviewIsOneDescriptionForTalkBackAndIgnoresTouch() {
        openSettings()
        openCustomize(rose)
        val sampleLine = hasText("Finish the report")
        // The merged tree, which is what TalkBack reads; the unmerged one keeps cleared nodes by design.
        assertEquals("the sample's lines are not in the tree", 0, count(sampleLine))
        assertEquals("one scroll area: the controls, not the preview's page", 1, count(scrollBox))

        val before = windowBitmap()
        // ThemeCustomizeFlowTestDirect shows this same swipe does scroll the same sample when nothing blocks it.
        rule.onNode(preview(rose)).performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTrue("a swipe moved nothing in the preview", before.sameAs(windowBitmap()))
    }

    // ---- d ----

    @Test
    fun d_cancelChangesNothingAndResetRemovesTheOverrideAndRestoresStock() {
        assertEditIsVisibleOn(rose)
        runWithLooper { container.settings.setThemeOverride(rose.id, lightBgOverride()) }
        openSettings()
        await { exists(onPage(tag)) }
        openCustomize(rose)
        await { previewPixelHex(rose) == editedHex }
        rule.onNode(resetButton).performScrollTo().assertIsEnabled()

        // Cancel.
        rule.onNode(resetButton).performClick()
        await { exists(inDialog(str(R.string.theme_customize_reset_title, themeName(rose)))) }
        rule.onNode(inDialog(str(R.string.theme_customize_reset_body))).assertExists()
        assertFalse("animation is on, so the body does not mention it", exists(inDialog(str(R.string.theme_customize_reset_body_animated))))
        rule.onNode(inDialog(str(R.string.action_cancel))).performClick()
        await { !dialogOpen() }
        assertEquals(lightBgOverride(), overrides()[rose.id])
        assertEquals(editedHex, previewPixelHex(rose))
        rule.onNode(resetButton).assertIsEnabled()

        // Reset.
        rule.onNode(resetButton).performClick()
        await { exists(inDialog(str(R.string.theme_customize_reset_confirm))) }
        rule.onNode(inDialog(str(R.string.theme_customize_reset_confirm))).performClick()
        await { !dialogOpen() && overrides()[rose.id] == null }
        await { previewPixelHex(rose) == stockLightBg(rose) }
        rule.onNode(resetButton).assertIsNotEnabled()
        assertEquals("the app's own page is stock again", stockLightBg(rose), hex(pageBackgroundPixel()))

        tapBackArrow()
        await { !exists(onPage(tag)) }
        assertEquals("the swatch is stock", stockLightBg(rose), hex(swatchOf(rose).paper))
    }

    @Test
    fun d_onAnAnimatedThemeSetOffResetSaysSoAndTurnsTheAnimationBackOn() {
        runWithLooper { container.settings.setThemeAnimated(petal.id, false) }
        openSettings()
        assertEquals("animation Off alone is not a customization", 0, count(onPage(tag)))
        openCustomize(petal)
        assertTrue(isSelected(segment(offLabel)))
        rule.onNode(resetButton).performScrollTo().assertIsEnabled()

        rule.onNode(resetButton).performClick()
        await { exists(inDialog(str(R.string.theme_customize_reset_title, themeName(petal)))) }
        rule.onNode(inDialog(str(R.string.theme_customize_reset_body_animated))).assertExists()
        assertFalse(exists(inDialog(str(R.string.theme_customize_reset_body))))
        rule.onNode(inDialog(str(R.string.theme_customize_reset_confirm))).performClick()

        await { !dialogOpen() && petal.id !in staticIds() }
        await { isSelected(segment(animatedLabel)) }
        assertFalse(isSelected(segment(offLabel)))
        rule.onNode(resetButton).assertIsNotEnabled()
    }

    /** A stored value that decodes to nothing looks stock, so it has no tag, yet Reset can still clear it. */
    @Test
    fun d_anUnreadableOverrideHasNoTagButResetCanClearIt() {
        runWithLooper { container.settings.setThemeOverride(rose.id, "not json") }
        openSettings()
        await { "not json" == overrides()[rose.id] }
        assertEquals(0, count(onPage(tag)))
        openCustomize(rose)
        rule.onNode(resetButton).performScrollTo().assertIsEnabled()
        rule.onNode(resetButton).performClick()
        await { exists(inDialog(str(R.string.theme_customize_reset_confirm))) }
        rule.onNode(inDialog(str(R.string.theme_customize_reset_confirm))).performClick()
        await { overrides()[rose.id] == null }
        await { !dialogOpen() }
        rule.onNode(resetButton).assertIsNotEnabled()
    }

    // ---- e and h ----

    @Test
    fun eh_everyStockThemeHasResetDisabledAndOnlyAnimatedThemesHaveAnimatedOff() {
        openSettings()
        AppThemes.all.forEach { theme ->
            openCustomize(theme)
            rule.onNode(resetButton).performScrollTo().assertIsNotEnabled()
            rule.onNode(resetButton).performClick()
            rule.waitForIdle()
            assertFalse("${theme.id}: a disabled Reset opens nothing", dialogOpen())
            assertEquals("${theme.id}: Animated", theme.animated, exists(segment(animatedLabel)))
            assertEquals("${theme.id}: Off", theme.animated, exists(segment(offLabel)))
            assertEquals("${theme.id}: heading", theme.animated, exists(onPage(str(R.string.theme_customize_animation))))
            if (theme.animated) assertTrue("${theme.id}: starts Animated", isSelected(segment(animatedLabel)))
            tapBackArrow()
        }
        assertEquals("nothing was written", emptySet<String>(), staticIds())
        assertEquals(emptyMap<String, String>(), overrides())
    }

    @Test
    fun h_offWritesStaticThemeIdsForThatThemeAndEnablesReset() {
        openSettings()
        openCustomize(petal)
        rule.onNode(resetButton).performScrollTo().assertIsNotEnabled()
        rule.onNode(segment(offLabel)).performClick()
        await { staticIds() == setOf(petal.id) }
        await { isSelected(segment(offLabel)) }
        assertFalse(isSelected(segment(animatedLabel)))
        rule.onNode(resetButton).performScrollTo().assertIsEnabled()
        assertEquals("the app's theme did not move", rose.id, storedTheme())
    }

    // ---- f ----

    @Test
    fun f_anOverrideOnAThemeThatIsNotActiveTagsItsRowAndLeavesTheAppsLookAlone() {
        assertEditIsVisibleOn(washi)
        await { tabSelected(R.string.tab_today) && hex(pageBackgroundPixel()) == stockLightBg(rose) }
        val barBefore = hex(barBackgroundPixel())

        runWithLooper { container.settings.setThemeOverride(washi.id, lightBgOverride()) }
        openSettings()
        // The tag proves the write has reached the screen, so what follows is not read too early.
        await { exists(onPage(tag)) }
        assertOnlyTagIsUnder(washi)
        assertEquals(listOf(themeName(rose)), selectedThemeNames())
        assertEquals(rose.id, storedTheme())
        assertEquals("washi's swatch reads its edit", editedHex, hex(swatchOf(washi).paper))
        assertEquals("rose's swatch is stock", stockLightBg(rose), hex(swatchOf(rose).paper))
        assertEquals("the Settings page is still rose", stockLightBg(rose), hex(pageBackgroundPixel()))
        assertEquals(barBefore, hex(barBackgroundPixel()))

        openTab(R.string.tab_today)
        await { hex(pageBackgroundPixel()) == stockLightBg(rose) }
        assertEquals(barBefore, hex(barBackgroundPixel()))
    }

    // ---- g ----

    @Test
    fun g_previewingDarkRepaintsOnlyThePreviewAndLeavesTheStoredSystemMode() {
        assertEquals(DarkMode.SYSTEM, storedDarkMode())
        openSettings()
        val bar = hex(barBackgroundPixel())
        openCustomize(rose)
        assertTrue("follows the app's light mode at first", isSelected(segment(lightLabel)))
        assertEquals(stockLightBg(rose), previewPixelHex(rose))

        rule.onNode(segment(darkLabel)).performClick()
        await { previewPixelHex(rose) == stockDarkBg(rose) }
        assertTrue(isSelected(segment(darkLabel)))
        assertFalse(isSelected(segment(lightLabel)))
        assertEquals("the stored mode did not move", DarkMode.SYSTEM, storedDarkMode())
        assertEquals("the app's page stays light", stockLightBg(rose), hex(pageBackgroundPixel()))
        assertEquals("the app's bar stays light", bar, hex(barBackgroundPixel()))
        assertTrue("the status bar icons stay dark", statusBarIconsAreDark())

        rule.onNode(segment(lightLabel)).performClick()
        await { previewPixelHex(rose) == stockLightBg(rose) }
        assertEquals(DarkMode.SYSTEM, storedDarkMode())

        tapBackArrow()
        assertTrue("Settings still shows Follow system", isSelected(modeRow(DarkMode.SYSTEM)))
    }

    @Test
    fun g_previewingLightInADarkAppLeavesTheStoredDarkMode() {
        runWithLooper { container.settings.setDarkMode(DarkMode.DARK) }
        openSettings()
        await { hex(pageBackgroundPixel()) == stockDarkBg(rose) }
        openCustomize(rose)
        assertTrue("follows the app's dark mode at first", isSelected(segment(darkLabel)))
        assertEquals(stockDarkBg(rose), previewPixelHex(rose))

        rule.onNode(segment(lightLabel)).performClick()
        await { previewPixelHex(rose) == stockLightBg(rose) }
        assertEquals(DarkMode.DARK, storedDarkMode())
        assertEquals("the app's page stays dark", stockDarkBg(rose), hex(pageBackgroundPixel()))
    }

    // ---- i ----

    /** Real lock flow, as LockFlowTest drives it: the route was kept as text while nothing was composed, and rebuilt with its back stack. */
    @Test
    fun i_unlockReturnsToTheSameThemesPageWithTheSameBackStack() {
        val unlockText = str(R.string.lock_unlock)
        runWithLooper { container.settings.setLockEnabled(true) }
        await { exists(unlockText) }
        container.lock.unlock()
        await { !exists(unlockText) && tabSelected(R.string.tab_today) }

        openSettings()
        openCustomize(washi)

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        container.lock.onBackground()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        container.onAppStart()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        await { exists(unlockText) }
        assertFalse("the page is not composed behind the lock", exists(preview(washi)))

        container.lock.unlock()
        await { !exists(unlockText) && exists(preview(washi)) }
        assertTrue(tabSelected(R.string.tab_settings))
        rule.onNode(hasText(themeName(washi))).assertIsDisplayed()

        pressSystemBack()
        await { exists(settingsPage) && !exists(preview(washi)) }
        assertTrue(tabSelected(R.string.tab_settings))
        pressSystemBack()
        await { tabSelected(R.string.tab_today) }
    }

    /** A rotation or any other recreate keeps the page and the preview's Light/Dark choice. */
    @Test
    fun i_recreatingTheActivityKeepsThePageAndThePreviewChoice() {
        openSettings()
        openCustomize(rose)
        rule.onNode(segment(darkLabel)).performClick()
        await { previewPixelHex(rose) == stockDarkBg(rose) }

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        await { exists(preview(rose)) }
        assertTrue(tabSelected(R.string.tab_settings))
        assertTrue(isSelected(segment(darkLabel)))
        await { previewPixelHex(rose) == stockDarkBg(rose) }
    }

    // ---- j ----

    private fun openPageAndMeasure(): Pair<Rect, Rect> {
        openSettings()
        openCustomize(rose)
        rule.onNode(resetButton).performScrollTo().assertIsDisplayed()
        rule.onNode(preview(rose)).assertIsDisplayed()
        return boundsOf(preview(rose)) to boundsOf(resetButton)
    }

    private fun assertSideBySide() {
        val (preview, reset) = openPageAndMeasure()
        assertTrue("preview $preview is left of Reset $reset", preview.right <= reset.left)
        assertTrue("they share the page's height: preview $preview, Reset $reset", preview.top < reset.bottom && reset.top < preview.bottom)
    }

    private fun assertStacked() {
        val (preview, reset) = openPageAndMeasure()
        assertTrue("preview $preview is above Reset $reset", preview.bottom <= reset.top)
    }

    @Test
    fun j_compactWidthStacksPreviewAboveControls() = assertStacked()

    @Test
    @Config(qualifiers = "en-rUS-w599dp-h360dp-land-xxhdpi")
    fun j_aShortCompactWindowStillStacksAndReachesReset() = assertStacked()

    @Test
    @Config(qualifiers = "en-rUS-w600dp-h412dp-land-xxhdpi")
    fun j_mediumWidthPutsPreviewLeftOfControls() = assertSideBySide()

    @Test
    @Config(qualifiers = "en-rUS-w840dp-h600dp-land-xxhdpi")
    fun j_expandedWidthPutsPreviewLeftOfControls() = assertSideBySide()
}

/** ThemeCustomizeScreen on its own, for the ids the real app's Settings cannot produce. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class ThemeCustomizeFlowTestDirect {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val container = ApplicationProvider.getApplicationContext<CommentariiApp>().container

    private var backs = 0

    private fun windowBitmap(): Bitmap {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        return bitmap
    }

    private fun backsFor(id: String?): Int {
        rule.setContent {
            CommentariiTheme(dark = false) {
                ThemeCustomizeScreen(container, id, WindowWidthSizeClass.Compact, onBack = { backs++ })
            }
        }
        rule.waitForIdle()
        return backs
    }

    @Test
    fun anUnknownIdPopsBackOnce() = assertEquals(1, backsFor("nope"))

    @Test
    fun anEmptyIdPopsBackOnce() = assertEquals(1, backsFor(""))

    @Test
    fun aMissingIdPopsBackOnce() = assertEquals(1, backsFor(null))

    @Test
    fun control_aKnownIdStaysAndShowsItsPage() {
        backsFor("washi_planner")
        val name = rule.activity.getString(R.string.theme_washi_planner_name)
        val preview = hasContentDescription(rule.activity.getString(R.string.theme_customize_preview_desc, name))
        rule.waitUntilWithLooper { rule.onAllNodes(preview).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, backs)
    }

    /** Control for the preview's touch test: without the screen's blocker, the same swipe scrolls the same sample page. */
    @Test
    fun control_theSampleScrollsWhenNothingBlocksTheSwipe() {
        rule.setContent {
            CommentariiTheme(dark = false) {
                Box(Modifier.size(380.dp)) {
                    DailyPageContent(sampleDailyUiState(), DailyActions(), WindowWidthSizeClass.Compact)
                }
            }
        }
        rule.waitForIdle()
        val before = windowBitmap()
        rule.onRoot().performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertFalse("the swipe moved the sample", before.sameAs(windowBitmap()))
    }
}
