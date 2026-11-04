package com.shibi.commentarii.ui.notes

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.text.TextRange
import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.testutil.NotesTestBase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Checklist #183, #184, #191, hardware key path only: Backspace at offset 0 through the real NoteEditorScreen and view model. A
 * soft keyboard may not send a key event there at all, which only a real keyboard can show (see BackspaceAtBlockStartTest), so
 * these items stay NEEDS-DEVICE and these tests are their hardware coverage.
 */
class NoteEditorBackspaceFlowTest : NotesTestBase() {
    private fun open(vararg blocks: DocBlock): Long {
        val id = seedNote("Note", *blocks)
        openNotesTab()
        await { exists(hasText("Note")) }
        openNote("Note", id)
        return id
    }

    private fun para(text: String) = DocBlock(text = text)

    @Test
    fun item183_backspaceAtTheStartMergesIntoThePreviousBlockWithTheCursorAtTheJoin() {
        val id = open(para("one"), para("two"))
        backspaceAtStartOf(1)

        assertEquals(listOf("onetwo"), shownBlocks())
        block(0).assertIsFocused()
        assertEquals("cursor at the join", TextRange(3), selectionOf(block(0)))
        await { savedTexts(id) == listOf("onetwo") }
    }

    /** Control for #183: the same key away from the start only deletes a character. */
    @Test
    fun item183_backspaceAwayFromTheStartDeletesACharacterAndDoesNotMerge() {
        val id = open(para("one"), para("two"))
        tapBlock(1)
        select(1, 2)
        pressBackspace(1)

        assertEquals(listOf("one", "to"), shownBlocks())
        await { savedTexts(id) == listOf("one", "to") }
    }

    @Test
    fun item184_backspaceAtTheStartOfTheFirstParagraphDoesNothing() {
        val id = open(para("one"), para("two"))
        backspaceAtStartOf(0)
        settleFor(700)

        assertEquals(listOf("one", "two"), shownBlocks())
        assertEquals("nothing to save either", listOf("one", "two"), savedTexts(id))
        assertEquals(TextRange(0), selectionOf(block(0)))

        // Control: the same helper does merge on the block after it, so the key really arrived.
        backspaceAtStartOf(1)
        assertEquals(listOf("onetwo"), shownBlocks())
    }

    private fun assertListItemTurnsIntoParagraphThenMerges(type: BlockType) {
        val id = open(para("one"), DocBlock(type = type, text = "two"))
        backspaceAtStartOf(1)

        assertEquals("keeps its text", listOf("one", "two"), shownBlocks())
        assertEquals(0, count(hasText("•")))
        assertEquals(0, count(isToggleable()))
        block(1).assertIsFocused()
        assertEquals(TextRange(0), selectionOf(block(1)))
        await { savedBlocks(id).map { it.type } == listOf(BlockType.PARAGRAPH, BlockType.PARAGRAPH) }

        pressBackspace(1)
        assertEquals("the second one merges", listOf("onetwo"), shownBlocks())
        await { savedTexts(id) == listOf("onetwo") }
    }

    @Test
    fun item191_bulletTurnsIntoParagraphThenSecondBackspaceMerges() = assertListItemTurnsIntoParagraphThenMerges(BlockType.BULLET)

    @Test
    fun item191_checklistItemTurnsIntoParagraphThenSecondBackspaceMerges() =
        assertListItemTurnsIntoParagraphThenMerges(BlockType.CHECKBOX)

    /** A list item as the very first block: it still becomes a paragraph, and only then is there nothing to merge into. */
    @Test
    fun item191_firstBlockListItemBecomesAParagraphAndThenStops() {
        val id = open(DocBlock(type = BlockType.BULLET, text = "one"))
        backspaceAtStartOf(0)
        await { savedBlocks(id).single().type == BlockType.PARAGRAPH }

        pressBackspace(0)
        assertEquals(listOf("one"), shownBlocks())
    }
}
