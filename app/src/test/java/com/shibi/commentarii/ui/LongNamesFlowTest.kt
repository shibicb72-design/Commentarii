package com.shibi.commentarii.ui

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
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

    /** The right edge as laid out: boundsInRoot is clipped to the parents, which would hide just the overflow looked for here. */
    private fun rightOf(node: SemanticsNode) = node.positionInRoot.x + node.size.width

    /** The sideways scroller holding [text] really has somewhere to scroll to, which is how a chip that long stays reachable. */
    private fun assertInASidewaysScroller(text: String) {
        val sideways = SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)
        val range = rule.onNode(sideways and hasScrollAction() and hasAnyDescendant(hasText(text))).fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue("\"$text\" can be scrolled to: max ${range.maxValue()}", range.maxValue() > 0f)
    }

    /**
     * Checklist #299: the daily header's folder and tag chips, one line each in their sideways row, on a day filed under both. The
     * page below stays where it is on a day without them, in the same run.
     */
    @Test
    fun item299_theDailyHeaderKeepsLongNamesOnOneLine() {
        open(today)
        val below = hasText(str(R.string.section_priorities))
        val plainTop = rule.onNode(below).fetchSemanticsNode().positionInRoot.y

        val folderId = seedFolder(folder)
        runBlocking {
            val id = pageId(today)
            container.pages.moveToFolder(id, folderId)
            container.pages.addTag(id, container.pages.getOrCreateTag(tag).id)
        }
        reopen()
        await { exists(hasText(folder)) && exists(hasText(tag)) }

        assertOneLine(folder)
        assertOneLine(tag)
        assertInASidewaysScroller(folder)
        assertEquals("the page below is unmoved", plainTop, rule.onNode(below).fetchSemanticsNode().positionInRoot.y, 1f)
    }

    /** Checklist #299: the Notes filter chips, folder and tag, and a note card: one line each, the chips in sideways scrollers. */
    @Test
    fun item299_theNotesFiltersKeepLongNamesOnOneLine() {
        seed()
        openNotesTab()
        await { exists(hasText("Named")) && exists(hasText(folder)) && exists(hasText("#$tag")) }

        for (text in listOf(folder, "#$tag")) {
            rule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().forEach {
                assertEquals("\"$text\" chip or card line", 1, layoutOf(it).lineCount)
            }
            assertInASidewaysScroller(text)
        }
        bounds(rule.onNode(hasText("Named"))).let { assertTrue("the card fits: $it of $rootWidth", it.right <= rootWidth + 1) }
    }

    /** Checklist #299: the editor's summary row wraps rather than scrolls, so each chip is one ellipsized line and inside the window. */
    @Test
    fun item299_theEditorSummaryKeepsLongNamesOnOneLineInsideTheWindow() {
        val id = seed()
        openNotesTab()
        await { exists(hasText("Named")) }
        openNote("Named", id)
        await { exists(hasText(folder)) }

        for (text in listOf(folder, "#$tag")) {
            assertOneLine(text)
            assertTrue("\"$text\" is ellipsized", layoutOf(label(text)).isLineEllipsized(0))
            assertTrue("\"$text\" ends at ${rightOf(label(text))} inside $rootWidth", rightOf(label(text)) <= rootWidth + 1)
        }
    }

    /** Checklist #299: the Folder and tags dialog cannot scroll sideways, and has a long name added through it: one ellipsized line each, inside the dialog. */
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

        val dialog = rule.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        for (text in listOf(folder, "#$tag")) {
            val node = rule.onAllNodes(hasText(text) and inDialog and !hasSetTextAction(), useUnmergedTree = true).fetchSemanticsNodes().first()
            assertEquals("\"$text\" lines", 1, layoutOf(node).lineCount)
            assertTrue("\"$text\" is ellipsized", layoutOf(node).isLineEllipsized(0))
            assertTrue("\"$text\" ends at ${rightOf(node)} inside the dialog at $dialog", rightOf(node) <= dialog.right + 1)
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
