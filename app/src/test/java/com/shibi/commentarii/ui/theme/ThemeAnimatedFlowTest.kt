package com.shibi.commentarii.ui.theme

import android.animation.ValueAnimator
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DEFAULT_THEME_ID
import com.shibi.commentarii.data.SettingsRepository
import com.shibi.commentarii.testutil.ThemeFlowTestBase
import com.shibi.commentarii.testutil.runWithLooper
import com.shibi.commentarii.testutil.waitUntilWithLooper
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Checklist #264-#266 on the real MainActivity. An animated theme's background never lets compose go idle, so whenever one can be
 * running the compose clock is held (mainClock.autoAdvance = false) and advanced by hand, and the window is compared pixel for
 * pixel between frames: movement is a difference, a freeze is none. (performScrollTo cannot be used while the clock is held, so all
 * scrolling and Settings taps are done with the clock running, on the Settings page, which has no animated background.)
 */
class ThemeAnimatedFlowTest : ThemeFlowTestBase() {
    @get:Rule val tmp = TemporaryFolder()

    private val animatedIds = listOf("cloud_drift", "petal_fall", "floating_hearts")
    private val petal = AppThemes.byId("petal_fall")
    private val animatedLabel get() = str(R.string.settings_theme_animated)
    private val offLabel get() = str(R.string.settings_theme_static)
    private val systemOffMessage get() = str(R.string.settings_theme_system_anim_off)

    @After
    fun calmDownAndRestoreAnimatorScale() {
        // Runs before the base class resets the settings: stop any animated background first so teardown can go idle.
        rule.mainClock.autoAdvance = false
        runCatching { runWithLooper { container.settings.setThemeId(DEFAULT_THEME_ID) } }
        repeat(60) { rule.mainClock.advanceTimeByFrame() }
        rule.mainClock.autoAdvance = true
        setAnimatorScale(1f)
    }

    private fun setAnimatorScale(scale: Float) {
        ReflectionHelpers.callStaticMethod<Any?>(ValueAnimator::class.java, "setDurationScale", ClassParameter.from(Float::class.javaPrimitiveType, scale))
    }

    private fun frames(n: Int) = repeat(n) { rule.mainClock.advanceTimeByFrame() }

    private fun awaitFrames(condition: () -> Boolean) = rule.waitUntilWithLooper { rule.mainClock.advanceTimeByFrame(); condition() }

    private fun differing(a: Bitmap, b: Bitmap): Int {
        var n = 0
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }

    private fun staticIds(): Set<String> = runWithLooper { container.settings.staticThemeIds.first() }

    private fun storedTheme(): String = runWithLooper { container.settings.themeId.first() }

    // ---- Settings page: the controls ----

    private fun nodesOf(label: String): List<SemanticsNode> = rule.onAllNodes(onPage(label)).fetchSemanticsNodes()

    /** The theme whose row is the nearest one above [node]. */
    private fun themeAbove(node: SemanticsNode): AppTheme {
        val y = contentY(node)
        return AppThemes.all.last { contentY(rule.onNode(themeRow(it)).fetchSemanticsNode()) < y }
    }

    private fun controlOf(theme: AppTheme, label: String): Int =
        nodesOf(label).indexOfFirst { themeAbove(it).id == theme.id }.also { check(it >= 0) { "no '$label' control under ${theme.id}" } }

    private fun isControlSelected(theme: AppTheme, label: String): Boolean =
        nodesOf(label)[controlOf(theme, label)].config[SemanticsProperties.Selected]

    private fun tapControl(theme: AppTheme, label: String) {
        val index = controlOf(theme, label)
        rule.onAllNodes(onPage(label))[index].performScrollTo()
        rule.onAllNodes(onPage(label))[index].performClick()
        rule.waitForIdle()
    }

    // ---- Today, with the clock held ----

    private fun showTodayHeld() {
        rule.mainClock.autoAdvance = false
        rule.onNode(tab(R.string.tab_today)).performClick()
        awaitFrames { tabSelected(R.string.tab_today) && exists("6:00 AM") }
        // Taking a picture lets the main looper run, which is when the page's data arrives; let that settle before any comparison.
        repeat(12) { windowBitmap(); frames(10) } // also outlasts the 700 ms tab crossfade
    }

