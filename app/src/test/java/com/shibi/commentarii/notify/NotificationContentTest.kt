package com.shibi.commentarii.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Checklist #103, #115, #314: the channel, the disabled path and the content of a posted reminder. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationContentTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
    }

    private fun fire(id: Long = 7, text: String = "call") = AlarmReceiver().onReceive(
        context,
        Intent(AlarmScheduler.ACTION_REMINDER)
            .putExtra(Notifications.EXTRA_ENTRY_ID, id)
            .putExtra(Notifications.EXTRA_MINUTE, 9 * 60)
            .putExtra(Notifications.EXTRA_DATE, "2031-03-12")
            .putExtra(Notifications.EXTRA_TEXT, text),
    )

    private fun log() = (context as CommentariiApp).container.debugLog.readAll()

    @Test
    fun item103_oneHighImportanceChannelWithItsNameAndNoLegacyDuplicate() {
        manager.createNotificationChannel(
            NotificationChannel(Notifications.LEGACY_CHANNEL_ID, "old", NotificationManager.IMPORTANCE_DEFAULT),
        )
        Notifications.ensureChannel(context)

        val channel = manager.notificationChannels.single()
        assertEquals(Notifications.CHANNEL_ID, channel.id)
        assertEquals("Schedule reminders", channel.name)
        assertEquals("Reminders at the time of a schedule entry.", channel.description)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    @Test
    fun item115_offInSystemSettingsPostsNothingAndLogsTheSkip() {
        fire(id = 6)
        assertEquals("control: posts while enabled", 1, shadowOf(manager).allNotifications.size)
        assertFalse(log().contains("NOTIFY id=6 skipped"))

        shadowOf(manager).setNotificationsEnabled(false)
        fire(id = 7)

        assertEquals(1, shadowOf(manager).allNotifications.size)
        assertTrue(log().contains("NOTIFY id=7 skipped: notifications disabled"))
        assertFalse(log().contains("NOTIFY id=7 posted"))
    }

    @Test
    fun item314_reminderHasNoActionsAndOnlyOpensTheApp() {
        fire()

        val posted = shadowOf(manager).allNotifications.single()
        assertEquals(Notifications.CHANNEL_ID, posted.channelId)
        assertTrue(posted.extras.getCharSequence("android.text").toString().endsWith(": call"))
        assertEquals("no snooze or mark-done buttons", 0, posted.actions?.size ?: 0)

        val tap = shadowOf(posted.contentIntent)
        assertTrue(tap.isActivity)
        assertEquals(MainActivity::class.java.name, tap.savedIntent.component?.className)
        assertEquals("2031-03-12", tap.savedIntent.getStringExtra(Notifications.EXTRA_DATE))
        assertNotEquals(0, posted.flags and Notification.FLAG_AUTO_CANCEL)
    }
}
