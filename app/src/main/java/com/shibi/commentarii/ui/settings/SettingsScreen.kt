package com.shibi.commentarii.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.fragment.app.FragmentActivity
import com.shibi.commentarii.ui.lock.BiometricAuth
import com.shibi.commentarii.ui.lock.SecuritySettings
import com.shibi.commentarii.ui.lock.requestLockChange
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.shibi.commentarii.ui.theme.AppTheme
import com.shibi.commentarii.ui.theme.AppThemes
import com.shibi.commentarii.ui.theme.systemAnimationsEnabled
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.shibi.commentarii.ui.daily.SectionCard

private const val LICENSE_ASSET = "licenses/DancingScript-OFL.txt"

/** Settings wired to [SettingsViewModel]: SAF launchers, import confirmation and snackbar. */
@Composable
fun SettingsScreen(container: AppContainer, modifier: Modifier = Modifier, onOpenDebug: () -> Unit = {}) {
    val context = LocalContext.current
    val licenseText = remember {
        runCatching { context.assets.open(LICENSE_ASSET).bufferedReader().use { it.readText() } }.getOrDefault("")
    }
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container, licenseText))
    val state by vm.state.collectAsStateWithLifecycle()
    val resolver = context.contentResolver
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var showLockHelp by remember { mutableStateOf(false) }

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
        ).copy(
            onLockChange = { enable ->
                val activity = context.findFragmentActivity()
                requestLockChange(
                    enable = enable,
                    available = activity != null && BiometricAuth.isAvailable(activity),
                    // Turning the lock on or off both need a successful authentication first.
                    authenticate = { done ->
                        if (activity != null) {
                            BiometricAuth.authenticate(activity, onSuccess = { done(true) }, onFailure = { done(false) })
                        }
                    },
                    apply = { value ->
                        if (value) container.lock.unlock()
                        vm.setLockEnabled(value)
                    },
                    onUnavailable = { showLockHelp = true },
                )
            },
            onOpenNotificationSettings = { openNotificationSettings(context) },
            onOpenBatterySettings = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
            onSendTestReminder = vm::sendTestReminder,
            onOpenDebug = onOpenDebug,
            onRequestExactAlarm = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }
            },
        )
    }
    LifecycleResumeEffect(vm) {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        vm.refreshReminderStatus(power.isIgnoringBatteryOptimizations(context.packageName))
        onPauseOrDispose { }
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

    if (showLockHelp) {
        AlertDialog(
            onDismissRequest = { showLockHelp = false },
            title = { Text(stringResource(R.string.lock_unavailable_title)) },
            text = { Text(stringResource(R.string.lock_unavailable_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    showLockHelp = false
                    SecuritySettings.open(context)
                }) { Text(stringResource(R.string.lock_open_security_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { showLockHelp = false }) { Text(stringResource(R.string.lock_dialog_close)) }
            },
        )
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
    SettingsMessage.TEST_SCHEDULED -> R.string.settings_msg_test_scheduled
    SettingsMessage.NOTIFICATIONS_OFF -> R.string.notif_blocked_message
}

/** Opens this app's system notification settings page. */
fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
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
            SectionCard(stringResource(R.string.settings_section_reminders)) {
                if (state.notificationsBlocked) {
                    NotificationsBlockedMessage(callbacks.onOpenNotificationSettings)
                }
                if (state.exactAlarmGranted) {
                    Text(
                        stringResource(R.string.settings_exact_alarm_ok),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                } else {
                    Text(
                        stringResource(R.string.settings_exact_alarm_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = callbacks.onRequestExactAlarm,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) { Text(stringResource(R.string.settings_exact_alarm_button)) }
                }
                Text(
                    stringResource(batteryStatusRes(state.batteryUnrestricted)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedButton(
                    onClick = callbacks.onOpenBatterySettings,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) { Text(stringResource(R.string.settings_battery_button)) }
                Text(
                    stringResource(R.string.settings_battery_samsung_note),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = callbacks.onSendTestReminder,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) { Text(stringResource(R.string.settings_test_reminder)) }
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
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                val dark = when (state.darkMode) {
                    DarkMode.DARK -> true
                    DarkMode.LIGHT -> false
                    DarkMode.SYSTEM -> isSystemInDarkTheme()
                }
                AppThemes.all.forEach { theme ->
                    ThemeRow(
                        theme = theme,
                        dark = dark,
                        selected = state.themeId == theme.id,
                        animatedOn = theme.id !in state.staticThemeIds,
                        onSelect = { callbacks.onThemeSelect(theme.id) },
                        onAnimated = { on -> callbacks.onThemeAnimated(theme.id, on) },
                    )
                }
            }
            SectionCard(stringResource(R.string.settings_debugging)) {
                OutlinedButton(
                    onClick = callbacks.onOpenDebug,
                    modifier = Modifier.padding(vertical = 8.dp),
                ) { Text(stringResource(R.string.settings_debug_open)) }
            }
            SectionCard(stringResource(R.string.settings_section_licences)) {
                LicencesBlock(state.licenseText)
            }
        }
    }
}

/** Message plus button; the text colour comes from the surrounding card/surface. */
@Composable
fun NotificationsBlockedMessage(onOpenSettings: () -> Unit) {
    Text(stringResource(R.string.notif_blocked_message), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.padding(vertical = 8.dp)) {
        Text(stringResource(R.string.notif_blocked_button))
    }
}

private fun modeLabel(m: DarkMode): Int = when (m) {
    DarkMode.SYSTEM -> R.string.settings_theme_system
    DarkMode.LIGHT -> R.string.settings_theme_light
    DarkMode.DARK -> R.string.settings_theme_dark
}

/** One picker entry; animated themes also get their own Animated/Off choice (no global switch). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeRow(
    theme: AppTheme,
    dark: Boolean,
    selected: Boolean,
    animatedOn: Boolean,
    onSelect: () -> Unit,
    onAnimated: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
            ThemeSwatch(theme, dark)
            Column(Modifier.weight(1f).padding(start = 12.dp, top = 6.dp, bottom = 6.dp)) {
                Text(stringResource(theme.nameRes), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(theme.descriptionRes), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (theme.animated) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 4.dp)) {
                SegmentedButton(
                    selected = animatedOn,
                    onClick = { onAnimated(true) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.settings_theme_animated)) }
                SegmentedButton(
                    selected = !animatedOn,
                    onClick = { onAnimated(false) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.settings_theme_static)) }
            }
            if (!systemAnimationsEnabled()) {
                Text(
                    stringResource(R.string.settings_theme_system_anim_off),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** 24dp background circle with three overlapping 12dp tab-colour dots. */
@Composable
private fun ThemeSwatch(theme: AppTheme, dark: Boolean) {
    val mode = if (dark) theme.dark else theme.light
    val tabs = mode.extras.tabs
    val outline = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .size(24.dp)
            .background(mode.scheme.background, CircleShape)
            .border(1.dp, outline, CircleShape),
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .offset(x = (i * 5).dp, y = (6).dp)
                    .size(12.dp)
                    .background(tabs[i % tabs.size], CircleShape),
            )
        }
    }
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
