package com.shibi.commentarii.ui.notes

import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.FolderEntity
import com.shibi.commentarii.data.TagEntity
import com.shibi.commentarii.data.TextSpan

private val sampleFolders = listOf(FolderEntity(1, "Home"), FolderEntity(2, "Work"), FolderEntity(3, "Ideas"))
private val sampleTags = listOf(TagEntity(1, "recipes"), TagEntity(2, "gift"), TagEntity(3, "travel"))

/** Sample data for previews and screenshot tests. */
fun sampleNotesUiState(
    selectedFolderId: Long? = null,
    selectedTagId: Long? = null,
): NotesUiState = NotesUiState(
    loaded = true,
    notes = listOf(
        NoteListItem(1, "Packing list", "Passport, charger, sunscreen, the blue scarf", true, 1, listOf(sampleTags[2])),
        NoteListItem(2, "Birthday ideas", "A pressed-flower frame, tea sampler, handwritten card", true, 3, listOf(sampleTags[1])),
        NoteListItem(3, "Lemon cake", "Zest two lemons, cream the butter and sugar until pale", false, 1, listOf(sampleTags[0])),
        NoteListItem(4, "Meeting notes", "Agenda, budget review, follow-ups for next week", false, 2),
        NoteListItem(5, "", "", false),
    ),
    folders = sampleFolders,
    tags = sampleTags,
    selectedFolderId = selectedFolderId,
    selectedTagId = selectedTagId,
)

/** Sample editor state with every block type and a bold span. */
fun sampleNoteEditorUiState(): NoteEditorUiState = NoteEditorUiState(
    loaded = true,
    title = "Weekend plans",
    blocks = listOf(
        EditorBlock(1, DocBlock(BlockType.H1, "Saturday")),
        EditorBlock(
            2,
            DocBlock(BlockType.PARAGRAPH, "Slow morning, then the farmers market.", spans = listOf(TextSpan(24, 38))),
        ),
        EditorBlock(3, DocBlock(BlockType.H2, "Shopping")),
        EditorBlock(4, DocBlock(BlockType.BULLET, "Lemons and thyme")),
        EditorBlock(5, DocBlock(BlockType.BULLET, "Fresh bread")),
        EditorBlock(6, DocBlock(BlockType.CHECKBOX, "Call mum", checked = true)),
        EditorBlock(7, DocBlock(BlockType.CHECKBOX, "Book the train", checked = false)),
    ),
    pinned = true,
    folderId = 1,
    folders = sampleFolders,
    allTags = sampleTags,
    tagIds = setOf(3L),
)
