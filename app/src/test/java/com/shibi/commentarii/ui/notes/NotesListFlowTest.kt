package com.shibi.commentarii.ui.notes

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.testutil.NotesTestBase
import com.shibi.commentarii.ui.Routes
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checklist #169-#180: the real Notes list inside AppNav, with notes in the real database. */
class NotesListFlowTest : NotesTestBase() {
    private val empty get() = str(R.string.nt_empty)
    private val emptyFiltered get() = str(R.string.nt_empty_filtered)
    private val all get() = str(R.string.nt_all)
    private val pinned get() = str(R.string.nt_pinned)
    private val untitled get() = str(R.string.nt_untitled)

    private val chipRow = SemanticsMatcher("a sideways list") {
        SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.HorizontalScrollAxisRange in it.config
    }

    private fun para(text: String) = DocBlock(text = text)

    private fun chip(name: String) = rule.onNode(hasText(name) and hasAnyAncestor(chipRow))

    private fun tapChip(name: String) {
        chip(name).performClick()
        rule.waitForIdle()
    }

    private fun cardTop(title: String) = rule.onNode(hasText(title)).fetchSemanticsNode().positionInRoot.y

    private fun order(vararg titles: String) = titles.sortedBy { cardTop(it) }

    private fun shows(title: String) = exists(hasText(title))

    private fun textNode(text: String) = rule.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode()

    @Test
    fun item169_emptyStateHasTheTitleCentredTextAndTheNewNoteButton() {
        openNotesTab()
        await { shows(empty) }

        val title = hasText(str(R.string.nt_title)) and !anyTab
        rule.onNode(title).assertIsDisplayed()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val text = bounds(rule.onNode(hasText(empty)))
        val fab = bounds(rule.onNode(hasContentDescription(newNote)))
        val tab = bounds(rule.onNode(tab(R.string.tab_notes)))
        val bodyBottom = fab.bottom + dp(16)
        val titleBottom = bounds(rule.onNode(title)).bottom

        assertEquals("centred across the width", root.width / 2, (text.left + text.right) / 2, dp(1))
        assertEquals("centred in the space below the title", (titleBottom + bodyBottom) / 2, (text.top + text.bottom) / 2, dp(2))
        assertEquals("the button sits at the right edge", root.right - dp(16), fab.right, dp(1))
        assertTrue("and at the bottom, just above the bar", fab.bottom <= tab.top && tab.top - fab.bottom < dp(40))
        assertFalse("no filter chips without folders or tags", exists(chipRow))
        assertFalse(shows(all))
    }

    /** Control for #169: the same screen does show the rows once there is something to filter by. */
    @Test
    fun item169_filterRowsAppearForAFolderAndForATag() {
        seedFolder("Work")
        runBlocking { container.pages.getOrCreateTag("idea") }
        openNotesTab()
        await { exists(chipRow) }

        assertEquals("one row each", 2, count(chipRow))
        chip(all).assertIsDisplayed()
        chip("Work").assertIsDisplayed()
        chip("#idea").assertIsDisplayed()
    }

    @Test
    fun item170_newNoteButtonCreatesANoteAndOpensTheEditor() {
        openNotesTab()
        await { shows(empty) }
        val id = createNoteWithFab()

        assertEquals("note/$id", routes.last())
        assertEquals(listOf(id), runBlocking { container.pages.observeNotes().first() }.map { it.id })
        for (control in listOf(backArrow, pinNote, organize, deleteNote)) {
            rule.onNode(hasContentDescription(control)).assertIsDisplayed()
        }
        rule.onNode(hasText(str(R.string.ed_title_hint)) and hasSetTextAction()).assertIsDisplayed()
        rule.onNode(hasText(str(R.string.ed_body_hint)) and hasSetTextAction()).assertIsDisplayed()
    }

    @Test
    fun item171_leavingANewNoteUntouchedLeavesAnUntitledCard() {
        openNotesTab()
        await { shows(empty) }
        val id = createNoteWithFab()
        leaveEditor()

        await { shows(untitled) }
        assertFalse("the list is no longer empty", shows(empty))
        assertEquals("", page(id)!!.title)
        assertEquals("one card", 1, count(hasText(untitled)))
    }

