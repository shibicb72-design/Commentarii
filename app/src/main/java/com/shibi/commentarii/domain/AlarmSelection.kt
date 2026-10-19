package com.shibi.commentarii.domain

import com.shibi.commentarii.data.ScheduleEntryEntity
import java.time.Instant
import java.time.ZoneId

/**
 * The alarms to register for [entries]: saved entries (id != 0) with non-blank text whose entry time is still ahead,
 * one per entry id and one per identical (trigger time, text) pair.
 */
fun selectAlarms(entries: List<ScheduleEntryEntity>, now: Instant, zone: ZoneId): List<Alarm> =
    upcomingAlarms(entries.filter { it.id != 0L }, now, zone)
        .distinctBy { it.entryId }
        .distinctBy { it.triggerAt to it.text.trim() }
