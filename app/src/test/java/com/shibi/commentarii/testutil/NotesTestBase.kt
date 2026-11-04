package com.shibi.commentarii.testutil

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import com.shibi.commentarii.R
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.PageEntity
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.ui.Routes
import com.shibi.commentarii.ui.notes.FORMAT_TOOLBAR_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * The real Notes list and note editor on the real container, inside AppNav unless a test says otherwise. Seeds through
 * container.pages and reads saved data back from there, so nothing here relies on a view model's memory. Fields are found by
 * position in the editor (title first, then the blocks top to bottom), because empty blocks have nothing else to match on.
 */
abstract class NotesTestBase : DailyPageTestBase() {
    protected val newNote get() = str(R.string.nt_new)
    protected val backArrow get() = str(R.string.ed_back)
    protected val pinNote get() = str(R.string.ed_pin)
    protected val unpinNote get() = str(R.string.ed_unpin)
    protected val organize get() = str(R.string.ed_organize)
    protected val deleteNote get() = str(R.string.ed_delete)
    protected val boldIcon get() = str(R.string.ed_bold)
    protected val bulletIcon get() = str(R.string.ed_bullet)
    protected val checklistIcon get() = str(R.string.ed_checkbox)

    protected val anyTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    protected val inDialog = hasAnyAncestor(isDialog())

    // ---- Seeding and reading back ----

    protected fun seedNote(
        title: String,
        vararg blocks: DocBlock,
        pinned: Boolean = false,
        folderId: Long? = null,
        tags: List<String> = emptyList(),
    ): Long = runBlocking {
        val pages = container.pages
        val id = pages.createNote(title).id
        if (blocks.isNotEmpty()) pages.saveBlockDocument(id, SectionKind.RICH_TEXT, BlockDocument(blocks.toList()))
        if (pinned) pages.setPinned(id, true)
        if (folderId != null) pages.moveToFolder(id, folderId)
        tags.forEach { pages.addTag(id, pages.getOrCreateTag(it).id) }
        // updatedAt has millisecond precision, and the list's order depends on it.
        Thread.sleep(3)
        id
    }

    protected fun seedFolder(name: String): Long = runBlocking { container.pages.createFolder(name).id }

    protected fun page(id: Long): PageEntity? = runBlocking { container.pages.getPage(id) }

    protected fun savedBlocks(id: Long): List<DocBlock> =
        runBlocking { container.pages.getBlockDocument(id, SectionKind.RICH_TEXT) }.blocks

    protected fun savedTexts(id: Long) = savedBlocks(id).map { it.text }

    protected fun savedTags(id: Long) = runBlocking { container.pages.getTagsForPage(id) }.map { it.name }

