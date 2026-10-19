package com.shibi.commentarii.notify

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.data.ScheduleEntryEntity
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BootReceiverTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var app: CommentariiApp
    private lateinit var tomorrow: LocalDate

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        app = context as CommentariiApp
        alarmManager = context.getSystemService(AlarmManager::class.java)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val container = app.container
        val today = LocalDate.now(container.clock)
        tomorrow = today.plusDays(1)
        runBlocking {
            val dao = container.db.pageDao()
            val yesterdayPage = container.pages.getOrCreateDaily(today.minusDays(1))
            val tomorrowPage = container.pages.getOrCreateDaily(tomorrow)
            // Inserted through the DAO so nothing is registered before the receiver runs.
            dao.insertSchedule(
                listOf(
                    entry(yesterdayPage.id, today.minusDays(1), 10 * 60 + 30, "past"),
                    entry(tomorrowPage.id, tomorrow, 10 * 60 + 30, "future"),
                    entry(tomorrowPage.id, tomorrow, 11 * 60 + 30, ""),
                ),
            )
        }
    }

    private fun entry(pageId: Long, date: LocalDate, minute: Int, text: String) =
        ScheduleEntryEntity(pageId = pageId, date = date, minuteOfDay = minute, text = text)

    private fun scheduled() = shadowOf(alarmManager).scheduledAlarms

    private fun send(action: String): BootReceiver {
        val receiver = BootReceiver()
        receiver.onReceive(context, Intent(action))
        runBlocking { receiver.job?.join() }
        return receiver
    }

    private fun expectedTrigger(): Long =
        tomorrow.atTime(LocalTime.of(10, 30)).atZone(app.container.zone).toInstant().toEpochMilli()

    @Test
    fun bootCompletedRegistersOnlyFutureNonBlankEntries() {
        assertEquals(0, scheduled().size)
        send(Intent.ACTION_BOOT_COMPLETED)
        assertEquals(1, scheduled().size)
        assertEquals(expectedTrigger(), scheduled().single().triggerAtMs)
    }

    @Test
    fun timezoneChangeRegistersAlarmsWithoutDuplicates() {
        send(Intent.ACTION_TIMEZONE_CHANGED)
        send(Intent.ACTION_TIMEZONE_CHANGED)
        assertEquals(1, scheduled().size)
        assertEquals(expectedTrigger(), scheduled().single().triggerAtMs)
    }

    @Test
    fun exactAlarmStateChangeReschedulesAndLogsReason() {
        send(BootReceiver.ACTION_EXACT_ALARM_STATE_CHANGED)
        assertEquals(1, scheduled().size)
        val log = app.container.debugLog.readAll()
        assertEquals(true, log.contains("RESCHEDULE_ALL reason=${BootReceiver.ACTION_EXACT_ALARM_STATE_CHANGED}"))
    }

    @Test
    fun appStartReschedulesAlarms() {
        assertEquals(0, scheduled().size)
        runBlocking { app.container.onAppStart().join() }
        assertEquals(1, scheduled().size)
        assertEquals(expectedTrigger(), scheduled().single().triggerAtMs)
        assertEquals(true, app.container.debugLog.readAll().contains("RESCHEDULE_ALL reason=app resume"))
    }

    @Test
    fun unrelatedActionIsIgnored() {
        val receiver = send(Intent.ACTION_BATTERY_LOW)
        assertNull(receiver.job)
        assertEquals(0, scheduled().size)
    }
}
