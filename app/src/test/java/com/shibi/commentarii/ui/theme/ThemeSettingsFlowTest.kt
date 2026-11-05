package com.shibi.commentarii.ui.theme

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.data.SettingsRepository
import com.shibi.commentarii.testutil.ThemeFlowTestBase
import com.shibi.commentarii.testutil.inkOf
import com.shibi.commentarii.testutil.runWithLooper
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Checklist #259-#261 and #263 on the real MainActivity and container: the Theme card of Settings, and what the whole window is
 * painted in. The swatch has no semantics, so its colours are read from the window's pixels. The system's dark mode is emulated by
 * a +night/+notnight qualifier and a recreate of the activity, which is what the system does to an activity that handles no
 * uiMode change (MainActivity declares none).
 */
class ThemeSettingsFlowTest : ThemeFlowTestBase() {
    @get:Rule val tmp = TemporaryFolder()

    private val names = listOf(
        "Rose & Cream", "Washi Planner", "Sunny Journal", "Cloud Drift", "Petal Fall", "Floating Hearts", "Matcha Tea", "Lavender Moon",
    )
    private val descriptions = listOf(
        "The original dusty-pink tabs on warm cream.",
        "Dense pastel planner: tab-label cards, washi tape, a peeking bear, potted plant, hearts and stars.",
        "Cozy cream paper with rainbow titles, highlighter swashes, a smiling sun, candles, mugs and leaf sprigs.",
        "Baby-blue sky with layered parallax clouds, a smiling sun with turning rays, sparkles and tiny birds.",
        "Blush blossom pages with petals tumbling and spinning down, a swaying branch and twinkles.",
        "Lilac and pink with hearts rising and wobbling, twinkling sparkles, soft glows and bobbing balloons.",
        "Calm matcha greens with a happy teacup, steam, tea leaves, dango and stitched-tag labels.",
        "Lavender dusk with a sleepy moon, dotted constellations, a ringed planet and starry stamps.",
    )

    private fun storedMode(): DarkMode = runWithLooper { container.settings.darkMode.first() }

    private fun storedTheme(): String = runWithLooper { container.settings.themeId.first() }

    private fun chooseMode(mode: DarkMode) {
        tapRow(modeRow(mode))
        await { storedMode() == mode }
        rule.waitForIdle()
        assertTrue("${mode.name} radio selected", isSelected(modeRow(mode)))
    }

    private fun chooseTheme(theme: AppTheme) {
        tapRow(themeRow(theme))
        await { storedTheme() == theme.id }
        rule.waitForIdle()
    }

    /** Back on the Settings page after a recreate (the shell restores the route, but do not rely on it). */
    private fun settingsShown() {
        await { exists(tab(R.string.tab_settings)) }
        if (!tabSelected(R.string.tab_settings)) openSettings()
        await { exists(onPage(str(R.string.settings_section_backup))) }
    }

    /**
     * Everything the user sees of light or dark, read from the real window: the page's own background, the colour the "Settings"
     * title and a body line are drawn in against it (at least 4.5:1, and lighter-than-paper exactly when dark), the swatch of the
     * default theme, and the icon colour the app asked for on the status and navigation bars.
     */
    private fun assertPaintedAs(dark: Boolean, theme: AppTheme = AppThemes.Default, why: String) {
        val mode = if (dark) theme.dark else theme.light
        scrollToText(str(R.string.tab_settings))
        val bitmap = windowBitmap()
        assertEquals("$why: page background", hex(argb(mode.scheme.background)), hex(pageBackgroundPixel(bitmap)))
        val title = inkOf(bitmap, rule.onNode(onPage(str(R.string.tab_settings))).fetchSemanticsNode())
        assertEquals("$why: title is drawn on the page background: $title", argb(mode.scheme.background), title.background)
        assertTrue("$why: title readable: $title", title.ratio >= 4.5)
        assertEquals("$why: title lighter than the page only in dark: $title", dark, title.inkIsLighter)
        val body = inkOf(bitmap, rule.onNode(onPage(str(R.string.settings_section_privacy))).fetchSemanticsNode())
        assertTrue("$why: card title readable: $body", body.ratio >= 4.5)
        assertEquals("$why: card title lighter than its card only in dark: $body", dark, body.inkIsLighter)
        assertEquals("$why: status bar icons are dark exactly on a light page", !dark, statusBarIconsAreDark())
        assertEquals("$why: navigation bar icons are dark exactly on a light page", !dark, navigationBarIconsAreDark())
    }

