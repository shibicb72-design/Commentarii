package com.shibi.commentarii.ui.notes

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.TextSpan
import com.shibi.commentarii.testutil.NotesTestBase
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist #181, #182, #185-#190, #192-#198, #205: typing and the format toolbar in the real editor, inside AppNav. Enter and
 * Backspace are real key events. Styles are read from the text layout, which is what the field really drew with.
 */
class NoteEditorBlocksFlowTest : NotesTestBase() {
    private fun para(text: String) = DocBlock(text = text)

    private fun open(title: String, vararg blocks: DocBlock): Long {
        val id = seedNote(title, *blocks)
        openNotesTab()
        await { exists(hasText(title)) }
        openNote(title, id)
        return id
    }

    private fun reopen(title: String, id: Long) {
        leaveEditor()
        await { exists(hasText(title)) }
        openNote(title, id)
    }

    private fun styleOf(index: Int): TextStyle = layoutOf(block(index).fetchSemanticsNode()).layoutInput.style

    private fun boldRanges(index: Int) = layoutOf(block(index).fetchSemanticsNode()).layoutInput.text.spanStyles
        .filter { it.item.fontWeight == FontWeight.Bold }
        .map { it.start to it.end }

    private fun checks() = rule.onAllNodes(isToggleable())

    private fun bullets() = count(hasText("•"))

    private fun tapBold() = tapDescription(boldIcon)

    private fun savedTypes(id: Long) = savedBlocks(id).map { it.type }

    /** Vertical middle of the first text line of a field. */
    private fun firstLineCenter(field: SemanticsNode): Float {
        val layout = layoutOf(field)
        return field.positionInRoot.y + (layout.getLineTop(0) + layout.getLineBottom(0)) / 2f
    }

    private fun assertCheckAlignedWithFirstLine(check: SemanticsNodeInteraction, index: Int) {
        assertEquals(
            "the check is centred on the first text line",
            firstLineCenter(block(index).fetchSemanticsNode()),
            centerY(check.fetchSemanticsNode()),
            dp(2),
        )
    }

    // ---- Title and Enter ----

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun item181_titleStaysOnOneLineWhenEnterIsPressed() {
        openNotesTab()
        val id = createNoteWithFab()
        titleField().performTextInput("Plan")
        rule.waitForIdle()
        titleField().assertIsFocused()

        titleField().performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()

        assertEquals("Plan", textOf(fields()[0]))
        assertEquals(1, layoutOf(titleField().fetchSemanticsNode()).lineCount)
        assertEquals("no block was added", listOf(""), shownBlocks())
        await { page(id)!!.title == "Plan" }
    }

    /**
     * The "saved after 500 ms" clause, with the screen left alone. `start` is taken before the edit, so the debounce can only end
     * later than start + 500 ms: a save seen any sooner means it does not wait that long.
     */
    @Test
    fun item181_titleIsSavedOnlyAfterTheDebounce() {
        openNotesTab()
        val id = createNoteWithFab()
        val start = System.nanoTime()
        titleField().performTextInput("Plan")

        val savedAfter = millisUntil(start) { page(id)!!.title == "Plan" }

        assertTrue("saved after only $savedAfter ms", savedAfter >= 500)
    }

    @Test
    fun item182_enterSplitsIntoTwoParagraphsAndMovesTheCursor() {
        openNotesTab()
        val id = createNoteWithFab()
        type(0, "one")
        pressEnter(block(0))

        assertEquals(listOf("one", ""), shownBlocks())
        block(1).assertIsFocused()
        assertEquals("cursor at the start of the new block", TextRange(0), selectionOf(block(1)))

        type(1, "two")
        assertEquals(listOf("one", "two"), shownBlocks())
        block(1).assertIsFocused()
        assertEquals(TextRange(3), selectionOf(block(1)))
        await { savedBlocks(id) == listOf(para("one"), para("two")) }
    }

    // ---- Headings ----

