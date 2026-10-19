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
    fun hourRowFiresAtTopOfHour() {
        assertEquals(Instant.parse("2026-10-18T09:00:00Z"), triggerAt(date, 9 * 60, utc, earlier))
    }

    @Test
    fun customTimeFiresAtExactMinute() {
        assertEquals(Instant.parse("2026-10-18T23:10:00Z"), triggerAt(date, 23 * 60 + 10, utc, earlier))
    }

    @Test
    fun pastIsSkipped() {
        val now = Instant.parse("2026-10-18T09:00:30Z")
        assertEquals(AlarmKind.PAST, classifyEntry(date, 9 * 60, "x", utc, now))
        assertNull(triggerAt(date, 9 * 60, utc, now))
        // The exact minute already started: strict comparison, no grace window.
        assertNull(triggerAt(date, 9 * 60, utc, Instant.parse("2026-10-18T09:00:00Z")))
    }

    @Test
    fun oneMinuteAheadIsScheduled() {
        val now = Instant.parse("2026-10-18T08:59:00Z")
        assertEquals(AlarmKind.SCHEDULED, classifyEntry(date, 9 * 60, "x", utc, now))
        assertEquals(Instant.parse("2026-10-18T09:00:00Z"), triggerAt(date, 9 * 60, utc, now))
    }

    @Test
    fun blankSkipped() {
        val now = Instant.parse("2026-10-18T08:00:00Z")
        assertEquals(AlarmKind.BLANK, classifyEntry(date, 9 * 60, "  ", utc, now))
        val e = ScheduleEntryEntity(id = 1, pageId = 1, date = date, minuteOfDay = 9 * 60, text = " ")
        assertEquals(emptyList<Alarm>(), upcomingAlarms(listOf(e), now, utc))
    }

    @Test
    fun springForwardGapShiftsForward() {
        // 02:30 does not exist on 2026-03-08 in New York; it shifts to 03:30 EDT (07:30Z).
        val t = triggerAt(LocalDate.of(2026, 3, 8), 2 * 60 + 30, ny, earlier)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), t)
    }

    @Test
    fun fallBackOverlapUsesEarlierOffset() {
        // 01:30 occurs twice on 2026-11-01; earlier is EDT (05:30Z). At 05:31Z it is past.
        val d = LocalDate.of(2026, 11, 1)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), triggerAt(d, 90, ny, earlier))
        assertEquals(AlarmKind.PAST, classifyEntry(d, 90, "x", ny, Instant.parse("2026-11-01T05:31:00Z")))
        assertNull(triggerAt(d, 90, ny, Instant.parse("2026-11-01T05:31:00Z")))
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
        assertEquals(Instant.parse("2026-10-18T07:00:00Z"), alarms[0].triggerAt)
        assertEquals("early", alarms[0].text)
    }
}