    // ---- #261 ----

    /**
     * Checklist #261: the Theme card lists 8 themes in the checklist's order, each with a swatch (read from pixels: paper and the
     * three tab dots of the light variant), its name and its description; exactly one radio is selected, Rose & Cream on defaults
     * (the store holds rose_cream); choosing another moves the one selection.
     */
    @Test
    fun item261_eightThemesInOrderEachWithSwatchNameDescriptionAndExactlyOneSelected() {
        openSettings()
        assertEquals("rose_cream", storedTheme())
        assertEquals(names, AppThemes.all.map { themeName(it) })
        assertEquals(descriptions, AppThemes.all.map { themeDescription(it) })

        var previousY = -1f
        AppThemes.all.forEachIndexed { i, theme ->
            rule.onNode(themeRow(theme)).performScrollTo()
            rule.waitForIdle()
            val node = rule.onNode(themeRow(theme)).fetchSemanticsNode()
            assertEquals(listOf(names[i], descriptions[i]), node.config[SemanticsProperties.Text].map { it.text })
            val y = contentY(node)
            assertTrue("${names[i]} is below the previous theme ($y after $previousY)", y > previousY)
            previousY = y

            val swatch = swatchOf(theme)
            val want = expectedSwatch(theme, dark = false)
            assertEquals("${names[i]} swatch paper", hex(want.paper), hex(swatch.paper))
            assertEquals("${names[i]} swatch dots", want.dots.map { hex(it) }, swatch.dots.map { hex(it) })
        }
        assertEquals("eight theme rows", 8, AppThemes.all.count { exists(themeRow(it)) })
        assertEquals("only the eight rows carry a description", 8, count(radioRole and hasDescriptionText()))
        assertEquals(listOf("Rose & Cream"), selectedThemeNames())

        chooseTheme(AppThemes.byId("washi_planner"))
        assertEquals(listOf("Washi Planner"), selectedThemeNames())
        chooseTheme(AppThemes.byId("lavender_moon"))
        assertEquals(listOf("Lavender Moon"), selectedThemeNames())
    }

