package com.shibi.commentarii.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DailyTaskRow
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.MoodEntity
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.SelfCareCheckEntity
import com.shibi.commentarii.data.SelfCareItemEntity
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.domain.weekDays
import com.shibi.commentarii.domain.weekGrid
import com.shibi.commentarii.ui.common.Autosaver
import com.shibi.commentarii.ui.common.padTasks
import com.shibi.commentarii.ui.common.saveTaskRows
import com.shibi.commentarii.ui.common.taskListActions
import com.shibi.commentarii.ui.daily.TaskUi
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WeekViewModel(
    private val container: AppContainer,
    val monday: LocalDate,
) : ViewModel() {
    private val pages = container.pages
    private val selfCare = container.selfCare
    private val days = weekDays(monday)

    private val _state = MutableStateFlow(
        WeekUiState(monday = monday, today = LocalDate.now(container.clock), loaded = false),
    )
    val state: StateFlow<WeekUiState> = _state.asStateFlow()

    private val keyGen = AtomicLong(1)
    private fun nextKey() = keyGen.getAndIncrement()
    private fun blankRow() = TaskUi(key = nextKey())

    private var pageId = 0L

    private val autosaver = Autosaver { key ->
        when (key) {
            DIRTY_NOTES -> saveDocument(SectionKind.NOTES, _state.value.notes)
            DIRTY_REVIEW_WELL -> saveDocument(SectionKind.REVIEW_WELL, _state.value.reviewWell)
            DIRTY_REVIEW_IMPROVE -> saveDocument(SectionKind.REVIEW_IMPROVE, _state.value.reviewImprove)
            else -> saveTasks(key)
        }
    }

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val page = pages.getOrCreateWeekly(monday)
        pageId = page.id
        pages.markOpened(page.id)
        val tasks = pages.getTasks(page.id).groupBy { it.section }
        _state.update { st ->
            st.copy(
                goals = padTasks(tasks[TaskSection.GOALS].orEmpty().map(::toUi), GOAL_SLOTS, ::blankRow),
                todo = padTasks(tasks[TaskSection.TODO].orEmpty().map(::toUi), MIN_TODO, ::blankRow),
                dontForget = padTasks(tasks[TaskSection.DONT_FORGET].orEmpty().map(::toUi), MIN_DONT_FORGET, ::blankRow),
                notes = text(SectionKind.NOTES),
                reviewWell = text(SectionKind.REVIEW_WELL),
                reviewImprove = text(SectionKind.REVIEW_IMPROVE),
                loaded = true,
            )
        }
        autosaver.markReady()

        val last = days.last()
        viewModelScope.launch {
            combine(
                pages.observeDailyTasks(monday, last),
                pages.observeMoods(monday, last),
                selfCare.observeItems(),
                selfCare.observeChecks(monday, last),
            ) { daily, moods, items, checks -> summarise(daily, moods, items, checks) }
                .collect { summary -> _state.update { summary.applyTo(it) } }
        }
    }

    private suspend fun text(kind: String): String =
        pages.getBlockDocument(pageId, kind).blocks.joinToString("\n") { it.text }

    private class Summary(
        val days: List<WeekDayUi>,
        val moods: List<Int?>,
        val habits: List<WeekHabitUi>,
    ) {
        fun applyTo(st: WeekUiState) = st.copy(days = days, moods = moods, habits = habits)
    }

    private fun summarise(
        daily: List<DailyTaskRow>,
        moods: List<MoodEntity>,
        items: List<SelfCareItemEntity>,
        checks: List<SelfCareCheckEntity>,
    ): Summary {
        val byDate = daily.filter { it.text.isNotBlank() }.groupBy { it.date }
        val dayUi = days.map { d ->
            val rows = byDate[d].orEmpty()
            val todo = rows.filter { it.section == TaskSection.TODO }
            WeekDayUi(
                date = d,
                priorities = rows.filter { it.section == TaskSection.PRIORITY }.take(PRIORITY_LINES).map { it.text },
                todoDone = todo.count { it.done },
                todoTotal = todo.size,
            )
        }
        val moodByDate = moods.associate { it.date to it.mood }
        val grid = weekGrid(items, checks, days)
        return Summary(
            days = dayUi,
            moods = days.map { moodByDate[it] },
            habits = items.map { WeekHabitUi(it.id, it.name, grid[it.id].orEmpty()) },
        )
    }

    // ---- Editing ----

    // Edits made before the initial load finishes are ignored: load() would overwrite them anyway.
    private fun editTasks(section: String, transform: (List<TaskUi>) -> List<TaskUi>) {
        if (!autosaver.ready) return
        _state.update { it.withTasks(section, transform(it.tasksOf(section))) }
        autosaver.markDirty(section)
    }

    fun buildActions(onSelectWeek: (LocalDate) -> Unit, onOpenDay: (LocalDate) -> Unit): WeekActions = WeekActions(
        onSelectWeek = onSelectWeek,
        onOpenDay = onOpenDay,
        goals = taskListActions({ editTasks(TaskSection.GOALS, it) }, ::blankRow, GOAL_SLOTS, GOAL_SLOTS),
        todo = taskListActions({ editTasks(TaskSection.TODO, it) }, ::blankRow, MIN_TODO),
        dontForget = taskListActions({ editTasks(TaskSection.DONT_FORGET, it) }, ::blankRow, MIN_DONT_FORGET),
        onToggleHabit = { id, date -> viewModelScope.launch { selfCare.toggleCheck(id, date) } },
        onNotes = { text ->
            if (autosaver.ready) {
                _state.update { it.copy(notes = text) }
                autosaver.markDirty(DIRTY_NOTES)
            }
        },
        onReviewWell = { text ->
            if (autosaver.ready) {
                _state.update { it.copy(reviewWell = text) }
                autosaver.markDirty(DIRTY_REVIEW_WELL)
            }
        },
        onReviewImprove = { text ->
            if (autosaver.ready) {
                _state.update { it.copy(reviewImprove = text) }
                autosaver.markDirty(DIRTY_REVIEW_IMPROVE)
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

    private suspend fun saveDocument(kind: String, text: String) {
        pages.saveBlockDocument(pageId, kind, BlockDocument(text.split("\n").map { DocBlock(text = it) }))
    }

    override fun onCleared() = autosaver.close()

    private fun toUi(e: TaskItemEntity) =
        TaskUi(key = nextKey(), text = e.text, done = e.done, id = e.id, carriedTo = e.carriedTo)

    companion object {
        const val GOAL_SLOTS = 3
        const val MIN_TODO = 3
        const val MIN_DONT_FORGET = 3
        private const val PRIORITY_LINES = 3
        private const val DIRTY_NOTES = "NOTES"
        private const val DIRTY_REVIEW_WELL = "REVIEW_WELL"
        private const val DIRTY_REVIEW_IMPROVE = "REVIEW_IMPROVE"

        fun factory(container: AppContainer, monday: LocalDate): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = WeekViewModel(container, monday) as T
            }
    }
}
