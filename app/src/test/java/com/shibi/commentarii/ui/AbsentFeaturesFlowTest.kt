package com.shibi.commentarii.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.NotesTestBase
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist #309, #310, #311, #313: things the app must not have. Each check first shows the right screen and does the action that
 * would offer the missing feature, and the words are also searched for in every string the app ships, with a control that finds the
 * delete words the same way.
 */
class AbsentFeaturesFlowTest : NotesTestBase() {
    private fun allStrings(): List<String> = R.string::class.java.fields.map { rule.activity.getString(it.getInt(null)) }

    private fun stringsWith(pattern: String) = allStrings().filter { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(it) }

    /** Any node, merged or not, whose text or description mentions [pattern]. */
    private fun mentions(pattern: String): SemanticsMatcher {
        val regex = Regex(pattern, RegexOption.IGNORE_CASE)
        return SemanticsMatcher("mentions $pattern") { node ->
            val texts = node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }
            val descriptions = node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
            (texts + descriptions).any(regex::containsMatchIn)
        }
    }

    private fun on(pattern: String) = rule.onAllNodes(mentions(pattern), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun assertNoUndoOnScreen(after: String) = assertEquals("an Undo after $after", 0, on("\\bundo\\b"))

    private val deleteConfirmTitle get() = str(R.string.ed_delete_title)

    /** The note editor's delete: the dialog's confirm button says what it does, so it is the one in the dialog that is not Cancel. */
    private fun deleteOpenNote() {
        tapDescription(deleteNote)
        await { exists(hasText(deleteConfirmTitle)) }
        assertTrue(exists(hasText(str(R.string.ed_delete_message))))
        val cancel = str(R.string.action_cancel)
        val buttons = rule.onAllNodes(hasClickAction() and inDialog)
        val index = buttons.fetchSemanticsNodes().indexOfFirst { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.none { it.text == cancel }
        }
        buttons[index].performClick()
        rule.waitForIdle()
    }

    /** Checklist #309: delete asks once, then the note is gone for good; no archive or trash word or place exists in the app. */
    @Test
    fun item309_deleteIsPermanentWithAConfirmAndThereIsNoArchiveOrTrash() {
        assertEquals("no archive, trash or bin in any string", emptyList<String>(), stringsWith("archiv|trash|recycle|\\bbin\\b|restore deleted"))
        assertTrue("control: the same search finds the delete words", stringsWith("\\bdelete\\b").isNotEmpty())

        val id = seedNote("Doomed note")
        openNotesTab()
        await { exists(hasText("Doomed note")) }
        openNote("Doomed note", id)
        assertEquals("control: the matcher sees the editor's delete", 1, on("^${Regex.escape(deleteNote)}$"))
        assertEquals("no archive or trash in the editor", 0, on("archiv|trash|recycle"))

        deleteOpenNote()
        await { routes.lastOrNull() == Routes.NOTES && exists(hasText(str(R.string.nt_empty))) }
        assertNull("the row itself is gone", page(id))
        assertEquals(emptyList<Long>(), runBlocking { container.pages.observeNotes().first() }.map { it.id })
        assertEquals("no archive or trash on the list", 0, on("archiv|trash|recycle"))
    }

    /** Checklist #310: the word exists in no string, and no Undo appears after deleting a note, removing a to-do or clearing a mood. */
    @Test
    fun item310_noUndoAfterDeletingANoteRemovingATodoOrClearingAMood() {
        assertEquals("no Undo in any string", emptyList<String>(), stringsWith("\\bundo\\b"))

        seedTasks(today, TaskSection.TODO, "Keep", "Remove me")
        val id = seedNote("Short lived")
        openApp()

        // A to-do removed.
        scrollTo(hasText("Remove me"))
        await { exists(hasText("Remove me")) }
        onRowOf(hasText("Remove me"), str(R.string.daily_remove))
        await { texts(today, TaskSection.TODO) == listOf("Keep") }
        settleFor(200)
        assertFalse(exists(hasText("Remove me")))
        assertNoUndoOnScreen("removing a to-do")

        // A mood set, then cleared by tapping it again.
        val face = hasContentDescription(str(R.string.mood_5))
        scrollTo(face)
        rule.onNode(face).performClick()
        await { runBlocking { container.pages.getMood(today) }?.mood == 5 }
        rule.onNode(face).performClick()
        await { runBlocking { container.pages.getMood(today) } == null }
        settleFor(200)
        assertNoUndoOnScreen("clearing a mood")

        // A note deleted.
        tapTab(R.string.tab_notes)
        await { exists(hasText("Short lived")) }
        openNote("Short lived", id)
        deleteOpenNote()
        await { routes.lastOrNull() == Routes.NOTES && page(id) == null }
        settleFor(200)
        assertNoUndoOnScreen("deleting a note")
    }

    /** The node of the control described [description] nearest to the row showing [row]. */
    private fun onRowOf(row: SemanticsMatcher, description: String) {
        val y = centerY(rule.onNode(row).fetchSemanticsNode())
        val controls = rule.onAllNodes(hasContentDescription(description))
        val nodes = controls.fetchSemanticsNodes()
        controls[nodes.indices.minBy { kotlin.math.abs(centerY(nodes[it]) - y) }].performClick()
        rule.waitForIdle()
    }

    /** Checklist #311: no page offers a delete or clear; text removed by hand leaves the page in place, on all three kinds. */
    @Test
    fun item311_dailyWeekAndMonthHaveNoDeleteAndEmptiedPagesRemain() {
        val pageWords = "delete|clear (this |the )?(page|day|week|month)"
        val id = seedNote("Control")
        openApp()
        val daily = pageId(today)
        val priority = str(R.string.daily_priority_hint, 1)
        rule.onNode(hasText(priority) and hasSetTextAction()).performTextInput("By hand")
        await { texts(today, TaskSection.PRIORITY) == listOf("By hand") }
        assertEquals("nothing on Today", 0, on(pageWords))
        rule.onNode(hasText("By hand") and hasSetTextAction()).performTextClearance()
        await { texts(today, TaskSection.PRIORITY).isEmpty() }
        assertEquals("the emptied day keeps its page", daily, pageId(today))
        assertNotNull(page(daily))

        tapTab(R.string.tab_week)
        await { exists(hasText(str(R.string.section_week_days))) }
        assertEquals("nothing on the week", 0, on(pageWords))

        tapTab(R.string.tab_month)
        await { exists(hasText(str(R.string.section_calendar))) }
        assertEquals("nothing on the month", 0, on(pageWords))
        val month = runBlocking { container.pages.getOrCreateMonthly(YearMonth.from(today).atDay(1)).id }
        assertNotNull(page(month))

        // Control: the same search finds the delete on a note.
        tapTab(R.string.tab_notes)
        await { exists(hasText("Control")) }
        openNote("Control", id)
        assertTrue("the search does find a delete", on(pageWords) > 0)
    }

    /** Checklist #313: Today has the five clickable faces; the note editor, week and month have none, the week only shows a day's face. */
    @Test
    fun item313_moodIsOnlySetOnDailyPages() {
        val faces = listOf(R.string.mood_5, R.string.mood_4, R.string.mood_3, R.string.mood_2, R.string.mood_1).map { str(it) }
        val id = seedNote("Moodless")
        runBlocking { container.pages.saveMood(today, 5) }
        openApp()
        fun clickableFaces() = faces.sumOf { count(hasContentDescription(it) and hasClickAction()) }

        val firstFace = hasContentDescription(faces.first())
        scrollTo(firstFace)
        assertEquals("control: the five faces on Today", 5, clickableFaces())

        tapTab(R.string.tab_notes)
        await { exists(hasText("Moodless")) }
        openNote("Moodless", id)
        assertEquals("the editor has no faces", 0, clickableFaces())
        assertEquals("nor the word", 0, on(str(R.string.section_mood)))
        tapDescription(backArrow)

        tapTab(R.string.tab_month)
        await { exists(hasText(str(R.string.section_calendar))) }
        assertEquals("the month has no faces", 0, clickableFaces())

        tapTab(R.string.tab_week)
        await { exists(hasText(str(R.string.section_week_days))) }
        scrollTo(hasText(str(R.string.section_mood_week)))
        assertTrue("the week has a single row, not a picker per day: ${clickableFaces()}", clickableFaces() <= 1)
        val shown = hasContentDescription(faces.first(), substring = true)
        rule.onAllNodes(shown)[0].performClick()
        await { routes.lastOrNull() == Routes.daily(today) }
        assertEquals("opening the day changes nothing", 5, runBlocking { container.pages.getMood(today) }?.mood)
    }
}