    private fun hasDescriptionText() = SemanticsMatcher("has a theme description") { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text in descriptions } == true
    }

    // ---- #263 ----

    /**
     * Checklist #263: with Dark on, every swatch is drawn from the theme's dark variant: its paper and its three tab dots are the
     * dark scheme's, which differ from the light ones. Control: before Dark is chosen the same swatches read as the light variant,
     * so the pixel reading can tell the two apart. One write only: a second risks the Windows DataStore rename race.
     */
    @Test
    fun item263_darkModeShowsTheDarkVariantOfEachSwatch() {
        openSettings()
        AppThemes.all.forEach { theme ->
            val light = expectedSwatch(theme, dark = false)
            val swatch = swatchOf(theme)
            assertEquals("${theme.id} light paper before Dark", hex(light.paper), hex(swatch.paper))
            assertEquals("${theme.id} light dots before Dark", light.dots.map { hex(it) }, swatch.dots.map { hex(it) })
        }
        chooseMode(DarkMode.DARK)
        AppThemes.all.forEach { theme ->
            val dark = expectedSwatch(theme, dark = true)
            val light = expectedSwatch(theme, dark = false)
            assertNotEquals("${theme.id}: dark paper differs from light paper", hex(light.paper), hex(dark.paper))
            val swatch = swatchOf(theme)
            assertEquals("${theme.id} dark paper", hex(dark.paper), hex(swatch.paper))
            assertEquals("${theme.id} dark dots", dark.dots.map { hex(it) }, swatch.dots.map { hex(it) })
        }
    }

    // ---- #259 ----

    /**
     * Checklist #259: "Follow system" is selected; with the system light the window is the light Rose & Cream, with dark
     * bar icons; the system goes dark (+night, activity recreated as the system does) and the same window is painted dark,
     * title and card text light on dark, light bar icons, the swatches switch to their dark variant; back to light it returns.
     * Exercised: qualifier change plus scenario.recreate(); not exercised: the quick-settings tile itself and the real bars.
     */
    @Test
    fun item259_followSystemRepaintsTheWholeWindowWhenTheSystemTurnsDarkAndBack() {
        openSettings()
        assertTrue("Follow system is selected by default", isSelected(modeRow(DarkMode.SYSTEM)))
        assertEquals(DarkMode.SYSTEM, storedMode())
        assertPaintedAs(dark = false, why = "system light")
        assertEquals(hex(expectedSwatch(AppThemes.Default, false).paper), hex(swatchOf(AppThemes.Default).paper))

        setSystemNight(true)
        settingsShown()
        assertTrue("still Follow system", isSelected(modeRow(DarkMode.SYSTEM)))
        assertPaintedAs(dark = true, why = "system dark")
        assertEquals(hex(expectedSwatch(AppThemes.Default, true).paper), hex(swatchOf(AppThemes.Default).paper))

        setSystemNight(false)
        settingsShown()
        assertPaintedAs(dark = false, why = "system light again")
    }

    // ---- #260 ----

    /**
     * Checklist #260, Light with the system dark: explicit Light is stored, the window is light with dark bar icons although
     * the activity runs in night mode, it stays light after a recreate and after the system flips to light and back.
     * Control: before choosing, the same system-dark state painted the window dark.
     */
    @Test
    fun item260_explicitLightStaysLightWhileTheSystemIsDark() {
        setSystemNight(true)
        openSettings()
        assertPaintedAs(dark = true, why = "control: Follow system while the system is dark")

        chooseMode(DarkMode.LIGHT)
        assertEquals(DarkMode.LIGHT, storedMode())
        assertPaintedAs(dark = false, why = "Light chosen, system dark")

        rule.activityRule.scenario.recreate()
        settingsShown()
        assertEquals("the new activity reads Light from the store", DarkMode.LIGHT, storedMode())
        assertTrue(isSelected(modeRow(DarkMode.LIGHT)))
        assertPaintedAs(dark = false, why = "Light after recreate, system dark")

        setSystemNight(false)
        settingsShown()
        assertPaintedAs(dark = false, why = "Light, system light")
        setSystemNight(true)
        settingsShown()
        assertPaintedAs(dark = false, why = "Light, system dark again")
    }

    /** Checklist #260, Dark with the system light: the mirror of the above. Control: Follow system painted light first. */
    @Test
    fun item260_explicitDarkStaysDarkWhileTheSystemIsLight() {
        setSystemNight(false)
        openSettings()
        assertPaintedAs(dark = false, why = "control: Follow system while the system is light")

        chooseMode(DarkMode.DARK)
        assertEquals(DarkMode.DARK, storedMode())
        assertPaintedAs(dark = true, why = "Dark chosen, system light")

        rule.activityRule.scenario.recreate()
        settingsShown()
        assertEquals("the new activity reads Dark from the store", DarkMode.DARK, storedMode())
        assertTrue(isSelected(modeRow(DarkMode.DARK)))
        assertPaintedAs(dark = true, why = "Dark after recreate, system light")

        setSystemNight(true)
        settingsShown()
        assertPaintedAs(dark = true, why = "Dark, system dark")
        setSystemNight(false)
        settingsShown()
        assertPaintedAs(dark = true, why = "Dark, system light again")
    }

    /**
     * Checklist #260, "persists after relaunch": a settings store on a temp file, written once per file (repeated writes hit an
     * IOException on this Windows host), then the scope cancelled and a second store opened on the same file. Real process death
     * is not exercised, only the file round trip.
     */
    @Test
    fun item260_lightAndDarkChoicesSurviveReopeningTheStoreFile() {
        for (mode in listOf(DarkMode.LIGHT, DarkMode.DARK)) {
            val file = File(tmp.newFolder(), "prefs.preferences_pb")
            val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val second = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                runBlocking {
                    val s1 = SettingsRepository(PreferenceDataStoreFactory.create(scope = first) { file })
                    assertEquals("a new file starts on Follow system", DarkMode.SYSTEM, s1.darkMode.first())
                    s1.setDarkMode(mode)
                    first.cancel()
                    val s2 = SettingsRepository(PreferenceDataStoreFactory.create(scope = second) { file })
                    assertEquals(mode, s2.darkMode.first())
                }
            } finally {
                first.cancel()
                second.cancel()
            }
        }
    }
}
