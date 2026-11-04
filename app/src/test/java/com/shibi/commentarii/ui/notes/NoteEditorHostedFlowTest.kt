package com.shibi.commentarii.ui.notes

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.testutil.NotesTestBase
import com.shibi.commentarii.ui.AppNav
import com.shibi.commentarii.ui.Routes
import com.shibi.commentarii.ui.theme.CommentariiTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist #215-#217: the back arrow's flush, a note that no longer exists, and dialogs across saved-state restore. #215 hosts
 * NoteEditorScreen directly so nothing else can save for it: the activity stays resumed, so neither ON_STOP nor onCleared fires.
 */
class NoteEditorHostedFlowTest : NotesTestBase() {
    private var backed = 0

    private fun host(id: Long) {
        rule.setContent { CommentariiTheme(dark = false) { NoteEditorScreen(container, id, onBack = { backed++ }) } }
        awaitEditor()
    }

    private fun savedText(id: Long) = savedTexts(id).single()

    /**
     * The compose clock cannot move the debounce, so it is held still only to run no frames inside the timed part. `start` is
     * taken before the edit exists, so a save seen under 500 ms after it can't be the debounce's. A slower attempt says nothing
     * either way, so it gets another go; each attempt is a new edit.
     */
    @Test
    fun item215_backArrowSavesTheEditBeforeTheDebounceCould() {
        val id = seedNote("Note", DocBlock(text = "text"))
        host(id)
        val timings = mutableListOf<Long>()
        for (attempt in 0 until 5) {
            val edit = "e$attempt"
            rule.mainClock.autoAdvance = false
            val start = System.nanoTime()
            block(0).performTextInput(edit)
            rule.onNode(hasContentDescription(backArrow)).performClick()
            val elapsed = millisUntil(start) { edit in savedText(id) }
            rule.mainClock.autoAdvance = true
            rule.waitForIdle()
            timings += elapsed
            println("item215 attempt $attempt saved after $elapsed ms")
            assertEquals("Back was delivered", attempt + 1, backed)
            if (elapsed < 500) return
        }
        throw AssertionError("no attempt saved inside the 500 ms debounce: $timings ms")
    }

    /** Control for #215: without Back the same edit is not there inside the debounce, and does arrive after it. */
    @Test
    fun item215_withoutBackTheEditWaitsForTheDebounce() {
        val id = seedNote("Note", DocBlock(text = "text"))
        host(id)
        var conclusive = 0
        for (attempt in 0 until 5) {
            val edit = "e$attempt"
            val start = System.nanoTime()
            block(0).performTextInput(edit)
            val early = savedText(id)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            if (elapsed < 450) {
                conclusive++
                assertFalse("saved after only $elapsed ms", edit in early)
            }
            val saved = millisUntil(start) { edit in savedText(id) }
            assertTrue("the debounce saved after $saved ms", saved >= 500)
        }
        assertTrue("no read landed inside the debounce, so this run proved nothing", conclusive > 0)
        assertEquals(0, backed)
    }

    @Test
    fun item215_backArrowInTheAppReturnsToTheListAndTheEditIsSaved() {
        val id = seedNote("Note", DocBlock(text = "text"))
        openNotesTab()
        await { exists(hasText("Note")) }
        openNote("Note", id)
        block(0).performTextInput("edit ")

        leaveEditor()

        assertEquals(Routes.NOTES, routes.last())
        await { "edit " in savedText(id) }
        await { exists(hasText(savedText(id))) }
    }

    // ---- A note that no longer exists ----

    private fun openAppOn(route: String) {
        rule.setContent {
            CommentariiTheme(dark = false) {
                AppNav(container, WindowWidthSizeClass.Compact, restoreRoutes = listOf(route), onRoutesChanged = { routes = it })
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun item216_aDeletedNoteOnARestoredBackStackShowsOnlyBackAndTheMessage() {
        val id = seedNote("Gone", DocBlock(text = "text"))
        runBlocking { container.pages.deletePage(id) }
        openAppOn(Routes.note(id))
        val message = str(R.string.ed_not_found)
        await { exists(hasText(message)) }

        rule.onNode(hasContentDescription(backArrow)).assertExists()
        for (absent in listOf(pinNote, unpinNote, organize, deleteNote, boldIcon, bulletIcon, checklistIcon)) {
            assertFalse("no $absent", exists(hasContentDescription(absent)))
        }
        assertEquals("no title or body field", 0, count(hasSetTextAction()))
        assertEquals("no format toolbar", 0, count(hasTestTag(FORMAT_TOOLBAR_TAG)))

        tapDescription(backArrow)
        await { routes.lastOrNull()?.startsWith("daily/") == true }
        assertFalse("left the message behind", exists(hasText(message)))
    }

    /** Control for #216: the same route to a note that exists shows the whole editor. */
    @Test
    fun item216_theSameRouteToALiveNoteShowsTheEditor() {
        val id = seedNote("Alive", DocBlock(text = "text"))
        openAppOn(Routes.note(id))
        await { exists(hasContentDescription(pinNote)) }

        awaitEditor()
        assertEquals(listOf("text"), shownBlocks())
        assertFalse(exists(hasText(str(R.string.ed_not_found))))
        rule.onNodeWithTag(FORMAT_TOOLBAR_TAG).assertExists()
    }

    // ---- Dialogs across saved-state restore ----

    private fun hostRestorable(id: Long): StateRestorationTester {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { CommentariiTheme(dark = false) { NoteEditorScreen(container, id, onBack = { backed++ }) } }
        awaitEditor()
        return restoration
    }

    private fun StateRestorationTester.restore() {
        emulateSavedInstanceStateRestore()
        rule.waitForIdle()
    }

    @Test
    fun item217_deleteDialogStaysOpenAcrossSavedStateRestore() {
        val id = seedNote("Note", DocBlock(text = "text"))
        val restoration = hostRestorable(id)
        val title = str(R.string.ed_delete_title)

        restoration.restore()
        assertFalse("control: a closed dialog stays closed", exists(hasText(title)))

        tapDescription(deleteNote)
        await { exists(hasText(title)) }
        restoration.restore()
        await { exists(hasText(title)) }

        rule.onNode(hasText(str(R.string.action_cancel)) and inDialog).performClick()
        rule.waitForIdle()
        assertFalse("and still closes", exists(hasText(title)))
        assertTrue(page(id) != null)
    }

    /** "May be lost" for the typed text, so the test needs only the dialog; the log line records which it was. */
    @Test
    fun item217_organizeDialogStaysOpenAcrossSavedStateRestore() {
        val id = seedNote("Note", DocBlock(text = "text"))
        val restoration = hostRestorable(id)
        val done = str(R.string.action_done)

        restoration.restore()
        assertFalse("control: a closed dialog stays closed", exists(hasText(done)))

        openOrganize()
        folderField().performTextInput("Half typed")
        rule.waitForIdle()
        restoration.restore()
        await { exists(hasText(done)) }

        folderField().assertExists()
        println("item217 typed folder text kept: ${exists(hasText("Half typed") and inDialog)}")
        tapDone()
        assertFalse("and still closes", exists(hasText(done)))
        assertTrue(page(id) != null)
    }
}