    private fun backToSettingsAndLetTheClockRun() {
        rule.onNode(tab(R.string.tab_settings)).performClick()
        awaitFrames { tabSelected(R.string.tab_settings) && exists(onPage(str(R.string.settings_section_backup))) }
        frames(80)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
    }

    /** Two window pictures [millis] of compose time apart. */
    private fun twoPicturesApart(millis: Int): Pair<Bitmap, Bitmap> {
        val first = windowBitmap()
        frames(millis / 16)
        return first to windowBitmap()
    }

    // ---- #264 ----

    /**
     * Checklist #264, the controls: exactly three themes (Cloud Drift, Petal Fall, Floating Hearts) have an "Animated | Off"
     * control, each under its own row, with Animated selected on defaults; the five static themes have none.
     */
    @Test
    fun item264_onlyCloudDriftPetalFallAndFloatingHeartsHaveTheAnimatedOffControl() {
        openSettings()
        assertEquals("Animated", animatedLabel)
        assertEquals("Off", offLabel)
        for (label in listOf(animatedLabel, offLabel)) {
            val nodes = nodesOf(label)
            assertEquals("three '$label' controls", 3, nodes.size)
            assertEquals(animatedIds, nodes.map { themeAbove(it).id })
        }
        for (id in animatedIds) {
            val theme = AppThemes.byId(id)
            assertTrue("$id: Animated selected on defaults", isControlSelected(theme, animatedLabel))
            assertFalse("$id: Off not selected on defaults", isControlSelected(theme, offLabel))
        }
        assertEquals(animatedIds.toSet(), AppThemes.all.filter { it.animated }.map { it.id }.toSet())
        assertEquals(emptySet<String>(), staticIds())
    }

    /**
     * Checklist #264, Off and Animated on Petal Fall: with Off set on Petal Fall (and Petal Fall chosen) two pictures of Today
     * 2.4 s of compose time apart are identical; with Animated they differ (so the comparison can see movement, and the loop
     * really runs). The choice is per theme (Cloud Drift and Floating Hearts stay Animated) and is in the settings store.
     */
    @Test
    fun item264_petalFallOffFreezesTheBackgroundAndAnimatedMovesItAgain() {
        openSettings()
        tapControl(petal, offLabel)
        await { staticIds() == setOf("petal_fall") }
        assertTrue("Off selected on Petal Fall", isControlSelected(petal, offLabel))
        assertFalse("Animated not selected on Petal Fall", isControlSelected(petal, animatedLabel))
        for (id in listOf("cloud_drift", "floating_hearts")) {
            assertTrue("$id still Animated", isControlSelected(AppThemes.byId(id), animatedLabel))
            assertFalse("$id not Off", isControlSelected(AppThemes.byId(id), offLabel))
        }
        tapRow(themeRow(petal))
        await { storedTheme() == "petal_fall" }

        showTodayHeld()
        val (offA, offB) = twoPicturesApart(2400)
        assertEquals("Off: the picture does not change over 2.4 s", 0, differing(offA, offB))

        backToSettingsAndLetTheClockRun()
        tapControl(petal, animatedLabel)
        await { staticIds().isEmpty() }
        assertTrue("Animated selected on Petal Fall", isControlSelected(petal, animatedLabel))

        showTodayHeld()
        val (onA, onB) = twoPicturesApart(1000)
        val moved = differing(onA, onB)
        println("item264: Animated moved $moved pixels in 1 s, Off moved 0 in 2.4 s")
        assertTrue("Animated: the picture changes within 1 s ($moved pixels)", moved > 1000)
    }

