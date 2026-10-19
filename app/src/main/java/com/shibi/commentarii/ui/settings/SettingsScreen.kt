package com.shibi.commentarii.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.ui.daily.AddRowButton
import com.shibi.commentarii.ui.daily.SectionCard
import com.shibi.commentarii.ui.daily.SmallIconButton

private const val LICENSE_ASSET = "licenses/DancingScript-OFL.txt"

/** Settings wired to [SettingsViewModel]: SAF launchers, import confirmation and snackbar. */
@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val licenseText = remember {
        runCatching { context.assets.open(LICENSE_ASSET).bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container, licenseText))
    val state by vm.state.collectAsStateWithLifecycle()
    val resolver = context.contentResolver
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) vm.exportTo(uri, resolver) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingImport = uri }

    val callbacks = remember(vm) {
        vm.buildCallbacks(
            onExport = { exportLauncher.launch(vm.suggestedFileName()) },
            onImport = { importLauncher.launch(arrayOf("*/*")) },
        )
    }

    val messageText = state.message?.let { stringResource(messageRes(it)) }
    LaunchedEffect(state.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            vm.messageShown()
        }
    }

    Box(modifier.fillMaxSize()) {
        SettingsContent(state, callbacks)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    pendingImport?.let { uri ->
        ImportConfirmDialog(
            onConfirm = {
                pendingImport = null
                vm.importFrom(uri, resolver)
            },
            onDismiss = { pendingImport = null },
        )
    }
}

private fun messageRes(m: SettingsMessage): Int = when (m) {
    SettingsMessage.EXPORT_OK -> R.string.settings_msg_export_ok
    SettingsMessage.EXPORT_FAILED -> R.string.settings_msg_export_failed
    SettingsMessage.IMPORT_OK -> R.string.settings_msg_import_ok
    SettingsMessage.IMPORT_BAD_FORMAT -> R.string.settings_msg_import_bad_format
    SettingsMessage.IMPORT_FAILED -> R.string.settings_msg_import_failed
}

/** Stateless settings page; safe to render with fake state. */
@Composable
fun SettingsContent(state: SettingsUiState, callbacks: SettingsCallbacks, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.displaySmall)
            SectionCard(stringResource(R.string.settings_section_privacy)) {
                SwitchRow(
                    stringResource(R.string.settings_lock),
                    stringResource(R.string.settings_lock_hint),
                    state.lockEnabled,
                    callbacks.onLockChange,
                )
            }
            SectionCard(stringResource(R.string.settings_section_backup)) {
                SwitchRow(
                    stringResource(R.string.settings_export_reminder),
                    stringResource(R.string.settings_export_reminder_hint),
                    state.exportReminderEnabled,
                    callbacks.onReminderChange,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = callbacks.onExport) { Text(stringResource(R.string.settings_export)) }
                    OutlinedButton(onClick = callbacks.onImport) { Text(stringResource(R.string.settings_import)) }
                }
                Text(
                    if (state.lastExportAt == null) {
                        stringResource(R.string.settings_never_exported)
                    } else {
                        stringResource(R.string.settings_last_export, state.lastExportAt.toString().take(10))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            SectionCard(stringResource(R.string.settings_section_theme)) {
                DarkMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = state.darkMode == mode, role = Role.RadioButton) {
                                callbacks.onDarkMode(mode)
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.darkMode == mode, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(stringResource(modeLabel(mode)))
                    }
                }
            }
            SectionCard(stringResource(R.string.section_self_care)) {
                SelfCareEditor(state.selfCare, callbacks)
            }
            SectionCard(stringResource(R.string.settings_section_licences)) {
                LicencesBlock(state.licenseText)
            }
        }
    }
}

private fun modeLabel(m: DarkMode): Int = when (m) {
    DarkMode.SYSTEM -> R.string.settings_theme_system
    DarkMode.LIGHT -> R.string.settings_theme_light
    DarkMode.DARK -> R.string.settings_theme_dark
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SelfCareEditor(items: List<SelfCareEditUi>, callbacks: SettingsCallbacks) {
    var renaming by remember { mutableStateOf<SelfCareEditUi?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    items.forEachIndexed { i, item ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            SmallIconButton(
                Icons.Filled.KeyboardArrowUp,
                stringResource(R.string.daily_move_up),
                { callbacks.onMoveSelfCare(item.id, -1) },
                enabled = i > 0,
            )
            SmallIconButton(
                Icons.Filled.KeyboardArrowDown,
                stringResource(R.string.daily_move_down),
                { callbacks.onMoveSelfCare(item.id, 1) },
                enabled = i < items.lastIndex,
            )
            SmallIconButton(Icons.Filled.Edit, stringResource(R.string.daily_rename), { renaming = item })
            SmallIconButton(
                Icons.Filled.Delete,
                stringResource(R.string.daily_remove),
                { callbacks.onRemoveSelfCare(item.id) },
            )
        }
    }
    AddRowButton(stringResource(R.string.settings_add_self_care), { adding = true })

    renaming?.let { item ->
        NameDialog(
            title = stringResource(R.string.daily_rename),
            initial = item.name,
            onConfirm = {
                callbacks.onRenameSelfCare(item.id, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    if (adding) {
        NameDialog(
            title = stringResource(R.string.settings_add_self_care),
            initial = "",
            onConfirm = {
                callbacks.onAddSelfCare(it)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.daily_name_hint)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun LicencesBlock(text: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    Text(stringResource(R.string.settings_licence_font), style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = { open = !open }) {
        Text(stringResource(if (open) R.string.settings_licence_hide else R.string.settings_licence_show))
    }
    if (open) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
    }
}

/** Import replaces everything, so it needs an explicit confirmation. */
@Composable
fun ImportConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_import_title)) },
        text = { Text(stringResource(R.string.settings_import_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_import_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
