package com.shibi.commentarii.ui.lock

import android.os.Looper
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.MainActivity
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.resetToDefaults
import com.shibi.commentarii.testutil.runWithLooper
import com.shibi.commentarii.testutil.waitUntilWithLooper
import java.time.Duration
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checklist #24, #30, #31, #32 on the real MainActivity. Robolectric has no fingerprint or screen lock enrolled, so the lock
 * screen shows without its system prompt, and a test stands in for a successful prompt by calling `lock.unlock()`, as
 * MainActivity's onSuccess does. Going to the background is the activity stopping plus the two calls CommentariiApp's
 * ProcessLifecycleOwner observer makes; that singleton stays attached to the sandbox's first Application, so it can't be driven.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class LockFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    // Not through rule.activity, which only works on the main thread: runWithLooper blocks run off it.
    private val container = ApplicationProvider.getApplicationContext<CommentariiApp>().container
    private val unlockText get() = rule.activity.getString(R.string.lock_unlock)

    @Before
    fun setUp() {
        runWithLooper { container.settings.resetToDefaults() }
    }

    @After
    fun tearDown() {
        runWithLooper { container.settings.resetToDefaults() }
    }

    private fun exists(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun tabSelected(label: Int) = rule.onAllNodes(tab(label) and isSelected()).fetchSemanticsNodes().isNotEmpty()

    private fun tab(label: Int) =
        hasText(rule.activity.getString(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun enableLockAndUnlock() {
        runWithLooper { container.settings.setLockEnabled(true) }
        rule.waitUntilWithLooper { exists(unlockText) }
        container.lock.unlock()
        rule.waitUntilWithLooper { !exists(unlockText) && tabSelected(R.string.tab_today) }
    }

    /** Home, [away] in the background, then back. */
    private fun backgroundFor(away: Duration) {
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        container.lock.onBackground()
        shadowOf(Looper.getMainLooper()).idleFor(away)
        container.onAppStart()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    @Test
    fun item024_cancelledPromptLeavesTheLockOff() {
        rule.waitUntilWithLooper { tabSelected(R.string.tab_today) }
        var applied: Boolean? = null
        val apply: (Boolean) -> Unit = { value ->
            applied = value
            runWithLooper { container.settings.setLockEnabled(value) }
        }

        // BiometricAuth reports Back/Cancel through onFailure, which SettingsScreen wires to done(false).
        requestLockChange(enable = true, available = true, authenticate = { done -> done(false) }, apply = apply, onUnavailable = {})
        assertEquals(null, applied)
        assertFalse(runWithLooper { container.settings.lockEnabled.first() })
        assertFalse("no lock screen appeared", exists(unlockText))
        assertTrue(tabSelected(R.string.tab_today))

        // A prompt that is still open has reported nothing yet.
        requestLockChange(enable = true, available = true, authenticate = { }, apply = apply, onUnavailable = {})
        assertEquals(null, applied)

        // Positive control: the same call with a successful prompt turns it on.
        requestLockChange(enable = true, available = true, authenticate = { done -> done(true) }, apply = apply, onUnavailable = {})
        assertEquals(true, applied)
        assertTrue(runWithLooper { container.settings.lockEnabled.first() })
    }

    @Test
    fun item030_moreThanSixtySecondsAwayShowsTheLockScreen() {
        enableLockAndUnlock()
        backgroundFor(Duration.ofSeconds(61))
        rule.waitUntilWithLooper { exists(unlockText) }
        assertTrue(container.lock.locked.value)
        assertEquals("the app is gone from the screen", false, tabSelected(R.string.tab_today))
        assertEquals(UnlockAction.PROMPT, unlockAction(lockEnabled = true, authAvailable = true))
    }

    @Test
    fun item031_thirtySecondsAwayShowsNoLockScreen() {
        enableLockAndUnlock()
        backgroundFor(Duration.ofSeconds(30))
        shadowOf(Looper.getMainLooper()).idle()
        rule.waitForIdle()
        assertFalse(container.lock.locked.value)
        assertTrue("still on the app", tabSelected(R.string.tab_today))
        assertFalse(exists(unlockText))
    }

    @Test
    fun item032_unlockReturnsToTheSameNoteWithTheSameBackStack() {
        runWithLooper { container.pages.createNote("Trip plan") }
        enableLockAndUnlock()

        rule.onNode(tab(R.string.tab_notes)).performClick()
        rule.waitUntilWithLooper { exists("Trip plan") }
        val newNote = hasContentDescription(rule.activity.getString(R.string.nt_new))
        rule.onNode(newNote).assertExists()
        rule.onNode(hasText("Trip plan")).performClick()
        rule.waitUntilWithLooper { rule.onAllNodes(newNote).fetchSemanticsNodes().isEmpty() && rule.onAllNodes(hasText("Trip plan") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }

        backgroundFor(Duration.ofSeconds(61))
        rule.waitUntilWithLooper { exists(unlockText) }
        assertEquals("the note is not composed behind the lock", 0, rule.onAllNodes(hasText("Trip plan")).fetchSemanticsNodes().size)
        container.lock.unlock()

        rule.waitUntilWithLooper { !exists(unlockText) && rule.onAllNodes(hasText("Trip plan") and hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, rule.onAllNodes(newNote).fetchSemanticsNodes().size)
        assertTrue(tabSelected(R.string.tab_notes))

        pressBack()
        rule.waitUntilWithLooper { rule.onAllNodes(newNote).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(tabSelected(R.string.tab_notes))

        pressBack()
        rule.waitUntilWithLooper { tabSelected(R.string.tab_today) }
    }

    @Test
    fun item032_unlockReturnsToTheSameWeekWithTheSameBackStack() {
        enableLockAndUnlock()

        rule.onNode(tab(R.string.tab_week)).performClick()
        rule.waitUntilWithLooper { tabSelected(R.string.tab_week) }

        backgroundFor(Duration.ofSeconds(61))
        rule.waitUntilWithLooper { exists(unlockText) }
        container.lock.unlock()

        rule.waitUntilWithLooper { !exists(unlockText) && tabSelected(R.string.tab_week) }
        pressBack()
        rule.waitUntilWithLooper { tabSelected(R.string.tab_today) }
    }
}
