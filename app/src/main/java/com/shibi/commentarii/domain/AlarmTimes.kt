package com.shibi.commentarii.domain

import com.shibi.commentarii.data.ScheduleEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

const val ALARM_LEAD_MINUTES = 10L

/** Delay used when the 10-minute lead is already gone but the entry itself is still ahead. */
const val IMMEDIATE_DELAY_SECONDS = 5L

/** How an entry relates to "now": drives both scheduling and the feedback shown to the user. */
enum class AlarmKind { SCHEDULED, IMMEDIATE, PAST, BLANK }

data class Alarm(
    val entryId: Long,
    val date: LocalDate,
    val minuteOfDay: Int,
    val text: String,
    val triggerAt: Instant,
    /** True when the alarm fires within seconds because the entry is less than 10 minutes away. */
    val immediate: Boolean = false,
)

/** The entry's own time as an absolute instant (DST-correct: gaps shift forward, overlaps use the earlier offset). */
fun entryInstant(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Instant {
    val local = LocalDateTime.of(date, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
    return ZonedDateTime.of(local, zone).toInstant()
}

/** Classifies an entry; [text] blank gives [AlarmKind.BLANK]. */
fun classifyEntry(date: LocalDate, minuteOfDay: Int, text: String, zone: ZoneId, now: Instant): AlarmKind {
    if (text.isBlank()) return AlarmKind.BLANK
    val entry = entryInstant(date, minuteOfDay, zone)
    val lead = entry.minusSeconds(ALARM_LEAD_MINUTES * 60)
    return when {
        lead.isAfter(now) -> AlarmKind.SCHEDULED
        entry.isAfter(now) -> AlarmKind.IMMEDIATE
        else -> AlarmKind.PAST
    }
}

/**
 * Entry time minus 10 minutes when that is after [now]; else [now] + 5 s when the entry itself is still ahead;
 * else null (entry time passed).
 */
fun triggerAt(date: LocalDate, minuteOfDay: Int, zone: ZoneId, now: Instant): Instant? {
    val entry = entryInstant(date, minuteOfDay, zone)
    val lead = entry.minusSeconds(ALARM_LEAD_MINUTES * 60)
    return when {
        lead.isAfter(now) -> lead
        entry.isAfter(now) -> now.plusSeconds(IMMEDIATE_DELAY_SECONDS)
        else -> null
    }
}

fun upcomingAlarms(entries: List<ScheduleEntryEntity>, now: Instant, zone: ZoneId): List<Alarm> =
    entries.mapNotNull { e ->
        val kind = classifyEntry(e.date, e.minuteOfDay, e.text, zone, now)
        if (kind != AlarmKind.SCHEDULED && kind != AlarmKind.IMMEDIATE) return@mapNotNull null
        val at = triggerAt(e.date, e.minuteOfDay, zone, now) ?: return@mapNotNull null
        Alarm(e.id, e.date, e.minuteOfDay, e.text, at, immediate = kind == AlarmKind.IMMEDIATE)
    }.sortedBy { it.triggerAt }