    /**
     * Milliseconds from [start] until [condition] holds, polling the database directly. The debounce runs on real time on
     * Dispatchers.Default, so neither the compose clock nor the looper moves it, and this needs neither of them.
     */
    protected fun millisUntil(start: Long, condition: () -> Boolean): Long {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "never saved" }
            Thread.sleep(1)
        }
        return (System.nanoTime() - start) / 1_000_000
    }

    // ---- Moving around in AppNav ----

    protected fun openNotesTab() {
        openApp()
        tapTab(R.string.tab_notes)
        await { routes.lastOrNull() == Routes.NOTES && exists(hasContentDescription(newNote)) }
    }

    /** Taps the card and waits until a new or cached view model has put the note on screen. */
    protected fun openNote(title: String, id: Long) {
        rule.onNode(hasText(title)).performClick()
        rule.waitForIdle()
        await { routes.lastOrNull() == Routes.note(id) }
        awaitEditor()
    }

    /** Taps the + button and returns the id of the note it opened. */
    protected fun createNoteWithFab(): Long {
        rule.onNode(hasContentDescription(newNote)).performClick()
        rule.waitForIdle()
        await { routes.lastOrNull()?.startsWith("note/") == true }
        awaitEditor()
        return routes.last().removePrefix("note/").toLong()
    }

    protected fun leaveEditor() {
        tapDescription(backArrow)
        await { routes.lastOrNull() == Routes.NOTES && exists(hasContentDescription(newNote)) }
    }

    // ---- The editor's fields ----

    private val editorField get() = hasSetTextAction() and !inDialog

    /** Blocks are only composed once the note has loaded; until then only the title field is there. */
    protected fun awaitEditor() = await { count(editorField) >= 2 }

    protected fun fields(): List<SemanticsNode> = rule.onAllNodes(editorField).fetchSemanticsNodes()

    protected fun titleField(): SemanticsNodeInteraction = rule.onAllNodes(editorField)[0]

    protected fun block(index: Int): SemanticsNodeInteraction = rule.onAllNodes(editorField)[index + 1]

    protected fun textOf(node: SemanticsNode) = node.config[SemanticsProperties.EditableText].text

    /** How far the editor's body has scrolled. */
    protected fun bodyScroll(): Float = rule.onNode(hasScrollAction() and !inDialog).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()

    /**
     * Enter then typing at the end of a long note, [times] times from block [lastIndex]: each new line must be in view above the
     * toolbar, the toolbar must not move, and the body must really have scrolled to get it there.
     */
    protected fun assertTypingAtTheEndStaysInView(lastIndex: Int, times: Int) {
        val toolbar = rule.onNodeWithTag(FORMAT_TOOLBAR_TAG)
        val pinned = bounds(toolbar)
        val scrollBefore = bodyScroll()
        for (i in 0 until times) {
            val last = lastIndex + i
            pressEnter(block(last))
            settleAnimations()
            block(last + 1).assertIsFocused()
            assertInViewAboveToolbar(last + 1, "after Enter $i")
            type(last + 1, "typed $i")
            settleAnimations()
            assertInViewAboveToolbar(last + 1, "after typing $i")
        }
        assertEquals("the toolbar stayed put throughout", pinned, bounds(toolbar))
        assertTrue("the body scrolled to follow", bodyScroll() > scrollBefore)
    }

    protected fun assertInViewAboveToolbar(index: Int, what: String) {
        val field = block(index).fetchSemanticsNode()
        val top = field.positionInRoot.y
        val bottom = top + field.size.height
        val toolbarTop = bounds(rule.onNodeWithTag(FORMAT_TOOLBAR_TAG)).top
        assertTrue("$what: bottom $bottom must be above the toolbar's top $toolbarTop", bottom <= toolbarTop + 1f)
        assertTrue("$what: top $top must be on screen", top >= 0f)
    }

    protected fun shownBlocks(): List<String> = fields().drop(1).map(::textOf)

    protected fun selectionOf(interaction: SemanticsNodeInteraction): TextRange =
        interaction.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    protected fun type(index: Int, text: String) {
        block(index).performTextInput(text)
        rule.waitForIdle()
    }

    /** Puts the cursor in block [index] by tapping it, as a user does before using the toolbar. */
    protected fun tapBlock(index: Int) {
        block(index).performClick()
        rule.waitForIdle()
    }

    protected fun select(index: Int, start: Int, end: Int = start) {
        block(index).performTextInputSelection(TextRange(start, end))
        rule.waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    protected fun pressEnter(interaction: SemanticsNodeInteraction) {
        interaction.performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
    }

    /** Hardware Backspace with the cursor at offset 0 of block [index]; the only path that reaches onBackspaceAtStart here. */
    @OptIn(ExperimentalTestApi::class)
    protected fun backspaceAtStartOf(index: Int) {
        block(index).performClick()
        select(index, 0)
        block(index).performKeyInput { pressKey(Key.Backspace) }
        rule.waitForIdle()
    }

    /** Hardware Backspace wherever the cursor already is, for checks that it stayed put after the last one. */
    @OptIn(ExperimentalTestApi::class)
    protected fun pressBackspace(index: Int) {
        block(index).performKeyInput { pressKey(Key.Backspace) }
        rule.waitForIdle()
    }

    protected fun tapToolbarText(text: String) {
        rule.onNode(hasText(text)).performClick()
        rule.waitForIdle()
    }

    protected fun openOrganize() {
        tapDescription(organize)
        await { exists(hasText(str(R.string.action_done))) }
    }

    protected fun tapDone() {
        rule.onNode(hasText(str(R.string.action_done))).performClick()
        rule.waitForIdle()
    }

    private fun dialogField(label: Int) = rule.onNode(hasText(str(label)) and hasSetTextAction() and inDialog)

    protected fun folderField() = dialogField(R.string.ed_new_folder)

    protected fun tagField() = dialogField(R.string.ed_new_tag)

    /** The dialog's two Add buttons look the same: the first belongs to the folder field, the second to the tag field. */
    protected fun addButton(index: Int) = rule.onAllNodes(hasText(str(R.string.action_add)) and inDialog)[index]

    protected fun addFolder(name: String) {
        folderField().performTextInput(name)
        rule.waitForIdle()
        addButton(0).performClick()
        rule.waitForIdle()
    }

    protected fun addTag(name: String) {
        tagField().performTextInput(name)
        rule.waitForIdle()
        addButton(1).performClick()
        rule.waitForIdle()
    }
}
