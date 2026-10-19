package com.shibi.commentarii.domain

import com.shibi.commentarii.data.ScheduleEntryEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

const val ALARM_LEAD_MINUTES = 10L

data class Alarm(
    val entryId: Long,
    val date: LocalDate,
    val minuteOfDay: Int,
    val text: String,
    val triggerAt: Instant,
)

/** Entry time minus 10 minutes (absolute time), or null if that is not after [now]. */
fun triggerAt(date: LocalDate, minuteOfDay: Int, zone: ZoneId, now: Instant): Instant? {
    val local = LocalDateTime.of(date, LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
    val trigger = ZonedDateTime.of(local, zone).minusMinutes(ALARM_LEAD_MINUTES).toInstant()
    return if (trigger.isAfter(now)) trigger else null
}

fun upcomingAlarms(entries: List<ScheduleEntryEntity>, now: Instant, zone: ZoneId): List<Alarm> =
    entries.mapNotNull { e ->
        if (e.text.isBlank()) return@mapNotNull null
        val at = triggerAt(e.date, e.minuteOfDay, zone, now) ?: return@mapNotNull null
        Alarm(e.id, e.date, e.minuteOfDay, e.text, at)
    }.sortedBy { it.triggerAt }
