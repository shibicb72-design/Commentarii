package com.shibi.commentarii.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.data.FolderEntity
import com.shibi.commentarii.data.PageEntity
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TagEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotesViewModel(container: AppContainer) : ViewModel() {
    private val pages = container.pages
    private val filters = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    private val refreshTick = MutableStateFlow(0)

    private class Snapshot(
        val notes: List<PageEntity>,
        val folders: List<FolderEntity>,
        val tags: List<TagEntity>,
        val filters: Pair<Long?, Long?>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<NotesUiState> = combine(
        pages.observeNotes(),
        pages.observeFolders(),
        pages.observeTags(),
        filters,
        refreshTick,
    ) { notes, folders, tags, f, _ -> Snapshot(notes, folders, tags, f) }
        .mapLatest { snap ->
            val items = snap.notes.map { page ->
                val snippet = pages.getBlockDocument(page.id, SectionKind.RICH_TEXT)
                    .flattenText().replace('\n', ' ').take(SNIPPET_LENGTH)
                NoteListItem(
                    id = page.id,
                    title = page.title,
                    snippet = snippet,
                    pinned = page.pinned,
                    folderId = page.folderId,
                    tags = pages.getTagsForPage(page.id),
                )
            }
            // A deleted folder/tag must not leave a stale filter selected.
            val folderSel = snap.filters.first?.takeIf { id -> snap.folders.any { it.id == id } }
            val tagSel = snap.filters.second?.takeIf { id -> snap.tags.any { it.id == id } }
            NotesUiState(true, items, snap.folders, snap.tags, folderSel, tagSel)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun selectFolder(id: Long?) = filters.update { it.copy(first = id) }

    fun selectTag(id: Long?) = filters.update { it.copy(second = id) }

    /** Re-reads tags and snippets (tag changes in the editor do not touch the pages table). */
    fun refresh() = refreshTick.update { it + 1 }

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val note = pages.createNote("")
            onCreated(note.id)
        }
    }

    companion object {
        private const val SNIPPET_LENGTH = 140

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = NotesViewModel(container) as T
        }
    }
}
