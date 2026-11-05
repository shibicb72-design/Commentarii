package com.shibi.commentarii.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class DarkMode { SYSTEM, LIGHT, DARK }

const val DEFAULT_THEME_ID = "rose_cream"

private const val THEME_OVERRIDE_PREFIX = "theme_override_"

class SettingsRepository(private val store: DataStore<Preferences>) {

    private object Keys {
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val exportReminderEnabled = booleanPreferencesKey("export_reminder_enabled")
        val lastExportAt = longPreferencesKey("last_export_at")
        val lastOpenedDate = longPreferencesKey("last_opened_date")
        val reminderDismissedAt = longPreferencesKey("reminder_dismissed_at")
        val darkMode = stringPreferencesKey("dark_mode")
        val notificationsAsked = booleanPreferencesKey("notifications_asked")
        val themeId = stringPreferencesKey("theme_id")
        val staticThemeIds = stringSetPreferencesKey("theme_static_ids")
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

    val themeId: Flow<String> = store.data.map { it[Keys.themeId] ?: DEFAULT_THEME_ID }

    /** Ids of animated themes the user switched to "Off"; absent means Animated. */
    val staticThemeIds: Flow<Set<String>> = store.data.map { it[Keys.staticThemeIds] ?: emptySet() }

    suspend fun setThemeId(id: String) {
        store.edit { it[Keys.themeId] = id }
    }

    suspend fun setThemeAnimated(id: String, animated: Boolean) {
        store.edit {
            val current = it[Keys.staticThemeIds] ?: emptySet()
            it[Keys.staticThemeIds] = if (animated) current - id else current + id
        }
    }

    /** Raw per-theme override JSON by theme id; absent means stock. Parsing lives in ui/theme/ThemeOverride.kt. */
    val themeOverrides: Flow<Map<String, String>> = store.data.map { p ->
        p.asMap().mapNotNull { (k, v) ->
            if (k.name.startsWith(THEME_OVERRIDE_PREFIX) && v is String) k.name.removePrefix(THEME_OVERRIDE_PREFIX) to v else null
        }.toMap()
    }.distinctUntilChanged()

    /** Null or blank [json] removes the override. */
    suspend fun setThemeOverride(id: String, json: String?) {
        val key = stringPreferencesKey(THEME_OVERRIDE_PREFIX + id)
        store.edit { if (json.isNullOrBlank()) it.remove(key) else it[key] = json }
    }

    /** Back to stock in one write: drops the override and puts the theme back to Animated. */
    suspend fun resetThemeCustomization(id: String) {
        val key = stringPreferencesKey(THEME_OVERRIDE_PREFIX + id)
        store.edit {
            it.remove(key)
            val current = it[Keys.staticThemeIds] ?: emptySet()
            if (id in current) it[Keys.staticThemeIds] = current - id
        }
    }

    /** Removes every theme override (used by test resets). No write when there are none. */
    suspend fun clearThemeOverrides() {
        if (store.data.first().asMap().keys.none { it.name.startsWith(THEME_OVERRIDE_PREFIX) }) return
        store.edit { p ->
            p.asMap().keys.filter { it.name.startsWith(THEME_OVERRIDE_PREFIX) }.forEach { p.remove(stringPreferencesKey(it.name)) }
        }
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
