package com.shibi.commentarii.domain

import com.shibi.commentarii.data.ScheduleEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** How an entry relates to "now": drives both scheduling and the feedback shown to the user. */
enum class AlarmKind { SCHEDULED, PAST, BLANK }

data class Alarm(
    val entryId: Long,
    val date: LocalDate,
    val minuteOfDay: Int,
    val text: String,
    val triggerAt: Instant,
)

/** The entry's own time as an absolute instant (DST-correct: gaps shift forward, overlaps use the earlier offset). */
fun entryInstant(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Instant {
    val local = LocalDateTime.of(date, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
    return ZonedDateTime.of(local, zone).toInstant()
}

/** Classifies an entry; [text] blank gives [AlarmKind.BLANK]; an entry at or before [now] is PAST. */
fun classifyEntry(date: LocalDate, minuteOfDay: Int, text: String, zone: ZoneId, now: Instant): AlarmKind {
    if (text.isBlank()) return AlarmKind.BLANK
    return if (entryInstant(date, minuteOfDay, zone).isAfter(now)) AlarmKind.SCHEDULED else AlarmKind.PAST
}

/** The entry's own instant when it is still ahead of [now]; null when it has passed. */
fun triggerAt(date: LocalDate, minuteOfDay: Int, zone: ZoneId, now: Instant): Instant? =
    entryInstant(date, minuteOfDay, zone).takeIf { it.isAfter(now) }

fun upcomingAlarms(entries: List<ScheduleEntryEntity>, now: Instant, zone: ZoneId): List<Alarm> =
    entries.mapNotNull { e ->
        if (classifyEntry(e.date, e.minuteOfDay, e.text, zone, now) != AlarmKind.SCHEDULED) return@mapNotNull null
        val at = triggerAt(e.date, e.minuteOfDay, zone, now) ?: return@mapNotNull null
        Alarm(e.id, e.date, e.minuteOfDay, e.text, at)
    }.sortedBy { it.triggerAt }
