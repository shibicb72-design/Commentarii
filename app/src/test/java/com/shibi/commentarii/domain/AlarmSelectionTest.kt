package com.shibi.commentarii.domain

import com.shibi.commentarii.data.ScheduleEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSelectionTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 10, 18)
    private val now = Instant.parse("2026-10-18T09:00:00Z")

    private fun entry(id: Long, minute: Int, text: String, date: LocalDate = day) =
        ScheduleEntryEntity(id = id, pageId = 1, date = date, minuteOfDay = minute, text = text, isCustom = false)

    @Test
    fun keepsOnlyFutureNonBlankSavedEntries() {
        val entries = listOf(
            entry(1, 8 * 60, "past"),
            entry(2, 12 * 60, "lunch"),
            entry(3, 13 * 60, "   "),
            entry(0, 14 * 60, "unsaved"),
            entry(4, 9 * 60 + 5, "too soon"), // five minutes ahead: scheduled at 9:05, sorted first
            entry(5, 1 * 60, "tomorrow", day.plusDays(1)),
        )
        assertEquals(listOf(4L, 2L, 5L), selectAlarms(entries, now, zone).map { it.entryId })
    }

    @Test
    fun dedupesSameIdAndSameTimeAndText() {
        val entries = listOf(
            entry(2, 12 * 60, "lunch"),
            entry(2, 12 * 60, "lunch"),
            entry(3, 12 * 60, "lunch "),
            entry(4, 12 * 60, "other"),
        )
        assertEquals(listOf(2L, 4L), selectAlarms(entries, now, zone).map { it.entryId })
    }

    @Test
    fun triggersAtEntryTime() {
        val a = selectAlarms(listOf(entry(2, 12 * 60, "lunch")), now, zone).single()
        assertEquals(Instant.parse("2026-10-18T12:00:00Z"), a.triggerAt)
    }
}
