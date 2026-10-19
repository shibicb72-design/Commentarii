package com.shibi.commentarii.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportReminderTest {
    private val now = Instant.parse("2026-10-18T10:00:00Z")
    private fun ago(days: Long) = now.minus(Duration.ofDays(days))

    private fun show(
        enabled: Boolean = true,
        hasData: Boolean = true,
        last: Instant? = null,
        dismissed: Instant? = null,
    ) = shouldShowExportReminder(enabled, hasData, last, dismissed, now)

    @Test fun neverExportedShows() = assertTrue(show())
    @Test fun disabledHides() = assertFalse(show(enabled = false))
    @Test fun noDataHides() = assertFalse(show(hasData = false))
    @Test fun recentExportHides() = assertFalse(show(last = ago(13)))
    @Test fun oldExportShows() = assertTrue(show(last = ago(14)))
    @Test fun exactly14DaysAgoShows() = assertTrue(show(last = ago(14)))
    @Test fun justUnder14DaysHides() = assertFalse(show(last = ago(14).plusSeconds(1)))
    @Test fun neverExportedWithoutDataHides() = assertFalse(show(hasData = false, last = null))
    @Test fun dismissedExactly14DaysAgoShows() = assertTrue(show(dismissed = ago(14)))
    @Test fun dismissedJustUnder14DaysHides() = assertFalse(show(dismissed = ago(14).plusSeconds(1)))
    @Test fun oldExportButRecentDismissHides() = assertFalse(show(last = ago(30), dismissed = ago(2)))
    @Test fun recentDismissHides() = assertFalse(show(dismissed = ago(3)))
    @Test fun oldDismissShowsAgain() = assertTrue(show(dismissed = ago(15)))
}
