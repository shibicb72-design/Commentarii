package com.shibi.commentarii.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.NotesTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist #299, #300: 60-character folder and tag names and a 200-character title on the real screens. A chip holds one line,
 * ellipsized or inside a sideways scroller, and nothing goes past the window where it cannot be scrolled to.
 */
class LongNamesFlowTest : NotesTestBase() {
    private val folder = "Folder with a very long name that keeps going on and on 1234".also { check(it.length == 60) }
    private val tag = "tagwithaverylongnamethatkeepsgoingonandonandonandon123456789".also { check(it.length == 60) }

    private fun seed(): Long {
        val folderId = seedFolder(folder)
        return seedNote("Named", folderId = folderId, tags = listOf(tag))
    }

    private fun label(text: String): SemanticsNode =
        rule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().first {
            SemanticsProperties.Text in it.config
        }

    private fun assertOneLine(text: String) = assertEquals("\"$text\" lines", 1, layoutOf(label(text)).lineCount)

    /** Checklist #299: the daily header's folder and tag chips, one line each in their sideways row, on a day filed under both. */
    @Test
    fun item299_theDailyHeaderKeepsLongNamesOnOneLine() {
        val folderId = seedFolder(folder)
        runBlocking {
            val id = pageId(today)
            container.pages.moveToFolder(id, folderId)
            container.pages.addTag(id, container.pages.getOrCreateTag(tag).id)
        }
        openApp()
        await { exists(hasText(folder)) && exists(hasText(tag)) }

        assertOneLine(folder)
        assertOneLine(tag)
        assertTrue("the page below is unmoved", exists(hasText(str(R.string.section_todo))))
    }

    /** Checklist #299: the Notes filter chips and a note card, one line each. */
    @Test
    fun item299_theNotesFiltersKeepLongNamesOnOneLine() {
        seed()
        openNotesTab()
        await { exists(hasText("Named")) && exists(hasText(folder)) }

        rule.onAllNodes(hasText(folder), useUnmergedTree = true).fetchSemanticsNodes().forEach {
            assertEquals("folder chip or card line", 1, layoutOf(it).lineCount)
        }
        bounds(rule.onNode(hasText("Named"))).let { assertTrue("the card fits: $it of $rootWidth", it.right <= rootWidth + 1) }
    }

    /** Checklist #299: the editor's summary row: each chip one line and inside the window, as it wraps rather than scrolls. */
    @Test
    fun item299_theEditorSummaryKeepsLongNamesOnOneLineInsideTheWindow() {
        val id = seed()
        openNotesTab()
        await { exists(hasText("Named")) }
        openNote("Named", id)
        await { exists(hasText(folder)) }

        for (text in listOf(folder, "#$tag")) {
            assertOneLine(text)
            val box = label(text).boundsInRoot
            assertTrue("\"$text\" at $box inside $rootWidth", box.right <= rootWidth + 1)
        }
    }

    /** Checklist #299: the Folder and tags dialog, with a long name added through it: one line each, inside the dialog. */
    @Test
    fun item299_theOrganizeDialogKeepsLongNamesOnOneLine() {
        val id = seedNote("Named")
        openNotesTab()
        await { exists(hasText("Named")) }
        openNote("Named", id)
        openOrganize()
        addFolder(folder)
        addTag(tag)
        await { exists(hasText(folder) and inDialog and !hasSetTextAction()) }
        await { exists(hasText("#$tag") and inDialog) }

        val dialogRight = rule.onAllNodes(inDialog).fetchSemanticsNodes().maxOf { it.boundsInRoot.right }
        for (text in listOf(folder, "#$tag")) {
            val node = rule.onAllNodes(hasText(text) and inDialog and !hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().first()
            assertEquals("\"$text\" lines", 1, layoutOf(node).lineCount)
            assertTrue("inside the dialog", node.boundsInRoot.right <= dialogRight + 1)
        }
        assertTrue("Done is still there", exists(hasText(str(R.string.action_done))))
    }

    private val longTitle = ("Long title " + "goes on and on ".repeat(20)).take(200).also { check(it.length == 200) }

    /** Checklist #300: on the list card a 200-character title is 1 line, ellipsized. */
    @Test
    fun item300_theListCardShowsOneEllipsizedLine() {
        seedNote(longTitle)
        openNotesTab()
        await { exists(hasText(longTitle)) }

        val layout = layoutOf(label(longTitle))
        assertEquals(1, layout.lineCount)
        assertTrue("ellipsized", layout.isLineEllipsized(0))
    }

    /** Checklist #300: the same title on a Search result takes 2 lines, ellipsized at the second. */
    @Test
    fun item300_aSearchResultShowsTwoEllipsizedLines() {
        seedNote(longTitle)
        openApp()
        tapTab(R.string.tab_search)
        await { exists(hasSetTextAction()) }
        rule.onNode(hasSetTextAction()).performTextInput("Long title")
        await { exists(hasText(longTitle, substring = true) and !hasSetTextAction()) }

        val layout = layoutOf(label(longTitle))
        assertEquals(2, layout.lineCount)
        assertTrue("ellipsized", layout.isLineEllipsized(1))
    }
}
