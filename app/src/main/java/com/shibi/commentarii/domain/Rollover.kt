package com.shibi.commentarii.domain

import com.shibi.commentarii.data.PageRepository
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import java.time.LocalDate

data class RolloverOffer(val fromDate: LocalDate, val items: List<TaskItemEntity>)

const val MAX_PRIORITIES = 3

/**
 * Offer to carry unfinished items over. Only on a new day ([lastOpenedDate] null or before [today]) and when a
 * previous daily page ([lastDailyDate], before [today]) has unfinished PRIORITY/TODO [unfinishedItems].
 */
fun computeRollover(
    today: LocalDate,
    lastOpenedDate: LocalDate?,
    lastDailyDate: LocalDate?,
    unfinishedItems: List<TaskItemEntity>,
): RolloverOffer? {
    if (lastOpenedDate != null && !lastOpenedDate.isBefore(today)) return null
    if (lastDailyDate == null || !lastDailyDate.isBefore(today)) return null
    val items = unfinishedItems.filter {
        !it.done && it.carriedTo == null && it.text.isNotBlank() && (it.section == TaskSection.PRIORITY || it.section == TaskSection.TODO)
    }
    if (items.isEmpty()) return null
    return RolloverOffer(lastDailyDate, items)
}

/**
 * Copies the offered items whose id is in [selectedIds] to today's daily page (priorities stay priorities while
 * fewer than [MAX_PRIORITIES] exist there, otherwise go to TODO; TODO stays TODO) and marks the originals
 * as carried to [today] (text kept, done=false).
 */
suspend fun applyRollover(
    repo: PageRepository,
    offer: RolloverOffer,
    selectedIds: Set<Long>,
    today: LocalDate,
) {
    val selected = offer.items.filter { it.id in selectedIds }
    if (selected.isEmpty()) return
    val todayPage = repo.getOrCreateDaily(today)
    val priorities = repo.getTasks(todayPage.id, TaskSection.PRIORITY).toMutableList()
    val todos = repo.getTasks(todayPage.id, TaskSection.TODO).toMutableList()
    for (item in selected) {
        val copy = TaskItemEntity(pageId = todayPage.id, section = "", position = 0, text = item.text)
        if (item.section == TaskSection.PRIORITY && priorities.size < MAX_PRIORITIES) {
            priorities += copy.copy(section = TaskSection.PRIORITY)
        } else {
            todos += copy.copy(section = TaskSection.TODO)
        }
    }
    repo.saveTasks(todayPage.id, TaskSection.PRIORITY, priorities)
    repo.saveTasks(todayPage.id, TaskSection.TODO, todos)

    // Mark originals, per source page and section.
    selected.groupBy { it.pageId to it.section }.forEach { (key, picked) ->
        val (pageId, section) = key
        val ids = picked.map { it.id }.toSet()
        val updated = repo.getTasks(pageId, section).map {
            if (it.id in ids) it.copy(carriedTo = today, done = false) else it
        }
        repo.saveTasks(pageId, section, updated)
    }
}
