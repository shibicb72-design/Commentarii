package com.shibi.commentarii.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.R
import com.shibi.commentarii.data.ScheduleEntryEntity
import com.shibi.commentarii.notify.BootReceiver
import com.shibi.commentarii.testutil.MainActivityTestBase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Checklist #117-#124 and #126: Settings > Reminders on the real MainActivity. Each status is read on resume, so a test shows
 * the screen first, changes the system state behind it, checks the old text is still there, and only then moves the activity
 * through a stop and resume. System pages cannot open here: what is asserted is the intent the app sends. A real toggle on them
 * is stood in for by the shadow.
 */
class ReminderStatusFlowTest : MainActivityTestBase() {
    private val exactOk get() = str(R.string.settings_exact_alarm_ok)
    private val exactExplainer get() = str(R.string.settings_exact_alarm_explainer)
    private val exactButton get() = str(R.string.settings_exact_alarm_button)
    private val blockedMessage get() = str(R.string.notif_blocked_message)
    private val blockedButton get() = str(R.string.notif_blocked_button)
    private val unrestricted get() = str(R.string.settings_battery_unrestricted)
    private val optimised get() = str(R.string.settings_battery_optimised)
    private val batteryButton get() = str(R.string.settings_battery_button)
    private val samsungNote get() = str(R.string.settings_battery_samsung_note)
    private val testReminder get() = str(R.string.settings_test_reminder)
    private val testScheduled get() = str(R.string.settings_msg_test_scheduled)
    private val remindersTitle get() = str(R.string.settings_section_reminders)
    private val packageName get() = rule.activity.packageName

    private val app get() = ApplicationProvider.getApplicationContext<CommentariiApp>()

    private fun openReminders() {
        openTab(R.string.tab_settings)
        await { exists(remindersTitle) }
        scrollIntoView(testReminder)
        scrollIntoView(remindersTitle)
    }

    private fun scrollIntoView(text: String) {
        rule.onNode(hasText(text)).performScrollTo()
        rule.waitForIdle()
    }

    private fun tap(text: String) {
        scrollIntoView(text)
        tapText(text)
    }

    private fun top(text: String) = boundsOf(hasText(text)).top

    private fun setBatteryUnrestricted(value: Boolean) {
        shadowOf(rule.activity.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(packageName, value)
    }

    private fun setNotificationsEnabled(value: Boolean) {
        shadowOf(rule.activity.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(value)
    }

    private fun startedIntent(): Intent? = shadowOf(rule.activity).nextStartedActivity

    private fun requestedPermissions() = shadowOf(rule.activity).lastRequestedPermission?.requestedPermissions?.toList()

    private fun assertExactAllowedShown() {
        assertTrue(exists(exactOk))
        assertFalse(exists(exactExplainer))
        assertFalse(exists(exactButton))
    }

    private fun assertExactRevokedShown() {
        assertFalse(exists(exactOk))
        assertTrue(exists(exactExplainer))
        assertTrue(exists(exactButton))
    }

    @Test
    fun item117_allowedShowsTheStatusAndNoButton() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        openReminders()
        assertEquals("Exact alarms: allowed", exactOk)
        assertExactAllowedShown()
    }

    @Test
    fun item118_revokingWhileAwayShowsTheExplainerAndButtonOnReturn() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        openReminders()
        assertExactAllowedShown()

        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        rule.waitForIdle()
        assertExactAllowedShown()

        leaveAndReturn()
        await { exists(exactExplainer) }
        assertEquals(
            "Without this permission reminders may arrive a few minutes late. Allow exact alarms for on-time reminders.",
            exactExplainer,
        )
        assertEquals("Allow exact alarms", exactButton)
        assertExactRevokedShown()
    }

    @Test
    fun item119_buttonRequestsTheAlarmsPageForThisApp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        openReminders()
        assertExactRevokedShown()

