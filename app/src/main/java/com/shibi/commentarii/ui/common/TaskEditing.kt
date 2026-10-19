package com.shibi.commentarii.ui.common

import com.shibi.commentarii.data.PageRepository
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.ui.daily.TaskListActions
import com.shibi.commentarii.ui.daily.TaskUi

/** [list] padded with blank rows from [newRow] up to [min] rows. */
fun padTasks(list: List<TaskUi>, min: Int, newRow: () -> TaskUi): List<TaskUi> =
    if (list.size >= min) list else list + (list.size until min).map { newRow() }

/**
 * Editing callbacks for one list. [edit] applies a transform to the list held in the screen state (and marks it dirty).
 * Rows never drop below [minRows]; [maxRows] caps adding (used for the three weekly goals).
 */
fun taskListActions(
    edit: ((List<TaskUi>) -> List<TaskUi>) -> Unit,
    newRow: () -> TaskUi,
    minRows: Int,
    maxRows: Int = Int.MAX_VALUE,
): TaskListActions = TaskListActions(
    onText = { i, text -> edit { l -> l.mapIndexed { idx, x -> if (idx == i) x.copy(text = text) else x } } },
    onToggleDone = { i -> edit { l -> l.mapIndexed { idx, x -> if (idx == i) x.copy(done = !x.done) else x } } },
    onAdd = { edit { l -> if (l.size < maxRows) l + newRow() else l } },
    onRemove = { i -> edit { l -> padTasks(l.filterIndexed { idx, _ -> idx != i }, minRows, newRow) } },
    onMove = { i, delta ->
        edit { l ->
            val j = i + delta
            if (i !in l.indices || j !in l.indices) l else l.toMutableList().apply { add(j, removeAt(i)) }
        }
    },
)

/** Saves one task section and returns the database id of each row keyed by [TaskUi.key]. */
suspend fun PageRepository.saveTaskRows(pageId: Long, section: String, rows: List<TaskUi>): Map<Long, Long> {
    val saved = saveTasks(
        pageId,
        section,
        rows.map {
            TaskItemEntity(
                id = it.id,
                pageId = pageId,
                section = section,
                position = 0,
                text = it.text,
                done = it.done,
                carriedTo = it.carriedTo,
            )
        },
    )
    return rows.zip(saved) { ui, row -> ui.key to row.id }.toMap()
}
