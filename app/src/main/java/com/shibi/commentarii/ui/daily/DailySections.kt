@file:OptIn(ExperimentalMaterial3Api::class)

package com.shibi.commentarii.ui.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SentimentDissatisfied
import androidx.compose.material.icons.filled.SentimentNeutral
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.SentimentVeryDissatisfied
import androidx.compose.material.icons.filled.SentimentVerySatisfied
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.components.FirstLineRow
import com.shibi.commentarii.ui.components.checklistTextStyle
import com.shibi.commentarii.ui.theme.AppColors
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun PrioritiesCard(items: List<TaskUi>, actions: TaskListActions, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.section_priorities), modifier) {
        items.forEachIndexed { i, item ->
            LineField(
                value = item.text,
                onValueChange = { actions.onText(i, it) },
                placeholder = stringResource(R.string.daily_priority_hint, i + 1),
                strike = item.done,
                leading = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(26.dp))
                        CircleCheck(item.done, { actions.onToggleDone(i) })
                    }
                },
            )
        }
    }
}

@Composable
fun TodoCard(items: List<TaskUi>, actions: TaskListActions, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.section_todo), modifier) {
        items.forEachIndexed { i, item ->
            LineField(
                value = item.text,
                onValueChange = { actions.onText(i, it) },
                placeholder = stringResource(R.string.daily_todo_hint),
                strike = item.done,
                leading = { CircleCheck(item.done, { actions.onToggleDone(i) }) },
                trailing = {
                    SmallIconButton(
                        Icons.Filled.KeyboardArrowUp,
                        stringResource(R.string.daily_move_up),
                        { actions.onMove(i, -1) },
                        enabled = i > 0,
                    )
                    SmallIconButton(
                        Icons.Filled.KeyboardArrowDown,
                        stringResource(R.string.daily_move_down),
                        { actions.onMove(i, 1) },
                        enabled = i < items.lastIndex,
                    )
                    SmallIconButton(Icons.Filled.Close, stringResource(R.string.daily_remove), { actions.onRemove(i) })
                },
            )
        }
        AddRowButton(stringResource(R.string.daily_add_item), actions.onAdd)
    }
}

@Composable
fun DontForgetCard(items: List<TaskUi>, actions: TaskListActions, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.section_dont_forget), modifier) {
        items.forEachIndexed { i, item ->
            LineField(
                value = item.text,
                onValueChange = { actions.onText(i, it) },
                placeholder = stringResource(R.string.daily_dont_forget_hint),
                leading = {
                    Box(Modifier.size(6.dp).background(LocalContentColor.current, CircleShape))
                },
                trailing = {
                    SmallIconButton(Icons.Filled.Close, stringResource(R.string.daily_remove), { actions.onRemove(i) })
                },
            )
        }
        AddRowButton(stringResource(R.string.daily_add_line), actions.onAdd)
    }
}

@Composable
fun GratefulCard(items: List<TaskUi>, actions: TaskListActions, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.section_grateful), modifier) {
        items.forEachIndexed { i, item ->
            LineField(
                value = item.text,
                onValueChange = { actions.onText(i, it) },
                placeholder = stringResource(R.string.daily_grateful_hint),
                leading = {
                    Icon(
                        painterResource(R.drawable.ic_heart),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
                    )
                },
                trailing = {
                    SmallIconButton(Icons.Filled.Close, stringResource(R.string.daily_remove), { actions.onRemove(i) })
                },
            )
        }
        AddRowButton(stringResource(R.string.daily_add_line), actions.onAdd)
    }
}

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun formatMinute(minuteOfDay: Int): String = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(TimeFormat)