    @Test
    fun item172_cardShowsOneLineTitleSnippetWithSpacesAndTags() {
        val longTitle = "A long title that cannot possibly fit on a single line of a phone screen at this size"
        seedNote(longTitle, para("line one"), para("line two"), para("line three"), tags = listOf("tag1", "tag2"))
        openNotesTab()
        await { shows(longTitle) }

        val titleLayout = layoutOf(textNode(longTitle))
        assertEquals("the title keeps to one line", 1, titleLayout.lineCount)
        assertTrue("and ends in an ellipsis", titleLayout.isLineEllipsized(0))
        assertTrue("newlines become spaces", shows("line one line two line three"))
        assertTrue("tags are joined with two spaces", shows("#tag1  #tag2"))
    }

    @Test
    fun item172_snippetStopsAt140CharactersAndTwoLines() {
        val body = "word ".repeat(60).trim()
        seedNote("Long body", para(body))
        openNotesTab()
        await { shows("Long body") }

        val expected = body.take(140)
        assertTrue("the snippet is the first 140 characters", shows(expected))
        val layout = layoutOf(textNode(expected))
        assertEquals("at most two lines", 2, layout.lineCount)
        assertTrue("the rest is cut with an ellipsis", layout.isLineEllipsized(1))
    }

    @Test
    fun item173_editingAnOlderNoteMovesItUpButPinnedStaysFirst() {
        seedNote("Pinned one", pinned = true)
        val older = seedNote("Older", para("body"))
        seedNote("Newer", para("body"))
        openNotesTab()
        await { shows("Newer") }
        assertEquals(listOf("Pinned one", "Newer", "Older"), order("Older", "Newer", "Pinned one"))

        openNote("Older", older)
        type(0, "edited ")
        leaveEditor()

        await { order("Older", "Newer", "Pinned one") == listOf("Pinned one", "Older", "Newer") }
    }

    @Test
    fun item174_pinningInTheEditorMovesTheCardUpAndShowsTheIcon() {
        val older = seedNote("Older", para("body"))
        seedNote("Newer", para("body"))
        openNotesTab()
        await { shows("Newer") }
        assertEquals(listOf("Newer", "Older"), order("Older", "Newer"))
        assertFalse("nothing is pinned yet", exists(hasContentDescription(pinned)))

        openNote("Older", older)
        tapDescription(pinNote)
        leaveEditor()

        await { exists(hasText("Older") and hasContentDescription(pinned)) }
        assertEquals("only that card", 1, count(hasContentDescription(pinned)))
        assertEquals(listOf("Older", "Newer"), order("Older", "Newer"))
    }

    @Test
    fun item175_folderChipFiltersAndTappingItAgainOrAllClearsIt() {
        val work = seedFolder("Work")
        val home = seedFolder("Home")
        seedNote("Report", folderId = work)
        seedNote("Garden", folderId = home)
        seedNote("Loose")
        openNotesTab()
        await { shows("Report") && exists(chipRow) }
        chip(all).assertIsSelected()

        tapChip("Work")
        await { !shows("Garden") }
        assertTrue(shows("Report"))
        assertFalse(shows("Loose"))
        chip("Work").assertIsSelected()
        chip(all).assertIsNotSelected()

        tapChip("Work")
        await { shows("Garden") && shows("Loose") }
        chip(all).assertIsSelected()
        chip("Work").assertIsNotSelected()

        tapChip("Home")
        await { !shows("Report") }
        tapChip(all)
        await { shows("Report") && shows("Garden") && shows("Loose") }
        chip(all).assertIsSelected()
        chip("Home").assertIsNotSelected()
    }

    @Test
    fun item176_tagChipFiltersAndTappingItAgainClearsIt() {
        seedNote("Tagged", tags = listOf("idea"))
        seedNote("Other", tags = listOf("later"))
        seedNote("Plain")
        openNotesTab()
        await { shows("Tagged") && exists(chipRow) }
        chip("#idea").assertIsNotSelected()

        tapChip("#idea")
        await { !shows("Other") }
        assertTrue(shows("Tagged"))
        assertFalse(shows("Plain"))
        chip("#idea").assertIsSelected()

        tapChip("#idea")
        await { shows("Other") && shows("Plain") }
        chip("#idea").assertIsNotSelected()
    }

