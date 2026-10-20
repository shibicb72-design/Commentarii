@file:OptIn(ExperimentalMaterial3Api::class)

package com.shibi.commentarii.ui.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.common.DatePickerDialogFor
import com.shibi.commentarii.ui.theme.ThemedTitle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class NameDialog { NONE, FOLDER, TAG }

@Composable
fun DailyHeader(state: DailyUiState, actions: DailyActions, modifier: Modifier = Modifier) {
    var showDatePicker by remember { mutableStateOf(false) }
    val title = remember(state.date) {
        state.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault()))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ThemedTitle(title, MaterialTheme.typography.displaySmall, Modifier.weight(1f))
            IconButton(onClick = { showDatePicker = true }) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.daily_pick_date))
            }
            IconButton(onClick = actions.onTogglePin) {
                Icon(
                    if (state.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = stringResource(if (state.pinned) R.string.daily_unpin else R.string.daily_pin),
                    tint = if (state.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        DateStrip(state.date, state.today, actions.onSelectDate)
        PageChips(state, actions)
    }
    if (showDatePicker) {
        DatePickerDialogFor(state.date, onPick = actions.onSelectDate, onDismiss = { showDatePicker = false })
    }
}

@Composable
private fun DateStrip(selected: LocalDate, today: LocalDate, onSelect: (LocalDate) -> Unit) {
    val days = remember(selected) { (-STRIP_DAYS..STRIP_DAYS).map { selected.plusDays(it.toLong()) } }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = STRIP_DAYS - 2)
    val weekdayFormat = remember { DateTimeFormatter.ofPattern("EEE", Locale.getDefault()) }
    LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(days, key = { it.toEpochDay() }) { day ->
            val isSelected = day == selected
            Surface(
                onClick = { onSelect(day) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                border = if (day == today) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.width(52.dp).semantics { this.selected = isSelected },
            ) {
                Column(
                    Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(day.format(weekdayFormat), style = MaterialTheme.typography.labelSmall)
                    Text(
                        day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private const val STRIP_DAYS = 7

@Composable
private fun PageChips(state: DailyUiState, actions: DailyActions) {
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(NameDialog.NONE) }
    val folderName = state.folders.firstOrNull { it.id == state.folderId }?.name
        ?: stringResource(R.string.daily_no_folder)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AssistChip(
                onClick = { menuOpen = true },
                label = { Text(folderName) },
                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.daily_no_folder)) },
                    onClick = {
                        menuOpen = false
                        actions.onSetFolder(null)
                    },
                )
                state.folders.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder.name) },
                        onClick = {
                            menuOpen = false
                            actions.onSetFolder(folder.id)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.daily_new_folder)) },
                    onClick = {
                        menuOpen = false
                        dialog = NameDialog.FOLDER
                    },
                )
            }
        }
        state.tags.forEach { tag ->
            InputChip(
                selected = false,
                onClick = {},
                label = { Text(tag.name) },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.daily_remove_tag, tag.name),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { actions.onRemoveTag(tag.id) },
                    )
                },
            )
        }
        AssistChip(
            onClick = { dialog = NameDialog.TAG },
            label = { Text(stringResource(R.string.daily_add_tag)) },
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }
    if (dialog != NameDialog.NONE) {
        val isFolder = dialog == NameDialog.FOLDER
        NameInputDialog(
            title = stringResource(if (isFolder) R.string.daily_new_folder else R.string.daily_new_tag),
            onDismiss = { dialog = NameDialog.NONE },
            onConfirm = { name ->
                dialog = NameDialog.NONE
                if (isFolder) actions.onCreateFolder(name) else actions.onAddTag(name)
            },
        )
    }
}

@Composable
private fun NameInputDialog(title: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.daily_name_hint)) },
                keyboardOptions = KeyboardOptions.Default,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
