package com.shibi.commentarii.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasText
import com.shibi.commentarii.R
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.NotesTestBase
import com.shibi.commentarii.ui.notes.FORMAT_TOOLBAR_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist #306: what TalkBack would read. Each listed control is a clickable node carrying the checklist's label in the real
 * semantics tree. TalkBack itself and its reading order are not run here.
 */
class TalkbackLabelsFlowTest : NotesTestBase() {
    private fun assertAnnounced(vararg labels: String) {
        for (label in labels) {
            assertTrue(
                "no clickable node announces \"$label\"",
                exists(hasContentDescription(label) and hasClickAction()),
            )
        }
    }

    /** Checklist #306, the words of the strings: the item lists them by what they say. */
    @Test
    fun item306_theLabelsSayWhatTheChecklistLists() {
        val expected = mapOf(
            R.string.daily_pick_date to "Pick a date",
            R.string.daily_pin to "Pin page",
            R.string.daily_move_up to "Move up",
            R.string.daily_move_down to "Move down",
            R.string.daily_remove to "Remove",
            R.string.ed_bold to "Bold",
            R.string.ed_h1 to "Heading 1",
            R.string.ed_h2 to "Heading 2",
            R.string.ed_bullet to "Bullet list",
            R.string.ed_checkbox to "Checklist",
            R.string.nt_new to "New note",
            R.string.sr_clear to "Clear search",
            R.string.mood_5 to "Very happy",
        )
        for ((res, words) in expected) assertEquals(words, str(res))
        assertEquals("Remove tag x", str(R.string.daily_remove_tag, "x"))
    }

    /** Checklist #306, Today: date, pin, a to-do's arrows and remove, a tag's remove, every mood. */
    @Test
    fun item306_todayAnnouncesItsControls() {
        seedTasks(today, TaskSection.TODO, "One", "Two")
        runBlocking {
            val id = pageId(today)
            container.pages.addTag(id, container.pages.getOrCreateTag("x").id)
        }
        openApp()
        await { exists(hasText("#x")) || exists(hasContentDescription(str(R.string.daily_remove_tag, "x"))) }
        assertAnnounced(str(R.string.daily_pick_date), str(R.string.daily_pin), str(R.string.daily_remove_tag, "x"))

        scrollTo(hasText("One"))
        assertAnnounced(str(R.string.daily_move_up), str(R.string.daily_move_down), str(R.string.daily_remove))

        val moods = listOf(R.string.mood_5, R.string.mood_4, R.string.mood_3, R.string.mood_2, R.string.mood_1).map { str(it) }
        scrollTo(hasContentDescription(moods.first()))
        assertAnnounced(*moods.toTypedArray())
    }

    /** Checklist #306, the editor's toolbar: all five buttons, H1 and H2 by their full names. Found failing: they only said "H1" and "H2". */
    @Test
    fun item306_theEditorToolbarAnnouncesItsButtons() {
        val id = seedNote("Note")
        openNotesTab()
        assertAnnounced(str(R.string.nt_new))
        await { exists(hasText("Note")) }
        openNote("Note", id)
        tapBlock(0)
        rule.onNodeWithTag(FORMAT_TOOLBAR_TAG).assertExists()

        assertAnnounced(str(R.string.ed_bold), str(R.string.ed_h1), str(R.string.ed_h2), str(R.string.ed_bullet), str(R.string.ed_checkbox))
    }

    /** Checklist #306, Search's clear button, present once there is text. */
    @Test
    fun item306_searchAnnouncesClear() {
        openApp()
        tapTab(R.string.tab_search)
        await { exists(hasSetTextAction()) }
        rule.onNode(hasSetTextAction()).performTextInput("x")
        await { exists(hasContentDescription(str(R.string.sr_clear))) }
        assertAnnounced(str(R.string.sr_clear))
    }
}
