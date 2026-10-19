package com.shibi.commentarii.ui.settings

import com.shibi.commentarii.data.DarkMode
import java.time.Instant
import java.time.LocalDate

data class SelfCareEditUi(val id: Long, val name: String)

/** One-shot result shown as a snackbar. */
enum class SettingsMessage { EXPORT_OK, EXPORT_FAILED, IMPORT_OK, IMPORT_BAD_FORMAT, IMPORT_FAILED }

data class SettingsUiState(
    val lockEnabled: Boolean = false,
    val exportReminderEnabled: Boolean = true,
    val lastExportAt: Instant? = null,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val selfCare: List<SelfCareEditUi> = emptyList(),
    val licenseText: String = "",
    val message: SettingsMessage? = null,
)

data class SettingsCallbacks(
    val onLockChange: (Boolean) -> Unit = {},
    val onReminderChange: (Boolean) -> Unit = {},
    val onExport: () -> Unit = {},
    val onImport: () -> Unit = {},
    val onDarkMode: (DarkMode) -> Unit = {},
    val onAddSelfCare: (String) -> Unit = {},
    val onRenameSelfCare: (Long, String) -> Unit = { _, _ -> },
    val onRemoveSelfCare: (Long) -> Unit = {},
    /** [delta] is -1 (up) or +1 (down). */
    val onMoveSelfCare: (Long, Int) -> Unit = { _, _ -> },
)

fun backupFileName(date: LocalDate): String = "commentarii-backup-$date.json"

/** Ids in their new order after moving [id] by [delta]; unchanged if it would leave the list. */
fun movedIds(ids: List<Long>, id: Long, delta: Int): List<Long> {
    val i = ids.indexOf(id)
    val j = i + delta
    if (i < 0 || j !in ids.indices) return ids
    return ids.toMutableList().apply { add(j, removeAt(i)) }
}
