package com.shibi.commentarii.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.backup.BackupFormatException
import com.shibi.commentarii.data.DarkMode
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val container: AppContainer,
    licenseText: String,
) : ViewModel() {
    private val settings = container.settings
    private val selfCare = container.selfCare
    private val message = MutableStateFlow<SettingsMessage?>(null)

    private val base = combine(
        settings.lockEnabled,
        settings.exportReminderEnabled,
        settings.lastExportAt,
        settings.darkMode,
        selfCare.observeItems(),
    ) { lock, reminder, last, dark, items ->
        SettingsUiState(
            lockEnabled = lock,
            exportReminderEnabled = reminder,
            lastExportAt = last,
            darkMode = dark,
            selfCare = items.map { SelfCareEditUi(it.id, it.name) },
            licenseText = licenseText,
        )
    }

    val state: StateFlow<SettingsUiState> = combine(base, message) { s, m -> s.copy(message = m) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(licenseText = licenseText))

    fun suggestedFileName(): String = backupFileName(LocalDate.now(container.clock))

    fun messageShown() {
        message.value = null
    }

    fun buildCallbacks(onExport: () -> Unit, onImport: () -> Unit) = SettingsCallbacks(
        // Phase C: BiometricPrompt gating (auth before enabling, lock on start/resume) hooks in here. For now this
        // only persists the preference.
        onLockChange = { v -> viewModelScope.launch { settings.setLockEnabled(v) } },
        onReminderChange = { v -> viewModelScope.launch { settings.setExportReminderEnabled(v) } },
        onExport = onExport,
        onImport = onImport,
        onDarkMode = { m: DarkMode -> viewModelScope.launch { settings.setDarkMode(m) } },
        onAddSelfCare = { name -> if (name.isNotBlank()) viewModelScope.launch { selfCare.addItem(name) } },
        onRenameSelfCare = { id, name -> if (name.isNotBlank()) viewModelScope.launch { selfCare.renameItem(id, name) } },
        onRemoveSelfCare = { id -> viewModelScope.launch { selfCare.removeItem(id) } },
        onMoveSelfCare = { id, delta ->
            val ids = state.value.selfCare.map { it.id }
            val moved = movedIds(ids, id, delta)
            if (moved != ids) viewModelScope.launch { selfCare.reorder(moved) }
        },
    )

    fun exportTo(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            message.value = try {
                val json = container.exportImport.export()
                withContext(Dispatchers.IO) {
                    val out = resolver.openOutputStream(uri, "wt") ?: error("Cannot open output")
                    out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                settings.setLastExportAt(container.clock.instant())
                SettingsMessage.EXPORT_OK
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                SettingsMessage.EXPORT_FAILED
            }
        }
    }

    fun importFrom(uri: Uri, resolver: ContentResolver) {
        viewModelScope.launch {
            message.value = try {
                val json = withContext(Dispatchers.IO) {
                    val input = resolver.openInputStream(uri) ?: error("Cannot open input")
                    input.use { String(it.readBytes(), Charsets.UTF_8) }
                }
                container.exportImport.import(json)
                SettingsMessage.IMPORT_OK
            } catch (e: CancellationException) {
                throw e
            } catch (_: BackupFormatException) {
                SettingsMessage.IMPORT_BAD_FORMAT
            } catch (_: Exception) {
                SettingsMessage.IMPORT_FAILED
            }
        }
    }

    companion object {
        fun factory(container: AppContainer, licenseText: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(container, licenseText) as T
            }
    }
}
