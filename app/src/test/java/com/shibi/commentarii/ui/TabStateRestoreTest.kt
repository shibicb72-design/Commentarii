package com.shibi.commentarii.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.DailyPageTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checklist #10: the Notes tab keeps its scroll position and the Search tab its query across tab switches, in the real AppNav. */
class TabStateRestoreTest : DailyPageTestBase() {
    private val noteTitle = SemanticsMatcher("a note's title") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.startsWith("Trip ") }
    }

    /** The first note on the screen, and how far down the window it is. */
    private fun topNote(): Pair<String, Float> {
        val node = rule.onAllNodes(noteTitle).fetchSemanticsNodes()
            .filter { it.boundsInRoot.height > 0f }
            .minBy { it.positionInRoot.y }
        val title = node.config[SemanticsProperties.Text].first { it.text.startsWith("Trip ") }.text
        return title to node.positionInRoot.y
    }

    @Test
    fun item010_notesScrollAndSearchQueryComeBackAfterSwitchingTabs() {
        runBlocking { repeat(30) { container.pages.createNote("Trip $it") } }
        openApp()

        tapTab(R.string.tab_notes)
        await { exists(noteTitle) }
        val atTheTop = topNote()
        rule.onNode(pageList).performScrollToIndex(20)
        rule.waitForIdle()
        val scrolled = topNote()
        assertNotEquals("the list really moved", atTheTop.first, scrolled.first)

        tapTab(R.string.tab_search)
        val empty = hasSetTextAction()
        await { exists(empty) }
        rule.onNode(empty).performTextInput("a")
        val typed = hasText("a") and hasSetTextAction()
        await { exists(typed) }

        tapTab(R.string.tab_notes)
        await { exists(hasText(scrolled.first)) }
        assertEquals("same note at the top, at the same height", scrolled, topNote())

        tapTab(R.string.tab_search)
        await { exists(hasSetTextAction()) }
        assertTrue("the query is back", exists(typed))

        // And the Notes position again, now that Search has been restored once.
        tapTab(R.string.tab_notes)
        await { exists(hasText(scrolled.first)) }
        assertEquals(scrolled, topNote())
    }
}
