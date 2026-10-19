package com.shibi.commentarii.ui.month

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.common.DatePickerDialogFor
import com.shibi.commentarii.ui.common.moodFace
import com.shibi.commentarii.ui.daily.AddRowButton
import com.shibi.commentarii.ui.daily.LineField
import com.shibi.commentarii.ui.daily.SectionCard
import com.shibi.commentarii.ui.daily.SmallIconButton
import com.shibi.commentarii.ui.theme.AppColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val EventDateFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val CellDateFormat = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())

/** Container and on-container colour of a day square: each tint comes with the colour of the number drawn on it. */
@Composable
private fun moodColors(mood: Int?): Pair<Color, Color> {
    val c = AppColors.current
    return if (mood != null && mood in 1..5) c.mood[mood - 1] to c.onMood[mood - 1] else Color.Transparent to LocalContentColor.current
}

/** Monday-first calendar. A dot marks days with entries, a tint the mood; tapping a day opens it. */
@Composable
fun MonthCalendarCard(
    grid: List<MonthDayUi>,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekdays = remember {
        DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
    }
    SectionCard(stringResource(R.string.section_calendar), modifier) {
        Row(Modifier.fillMaxWidth()) {
            weekdays.forEach { name ->
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        grid.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day -> DayCell(day, day.date == today, onOpenDay, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: MonthDayUi, isToday: Boolean, onOpenDay: (LocalDate) -> Unit, modifier: Modifier) {
    val outer = modifier
        .aspectRatio(1f)
        .padding(2.dp)
    if (!day.inMonth) {
        Box(outer, contentAlignment = Alignment.Center) {
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
        return
    }
    val (container, content) = moodColors(day.mood)
    val moodLabel = day.mood?.let(::moodFace)?.let { stringResource(it.second) }
    val entries = if (day.hasContent) stringResource(R.string.month_day_has_entries) else null
    val description = listOfNotNull(day.date.format(CellDateFormat), moodLabel, entries).joinToString(", ")
    Surface(
        onClick = { onOpenDay(day.date) },
        shape = RoundedCornerShape(10.dp),
        color = container,
        contentColor = content,
        border = if (isToday) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = outer.semantics { contentDescription = description },
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                day.date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .size(5.dp)
                    .background(if (day.hasContent) LocalContentColor.current else Color.Transparent, CircleShape),
            )
        }
    }
}

/** Dated events (no reminders): a date button, the text and a remove button per line, kept sorted by date. */
@Composable
fun EventsCard(
    events: List<EventUi>,
    onAdd: () -> Unit,
    onText: (key: Long, text: String) -> Unit,
    onDate: (key: Long, date: LocalDate) -> Unit,
    onRemove: (key: Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    SectionCard(stringResource(R.string.section_events), modifier) {
        events.forEach { event ->
            key(event.key) {
                var picking by remember { mutableStateOf(false) }
                val date = LocalDate.ofEpochDay(event.epochDay)
                LineField(
                    value = event.text,
                    onValueChange = { onText(event.key, it) },
                    placeholder = stringResource(R.string.event_hint),
                    enabled = enabled,
                    leading = {
                        TextButton(onClick = { picking = true }, enabled = enabled) { Text(date.format(EventDateFormat)) }
                    },
                    trailing = {
                        SmallIconButton(
                            Icons.Filled.Close,
                            stringResource(R.string.daily_remove),
                            { onRemove(event.key) },
                            enabled = enabled,
                        )
                    },
                )
                if (picking) {
                    DatePickerDialogFor(date, onPick = { onDate(event.key, it) }, onDismiss = { picking = false })
                }
            }
        }
        AddRowButton(stringResource(R.string.event_add), onAdd, enabled = enabled)
    }
}

/** Per-habit total for the month, e.g. "22/30"; "-" for a month that has not started. */
@Composable
fun HabitSummaryCard(habits: List<MonthHabitUi>, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.section_self_care), modifier) {
        if (habits.isEmpty()) {
            Text(stringResource(R.string.habit_summary_empty), style = MaterialTheme.typography.bodyMedium)
        }
        habits.forEach { habit ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(habit.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    habit.denominator?.let { stringResource(R.string.habit_summary_fmt, habit.done, it) }
                        ?: stringResource(R.string.habit_summary_none),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