        tap(exactButton)
        val intent = checkNotNull(startedIntent())
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
        assertEquals(Uri.parse("package:$packageName"), intent.data)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
    fun item119_belowApi31ThePermissionDoesNotExistAndTheButtonIsNeverShown() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        openReminders()
        assertExactAllowedShown()
        leaveAndReturn()
        assertExactAllowedShown()
    }

    @Test
    fun item120_grantingShowsAllowedOnReturnAndResumeReRegistersAsExact() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val tomorrow = LocalDate.now(container.clock).plusDays(1)
        val entry = seedReminder(tomorrow)
        assertTrue("registered inexact while revoked", container.debugLog.readAll().contains("id=$entry date=$tomorrow minute=540"))
        assertTrue(scheduleLine(entry, tomorrow).contains(" inexact "))
        openReminders()
        assertExactRevokedShown()

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        rule.waitForIdle()
        assertExactRevokedShown()

        leaveAndReturn()
        await { exists(exactOk) }
        assertExactAllowedShown()

        // What CommentariiApp's process observer runs on the way back. That observer stays attached to the sandbox's first
        // Application, so it cannot be driven from here (see LockFlowTest).
        container.debugLog.clear()
        runBlocking { container.onAppStart().join() }
        assertTrue(container.debugLog.readAll(), container.debugLog.readAll().contains("RESCHEDULE_ALL reason=app resume entries=1 registered=1 exact=true"))
        assertTrue(scheduleLine(entry, tomorrow).contains(" exact "))
    }

    @Test
    fun item120_thePermissionBroadcastAlsoReRegistersAsExact() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val tomorrow = LocalDate.now(container.clock).plusDays(1)
        val entry = seedReminder(tomorrow)
        assertTrue(scheduleLine(entry, tomorrow).contains(" inexact "))

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        container.debugLog.clear()
        val receiver = BootReceiver()
        receiver.onReceive(app, Intent(BootReceiver.ACTION_EXACT_ALARM_STATE_CHANGED))
        runBlocking { checkNotNull(receiver.job).join() }
        assertTrue(
            container.debugLog.readAll(),
            container.debugLog.readAll().contains("RESCHEDULE_ALL reason=${BootReceiver.ACTION_EXACT_ALARM_STATE_CHANGED} entries=1 registered=1 exact=true"),
        )
        assertTrue(scheduleLine(entry, tomorrow).contains(" exact "))
    }

    private fun seedReminder(date: LocalDate): Long = runBlocking {
        val page = container.pages.getOrCreateDaily(date)
        val rows = container.pages.getSchedule(page.id).map { if (it.minuteOfDay == 540) it.copy(text = "Dentist") else it } +
            if (container.pages.getSchedule(page.id).none { it.minuteOfDay == 540 }) {
                listOf(ScheduleEntryEntity(pageId = page.id, date = date, minuteOfDay = 540, text = "Dentist"))
            } else {
                emptyList()
            }
        container.pages.saveSchedule(page.id, rows).single { it.minuteOfDay == 540 }.id
    }

    private fun scheduleLine(entry: Long, date: LocalDate) =
        container.debugLog.lines().last { it.contains(" SCHEDULE ") && it.contains("id=$entry date=$date minute=540") }

    @Test
    fun item122_blockedNotificationsShowAtTheTopOfTheCardAndTheButtonOpensTheAppsPage() {
        setNotificationsEnabled(true)
        openReminders()
        assertFalse("not blocked yet", exists(blockedMessage))
        assertFalse(exists(blockedButton))

        setNotificationsEnabled(false)
        rule.waitForIdle()
        assertFalse("read on resume only", exists(blockedMessage))

        leaveAndReturn()
        await { exists(blockedMessage) }
        scrollIntoView(remindersTitle)
        assertEquals("Notifications are off - reminders can't appear", blockedMessage)
        assertEquals("Open notification settings", blockedButton)
        assertTrue("under the card's title", top(remindersTitle) < top(blockedMessage))
        assertTrue("message, then button", top(blockedMessage) < top(blockedButton))
        assertTrue("above the rest of the card", top(blockedButton) < top(exactOk))

        tap(blockedButton)
        val intent = checkNotNull(startedIntent())
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))

        setNotificationsEnabled(true)
        leaveAndReturn()
        await { !exists(blockedMessage) }
        assertFalse(exists(blockedButton))
    }

    @Test
    fun item123_batteryUnrestrictedMatchesTheSystemWithTheSamsungHintBelow() {
        setBatteryUnrestricted(true)
        openReminders()
        scrollIntoView(batteryButton)
        assertEquals("Battery: unrestricted", unrestricted)
        assertTrue(exists(unrestricted))
        assertFalse(exists(optimised))
        assertTrue("hint below the status", top(unrestricted) < top(samsungNote))
    }

    @Test
    fun item123_batteryOptimisedMatchesTheSystemWithTheSamsungHintBelow() {
        setBatteryUnrestricted(false)
        openReminders()
        scrollIntoView(batteryButton)
        await { exists(optimised) }
        assertEquals("Battery: optimised - reminders may be delayed", optimised)
        assertFalse(exists(unrestricted))
        assertTrue("hint below the status", top(optimised) < top(samsungNote))
    }

    @Test
    fun item124_buttonOpensTheBatteryListAndTheLineUpdatesOnReturn() {
        setBatteryUnrestricted(false)
        openReminders()
        scrollIntoView(batteryButton)
        await { exists(optimised) }

        tap(batteryButton)
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, checkNotNull(startedIntent()).action)

        setBatteryUnrestricted(true)
        rule.waitForIdle()
        assertTrue("read on resume only", exists(optimised))
        assertFalse(exists(unrestricted))

        leaveAndReturn()
        await { exists(unrestricted) }
        assertFalse(exists(optimised))

        // And back: the user restricts the app again.
        setBatteryUnrestricted(false)
        rule.waitForIdle()
        assertTrue(exists(unrestricted))
        leaveAndReturn()
        await { exists(optimised) }
        assertFalse(exists(unrestricted))
    }

    /** The status line in the card has the same text as the snackbar, so a shown snackbar makes two nodes of it. */
    @Test
    fun item126_blockedTestReminderShowsTheSnackbarAndRequestsThePermissionEachTime() {
        setNotificationsEnabled(false)
        leaveAndReturn()
        openReminders()
        await { exists(blockedMessage) }
        assertEquals("the card's message only", 1, count(hasText(blockedMessage)))
        assertNull("nothing asked yet", requestedPermissions())

        tap(testReminder)
        await { requestedPermissions() != null }
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), requestedPermissions())
        await { count(hasText(blockedMessage)) == 2 }
        assertFalse(exists(testScheduled))
        assertFalse("the flag is consumed", container.notificationPermissionRequest.value)

        // Only the system decides whether a dialog still shows. The app must ask again, not stop after the first time.
        val first = shadowOf(rule.activity).lastRequestedPermission
        tap(testReminder)
        await { shadowOf(rule.activity).lastRequestedPermission !== first }
        assertEquals(listOf(Manifest.permission.POST_NOTIFICATIONS), requestedPermissions())
    }

    @Test
    fun item126_positiveControlWithNotificationsOnThereIsNoRequestAndTheNormalMessage() {
        setNotificationsEnabled(true)
        openReminders()
        tap(testReminder)
        await { exists(testScheduled) }
        assertFalse(exists(blockedMessage))
        assertNull(requestedPermissions())
        assertNotNull("the test reminder is set", shadowOf(rule.activity.getSystemService(android.app.AlarmManager::class.java)).scheduledAlarms.singleOrNull())
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
    fun item126_belowAndroid13TheSnackbarShowsWithNoPermissionRequest() {
        setNotificationsEnabled(false)
        leaveAndReturn()
        openReminders()
        await { exists(blockedMessage) }
        tap(testReminder)
        await { count(hasText(blockedMessage)) == 2 }
        assertNull(requestedPermissions())
        assertFalse(container.notificationPermissionRequest.value)
    }
}
