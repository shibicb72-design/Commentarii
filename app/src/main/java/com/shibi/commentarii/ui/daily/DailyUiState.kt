package com.shibi.commentarii.ui.daily

import com.shibi.commentarii.data.TaskSection
import java.time.LocalDate

/** A list row. [key] is a stable local identity for the row; [id] is the database id (0 until first saved). */
data class TaskUi(
    val key: Long,
    val text: String = "",
    val done: Boolean = false,
    val id: Long = 0,
    val carriedTo: LocalDate? = null,
)

data class ScheduleRowUi(
    val key: Long,
    val minuteOfDay: Int,
    val text: String = "",
    val isCustom: Boolean = false,
    val id: Long = 0,
)

data class ChipUi(val id: Long, val name: String)

/** A goal of the current week or month, shown read-only on the daily page. */
data class PeriodGoal(val text: String, val done: Boolean = false)

/** Everything the daily page shows. Plain data so screenshot tests can build it without a database. */
data class DailyUiState(
    val date: LocalDate,
    val today: LocalDate,
    val pinned: Boolean = false,
    val folders: List<ChipUi> = emptyList(),
    val folderId: Long? = null,
    val tags: List<ChipUi> = emptyList(),
    val priorities: List<TaskUi> = emptyList(),
    val todo: List<TaskUi> = emptyList(),
    val schedule: List<ScheduleRowUi> = emptyList(),
    val dontForget: List<TaskUi> = emptyList(),
    /** 1 (very sad) .. 5 (very happy), null when not chosen. */
    val mood: Int? = null,
    val notes: String = "",
    val weekGoals: List<PeriodGoal> = emptyList(),
    val monthGoals: List<PeriodGoal> = emptyList(),
) {
    internal fun tasksOf(section: String): List<TaskUi> = when (section) {
        TaskSection.PRIORITY -> priorities
        TaskSection.TODO -> todo
        else -> dontForget
    }

    internal fun withTasks(section: String, items: List<TaskUi>): DailyUiState = when (section) {
        TaskSection.PRIORITY -> copy(priorities = items)
        TaskSection.TODO -> copy(todo = items)
        else -> copy(dontForget = items)
    }
}

/** Callbacks for one editable list; rows are addressed by their index in the displayed list. */
data class TaskListActions(
    val onText: (index: Int, text: String) -> Unit = { _, _ -> },
    val onToggleDone: (index: Int) -> Unit = {},
    val onAdd: () -> Unit = {},
    val onRemove: (index: Int) -> Unit = {},
    /** [delta] is -1 (up) or +1 (down). */
    val onMove: (index: Int, delta: Int) -> Unit = { _, _ -> },
)

data class DailyActions(
    val onSelectDate: (LocalDate) -> Unit = {},
    val onTogglePin: () -> Unit = {},
    val onSetFolder: (Long?) -> Unit = {},
    val onCreateFolder: (String) -> Unit = {},
    val onAddTag: (String) -> Unit = {},
    val onRemoveTag: (Long) -> Unit = {},
    val priorities: TaskListActions = TaskListActions(),
    val todo: TaskListActions = TaskListActions(),
    val dontForget: TaskListActions = TaskListActions(),
    val onScheduleText: (key: Long, text: String) -> Unit = { _, _ -> },
    val onAddCustomTime: (minuteOfDay: Int) -> Unit = {},
    val onRemoveCustomTime: (key: Long) -> Unit = {},
    val onMood: (Int) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onOpenWeek: () -> Unit = {},
    val onOpenMonth: () -> Unit = {},
)
