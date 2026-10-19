package com.shibi.commentarii.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationsTest {
    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
    }

    @Test
    fun channelV2IsHighImportanceAndLegacyIsDeleted() {
        manager.createNotificationChannel(
            NotificationChannel("schedule_reminders", "old", NotificationManager.IMPORTANCE_DEFAULT),
        )
        Notifications.ensureChannel(context)
        assertNull(manager.getNotificationChannel("schedule_reminders"))
        val channel = manager.getNotificationChannel("schedule_reminders_v2")
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    private fun reminderIntent(id: Long, text: String, minute: Int = -1) = Intent(AlarmScheduler.ACTION_REMINDER)
        .putExtra(Notifications.EXTRA_ENTRY_ID, id)
        .putExtra(Notifications.EXTRA_MINUTE, minute)
        .putExtra(Notifications.EXTRA_DATE, "2026-10-18")
        .putExtra(Notifications.EXTRA_TEXT, text)

    @Test
    fun receiverPostsNotificationForNormalAndTestIds() {
        AlarmReceiver().onReceive(context, reminderIntent(7, "call"))
        AlarmReceiver().onReceive(context, reminderIntent(AlarmScheduler.TEST_ENTRY_ID, "Test reminder"))
        assertEquals(2, shadowOf(manager).allNotifications.size)
        val log = (context as CommentariiApp).container.debugLog.readAll()
        assertTrue(log.contains("FIRE id=7 action=${AlarmScheduler.ACTION_REMINDER}"))
        assertTrue(log.contains("channelImportance=${NotificationManager.IMPORTANCE_HIGH}"))
        assertTrue(log.contains("enabled=true"))
    }

    @Test
    fun reminderTextShowsEntryTimeOrJustNowForTest() {
        val time = java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
            .format(java.time.LocalTime.of(9, 30))
        assertEquals("Now ($time): Dentist", Notifications.reminderText(context, "Dentist", 9 * 60 + 30))
        assertEquals("Now: Test reminder", Notifications.reminderText(context, "Test reminder", -1))
        AlarmReceiver().onReceive(context, reminderIntent(7, "Dentist", 9 * 60 + 30))
        val text = shadowOf(manager).allNotifications.single().extras.getCharSequence("android.text").toString()
        assertEquals("Now ($time): Dentist", text)
    }

    @Test
    fun receiverDropsBlankText() {
        AlarmReceiver().onReceive(context, reminderIntent(7, " "))
        assertEquals(0, shadowOf(manager).allNotifications.size)
    }

    @Test
    fun disabledNotificationsAreLoggedAndNotPosted() {
        shadowOf(manager).setNotificationsEnabled(false)
        AlarmReceiver().onReceive(context, reminderIntent(7, "call"))
        assertEquals(0, shadowOf(manager).allNotifications.size)
        assertTrue((context as CommentariiApp).container.debugLog.readAll().contains("skipped: notifications disabled"))
    }
}
