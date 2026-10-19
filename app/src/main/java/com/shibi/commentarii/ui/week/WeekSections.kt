package com.shibi.commentarii.ui.week

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.common.moodFace
import com.shibi.commentarii.ui.daily.CircleCheck
import com.shibi.commentarii.ui.daily.SectionCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

/** The seven days; each shows up to three priorities and the to-do progress and opens that day's page. */
@Composable
fun WeekDaysCard(days: List<WeekDayUi>, today: LocalDate, onOpenDay: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    SectionCard(stringResource(R.string.section_week_days), modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEach { day -> WeekDayRow(day, day.date == today, onOpenDay) }
        }
    }
}

@Composable
private fun WeekDayRow(day: WeekDayUi, isToday: Boolean, onOpenDay: (LocalDate) -> Unit) {
    val container = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val content = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = { onOpenDay(day.date) },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.date.format(DayFormat), style = MaterialTheme.typography.labelLarge)
                Text(
                    day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (day.priorities.isEmpty()) {
                    Text(
                        stringResource(R.string.week_day_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                } else {
                    day.priorities.forEachIndexed { i, text ->
                        Text("${i + 1}. $text", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                    }
                }
                if (day.todoTotal > 0) {
                    Text(
                        stringResource(R.string.week_todo_fmt, day.todoDone, day.todoTotal),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

/** Habit grid: one row per habit, one check per day. Toggling writes the same check the daily page shows. */
@Composable
fun WeekHabitCard(
    days: List<LocalDate>,
    habits: List<WeekHabitUi>,
    onToggle: (itemId: Long, date: LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val initials = remember(days) { days.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) } }
    SectionCard(stringResource(R.string.section_self_care), modifier) {
        Row(Modifier.fillMaxWidth()) {
            initials.forEach { letter ->
                Text(
                    letter,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        habits.forEach { habit ->
            Text(habit.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
            Row(Modifier.fillMaxWidth()) {
                days.forEachIndexed { i, date ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        CircleCheck(habit.checks.getOrElse(i) { false }, { onToggle(habit.id, date) }, enabled = enabled)
                    }
                }
            }
        }
    }
}

/** One mood face per day (set on the daily page); tapping a day opens it. */
@Composable
fun WeekMoodCard(
    days: List<LocalDate>,
    moods: List<Int?>,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(stringResource(R.string.section_mood_week), modifier) {
        Row(Modifier.fillMaxWidth()) {
            days.forEachIndexed { i, date ->
                val face = moods.getOrNull(i)?.let(::moodFace)
                val label = stringResource(face?.second ?: R.string.mood_none)
                Surface(
                    onClick = { onOpenDay(date) },
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    contentColor = LocalContentColor.current,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = label },
                ) {
                    Column(Modifier.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (face != null) {
                            Icon(face.first, contentDescription = null, modifier = Modifier.size(28.dp))
                        } else {
                            Text(stringResource(R.string.habit_summary_none), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