@Composable
fun ScheduleCard(
    rows: List<ScheduleRowUi>,
    onText: (key: Long, text: String) -> Unit,
    onAddCustomTime: (minuteOfDay: Int) -> Unit,
    onRemoveCustomTime: (key: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.section_schedule), modifier) {
        rows.forEach { row ->
            LineField(
                value = row.text,
                onValueChange = { onText(row.key, it) },
                placeholder = stringResource(R.string.daily_schedule_hint),
                leading = {
                    Text(
                        formatMinute(row.minuteOfDay),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (row.isCustom) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.width(78.dp),
                    )
                },
                trailing = if (row.isCustom) {
                    {
                        SmallIconButton(
                            Icons.Filled.Close,
                            stringResource(R.string.daily_remove),
                            { onRemoveCustomTime(row.key) },
                        )
                    }
                } else {
                    null
                },
            )
        }
        AddRowButton(stringResource(R.string.daily_custom_time), { picking = true })
    }
    if (picking) {
        TimePickerDialog(
            onDismiss = { picking = false },
            onConfirm = { minute ->
                picking = false
                onAddCustomTime(minute)
            },
        )
    }
}

@Composable
private fun TimePickerDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            androidx.compose.foundation.layout.Column(Modifier.padding(24.dp)) {
                TimePicker(state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            }
        }
    }
}

@Composable
fun SelfCareCard(
    items: List<SelfCareUi>,
    onToggle: (Long) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    SectionCard(stringResource(R.string.section_self_care), modifier) {
        items.forEach { item ->
            androidx.compose.runtime.key(item.id) { SelfCareRow(item, onToggle, onRename, onRemove) }
        }
        if (adding) {
            LineField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = stringResource(R.string.daily_self_care_hint),
                trailing = {
                    SmallIconButton(Icons.Filled.Check, stringResource(R.string.daily_confirm), {
                        onAdd(draft)
                        draft = ""
                        adding = false
                    })
                    SmallIconButton(Icons.Filled.Close, stringResource(R.string.action_cancel), {
                        draft = ""
                        adding = false
                    })
                },
            )
        } else {
            AddRowButton(stringResource(R.string.daily_add_item), { adding = true })
        }
    }
}

@Composable
private fun SelfCareRow(
    item: SelfCareUi,
    onToggle: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(item.name) }
    if (editing) {
        LineField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = stringResource(R.string.daily_self_care_hint),
            trailing = {
                SmallIconButton(Icons.Filled.Check, stringResource(R.string.daily_confirm), {
                    onRename(item.id, draft)
                    editing = false
                })
            },
        )
    } else {
        val style = checklistTextStyle(MaterialTheme.typography.bodyLarge)
        FirstLineRow(
            style,
            leading = { CircleCheck(item.checked, { onToggle(item.id) }) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.streak > 0) StreakBadge(item.streak)
                    SmallIconButton(Icons.Filled.Edit, stringResource(R.string.daily_rename), {
                        draft = item.name
                        editing = true
                    })
                    SmallIconButton(Icons.Filled.Delete, stringResource(R.string.daily_remove), { onRemove(item.id) })
                }
            },
        ) { pos ->
            Text(item.name, style = style, modifier = Modifier.weight(1f).then(pos))
        }
    }
}

@Composable
private fun StreakBadge(streak: Int) {
    val colors = AppColors.current
    val description = stringResource(R.string.daily_streak_description, streak)
    Surface(
        color = colors.sage,
        contentColor = colors.onSage,
        shape = CircleShape,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            stringResource(R.string.daily_streak_badge, streak),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun MoodCard(mood: Int?, onMood: (Int) -> Unit, modifier: Modifier = Modifier) {
    val faces = listOf(
        5 to (Icons.Filled.SentimentVerySatisfied to R.string.mood_5),
        4 to (Icons.Filled.SentimentSatisfied to R.string.mood_4),
        3 to (Icons.Filled.SentimentNeutral to R.string.mood_3),
        2 to (Icons.Filled.SentimentDissatisfied to R.string.mood_2),
        1 to (Icons.Filled.SentimentVeryDissatisfied to R.string.mood_1),
    )
    SectionCard(stringResource(R.string.section_mood), modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            faces.forEach { (value, face) ->
                val selected = mood == value
                Surface(
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        AppColors.current.onCard
                    },
                ) {
                    IconButton(onClick = { onMood(value) }) {
                        Icon(face.first, contentDescription = stringResource(face.second), modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun NotesCard(notes: String, onNotes: (String) -> Unit, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.section_notes), modifier) {
        Box {
            LineField(
                value = notes,
                onValueChange = onNotes,
                placeholder = stringResource(R.string.daily_notes_hint),
                singleLine = false,
                minLines = 5,
            )
        }
    }
}
