package com.shibi.commentarii.ui.week

import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.ui.daily.TaskListActions
import com.shibi.commentarii.ui.daily.TaskUi
import java.time.LocalDate

/** Summary of one day of the week: up to three priorities and the to-do progress. */
data class WeekDayUi(
    val date: LocalDate,
    val priorities: List<String> = emptyList(),
    val todoDone: Int = 0,
    val todoTotal: Int = 0,
)

/** A habit with one flag per day of the week (Monday first). */
data class WeekHabitUi(val id: Long, val name: String, val checks: List<Boolean>)

/** Everything the weekly page shows. Plain data so screenshot tests can build it without a database. */
data class WeekUiState(
    val monday: LocalDate,
    val today: LocalDate,
    val goals: List<TaskUi> = emptyList(),
    val todo: List<TaskUi> = emptyList(),
    val dontForget: List<TaskUi> = emptyList(),
    val days: List<WeekDayUi> = emptyList(),
    val habits: List<WeekHabitUi> = emptyList(),
    /** One entry per day (1..5 or null when not chosen). */
    val moods: List<Int?> = emptyList(),
    val notes: String = "",
    val reviewWell: String = "",
    val reviewImprove: String = "",
    /** False until the ViewModel finished its initial load; editing is disabled meanwhile. */
    val loaded: Boolean = true,
) {
    internal fun tasksOf(section: String): List<TaskUi> = when (section) {
        TaskSection.GOALS -> goals
        TaskSection.TODO -> todo
        else -> dontForget
    }

    internal fun withTasks(section: String, items: List<TaskUi>): WeekUiState = when (section) {
        TaskSection.GOALS -> copy(goals = items)
        TaskSection.TODO -> copy(todo = items)
        else -> copy(dontForget = items)
    }
}

data class WeekActions(
    /** Any date of the wanted week. */
    val onSelectWeek: (LocalDate) -> Unit = {},
    val onOpenDay: (LocalDate) -> Unit = {},
    val goals: TaskListActions = TaskListActions(),
    val todo: TaskListActions = TaskListActions(),
    val dontForget: TaskListActions = TaskListActions(),
    val onToggleHabit: (itemId: Long, date: LocalDate) -> Unit = { _, _ -> },
    val onNotes: (String) -> Unit = {},
    val onReviewWell: (String) -> Unit = {},
    val onReviewImprove: (String) -> Unit = {},
)
