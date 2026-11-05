package com.shibi.commentarii.ui.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.MainActivity
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.testutil.luminanceOf
import com.shibi.commentarii.testutil.resetToDefaults
import com.shibi.commentarii.testutil.runWithLooper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checklist #268: Lavender Moon + Dark stored, then a fresh launch of the real MainActivity. The compose clock is held before the
 * launch, and the window is drawn straight from the test thread (not through rule.runOnUiThread, which would run the main looper and
 * deliver the stores' values first), so the picture is the first composed frame, before the settings stores have answered.
 * Light, system-light device (the default qualifiers), as the item's wording ("without flashing ... light mode first") describes.
 * Not provable here: a real force-stop and the timing of a real device's first frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class ThemeStartupFlashTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val container = ApplicationProvider.getApplicationContext<CommentariiApp>().container
    private val lavender = AppThemes.byId("lavender_moon")
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun storeLavenderMoonDark() {
        runWithLooper {
            container.settings.resetToDefaults()
            container.settings.setThemeId("lavender_moon")
            container.settings.setDarkMode(DarkMode.DARK)
        }
    }

    @After
    fun restoreDefaults() {
        scenario?.close()
        runWithLooper { container.settings.resetToDefaults() }
    }

    private fun launchHeld(): MainActivity {
        rule.mainClock.autoAdvance = false
        val launched = ActivityScenario.launch(MainActivity::class.java)
        scenario = launched
        var activity: MainActivity? = null
        launched.onActivity { activity = it }
        return checkNotNull(activity)
    }

    private fun draw(activity: MainActivity): Bitmap {
        val view = activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun hex(argb: Int) = "#%08X".format(argb)

    /**
     * Checklist #268: the very first frame is the saved Lavender Moon dark, not Rose & Cream and not light. Found failing with
     * "first frame #FFFBF6F3 is Rose & Cream's background": MainActivity painted its loading Surface in the default theme.
     */
    @Test
    fun item268_firstFrameIsNeitherRoseCreamNorLightWhenLavenderMoonDarkIsSaved() {
        val activity = launchHeld()
        val first = draw(activity).getPixel(1, 1)
        val roseCream = listOf(AppThemes.Default.light, AppThemes.Default.dark).map { it.scheme.background.toArgb() }
        assertTrue("first frame ${hex(first)} is Rose & Cream's background", first !in roseCream)
        assertTrue("first frame ${hex(first)} is a light colour", luminanceOf(first) < 0.2)
        assertEquals("first frame", hex(lavender.dark.scheme.background.toArgb()), hex(first))
    }

    /** Checklist #268: the frames after the first one stay Lavender Moon dark, so nothing changes once the stores answer. */
    @Test
    fun item268_theWindowStaysLavenderMoonDarkAfterTheStoresAnswer() {
        val activity = launchHeld()
        val first = draw(activity).getPixel(1, 1)
        rule.mainClock.advanceTimeByFrame()
        assertEquals("page paper", hex(lavender.dark.scheme.background.toArgb()), hex(draw(activity).getPixel(1, 1)))
        assertEquals("the first frame is the loaded picture", hex(first), hex(draw(activity).getPixel(1, 1)))
    }

    /** Checklist #268, positive control: with the defaults stored the same first-frame capture does show Rose & Cream light. */
    @Test
    fun item268_control_withTheDefaultsStoredTheFirstFrameIsRoseCreamLight() {
        runWithLooper { container.settings.resetToDefaults() }
        val first = draw(launchHeld()).getPixel(1, 1)
        assertEquals("first frame", hex(AppThemes.Default.light.scheme.background.toArgb()), hex(first))
    }

    /** One write on top of the setup's: repeated writes to one store trip a Windows host IOException. Returns the colour the page should be. */
    private fun storeDarkBgOverride(): String {
        val o = ThemeOverride.Empty.withColor("d.bg", 0xFF12304A)
        runWithLooper { container.settings.setThemeOverride("lavender_moon", ThemeOverrideCodec.encode(o)) }
        val edited = hex(0xFF12304A.toInt())
        assertEquals("the resolver accepts this bg", edited, hex(lavender.customized(o).dark.scheme.background.toArgb()))
        assertNotEquals("the override differs from stock", hex(lavender.dark.scheme.background.toArgb()), edited)
        return edited
    }

    /** Trap 7: a saved override is on the very first frame, read before setContent like the theme id, not stock until the stores answer. */
    @Test
    fun aSavedOverrideIsOnTheVeryFirstFrame() {
        val edited = storeDarkBgOverride()
        val first = draw(launchHeld()).getPixel(1, 1)
        assertEquals("first frame", edited, hex(first))
    }

    /** Trap 7, control: the frames after the first stay on the override, so nothing changes once the stores answer. */
    @Test
    fun theWindowStaysOnTheSavedOverrideAfterTheStoresAnswer() {
        val edited = storeDarkBgOverride()
        val activity = launchHeld()
        assertEquals("first frame", edited, hex(draw(activity).getPixel(1, 1)))
        rule.mainClock.advanceTimeByFrame()
        assertEquals("next frame", edited, hex(draw(activity).getPixel(1, 1)))
    }
}