    @Test
    fun item185_h1ThenH2ThenH2AgainGoesBackToAParagraph() {
        val id = open("Note", para("text"))
        tapBlock(0)
        val paragraphSize = styleOf(0).fontSize

        tapToolbarText("H1")
        assertEquals(26.sp, styleOf(0).fontSize)
        assertEquals(FontWeight.Bold, styleOf(0).fontWeight)
        block(0).assertIsFocused()
        await { savedTypes(id) == listOf(BlockType.H1) }

        tapToolbarText("H2")
        assertEquals(21.sp, styleOf(0).fontSize)
        assertEquals(FontWeight.SemiBold, styleOf(0).fontWeight)
        await { savedTypes(id) == listOf(BlockType.H2) }

        tapToolbarText("H2")
        assertEquals("back to the paragraph size", paragraphSize, styleOf(0).fontSize)
        assertFalse("and no longer heavy", styleOf(0).fontWeight == FontWeight.Bold || styleOf(0).fontWeight == FontWeight.SemiBold)
        await { savedTypes(id) == listOf(BlockType.PARAGRAPH) }
        assertEquals("the text was kept", listOf("text"), savedTexts(id))
    }

    @Test
    fun item186_enterAtTheEndOfAHeadingStartsAPlainParagraph() {
        val id = open("Note", DocBlock(type = BlockType.H1, text = "Head"))
        tapBlock(0)
        select(0, 4)
        pressEnter(block(0))

        assertEquals(listOf("Head", ""), shownBlocks())
        assertEquals("the heading stays", 26.sp, styleOf(0).fontSize)
        assertFalse("the new block is not one", styleOf(1).fontSize == 26.sp)
        block(1).assertIsFocused()
        await { savedTypes(id) == listOf(BlockType.H1, BlockType.PARAGRAPH) }
    }

    // ---- Bold ----

    @Test
    fun item187_boldTogglesOnASelectionAndDoesNothingWithoutOne() {
        val id = open("Note", para("hello world"))
        tapBlock(0)

        select(0, 6, 11)
        tapBold()
        assertEquals("the word turns bold", listOf(6 to 11), boldRanges(0))
        await { savedBlocks(id).single().spans == listOf(TextSpan(6, 11)) }

        select(0, 6, 11)
        tapBold()
        assertEquals("and back", emptyList<Pair<Int, Int>>(), boldRanges(0))
        await { savedBlocks(id).single().spans.isEmpty() }

        select(0, 3)
        tapBold()
        settleFor(700)
        assertEquals("nothing selected, nothing changes", emptyList<Pair<Int, Int>>(), boldRanges(0))
        assertEquals(emptyList<TextSpan>(), savedBlocks(id).single().spans)
    }

    @Test
    fun item188_boldSpanIsStillOnTheSameCharactersAfterReopening() {
        val id = open("Note", para("hello world"))
        tapBlock(0)
        select(0, 6, 11)
        tapBold()
        await { savedBlocks(id).single().spans == listOf(TextSpan(6, 11)) }

        reopen("Note", id)

        assertEquals(listOf("hello world"), shownBlocks())
        assertEquals(listOf(6 to 11), boldRanges(0))
        assertEquals(listOf(TextSpan(6, 11)), savedBlocks(id).single().spans)
    }

    // ---- Lists ----

    @Test
    fun item189_bulletShowsItsPrefixAndEnterContinuesTheList() {
        val id = open("Note", para(""))
        tapBlock(0)
        assertEquals(0, bullets())

        tapDescription(bulletIcon)
        assertEquals(1, bullets())
        block(0).assertIsFocused()

        type(0, "first")
        pressEnter(block(0))

        assertEquals(listOf("first", ""), shownBlocks())
        assertEquals("another bullet", 2, bullets())
        block(1).assertIsFocused()
        await { savedTypes(id) == listOf(BlockType.BULLET, BlockType.BULLET) }
    }

    private fun assertEnterOnEmptyItemEndsTheList(type: BlockType, markers: () -> Int) {
        val id = open("Note", DocBlock(type = type, text = "first"), DocBlock(type = type))
        assertEquals(2, markers())
        tapBlock(1)
        pressEnter(block(1))

        assertEquals("no block was added", listOf("first", ""), shownBlocks())
        assertEquals("only the first item is still a list item", 1, markers())
        block(1).assertIsFocused()
        await { savedTypes(id) == listOf(type, BlockType.PARAGRAPH) }
    }

