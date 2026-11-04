package com.shibi.commentarii.ui.settings

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * #98: the notification settings page must open in the caller's task when there is an Activity.
 * JVM only: this proves which flags are sent, not how One UI places the page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenNotificationSettingsTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    private fun assertTargetsThisApp(intent: Intent) {
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(application.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    private fun Intent.hasNewTask() = flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0

    @Test
    fun activityContextDoesNotStartNewTask() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        openNotificationSettings(activity)
        val intent = shadowOf(activity).nextStartedActivity
        assertTargetsThisApp(intent)
        assertFalse("flags ${intent.flags} must not contain NEW_TASK", intent.hasNewTask())
    }

    @Test
    fun wrappedActivityContextDoesNotStartNewTask() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val wrapped: Context = ContextThemeWrapper(activity, android.R.style.Theme_Material_Light)
        openNotificationSettings(wrapped)
        val intent = shadowOf(activity).nextStartedActivity
        assertTargetsThisApp(intent)
        assertFalse("flags ${intent.flags} must not contain NEW_TASK", intent.hasNewTask())
    }

    @Test
    fun applicationContextStartsNewTask() {
        openNotificationSettings(application)
        val intent = shadowOf(application).nextStartedActivity
        assertTargetsThisApp(intent)
        assertTrue("flags ${intent.flags} must contain NEW_TASK", intent.hasNewTask())
    }
}