    /**
     * Checklist #264, persistence: a settings store on a temp file, one write ("Off" on Petal Fall), the scope cancelled and a
     * second store opened on the same file reads exactly that theme as Off. Real process death is not exercised.
     */
    @Test
    fun item264_offOnPetalFallPersistsInTheStoreFileAndOnlyForThatTheme() {
        val file = File(tmp.newFolder(), "prefs.preferences_pb")
        val first = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val second = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            runBlocking {
                val s1 = SettingsRepository(PreferenceDataStoreFactory.create(scope = first) { file })
                s1.setThemeAnimated("petal_fall", false)
                first.cancel()
                val s2 = SettingsRepository(PreferenceDataStoreFactory.create(scope = second) { file })
                assertEquals(setOf("petal_fall"), s2.staticThemeIds.first())
            }
        } finally {
            first.cancel()
            second.cancel()
        }
    }

    // ---- #265 ----

    /**
     * Checklist #265: with the animator duration scale at 0 (ValueAnimator.areAnimatorsEnabled() is false, asserted first), the
     * Theme card shows "System animations are off, so this stays still." under each of the three animated themes and nowhere
     * else; with Petal Fall Animated and chosen, two pictures of Today 2.4 s apart are identical.
     */
    @Test
    fun item265_withSystemAnimationsOffTheMessageShowsAndTheBackgroundStaysStill() {
        setAnimatorScale(0f)
        assertFalse("the sandbox's animators are off", ValueAnimator.areAnimatorsEnabled())
        openSettings()
        assertEquals("System animations are off, so this stays still.", systemOffMessage)
        val messages = nodesOf(systemOffMessage)
        assertEquals(animatedIds, messages.map { themeAbove(it).id })
        assertTrue("Petal Fall is on Animated", isControlSelected(petal, animatedLabel))

        tapRow(themeRow(petal))
        await { storedTheme() == "petal_fall" }
        assertEquals(emptySet<String>(), staticIds())

        showTodayHeld()
        val (a, b) = twoPicturesApart(2400)
        assertEquals("system animations off: the background does not move", 0, differing(a, b))
    }

    /** Control for #265: with the scale at 1 the message is absent and the same Petal Fall page moves. */
    @Test
    fun item265_control_withSystemAnimationsOnThereIsNoMessageAndTheBackgroundMoves() {
        setAnimatorScale(1f)
        assertTrue(ValueAnimator.areAnimatorsEnabled())
        openSettings()
        assertEquals("the three controls are there to be compared with", 3, nodesOf(animatedLabel).size)
        assertEquals("no message", 0, nodesOf(systemOffMessage).size)

        tapRow(themeRow(petal))
        await { storedTheme() == "petal_fall" }
        showTodayHeld()
        val (a, b) = twoPicturesApart(1000)
        val moved = differing(a, b)
        assertTrue("animators on: the picture changes ($moved pixels)", moved > 1000)
    }

    // ---- #266 ----

    /**
     * Checklist #266, lifecycle: Petal Fall animated on Today moves; once the activity is only CREATED (after Home) two pictures
     * 2.4 s of compose time apart are identical, i.e. the frame loop is not running; on RESUMED it moves again, continuing from
     * the picture it stopped on (5 frames later it is closer to the stopped picture than 60 frames later is to that one).
     * Not provable here: battery use itself.
     */
    @Test
    fun item266_theBackgroundLoopStopsWhenTheActivityIsStoppedAndResumesOnReturn() {
        openTab(R.string.tab_today)
        await { exists("6:00 AM") }
        rule.mainClock.autoAdvance = false
        runWithLooper { container.settings.setThemeId("petal_fall") }
        val paper = petal.light.scheme.surfaceContainer.toArgb()
        awaitFrames { hex(barBackgroundPixel()) == hex(paper) }
        frames(40)

        val (runA, runB) = twoPicturesApart(1000)
        assertTrue("running: the picture moves", differing(runA, runB) > 1000)

        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        frames(30)
        val (stoppedA, stoppedB) = twoPicturesApart(2400)
        assertEquals("stopped: no movement over 2.4 s", 0, differing(stoppedA, stoppedB))

        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        frames(5)
        val soon = windowBitmap()
        frames(60)
        val later = windowBitmap()
        val sinceStop = differing(stoppedB, soon)
        val sinceSoon = differing(soon, later)
        println("item266: after 5 frames $sinceStop pixels from the stopped picture; 60 frames on, $sinceSoon more")
        assertTrue("resumed: it moves again ($sinceSoon)", sinceSoon > 1000)
        assertTrue("resumed smoothly: the first frames stay near the stopped picture ($sinceStop vs $sinceSoon)", sinceStop < sinceSoon)
    }
}
