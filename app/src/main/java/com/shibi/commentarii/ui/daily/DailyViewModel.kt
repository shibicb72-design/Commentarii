package com.shibi.commentarii.ui.daily

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.ScheduleEntryEntity
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.domain.RolloverOffer
import com.shibi.commentarii.domain.RolloverSession
import com.shibi.commentarii.domain.applyRollover
import com.shibi.commentarii.domain.computeRollover
import com.shibi.commentarii.domain.shouldShowExportReminder
import com.shibi.commentarii.domain.streaksFor
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DailyViewModel(
    private val container: AppContainer,
    val date: LocalDate,
) : ViewModel() {
    private val pages = container.pages
    private val selfCare = container.selfCare

    private val _state = MutableStateFlow(DailyUiState(date = date, today = LocalDate.now(container.clock)))
    val state: StateFlow<DailyUiState> = _state.asStateFlow()

    private val settings = container.settings
    private val isToday = date == LocalDate.now(container.clock)

    private val rolloverSession = RolloverSession(
        date = date,
        markHandled = { settings.setLastOpenedDate(it) },
        apply = { offer, ids ->
            applyRollover(pages, offer, ids, date)
            reloadCarriedSections()
        },
    )

    /** Pending "carry over unfinished items" offer; only ever set for today's page. */
    val rollover: StateFlow<RolloverOffer?> = rolloverSession.offer

    /** Whether the export reminder banner shows (today's page only). */
    val reminderVisible: StateFlow<Boolean> = combine(
        settings.exportReminderEnabled,
        settings.lastExportAt,
        settings.reminderDismissedAt,
        flow { emit(pages.hasUserData()) },
    ) { enabled, last, dismissed, hasData ->
        isToday && shouldShowExportReminder(enabled, hasData, last, dismissed, container.clock.instant())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissReminder() {
        viewModelScope.launch { settings.setReminderDismissedAt(container.clock.instant()) }
    }

    private suspend fun checkRollover() {
        val previous = pages.getLatestDailyBefore(date)
        val offer = previous?.let {
            computeRollover(date, settings.lastOpenedDate.first(), it.date, pages.getTasks(it.id))
        }
        rolloverSession.onChecked(offer)
    }

    /** Applies the chosen items (empty = skip) and records that today has been opened. */
    fun resolveRollover(selectedIds: Set<Long>) {
        viewModelScope.launch { rolloverSession.resolve(selectedIds) }
    }

    /** Tap outside / back: hide the offer but ask again next time the page is opened. */
    fun dismissRollover() = rolloverSession.dismiss()

    private suspend fun reloadCarriedSections() {
        val tasks = pages.getTasks(pageId).groupBy { it.section }
        _state.update { st ->
            st.copy(
                priorities = pad(tasks[TaskSection.PRIORITY].orEmpty().map(::toUi), PRIORITY_SLOTS),
                todo = pad(tasks[TaskSection.TODO].orEmpty().map(::toUi), MIN_TODO),
            )
        }
    }

    private val keyGen = AtomicLong(1)
    private fun nextKey() = keyGen.getAndIncrement()

    private var pageId = 0L

    @Volatile
    private var loaded = false

    // Autosave: edits mark a section dirty; a debounced job writes dirty sections. The scope outlives the
    // ViewModel so the last edits are still written when the user navigates away.
    private val dirty = HashSet<String>()
    private val saveMutex = Mutex()
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debounceJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val page = pages.getOrCreateDaily(date)
        pageId = page.id
        pages.markOpened(page.id)
        val tasks = pages.getTasks(page.id).groupBy { it.section }
        val schedule = pages.getSchedule(page.id)
        val notes = pages.getBlockDocument(page.id, SectionKind.NOTES)
        _state.update { st ->
            st.copy(
                priorities = pad(tasks[TaskSection.PRIORITY].orEmpty().map(::toUi), PRIORITY_SLOTS),
                todo = pad(tasks[TaskSection.TODO].orEmpty().map(::toUi), MIN_TODO),
                dontForget = pad(tasks[TaskSection.DONT_FORGET].orEmpty().map(::toUi), MIN_DONT_FORGET),
                grateful = pad(tasks[TaskSection.GRATEFUL].orEmpty().map(::toUi), MIN_GRATEFUL),
                schedule = schedule.map {
                    ScheduleRowUi(nextKey(), it.minuteOfDay, it.text, it.isCustom, it.id)
                }.sortedSchedule(),
                notes = notes.blocks.joinToString("\n") { it.text },
            )
        }
        loaded = true
        if (isToday) checkRollover()

        viewModelScope.launch {
            pages.observePage(page.id).collect { p ->
                if (p != null) _state.update { it.copy(pinned = p.pinned, folderId = p.folderId) }
            }
        }
        viewModelScope.launch {
            pages.observeFolders().collect { list ->
                _state.update { it.copy(folders = list.map { f -> ChipUi(f.id, f.name) }) }
            }
        }
        viewModelScope.launch {
            pages.observeTagsForPage(page.id).collect { list ->
                _state.update { it.copy(tags = list.map { t -> ChipUi(t.id, t.name) }) }
            }
        }
        viewModelScope.launch {
            combine(
                selfCare.observeItems(),
                selfCare.observeChecks(date.minusDays(STREAK_WINDOW_DAYS), date),
            ) { items, checks -> items to checks }.collect { (items, checks) ->
                val streaks = streaksFor(items, checks, date)
                val checkedToday = checks.filter { it.date == date }.map { it.itemId }.toSet()
                _state.update { st ->
                    st.copy(
                        selfCare = items.map {
                            SelfCareUi(it.id, it.name, it.id in checkedToday, streaks[it.id] ?: 0)
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            pages.observeMood(date).collect { m -> _state.update { it.copy(mood = m?.mood) } }
        }
    }

    // ---- Editing ----

    private fun editTasks(section: String, transform: (List<TaskUi>) -> List<TaskUi>) {
        _state.update { it.withTasks(section, transform(it.tasksOf(section))) }
        markDirty(section)
    }

    private fun taskActions(section: String, minRows: Int, fixedRows: Boolean = false) = TaskListActions(
        onText = { i, text ->
            editTasks(section) { l -> l.mapIndexed { idx, x -> if (idx == i) x.copy(text = text) else x } }
        },
        onToggleDone = { i ->
            editTasks(section) { l -> l.mapIndexed { idx, x -> if (idx == i) x.copy(done = !x.done) else x } }
        },
        onAdd = { if (!fixedRows) editTasks(section) { it + TaskUi(key = nextKey()) } },
        onRemove = { i ->
            if (!fixedRows) editTasks(section) { l -> pad(l.filterIndexed { idx, _ -> idx != i }, minRows) }
        },
        onMove = { i, delta ->
            editTasks(section) { l ->
                val j = i + delta
                if (i !in l.indices || j !in l.indices) {
                    l
                } else {
                    l.toMutableList().apply { add(j, removeAt(i)) }
                }
            }
        },
    )

    fun buildActions(onSelectDate: (LocalDate) -> Unit): DailyActions = DailyActions(
        onSelectDate = onSelectDate,
        onTogglePin = {
            viewModelScope.launch { pages.setPinned(pageId, !_state.value.pinned) }
        },
        onSetFolder = { id -> viewModelScope.launch { pages.moveToFolder(pageId, id) } },
        onCreateFolder = { name ->
            if (name.isNotBlank()) {
                viewModelScope.launch {
                    val folder = pages.createFolder(name)
                    pages.moveToFolder(pageId, folder.id)
                }
            }
        },
        onAddTag = { name ->
            if (name.isNotBlank()) {
                viewModelScope.launch { pages.addTag(pageId, pages.getOrCreateTag(name).id) }
            }
        },
        onRemoveTag = { id -> viewModelScope.launch { pages.removeTag(pageId, id) } },
        priorities = taskActions(TaskSection.PRIORITY, PRIORITY_SLOTS, fixedRows = true),
        todo = taskActions(TaskSection.TODO, MIN_TODO),
        dontForget = taskActions(TaskSection.DONT_FORGET, MIN_DONT_FORGET),
        grateful = taskActions(TaskSection.GRATEFUL, MIN_GRATEFUL),
        onScheduleText = { key, text ->
            editSchedule { l -> l.map { if (it.key == key) it.copy(text = text) else it } }
        },
        onAddCustomTime = { minute ->
            editSchedule { it + ScheduleRowUi(key = nextKey(), minuteOfDay = minute, isCustom = true) }
        },
        onRemoveCustomTime = { key -> editSchedule { l -> l.filterNot { it.key == key && it.isCustom } } },
        onToggleSelfCare = { id -> viewModelScope.launch { selfCare.toggleCheck(id, date) } },
        onAddSelfCare = { name ->
            if (name.isNotBlank()) viewModelScope.launch { selfCare.addItem(name) }
        },
        onRenameSelfCare = { id, name ->
            if (name.isNotBlank()) viewModelScope.launch { selfCare.renameItem(id, name) }
        },
        onRemoveSelfCare = { id -> viewModelScope.launch { selfCare.removeItem(id) } },
        onMood = { m ->
            viewModelScope.launch { pages.saveMood(date, if (_state.value.mood == m) null else m) }
        },
        onNotes = { text ->
            _state.update { it.copy(notes = text) }
            markDirty(DIRTY_NOTES)
        },
    )

    private fun editSchedule(transform: (List<ScheduleRowUi>) -> List<ScheduleRowUi>) {
        _state.update { it.copy(schedule = transform(it.schedule).sortedSchedule()) }
        markDirty(DIRTY_SCHEDULE)
    }

    // ---- Autosave ----

    private fun markDirty(section: String) {
        synchronized(dirty) { dirty += section }
        debounceJob?.cancel()
        debounceJob = saveScope.launch {
            delay(DEBOUNCE_MS)
            withContext(NonCancellable) { flush() }
        }
    }

    /** Writes pending edits now (e.g. when the app goes to the background). */
    fun flushNow() {
        debounceJob?.cancel()
        saveScope.launch { flush() }
    }

    private suspend fun flush() = saveMutex.withLock {
        if (!loaded) return@withLock
        val sections = synchronized(dirty) { dirty.toList().also { dirty.clear() } }
        for (section in sections) {
            try {
                when (section) {
                    DIRTY_SCHEDULE -> saveSchedule()
                    DIRTY_NOTES -> saveNotes()
                    else -> saveTasks(section)
                }
            } catch (e: CancellationException) {
                synchronized(dirty) { dirty += section }
                throw e
            } catch (_: Exception) {
                synchronized(dirty) { dirty += section }
            }
        }
    }

    private suspend fun saveTasks(section: String) {
        val snapshot = _state.value.tasksOf(section)
        val saved = pages.saveTasks(
            pageId,
            section,
            snapshot.map {
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
        val idByKey = snapshot.zip(saved) { ui, row -> ui.key to row.id }.toMap()
        _state.update { st ->
            st.withTasks(
                section,
                st.tasksOf(section).map { if (it.id == 0L) it.copy(id = idByKey[it.key] ?: 0L) else it },
            )
        }
    }

    private suspend fun saveSchedule() {
        val snapshot = _state.value.schedule
        val saved = pages.saveSchedule(
            pageId,
            snapshot.map {
                ScheduleEntryEntity(
                    id = it.id,
                    pageId = pageId,
                    date = date,
                    minuteOfDay = it.minuteOfDay,
                    text = it.text,
                    isCustom = it.isCustom,
                )
            },
        )
        if (saved.any { it.text.isNotBlank() }) container.onTimedEntryHasText()
        val idByKey = snapshot.zip(saved) { ui, row -> ui.key to row.id }.toMap()
        _state.update { st ->
            st.copy(
                schedule = st.schedule.map { if (it.id == 0L) it.copy(id = idByKey[it.key] ?: 0L) else it },
            )
        }
    }

    private suspend fun saveNotes() {
        val lines = _state.value.notes.split("\n")
        pages.saveBlockDocument(pageId, SectionKind.NOTES, BlockDocument(lines.map { DocBlock(text = it) }))
    }

    override fun onCleared() {
        debounceJob?.cancel()
        saveScope.launch { flush() }
    }

    // ---- Helpers ----

    private fun toUi(e: TaskItemEntity) =
        TaskUi(key = nextKey(), text = e.text, done = e.done, id = e.id, carriedTo = e.carriedTo)

    private fun pad(list: List<TaskUi>, min: Int): List<TaskUi> =
        if (list.size >= min) list else list + (list.size until min).map { TaskUi(key = nextKey()) }

    private fun List<ScheduleRowUi>.sortedSchedule() = sortedWith(compareBy({ it.minuteOfDay }, { it.isCustom }))

    companion object {
        const val PRIORITY_SLOTS = 3
        const val MIN_TODO = 3
        const val MIN_DONT_FORGET = 3
        const val MIN_GRATEFUL = 5
        private const val DEBOUNCE_MS = 500L
        private const val STREAK_WINDOW_DAYS = 365L
        private const val DIRTY_SCHEDULE = "SCHEDULE"
        private const val DIRTY_NOTES = "NOTES"

        fun factory(container: AppContainer, date: LocalDate): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = DailyViewModel(container, date) as T
            }
    }
}
