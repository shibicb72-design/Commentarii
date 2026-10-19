package com.shibi.commentarii.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class DarkMode { SYSTEM, LIGHT, DARK }

class SettingsRepository(private val store: DataStore<Preferences>) {

    private object Keys {
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val exportReminderEnabled = booleanPreferencesKey("export_reminder_enabled")
        val lastExportAt = longPreferencesKey("last_export_at")
        val lastOpenedDate = longPreferencesKey("last_opened_date")
        val reminderDismissedAt = longPreferencesKey("reminder_dismissed_at")
        val darkMode = stringPreferencesKey("dark_mode")
        val notificationsAsked = booleanPreferencesKey("notifications_asked")
    }

    val lockEnabled: Flow<Boolean> = store.data.map { it[Keys.lockEnabled] ?: false }
    val exportReminderEnabled: Flow<Boolean> = store.data.map { it[Keys.exportReminderEnabled] ?: true }
    val lastExportAt: Flow<Instant?> = store.data.map { p -> p[Keys.lastExportAt]?.let { Instant.ofEpochMilli(it) } }
    val lastOpenedDate: Flow<LocalDate?> = store.data.map { p -> p[Keys.lastOpenedDate]?.let { LocalDate.ofEpochDay(it) } }
    val reminderDismissedAt: Flow<Instant?> =
        store.data.map { p -> p[Keys.reminderDismissedAt]?.let { Instant.ofEpochMilli(it) } }
    val darkMode: Flow<DarkMode> = store.data.map { p ->
        p[Keys.darkMode]?.let { runCatching { DarkMode.valueOf(it) }.getOrNull() } ?: DarkMode.SYSTEM
    }

    /** True once the POST_NOTIFICATIONS prompt has been shown (it is only requested once, lazily). */
    val notificationsAsked: Flow<Boolean> = store.data.map { it[Keys.notificationsAsked] ?: false }

    suspend fun setNotificationsAsked(value: Boolean) {
        store.edit { it[Keys.notificationsAsked] = value }
    }

    suspend fun setLockEnabled(value: Boolean) {
        store.edit { it[Keys.lockEnabled] = value }
    }

    suspend fun setExportReminderEnabled(value: Boolean) {
        store.edit { it[Keys.exportReminderEnabled] = value }
    }

    suspend fun setLastExportAt(value: Instant?) {
        store.edit { if (value == null) it.remove(Keys.lastExportAt) else it[Keys.lastExportAt] = value.toEpochMilli() }
    }

    suspend fun setLastOpenedDate(value: LocalDate?) {
        store.edit { if (value == null) it.remove(Keys.lastOpenedDate) else it[Keys.lastOpenedDate] = value.toEpochDay() }
    }

    suspend fun setReminderDismissedAt(value: Instant?) {
        store.edit {
            if (value == null) it.remove(Keys.reminderDismissedAt) else it[Keys.reminderDismissedAt] = value.toEpochMilli()
        }
    }

    suspend fun setDarkMode(value: DarkMode) {
        store.edit { it[Keys.darkMode] = value.name }
    }
}
