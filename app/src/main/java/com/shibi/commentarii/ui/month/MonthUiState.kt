package com.shibi.commentarii.ui.month

import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.ui.daily.TaskListActions
import com.shibi.commentarii.ui.daily.TaskUi
import java.time.LocalDate
import java.time.YearMonth

/** One square of the calendar: [hasContent] draws the dot, [mood] (1..5) tints the square. */
data class MonthDayUi(
    val date: LocalDate,
    val inMonth: Boolean,
    val hasContent: Boolean = false,
    val mood: Int? = null,
)

/** A dated line of the events list; [key] is a stable local identity for the row. */
data class EventUi(val key: Long, val epochDay: Long, val text: String = "")

/** Habit total for the month; [denominator] is null for a future month (shown as "-"). */
data class MonthHabitUi(val id: Long, val name: String, val done: Int, val denominator: Int?)

/** Everything the monthly page shows. Plain data so screenshot tests can build it without a database. */
data class MonthUiState(
    val month: YearMonth,
    val today: LocalDate,
    val grid: List<MonthDayUi> = emptyList(),
    val goals: List<TaskUi> = emptyList(),
    val events: List<EventUi> = emptyList(),
    val todo: List<TaskUi> = emptyList(),
    val habits: List<MonthHabitUi> = emptyList(),
    val notes: String = "",
    val review: String = "",
) {
    internal fun tasksOf(section: String): List<TaskUi> = if (section == TaskSection.GOALS) goals else todo

    internal fun withTasks(section: String, items: List<TaskUi>): MonthUiState =
        if (section == TaskSection.GOALS) copy(goals = items) else copy(todo = items)
}

data class MonthActions(
    val onSelectMonth: (YearMonth) -> Unit = {},
    val onOpenDay: (LocalDate) -> Unit = {},
    val goals: TaskListActions = TaskListActions(),
    val todo: TaskListActions = TaskListActions(),
    val onAddEvent: () -> Unit = {},
    val onEventText: (key: Long, text: String) -> Unit = { _, _ -> },
    val onEventDate: (key: Long, date: LocalDate) -> Unit = { _, _ -> },
    val onRemoveEvent: (key: Long) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onReview: (String) -> Unit = {},
)
