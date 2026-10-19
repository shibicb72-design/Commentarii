package com.shibi.commentarii.domain

import com.shibi.commentarii.data.ScheduleEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmTimesTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val ny = ZoneId.of("America/New_York")
    private val date = LocalDate.of(2026, 10, 18)
    private val earlier = Instant.parse("2026-01-01T00:00:00Z")

    @Test
    fun tenMinutesBefore() {
        assertEquals(Instant.parse("2026-10-18T08:50:00Z"), triggerAt(date, 9 * 60, utc, earlier))
    }

    @Test
    fun customTime() {
        assertEquals(Instant.parse("2026-10-18T07:25:00Z"), triggerAt(date, 7 * 60 + 35, utc, earlier))
    }

    @Test
    fun pastSkipped() {
        val now = Instant.parse("2026-10-18T08:50:00Z")
        assertNull(triggerAt(date, 9 * 60, utc, now))
        assertNull(triggerAt(date, 9 * 60, utc, now.plusSeconds(1)))
        assertEquals(now, triggerAt(date, 9 * 60, utc, now.minusSeconds(1)))
    }

    @Test
    fun springForwardGapShiftsForward() {
        // 02:30 does not exist on 2026-03-08 in New York; ZonedDateTime.of shifts it to 03:30 EDT (07:30Z).
        val t = triggerAt(LocalDate.of(2026, 3, 8), 2 * 60 + 30, ny, earlier)
        assertEquals(Instant.parse("2026-03-08T07:20:00Z"), t)
    }

    @Test
    fun fallBackOverlapUsesEarlierOffset() {
        // 01:30 occurs twice on 2026-11-01; earlier is EDT (05:30Z).
        val t = triggerAt(LocalDate.of(2026, 11, 1), 90, ny, earlier)
        assertEquals(Instant.parse("2026-11-01T05:20:00Z"), t)
    }

    @Test
    fun blankTextSkippedAndSortedByTrigger() {
        val entries = listOf(
            ScheduleEntryEntity(id = 1, pageId = 1, date = date, minuteOfDay = 10 * 60, text = "later"),
            ScheduleEntryEntity(id = 2, pageId = 1, date = date, minuteOfDay = 8 * 60, text = "  "),
            ScheduleEntryEntity(id = 3, pageId = 1, date = date, minuteOfDay = 7 * 60, text = "early"),
            ScheduleEntryEntity(id = 4, pageId = 1, date = date, minuteOfDay = 6 * 60, text = "past"),
        )
        val now = Instant.parse("2026-10-18T06:30:00Z")
        val alarms = upcomingAlarms(entries, now, utc)
        assertEquals(listOf(3L, 1L), alarms.map { it.entryId })
        assertEquals(Instant.parse("2026-10-18T06:50:00Z"), alarms[0].triggerAt)
        assertEquals("early", alarms[0].text)
    }

    @Test
    fun justAfterMidnightTriggersPreviousDay() {
        val t = triggerAt(date, 5, utc, earlier)
        assertEquals(Instant.parse("2026-10-17T23:55:00Z"), t)
    }
}
