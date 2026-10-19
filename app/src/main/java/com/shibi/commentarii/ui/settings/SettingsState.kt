package com.shibi.commentarii.ui.settings

import com.shibi.commentarii.data.DarkMode
import java.time.Instant
import java.time.LocalDate

/** One-shot result shown as a snackbar. */
enum class SettingsMessage { EXPORT_OK, EXPORT_FAILED, IMPORT_OK, IMPORT_BAD_FORMAT, IMPORT_FAILED, TEST_SCHEDULED, NOTIFICATIONS_OFF }

data class SettingsUiState(
    val lockEnabled: Boolean = false,
    /** False when exact alarms are possible but not allowed; Settings then explains and offers the system screen. */
    val exactAlarmGranted: Boolean = true,
    val notificationsBlocked: Boolean = false,
    val batteryUnrestricted: Boolean = true,
    val exportReminderEnabled: Boolean = true,
    val lastExportAt: Instant? = null,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val licenseText: String = "",
    val message: SettingsMessage? = null,
)

data class SettingsCallbacks(
    val onLockChange: (Boolean) -> Unit = {},
    val onReminderChange: (Boolean) -> Unit = {},
    val onRequestExactAlarm: () -> Unit = {},
    val onOpenNotificationSettings: () -> Unit = {},
    val onOpenBatterySettings: () -> Unit = {},
    val onSendTestReminder: () -> Unit = {},
    val onOpenDebug: () -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onDarkMode: (DarkMode) -> Unit = {},
)

fun backupFileName(date: LocalDate): String = "commentarii-backup-$date.json"