    @Test
    fun item190_enterOnAnEmptyBulletEndsTheList() = assertEnterOnEmptyItemEndsTheList(BlockType.BULLET, ::bullets)

    @Test
    fun item190_enterOnAnEmptyChecklistItemEndsTheList() =
        assertEnterOnEmptyItemEndsTheList(BlockType.CHECKBOX) { count(isToggleable()) }

    /** The bug's other door: a newline committed as text, the way a soft keyboard can send it, then typing carries on in that line. */
    @Test
    fun item190_committedNewlineOnAnEmptyItemEndsTheListAndTypingContinuesThere() {
        val id = open("Note", DocBlock(type = BlockType.BULLET, text = "first"), DocBlock(type = BlockType.BULLET))
        tapBlock(1)
        type(1, "\n")

        assertEquals("no block was added", listOf("first", ""), shownBlocks())
        assertEquals(1, bullets())
        block(1).assertIsFocused()

        type(1, "after")
        assertEquals(listOf("first", "after"), shownBlocks())
        await { savedBlocks(id) == listOf(DocBlock(type = BlockType.BULLET, text = "first"), para("after")) }
    }

    @Test
    fun item192_checklistButtonMakesItemsWithAlignedMaterialCheckboxes() {
        val id = open("Note", para(""))
        tapBlock(0)
        tapDescription(checklistIcon)
        block(0).assertIsFocused()

        type(0, "Milk")
        pressEnter(block(0))
        type(1, "Eggs")

        assertEquals(listOf("Milk", "Eggs"), shownBlocks())
        assertEquals(2, count(isToggleable()))
        val checkboxRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)
        assertEquals("real checkboxes", 2, count(isToggleable() and checkboxRole))
        assertCheckAlignedWithFirstLine(checks()[0], 0)
        assertCheckAlignedWithFirstLine(checks()[1], 1)
        await { savedTypes(id) == listOf(BlockType.CHECKBOX, BlockType.CHECKBOX) }
    }

    @Test
    fun item193_tickingGreysAndStrikesTheTextAndSurvivesReopening() {
        val id = open("Note", DocBlock(type = BlockType.CHECKBOX, text = "Milk"), DocBlock(type = BlockType.CHECKBOX, text = "Eggs"))
        checks()[0].assertIsOff()
        val normal = styleOf(0)
        assertEquals(TextDecoration.None, normal.textDecoration)
        assertEquals(1f, normal.color.alpha, 0.01f)

        checks()[0].performClick()
        rule.waitForIdle()
        checks()[0].assertIsOn()
        assertEquals("struck through", TextDecoration.LineThrough, styleOf(0).textDecoration)
        assertEquals("greyed to 60%", 0.6f, styleOf(0).color.alpha, 0.01f)
        assertEquals("the other item is left alone", TextDecoration.None, styleOf(1).textDecoration)
        await { savedBlocks(id)[0].checked }

        checks()[0].performClick()
        rule.waitForIdle()
        checks()[0].assertIsOff()
        assertEquals("restored", TextDecoration.None, styleOf(0).textDecoration)
        assertEquals(1f, styleOf(0).color.alpha, 0.01f)
        await { !savedBlocks(id)[0].checked }

        checks()[0].performClick()
        rule.waitForIdle()
        await { savedBlocks(id)[0].checked }
        reopen("Note", id)

        checks()[0].assertIsOn()
        checks()[1].assertIsOff()
        assertEquals(TextDecoration.LineThrough, styleOf(0).textDecoration)
        assertEquals(0.6f, styleOf(0).color.alpha, 0.01f)
        assertEquals(listOf(true, false), savedBlocks(id).map { it.checked })
    }

    @Test
    fun item194_checklistButtonOnAChecklistItemTurnsItBackIntoAParagraph() {
        val id = open("Note", DocBlock(type = BlockType.CHECKBOX, text = "Milk"))
        assertEquals(1, count(isToggleable()))
        tapBlock(0)
        tapDescription(checklistIcon)

        assertEquals(0, count(isToggleable()))
        assertEquals(listOf("Milk"), shownBlocks())
        await { savedTypes(id) == listOf(BlockType.PARAGRAPH) }
    }

    @Test
    fun item195_bulletButtonOnATickedItemMakesABulletAndHidesTheTick() {
        val id = open("Note", DocBlock(type = BlockType.CHECKBOX, text = "Milk", checked = true))
        assertEquals("control: struck through while it is a ticked item", TextDecoration.LineThrough, styleOf(0).textDecoration)
        tapBlock(0)
        tapDescription(bulletIcon)

        assertEquals(1, bullets())
        assertEquals("no checkbox", 0, count(isToggleable()))
        assertEquals("no strike-through", TextDecoration.None, styleOf(0).textDecoration)
        assertEquals("no grey", 1f, styleOf(0).color.alpha, 0.01f)
        assertEquals(listOf("Milk"), shownBlocks())
        await { savedTypes(id) == listOf(BlockType.BULLET) }
    }

    @Test
    fun item196_longChecklistItemWrapsAndItsCheckStaysOnTheFirstLine() {
        val long = "This checklist item goes on for long enough that it cannot fit on one line of the screen at all"
        open("Note", DocBlock(type = BlockType.CHECKBOX, text = long), DocBlock(type = BlockType.CHECKBOX, text = "short"))

        val field = block(0).fetchSemanticsNode()
        assertTrue("it wraps", layoutOf(field).lineCount > 1)
        assertTrue("inside the screen", field.boundsInRoot.right <= rule.onRoot().fetchSemanticsNode().boundsInRoot.right)
        assertCheckAlignedWithFirstLine(checks()[0], 0)
        val wholeFieldCenter = centerY(field)
        assertTrue(
            "control: the first line is not simply the field's middle",
            abs(wholeFieldCenter - centerY(checks()[0].fetchSemanticsNode())) > dp(4),
        )
    }

    /** Real clipboard and the field's own paste action; the long-press menu itself is left for a device. */
    @Test
    fun item197_pastingThreeLinesIntoAChecklistItemMakesThreeItems() {
        val id = open("Note", DocBlock(type = BlockType.CHECKBOX))
        tapBlock(0)
        rule.activity.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("list", "Milk\nEggs\nBread"))
        rule.waitForIdle()

        block(0).performSemanticsAction(SemanticsActions.PasteText)
        rule.waitForIdle()

        assertEquals(listOf("Milk", "Eggs", "Bread"), shownBlocks())
        assertEquals("each with its checkbox", 3, count(isToggleable()))
        block(2).assertIsFocused()
        await { savedTypes(id) == List(3) { BlockType.CHECKBOX } }
    }

    @Test
    fun item198_h1WithNothingFocusedGoesToTheLastBlock() {
        val id = open("Note", para("first"), para("last"))
        val paragraphSize = styleOf(0).fontSize

        tapToolbarText("H1")

        assertEquals("the last block gets it", 26.sp, styleOf(1).fontSize)
        assertEquals("the first does not", paragraphSize, styleOf(0).fontSize)
        await { savedTypes(id) == listOf(BlockType.PARAGRAPH, BlockType.H1) }
    }

    // ---- A long note ----

    private val toolbar get() = rule.onNodeWithTag(FORMAT_TOOLBAR_TAG)

    @Test
    fun item205_longNoteScrollsWithTheToolbarPinnedAndTypingAtTheEndStaysInView() {
        val blocks = Array(85) { para("Block $it") }
        open("Note", *blocks)
        assertEquals(85, shownBlocks().size)
        val pinned = bounds(toolbar)
        val lastAtStart = block(84).fetchSemanticsNode().positionInRoot.y
        assertTrue("the last block starts below the toolbar", lastAtStart > pinned.bottom)

        block(84).performScrollTo()
        rule.waitForIdle()
        assertTrue("the body moved", block(84).fetchSemanticsNode().positionInRoot.y < lastAtStart)
        assertInViewAboveToolbar(84, "after scrolling")
        assertEquals("the toolbar did not move", pinned, bounds(toolbar))

        tapBlock(84)
        assertTypingAtTheEndStaysInView(lastIndex = 84, times = 12)
    }
}
