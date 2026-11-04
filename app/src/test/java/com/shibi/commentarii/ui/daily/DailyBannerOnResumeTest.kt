package com.shibi.commentarii.ui.daily

import android.app.NotificationManager
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.MainActivity
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.resetToDefaults
import com.shibi.commentarii.testutil.runWithLooper
import com.shibi.commentarii.testutil.waitUntilWithLooper
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Checklist #97 on the real MainActivity: nothing tells the app about a settings change, so the banner relies on its onResume. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class DailyBannerOnResumeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    // Not through rule.activity, which only works on the main thread: runWithLooper blocks run off it.
    private val container = ApplicationProvider.getApplicationContext<CommentariiApp>().container

    @Before
    fun setUp() {
        runWithLooper { container.settings.resetToDefaults() }
    }

    @After
    fun tearDown() {
        runWithLooper { container.settings.resetToDefaults() }
    }

    private fun exists(text: String) = rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun setNotifications(enabled: Boolean) {
        shadowOf(rule.activity.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(enabled)
    }

    @Test
    fun item097_notificationsTurnedOffWhileAwayShowTheBannerOnReturn() {
        val message = rule.activity.getString(R.string.notif_blocked_message)
        val button = rule.activity.getString(R.string.notif_blocked_button)
        val scenario = rule.activityRule.scenario
        rule.waitUntilWithLooper { exists(rule.activity.getString(R.string.section_priorities)) }
        assertFalse("control: not while notifications are on", exists(message))

        setNotifications(false)
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntilWithLooper { exists(message) && exists(button) }

        setNotifications(true)
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntilWithLooper { !exists(message) }
    }
}
