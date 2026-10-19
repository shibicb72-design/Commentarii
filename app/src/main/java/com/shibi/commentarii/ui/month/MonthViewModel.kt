package com.shibi.commentarii.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.EventItem
import com.shibi.commentarii.data.EventList
import com.shibi.commentarii.data.MoodEntity
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.domain.monthGrid
import com.shibi.commentarii.domain.monthSummary
import com.shibi.commentarii.ui.common.Autosaver
import com.shibi.commentarii.ui.common.padTasks
import com.shibi.commentarii.ui.common.saveTaskRows
import com.shibi.commentarii.ui.common.taskListActions
import com.shibi.commentarii.ui.daily.TaskUi
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MonthViewModel(
    private val container: AppContainer,
    val month: YearMonth,
) : ViewModel() {
    private val pages = container.pages
    private val selfCare = container.selfCare
    private val today = LocalDate.now(container.clock)
    private val cells = monthGrid(month)

    private val _state = MutableStateFlow(
        MonthUiState(
            month = month,
            today = today,
            grid = cells.map { MonthDayUi(it.date, it.inMonth) },
        ),
    )
    val state: StateFlow<MonthUiState> = _state.asStateFlow()

    private val keyGen = AtomicLong(1)
    private fun nextKey() = keyGen.getAndIncrement()
    private fun blankRow() = TaskUi(key = nextKey())

    private var pageId = 0L

    private val autosaver = Autosaver { key ->
        when (key) {
            DIRTY_NOTES -> saveDocument(SectionKind.NOTES, _state.value.notes)
            DIRTY_REVIEW -> saveDocument(SectionKind.REVIEW, _state.value.review)
            DIRTY_EVENTS -> saveEvents()
            else -> saveTasks(key)
        }
    }

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val page = pages.getOrCreateMonthly(month.atDay(1))
        pageId = page.id
        pages.markOpened(page.id)
        val tasks = pages.getTasks(page.id).groupBy { it.section }
        val events = pages.getEvents(page.id).items.map { EventUi(nextKey(), it.epochDay, it.text) }
        _state.update { st ->
            st.copy(
                goals = tasks[TaskSection.GOALS].orEmpty().map(::toUi),
                todo = padTasks(tasks[TaskSection.TODO].orEmpty().map(::toUi), MIN_TODO, ::blankRow),
                events = events,
                notes = text(SectionKind.NOTES),
                review = text(SectionKind.REVIEW),
            )
        }
        autosaver.markReady()

        val first = cells.first().date
        val last = cells.last().date
        viewModelScope.launch {
            combine(
                pages.observeContentDates(first, last),
                pages.observeMoods(first, last),
            ) { dates, moods -> dates.toSet() to moods }.collect { (dates, moods) ->
                _state.update { it.copy(grid = grid(dates, moods)) }
            }
        }
        viewModelScope.launch {
            combine(
                selfCare.observeItems(),
                selfCare.observeChecks(month.atDay(1), month.atEndOfMonth()),
            ) { items, checks -> items to monthSummary(items, checks, month, today) }.collect { (items, summary) ->
                val names = items.associate { it.id to it.name }
                _state.update { st ->
                    st.copy(habits = summary.map { MonthHabitUi(it.itemId, names[it.itemId].orEmpty(), it.done, it.denominator) })
                }
            }
        }
    }

    private fun grid(contentDates: Set<LocalDate>, moods: List<MoodEntity>): List<MonthDayUi> {
        val moodByDate = moods.associate { it.date to it.mood }
        return cells.map { MonthDayUi(it.date, it.inMonth, it.date in contentDates, moodByDate[it.date]) }
    }

    private suspend fun text(kind: String): String =
        pages.getBlockDocument(pageId, kind).blocks.joinToString("\n") { it.text }

    // ---- Editing ----

    // Edits made before the initial load finishes are ignored: load() would overwrite them anyway.
    private fun editTasks(section: String, transform: (List<TaskUi>) -> List<TaskUi>) {
        if (!autosaver.ready) return
        _state.update { it.withTasks(section, transform(it.tasksOf(section))) }
        autosaver.markDirty(section)
    }

    private fun editEvents(transform: (List<EventUi>) -> List<EventUi>) {
        if (!autosaver.ready) return
        _state.update { it.copy(events = transform(it.events)) }
        autosaver.markDirty(DIRTY_EVENTS)
    }

    private fun List<EventUi>.sortedByDate() = sortedBy { it.epochDay }

    fun buildActions(onSelectMonth: (YearMonth) -> Unit, onOpenDay: (LocalDate) -> Unit): MonthActions = MonthActions(
        onSelectMonth = onSelectMonth,
        onOpenDay = onOpenDay,
        goals = taskListActions({ editTasks(TaskSection.GOALS, it) }, ::blankRow, minRows = 0),
        todo = taskListActions({ editTasks(TaskSection.TODO, it) }, ::blankRow, MIN_TODO),
        onAddEvent = {
            val day = if (YearMonth.from(today) == month) today else month.atDay(1)
            editEvents { (it + EventUi(nextKey(), day.toEpochDay())).sortedByDate() }
        },
        onEventText = { key, text -> editEvents { l -> l.map { if (it.key == key) it.copy(text = text) else it } } },
        onEventDate = { key, date ->
            editEvents { l -> l.map { if (it.key == key) it.copy(epochDay = date.toEpochDay()) else it }.sortedByDate() }
        },
        onRemoveEvent = { key -> editEvents { l -> l.filterNot { it.key == key } } },
        onNotes = { text ->
            if (autosaver.ready) {
                _state.update { it.copy(notes = text) }
                autosaver.markDirty(DIRTY_NOTES)
            }
        },
        onReview = { text ->
            if (autosaver.ready) {
                _state.update { it.copy(review = text) }
                autosaver.markDirty(DIRTY_REVIEW)
            }
        },
    )

    // ---- Autosave ----

    /** Writes pending edits now (e.g. when the app goes to the background). */
    fun flushNow() = autosaver.flushNow()

    private suspend fun saveTasks(section: String) {
        val snapshot = _state.value.tasksOf(section)
        val idByKey = pages.saveTaskRows(pageId, section, snapshot)
        _state.update { st ->
            st.withTasks(
                section,
                st.tasksOf(section).map { if (it.id == 0L) it.copy(id = idByKey[it.key] ?: 0L) else it },
            )
        }
    }

    private suspend fun saveEvents() {
        pages.saveEvents(pageId, EventList(_state.value.events.map { EventItem(it.epochDay, it.text) }))
    }

    private suspend fun saveDocument(kind: String, text: String) {
        pages.saveBlockDocument(pageId, kind, BlockDocument(text.split("\n").map { DocBlock(text = it) }))
    }

    override fun onCleared() = autosaver.close()

    private fun toUi(e: TaskItemEntity) =
        TaskUi(key = nextKey(), text = e.text, done = e.done, id = e.id, carriedTo = e.carriedTo)

    companion object {
        const val MIN_TODO = 3
        private const val DIRTY_NOTES = "NOTES"
        private const val DIRTY_REVIEW = "REVIEW"
        private const val DIRTY_EVENTS = "EVENTS"

        fun factory(container: AppContainer, month: YearMonth): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = MonthViewModel(container, month) as T
            }
    }
}
