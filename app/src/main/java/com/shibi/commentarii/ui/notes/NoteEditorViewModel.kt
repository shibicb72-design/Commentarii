package com.shibi.commentarii.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.PageType
import com.shibi.commentarii.data.SectionKind
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class NoteEditorViewModel(
    container: AppContainer,
    private val noteId: Long,
) : ViewModel() {
    private val pages = container.pages

    private val _state = MutableStateFlow(NoteEditorUiState())
    val state: StateFlow<NoteEditorUiState> = _state.asStateFlow()

    private val keyGen = AtomicLong(1)
    private fun nextKey() = keyGen.getAndIncrement()

    // Selection is tracked outside the UI state: it changes on every cursor move and only the toolbar reads it.
    private var focusedKey: Long? = null
    private var selStart = 0
    private var selEnd = 0

    @Volatile
    private var loaded = false

    @Volatile
    private var deleted = false

    // The save scope outlives the ViewModel so the last edits are written after navigating away.
    private val dirty = HashSet<String>()
    private val saveMutex = Mutex()
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debounceJob: Job? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val page = pages.getPage(noteId)
        if (page == null || page.type != PageType.NOTE) {
            _state.update { it.copy(loaded = true, notFound = true) }
            return
        }
        pages.markOpened(noteId)
        val doc = pages.getBlockDocument(noteId, SectionKind.RICH_TEXT)
        val blocks = doc.blocks.ifEmpty { listOf(DocBlock()) }.map { EditorBlock(nextKey(), it) }
        _state.update {
            it.copy(
                loaded = true,
                title = page.title,
                blocks = blocks,
                pinned = page.pinned,
                folderId = page.folderId,
            )
        }
        loaded = true
        viewModelScope.launch { pages.observeFolders().collect { f -> _state.update { it.copy(folders = f) } } }
        viewModelScope.launch { pages.observeTags().collect { t -> _state.update { it.copy(allTags = t) } } }
        viewModelScope.launch {
            pages.observeTagsForPage(noteId).collect { t ->
                _state.update { it.copy(tagIds = t.map { tag -> tag.id }.toSet()) }
            }
        }
    }

    // ---- Editing ----

    fun onTitle(title: String) {
        if (!loaded) return
        _state.update { it.copy(title = title) }
        markDirty(DIRTY_TITLE)
    }

    fun onSelection(key: Long, start: Int, end: Int) {
        focusedKey = key
        selStart = minOf(start, end)
        selEnd = maxOf(start, end)
    }

    fun onFocusHandled() = _state.update { it.copy(focusRequest = null) }

    fun onBlockText(key: Long, text: String) {
        if (!loaded) return
        val blocks = _state.value.blocks
        val idx = blocks.indexOfFirst { it.key == key }
        if (idx < 0) return
        val cur = blocks[idx].block
        if (cur.text == text) return
        val updated = cur.withText(text)
        if ('\n' !in text) {
            replaceBlocks { list -> list.map { if (it.key == key) it.copy(block = updated) else it } }
            return
        }
        // Enter pressed (or multi-line paste): split at every newline.
        val parts = ArrayList<DocBlock>()
        var rest = updated
        while (true) {
            val nl = rest.text.indexOf('\n')
            if (nl < 0) {
                parts += rest
                break
            }
            val (l, r) = splitBlock(rest, nl, 1)
            parts += l
            rest = r
        }
        val emptyListItem = parts.size == 2 && parts.all { it.text.isEmpty() } &&
            (cur.type == BlockType.BULLET || cur.type == BlockType.CHECKBOX)
        if (emptyListItem) {
            // Enter on an empty list item ends the list.
            replaceBlocks(FocusRequest(key, 0)) { list ->
                list.map { if (it.key == key) it.copy(block = parts[0].copy(type = BlockType.PARAGRAPH)) else it }
            }
            return
        }
        val newBlocks = parts.mapIndexed { i, b -> EditorBlock(if (i == 0) key else nextKey(), b) }
        replaceBlocks(FocusRequest(newBlocks.last().key, 0)) { list ->
            list.flatMap { if (it.key == key) newBlocks else listOf(it) }
        }
    }

    fun onBackspaceAtStart(key: Long) {
        if (!loaded) return
        val blocks = _state.value.blocks
        val idx = blocks.indexOfFirst { it.key == key }
        if (idx < 0) return
        val cur = blocks[idx]
        if (cur.block.type != BlockType.PARAGRAPH) {
            replaceBlocks(FocusRequest(key, 0)) { list ->
                list.map { if (it.key == key) it.copy(block = it.block.copy(type = BlockType.PARAGRAPH)) else it }
            }
            return
        }
        if (idx == 0) return
        val prev = blocks[idx - 1]
        val merged = mergeBlocks(prev.block, cur.block)
        replaceBlocks(FocusRequest(prev.key, prev.block.text.length)) { list ->
            list.filter { it.key != key }.map { if (it.key == prev.key) it.copy(block = merged) else it }
        }
    }

    fun onToggleChecked(key: Long) {
        replaceBlocks { list ->
            list.map { if (it.key == key) it.copy(block = it.block.copy(checked = !it.block.checked)) else it }
        }
    }

    fun onBold() {
        val key = focusedKey ?: return
        if (selEnd <= selStart) return
        val start = selStart
        val end = selEnd
        replaceBlocks { list ->
            list.map { if (it.key == key) it.copy(block = it.block.toggleBold(start, end)) else it }
        }
    }

    /** Sets [type] on the focused block (or the last one); toggles back to a paragraph if it already has it. */
    fun onSetType(type: BlockType) {
        val blocks = _state.value.blocks
        val key = focusedKey?.takeIf { k -> blocks.any { it.key == k } } ?: blocks.lastOrNull()?.key ?: return
        replaceBlocks(FocusRequest(key, if (key == focusedKey) selEnd else Int.MAX_VALUE)) { list ->
            list.map {
                if (it.key == key) {
                    val newType = if (it.block.type == type) BlockType.PARAGRAPH else type
                    it.copy(block = it.block.copy(type = newType))
                } else {
                    it
                }
            }
        }
    }

    private fun replaceBlocks(focus: FocusRequest? = null, f: (List<EditorBlock>) -> List<EditorBlock>) {
        _state.update { it.copy(blocks = f(it.blocks), focusRequest = focus ?: it.focusRequest) }
        markDirty(DIRTY_DOC)
    }

    // ---- Page properties ----

    fun onTogglePin() {
        val pinned = !_state.value.pinned
        _state.update { it.copy(pinned = pinned) }
        viewModelScope.launch { pages.setPinned(noteId, pinned) }
    }

    fun onSetFolder(folderId: Long?) {
        _state.update { it.copy(folderId = folderId) }
        viewModelScope.launch { pages.moveToFolder(noteId, folderId) }
    }

    fun onCreateFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val folder = pages.createFolder(name)
            onSetFolder(folder.id)
        }
    }

    fun onToggleTag(tagId: Long) {
        viewModelScope.launch {
            if (tagId in _state.value.tagIds) pages.removeTag(noteId, tagId) else pages.addTag(noteId, tagId)
        }
    }

    fun onCreateTag(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val tag = pages.getOrCreateTag(name)
            pages.addTag(noteId, tag.id)
        }
    }

    fun delete(onDone: () -> Unit) {
        deleted = true
        debounceJob?.cancel()
        viewModelScope.launch {
            saveMutex.withLock { pages.deletePage(noteId) }
            onDone()
        }
    }

    // ---- Autosave ----

    private fun markDirty(what: String) {
        synchronized(dirty) { dirty += what }
        debounceJob?.cancel()
        debounceJob = saveScope.launch {
            delay(DEBOUNCE_MS)
            withContext(NonCancellable) { flush() }
        }
    }

    /** Writes pending edits now (leaving the screen, app to background). */
    fun flushNow() {
        debounceJob?.cancel()
        saveScope.launch { flush() }
    }

    private suspend fun flush() = saveMutex.withLock {
        if (!loaded || deleted) return@withLock
        val parts = synchronized(dirty) { dirty.toList().also { dirty.clear() } }
        for (part in parts) {
            try {
                val st = _state.value
                if (part == DIRTY_TITLE) {
                    pages.setTitle(noteId, st.title)
                } else {
                    pages.saveBlockDocument(noteId, SectionKind.RICH_TEXT, BlockDocument(st.blocks.map { it.block }))
                }
            } catch (e: CancellationException) {
                synchronized(dirty) { dirty += part }
                throw e
            } catch (_: Exception) {
                synchronized(dirty) { dirty += part }
            }
        }
    }

    override fun onCleared() {
        flushNow()
    }

    companion object {
        private const val DEBOUNCE_MS = 500L
        private const val DIRTY_TITLE = "TITLE"
        private const val DIRTY_DOC = "DOC"

        fun factory(container: AppContainer, noteId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NoteEditorViewModel(container, noteId) as T
            }
    }
}