    @Test
    fun item177_folderAndTagTogetherNarrowToBothAndSayWhenNothingMatches() {
        val work = seedFolder("Work")
        val home = seedFolder("Home")
        seedNote("Both", folderId = work, tags = listOf("idea"))
        seedNote("Folder only", folderId = work)
        seedNote("Tag only", tags = listOf("idea"))
        seedNote("Elsewhere", folderId = home)
        openNotesTab()
        await { shows("Both") && exists(chipRow) }

        tapChip("Work")
        await { !shows("Tag only") }
        tapChip("#idea")
        await { !shows("Folder only") }
        assertTrue("the note in both stays", shows("Both"))
        assertFalse(shows("Elsewhere"))
        assertFalse(shows(emptyFiltered))

        tapChip("Home")
        await { shows(emptyFiltered) }
        assertFalse(shows("Both"))
        assertFalse("not the empty text for no notes at all", shows(empty))
    }

    @Test
    fun item178_folderMadeOnADailyPageIsAChipButTheDailyPageIsNotListed() {
        openApp()
        tapInDialog(str(R.string.daily_no_folder))
        rule.onNode(hasText(str(R.string.daily_new_folder)) and hasAnyAncestor(isPopup())).performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction() and inDialog).performTextInput("Shared")
        tapInDialog(str(R.string.action_ok))
        await { shows("Shared") }
        val folderId = runBlocking { container.pages.getFolders() }.single().id
        assertEquals("the daily page really is in it", folderId, page(pageId(today))!!.folderId)

        tapTab(R.string.tab_notes)
        await { routes.lastOrNull() == Routes.NOTES && exists(chipRow) && shows(empty) }
        chip("Shared").assertIsDisplayed()

        tapChip("Shared")
        await { shows(emptyFiltered) }

        // Control: a note in the same folder does show, so the daily page is what the list leaves out.
        seedNote("In shared", folderId = folderId)
        await { shows("In shared") }
        assertEquals("the only card", listOf("In shared"), runBlocking { container.pages.observeNotes().first() }.map { it.title })
    }

    @Test
    fun item179_manyFoldersAndTagsScrollSidewaysAndSortIgnoringCase() {
        val names = listOf("alpha", "Bravo", "charlie", "Delta", "echo", "Foxtrot", "golf", "Hotel", "india", "Juliet")
        names.reversed().forEach { seedFolder(it) }
        runBlocking { names.reversed().forEach { container.pages.getOrCreateTag("t$it") } }
        openNotesTab()
        await { count(chipRow) == 2 }

        val rows = rule.onAllNodes(chipRow)
        for (i in 0..1) {
            assertTrue("row $i has more than fits", rows[i].fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0f)
        }
        assertFalse("the last folder starts out of view", exists(hasText("Juliet")))
        assertFalse("and so does the last tag", exists(hasText("#tJuliet")))

        assertEquals("folders A-Z, whatever the case", names, chipsInOrder(0, names, itemCount = 11))
        val tags = names.map { "#t$it" }
        assertEquals("tags too", tags, chipsInOrder(1, tags, itemCount = 10))
        assertTrue("scrolling reached the last folder", exists(hasText("Juliet")))
        assertTrue("and the last tag", exists(hasText("#tJuliet")))
    }

    /** Walks a row from start to end. Only chips near the view exist, so each step adds the ones it can see, left to right. */
    private fun chipsInOrder(row: Int, names: List<String>, itemCount: Int): List<String> {
        val seen = LinkedHashSet<String>()
        for (index in 0 until itemCount) {
            rule.onAllNodes(chipRow)[row].performScrollToIndex(index)
            rule.waitForIdle()
            seen += names.filter { exists(hasText(it)) }.sortedBy { rule.onNode(hasText(it)).fetchSemanticsNode().positionInRoot.x }
        }
        return seen.toList()
    }

    @Test
    fun item180_newTagAndBodyTextShowOnTheCardOnReturn() {
        val id = seedNote("Keep", para("old body"))
        openNotesTab()
        await { shows("old body") }
        assertFalse(shows("#fresh"))

        openNote("Keep", id)
        block(0).performTextClearance()
        block(0).performTextInput("new body")
        rule.waitForIdle()
        openOrganize()
        addTag("fresh")
        await { savedTags(id) == listOf("fresh") }
        tapDone()
        leaveEditor()

        await { shows("new body") && shows("#fresh") }
        assertFalse(shows("old body"))
        assertTrue("on that card", abs(cardTop("Keep") - cardTop("new body")) < 1f)
    }
}
