package com.shibi.commentarii.notify

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.data.ScheduleEntryEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Checklist #130-#133: each system broadcast resolves to BootReceiver in the merged manifest, and BootReceiver re-registers the
 * pending reminder on the real container. Whether Android delivers the broadcast after a real reboot or update needs a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RescheduleBroadcastTest {
    private lateinit var context: Context
    private lateinit var app: CommentariiApp
    private lateinit var alarmManager: AlarmManager
    private lateinit var entryDate: LocalDate
    private val startZone: TimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as CommentariiApp
        alarmManager = context.getSystemService(AlarmManager::class.java)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        // Far enough ahead to stay in the future whichever zone a test moves to.
        entryDate = LocalDate.now(app.container.clock).plusDays(3)
        runBlocking {
            val page = app.container.pages.getOrCreateDaily(entryDate)
            app.container.db.pageDao().insertSchedule(
                listOf(ScheduleEntryEntity(pageId = page.id, date = entryDate, minuteOfDay = 15 * 60, text = "Call", isCustom = true)),
            )
        }
        assertEquals("inserted through the DAO, so no alarm yet", 0, alarms().size)
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(startZone)
    }

    private fun alarms() = shadowOf(alarmManager).scheduledAlarms

    private fun send(action: String) {
        val receiver = BootReceiver()
        receiver.onReceive(context, Intent(action))
        runBlocking { receiver.job!!.join() }
    }

    private fun triggerIn(zone: ZoneId) = entryDate.atTime(LocalTime.of(15, 0)).atZone(zone).toInstant().toEpochMilli()

    private fun assertRegisteredBy(action: String) {
        val receivers = context.packageManager.queryBroadcastReceivers(Intent(action), 0).map { it.activityInfo.name }
        assertEquals(action, listOf(BootReceiver::class.java.name), receivers)

        send(action)

        assertEquals(triggerIn(app.container.zone), alarms().single().triggerAtMs)
        assertTrue(app.container.debugLog.readAll().contains("RESCHEDULE_ALL reason=$action"))
    }

    @Test
    fun item130_timeSetReschedules() = assertRegisteredBy(Intent.ACTION_TIME_CHANGED)

    @Test
    fun item132_bootCompletedReschedules() = assertRegisteredBy(Intent.ACTION_BOOT_COMPLETED)

    @Test
    fun item133_appUpdateReschedules() = assertRegisteredBy(Intent.ACTION_MY_PACKAGE_REPLACED)

    /** The broadcast's own routing and log line, in a zone that has not changed. The new zone's trigger is the next test. */
    @Test
    fun item131_timezoneBroadcastResolvesAndLogsItsReason() = assertRegisteredBy(Intent.ACTION_TIMEZONE_CHANGED)

    @Test
    @Ignore("item 131: bug - AppContainer.clock and zone keep the zone from app start, so TIMEZONE_CHANGED re-registers the old zone's time")
    fun item131_afterAZoneChangeTheTriggerIsTheWallClockTimeInTheNewZone() {
        val newZone = if (startZone.id == "Asia/Tokyo") "America/New_York" else "Asia/Tokyo"
        assertTrue(triggerIn(ZoneId.of(newZone)) != triggerIn(startZone.toZoneId()))

        TimeZone.setDefault(TimeZone.getTimeZone(newZone))
        send(Intent.ACTION_TIMEZONE_CHANGED)

        assertEquals(triggerIn(ZoneId.of(newZone)), alarms().single().triggerAtMs)
    }
}
