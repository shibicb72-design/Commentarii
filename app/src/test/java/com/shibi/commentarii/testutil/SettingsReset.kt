package com.shibi.commentarii.testutil

import com.shibi.commentarii.data.DEFAULT_THEME_ID
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.data.SettingsRepository
import kotlinx.coroutines.runBlocking

/**
 * Puts the real container's settings back to their defaults. That DataStore is a process-wide singleton and leaks across
 * test methods and classes, so call this in both @Before and @After of any test that goes through the real container.
 */
fun SettingsRepository.resetToDefaults() = runBlocking {
    setLastOpenedDate(null)
    setLastExportAt(null)
    setReminderDismissedAt(null)
    setExportReminderEnabled(true)
    setLockEnabled(false)
    setNotificationsAsked(false)
    setThemeId(DEFAULT_THEME_ID)
    setDarkMode(DarkMode.SYSTEM)
}
