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
    fun withinLeadFiresImmediatelyAndPastIsSkipped() {
        val now = Instant.parse("2026-10-18T08:50:00Z")
        assertEquals(now.plusSeconds(5), triggerAt(date, 9 * 60, utc, now))
        val late = Instant.parse("2026-10-18T08:59:59Z")
        assertEquals(late.plusSeconds(5), triggerAt(date, 9 * 60, utc, late))
        assertNull(triggerAt(date, 9 * 60, utc, Instant.parse("2026-10-18T09:00:00Z")))
        assertNull(triggerAt(date, 9 * 60, utc, Instant.parse("2026-10-18T09:30:00Z")))
        assertEquals(Instant.parse("2026-10-18T08:50:00Z"), triggerAt(date, 9 * 60, utc, now.minusSeconds(1)))
    }

    @Test
    fun exactlyTenMinutesAwayIsImmediateNotScheduled() {
        val now = Instant.parse("2026-10-18T08:50:00Z")
        assertEquals(AlarmKind.IMMEDIATE, classifyEntry(date, 9 * 60, "x", utc, now))
        assertEquals(AlarmKind.SCHEDULED, classifyEntry(date, 9 * 60, "x", utc, now.minusSeconds(1)))
    }

    @Test
    fun classifyCoversAllKinds() {
        val now = Instant.parse("2026-10-18T08:55:00Z")
        assertEquals(AlarmKind.BLANK, classifyEntry(date, 12 * 60, "  ", utc, now))
        assertEquals(AlarmKind.SCHEDULED, classifyEntry(date, 12 * 60, "a", utc, now))
        assertEquals(AlarmKind.IMMEDIATE, classifyEntry(date, 9 * 60, "a", utc, now))
        assertEquals(AlarmKind.PAST, classifyEntry(date, 8 * 60, "a", utc, now))
        assertEquals(AlarmKind.PAST, classifyEntry(date, 8 * 60 + 55, "a", utc, now))
    }

    @Test
    fun upcomingAlarmsFlagsImmediate() {
        val now = Instant.parse("2026-10-18T08:55:00Z")
        val e = ScheduleEntryEntity(id = 1, pageId = 1, date = date, minuteOfDay = 9 * 60, text = "soon")
        val a = upcomingAlarms(listOf(e), now, utc).single()
        assertEquals(true, a.immediate)
        assertEquals(now.plusSeconds(5), a.triggerAt)
    }

    @Test
    fun springForwardDayUsesRealElapsedTime() {
        // 2026-03-08 NY: at 06:30Z (01:30 EST) an entry at 03:30 EDT (07:30Z) is a real hour away -> scheduled.
        val now = Instant.parse("2026-03-08T06:30:00Z")
        val d = LocalDate.of(2026, 3, 8)
        assertEquals(AlarmKind.SCHEDULED, classifyEntry(d, 3 * 60 + 30, "x", ny, now))
        assertEquals(Instant.parse("2026-03-08T07:20:00Z"), triggerAt(d, 3 * 60 + 30, ny, now))
        // 07:25Z: 5 minutes before the entry -> immediate.
        val soon = Instant.parse("2026-03-08T07:25:00Z")
        assertEquals(soon.plusSeconds(5), triggerAt(d, 3 * 60 + 30, ny, soon))
    }

    @Test
    fun fallBackDayUsesEarlierOffsetForOverlap() {
        val d = LocalDate.of(2026, 11, 1)
        // 01:30 EDT = 05:30Z; at 05:25Z it is immediate, at 05:31Z (still 01:31 EDT) it is past.
        assertEquals(AlarmKind.IMMEDIATE, classifyEntry(d, 90, "x", ny, Instant.parse("2026-11-01T05:25:00Z")))
        assertEquals(AlarmKind.PAST, classifyEntry(d, 90, "x", ny, Instant.parse("2026-11-01T05:31:00Z")))
        // 02:00 EST (07:00Z) is an hour after the repeated 01:30 EST (06:30Z).
        assertEquals(Instant.parse("2026-11-01T06:50:00Z"), triggerAt(d, 120, ny, Instant.parse("2026-11-01T05:40:00Z")))
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
