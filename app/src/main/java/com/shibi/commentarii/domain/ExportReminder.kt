package com.shibi.commentarii.domain

import java.time.Duration
import java.time.Instant

const val EXPORT_REMINDER_DAYS = 14L
private val REMINDER_INTERVAL: Duration = Duration.ofDays(EXPORT_REMINDER_DAYS)

/**
 * Whether the "back up your data" banner shows: reminder enabled, there is data to lose, the last export is missing
 * or at least [EXPORT_REMINDER_DAYS] old, and the banner was not dismissed within the last [EXPORT_REMINDER_DAYS].
 */
fun shouldShowExportReminder(
    enabled: Boolean,
    hasData: Boolean,
    lastExportAt: Instant?,
    dismissedAt: Instant?,
    now: Instant,
): Boolean {
    if (!enabled || !hasData) return false
    if (lastExportAt != null && Duration.between(lastExportAt, now) < REMINDER_INTERVAL) return false
    if (dismissedAt != null && Duration.between(dismissedAt, now) < REMINDER_INTERVAL) return false
    return true
}
