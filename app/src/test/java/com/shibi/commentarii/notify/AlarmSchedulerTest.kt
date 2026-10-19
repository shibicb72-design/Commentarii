package com.shibi.commentarii.notify

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.data.ScheduleEntryEntity
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmSchedulerTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 10, 18)
    private lateinit var scheduler: AlarmScheduler
    private lateinit var alarmManager: AlarmManager

    private fun entry(id: Long, minute: Int, text: String) =
        ScheduleEntryEntity(id = id, pageId = 1, date = day, minuteOfDay = minute, text = text, isCustom = false)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduler = AlarmScheduler(context, clock)
    }

    private fun count() = shadowOf(alarmManager).scheduledAlarms.size

    private val entries = listOf(
        entry(1, 8 * 60, "past"),
        entry(2, 12 * 60, "lunch"),
        entry(3, 15 * 60, "call"),
        entry(4, 16 * 60, ""),
    )

    @Test
    fun rescheduleAllRegistersOnlyFutureNonBlankEntries() {
        scheduler.rescheduleAll(entries)
        assertEquals(2, count())
        assertEquals(
            Instant.parse("2026-10-18T12:00:00Z").toEpochMilli(),
            shadowOf(alarmManager).scheduledAlarms.minOf { it.triggerAtMs },
        )
    }

    @Test
    fun rescheduleAllTwiceDoesNotDuplicate() {
        scheduler.rescheduleAll(entries)
        scheduler.rescheduleAll(entries)
        assertEquals(2, count())
    }

    @Test
    fun scheduleChangeCancelsEditedAndDeletedEntries() {
        scheduler.rescheduleAll(entries)
        // Entry 2 was blanked, entry 3 deleted, entry 5 is new.
        scheduler.onScheduleChanged(listOf(2L, 3L, 5L), listOf(entry(2, 12 * 60, ""), entry(5, 18 * 60, "dinner")))
        assertEquals(1, count())
    }
}
