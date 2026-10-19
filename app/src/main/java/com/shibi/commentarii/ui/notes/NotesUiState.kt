package com.shibi.commentarii.ui.notes

import androidx.compose.runtime.Immutable
import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.FolderEntity
import com.shibi.commentarii.data.TagEntity

@Immutable
data class NoteListItem(
    val id: Long,
    val title: String,
    val snippet: String,
    val pinned: Boolean,
    val folderId: Long? = null,
    val tags: List<TagEntity> = emptyList(),
)

@Immutable
data class NotesUiState(
    val loaded: Boolean = false,
    val notes: List<NoteListItem> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val tags: List<TagEntity> = emptyList(),
    val selectedFolderId: Long? = null,
    val selectedTagId: Long? = null,
) {
    /** Filtered by the selected folder and tag; pinned notes first (stable otherwise). */
    fun visibleNotes(): List<NoteListItem> = notes
        .filter { selectedFolderId == null || it.folderId == selectedFolderId }
        .filter { n -> selectedTagId == null || n.tags.any { it.id == selectedTagId } }
        .sortedByDescending { it.pinned }
}

class NotesActions(
    val onOpenNote: (Long) -> Unit = {},
    val onNewNote: () -> Unit = {},
    val onSelectFolder: (Long?) -> Unit = {},
    val onSelectTag: (Long?) -> Unit = {},
)

@Immutable
data class EditorBlock(val key: Long, val block: DocBlock)

@Immutable
data class FocusRequest(val key: Long, val cursor: Int)

@Immutable
data class NoteEditorUiState(
    val loaded: Boolean = false,
    val notFound: Boolean = false,
    val title: String = "",
    val blocks: List<EditorBlock> = emptyList(),
    val pinned: Boolean = false,
    val folderId: Long? = null,
    val folders: List<FolderEntity> = emptyList(),
    val allTags: List<TagEntity> = emptyList(),
    val tagIds: Set<Long> = emptySet(),
    val focusRequest: FocusRequest? = null,
)

class NoteEditorActions(
    val onBack: () -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onBlockText: (key: Long, text: String) -> Unit = { _, _ -> },
    val onSelection: (key: Long, start: Int, end: Int) -> Unit = { _, _, _ -> },
    val onFocusHandled: () -> Unit = {},
    val onToggleChecked: (key: Long) -> Unit = {},
    val onBackspaceAtStart: (key: Long) -> Unit = {},
    val onBold: () -> Unit = {},
    val onSetType: (BlockType) -> Unit = {},
    val onTogglePin: () -> Unit = {},
    val onSetFolder: (Long?) -> Unit = {},
    val onCreateFolder: (String) -> Unit = {},
    val onToggleTag: (Long) -> Unit = {},
    val onCreateTag: (String) -> Unit = {},
    val onDelete: () -> Unit = {},
)
