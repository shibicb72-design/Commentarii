package com.shibi.commentarii.ui.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.testutil.NotesTestBase
import com.shibi.commentarii.ui.Routes
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/** Checklist #206-#214: pin, the folder and tags dialog, and delete, on the real editor inside AppNav. */
class NoteEditorOrganizeFlowTest : NotesTestBase() {
    private val done get() = str(R.string.action_done)
    private val noFolder get() = str(R.string.ed_no_folder)
    private val addFolderOrTag get() = str(R.string.ed_add_folder_tag)
    private val deleteTitle get() = str(R.string.ed_delete_title)
    private val deleteMessage get() = str(R.string.ed_delete_message)
    private val delete get() = str(R.string.action_delete)
    private val cancel get() = str(R.string.action_cancel)
    private val pinned get() = str(R.string.nt_pinned)

    private fun open(title: String = "Note", folderId: Long? = null, tags: List<String> = emptyList()): Long {
        val id = seedNote(title, DocBlock(text = "body"), folderId = folderId, tags = tags)
        openNotesTab()
        await { exists(hasText(title)) }
        openNote(title, id)
        return id
    }

    private fun dialogChip(name: String) = rule.onNode(hasText(name) and inDialog and !hasSetTextAction())

    private fun tapDialogChip(name: String) {
        dialogChip(name).performClick()
        rule.waitForIdle()
    }

    private fun summaryChip(name: String) = hasText(name) and !inDialog

    /** Pixels within the node's bounds in [color], from a software draw of the window. */
    private fun pixelsOf(color: Color, matcher: SemanticsMatcher): Int {
        settleAnimations()
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        val area = rule.onNode(matcher).fetchSemanticsNode().boundsInWindow
        val wanted = color.toArgb()
        var found = 0
        for (y in area.top.toInt() until area.bottom.toInt()) {
            for (x in area.left.toInt() until area.right.toInt()) {
                if (bitmap.getPixel(x, y) == wanted) found++
            }
        }
        return found
    }

    // ---- Pin ----

    @Test
    fun item206_pinTintsTheIconSwitchesItsDescriptionAndTheListFollows() {
        val id = open()
        val pin = hasContentDescription(pinNote)
        assertEquals("not tinted before", 0, pixelsOf(colors.primary, pin))

        tapDescription(pinNote)
        val unpin = hasContentDescription(unpinNote)
        assertTrue(exists(unpin))
        assertFalse(exists(pin))
        assertTrue("tinted primary once pinned", pixelsOf(colors.primary, unpin) > 0)
        await { page(id)!!.pinned }

        leaveEditor()
        await { exists(hasText("Note") and hasContentDescription(pinned)) }

        openNote("Note", id)
        await { exists(unpin) }
        tapDescription(unpinNote)
        assertTrue(exists(pin))
        assertEquals("the tint goes again", 0, pixelsOf(colors.primary, pin))
        await { !page(id)!!.pinned }

        leaveEditor()
        await { exists(hasText("Note")) && !exists(hasContentDescription(pinned)) }
    }

    // ---- The dialog ----

    private fun assertDialogShowsEverything() {
        assertTrue("title", exists(hasText(organize) and inDialog))
        assertTrue("Folder heading", exists(hasText(str(R.string.ed_folder)) and inDialog))
        assertTrue(exists(hasText(noFolder) and inDialog))
        assertTrue("existing folder", exists(hasText("Work") and inDialog))
        folderField().assertExists()
        assertTrue("Tags heading", exists(hasText(str(R.string.ed_tags)) and inDialog))
        assertTrue("existing tag", exists(hasText("#idea") and inDialog))
        tagField().assertExists()
        assertEquals("one Add per field", 2, count(hasText(str(R.string.action_add)) and inDialog))
        assertTrue(exists(hasText(done) and inDialog))
    }

    @Test
    fun item207_labelIconAndChipBothOpenTheDialogWithEverythingInIt() {
        seedFolder("Work")
        runBlocking { container.pages.getOrCreateTag("idea") }
        open()
        assertFalse("closed to begin with", exists(hasText(done)))

        tapDescription(organize)
        await { exists(hasText(done)) }
        assertDialogShowsEverything()
        tapDone()
        assertFalse(exists(hasText(done)))

        rule.onNode(hasText(addFolderOrTag)).performClick()
        rule.waitForIdle()
        await { exists(hasText(done)) }
        assertDialogShowsEverything()
    }

    @Test
    fun item208_pickingAFolderMovesTheSelectionAndTheChipUnderTheTitle() {
        val work = seedFolder("Work")
        val id = open()
        assertTrue("no folder yet", exists(summaryChip(addFolderOrTag)))

        openOrganize()
        dialogChip(noFolder).assertIsSelected()
        tapDialogChip("Work")
        await { page(id)!!.folderId == work }
        dialogChip("Work").assertIsSelected()
        dialogChip(noFolder).assertIsNotSelected()
        tapDone()

        assertTrue("the chip shows the folder", exists(summaryChip("Work")))
        assertFalse(exists(summaryChip(addFolderOrTag)))
        val title = titleField().fetchSemanticsNode().positionInRoot.y
        val chip = rule.onNode(summaryChip("Work")).fetchSemanticsNode().positionInRoot.y
        val body = block(0).fetchSemanticsNode().positionInRoot.y
        assertTrue("under the title, above the body", chip > title && chip < body)

        openOrganize()
        tapDialogChip(noFolder)
        await { page(id)!!.folderId == null }
        dialogChip(noFolder).assertIsSelected()
        dialogChip("Work").assertIsNotSelected()
        tapDone()
        assertTrue("back to the add chip", exists(summaryChip(addFolderOrTag)))
        assertFalse(exists(summaryChip("Work")))
    }

    @Test
    fun item209_newFolderIsCreatedSelectedAndTheFieldClearsAndAddIsDisabledWhileBlank() {
        val id = open()
        openOrganize()
        addButton(0).assertIsNotEnabled()

        folderField().performTextInput("   ")
        rule.waitForIdle()
        addButton(0).assertIsNotEnabled()
        rule.onNode(hasText("   ") and hasSetTextAction() and inDialog).performTextClearance()
        rule.waitForIdle()

        folderField().performTextInput("Ideas")
        rule.waitForIdle()
        addButton(0).assertIsEnabled()
        addButton(0).performClick()
        rule.waitForIdle()

        await { exists(hasText("Ideas") and inDialog and !hasSetTextAction()) }
        assertEquals(listOf("Ideas"), runBlocking { container.pages.getFolders() }.map { it.name })
        await { page(id)!!.folderId == runBlocking { container.pages.getFolders() }.single().id }
        dialogChip("Ideas").assertIsSelected()
        dialogChip(noFolder).assertIsNotSelected()
        assertFalse("the field cleared", exists(hasText("Ideas") and hasSetTextAction()))
        addButton(0).assertIsNotEnabled()
    }

    @Test
    fun item210_severalTagsCanBeOnAndANewTagIsCreatedAndAttached() {
        runBlocking {
            container.pages.getOrCreateTag("alpha")
            container.pages.getOrCreateTag("beta")
            container.pages.getOrCreateTag("gamma")
        }
        val id = open()
        openOrganize()
        await { exists(hasText("#gamma") and inDialog) }

        tapDialogChip("#alpha")
        tapDialogChip("#beta")
        await { savedTags(id) == listOf("alpha", "beta") }
        addButton(1).assertIsNotEnabled()
        addTag("urgent")
        await { savedTags(id) == listOf("alpha", "beta", "urgent") }

        await { exists(hasText("#urgent") and inDialog) }
        dialogChip("#alpha").assertIsSelected()
        dialogChip("#beta").assertIsSelected()
        dialogChip("#urgent").assertIsSelected()
        dialogChip("#gamma").assertIsNotSelected()

        tapDone()
        for (shown in listOf("#alpha", "#beta", "#urgent")) assertTrue("$shown under the title", exists(summaryChip(shown)))
        assertFalse(exists(summaryChip("#gamma")))
    }

    @Test
    fun item211_doneClosesAndTheChangesWereAlreadySaved() {
        val work = seedFolder("Work")
        val id = open()
        openOrganize()
        tapDialogChip("Work")

        await { page(id)!!.folderId == work }
        assertTrue("saved while the dialog is still open", exists(hasText(done)))

        tapDone()
        assertFalse(exists(hasText(done)))
        assertEquals(work, page(id)!!.folderId)
    }

    /** Synthetic touch outside the dialog's window; a finger on a real screen is left for a device. */
    @Test
    fun item211_touchOutsideClosesAndKeepsTheChanges() {
        val work = seedFolder("Work")
        val id = open()
        openOrganize()
        tapDialogChip("Work")
        await { page(id)!!.folderId == work }

        tapOutsideDialog()

        assertFalse(exists(hasText(done)))
        assertEquals(work, page(id)!!.folderId)
        assertTrue(exists(summaryChip("Work")))
    }

    // ---- Delete ----

    private fun openDelete() {
        tapDescription(deleteNote)
        await { exists(hasText(deleteTitle)) }
    }

    @Test
    fun item212_trashIconAsksFirstWithDeleteAndCancel() {
        val id = open()
        assertFalse("no dialog before the tap", exists(hasText(deleteTitle)))
        openDelete()

        assertTrue(exists(hasText(deleteTitle) and inDialog))
        assertTrue(exists(hasText(deleteMessage) and inDialog))
        assertTrue(exists(hasText(delete) and inDialog))
        assertTrue(exists(hasText(cancel) and inDialog))
        assertTrue("asking deletes nothing", page(id) != null)
    }

    private fun assertNoteIntactAfterDialogClosed(id: Long) {
        assertFalse("dialog closed", exists(hasText(deleteTitle)))
        settleFor(300)
        assertEquals("Note", page(id)!!.title)
        assertEquals(listOf("body"), savedTexts(id))
        assertEquals("still on the editor", Routes.note(id), routes.last())
        leaveEditor()
        await { exists(hasText("Note")) }
    }

    @Test
    fun item213_cancelClosesAndLeavesTheNote() {
        val id = open()
        openDelete()
        rule.onNode(hasText(cancel) and inDialog).performClick()
        rule.waitForIdle()
        assertNoteIntactAfterDialogClosed(id)
    }

    /** Synthetic touch outside the dialog's window; a finger on a real screen is left for a device. */
    @Test
    fun item213_touchOutsideClosesAndLeavesTheNote() {
        val id = open()
        openDelete()
        tapOutsideDialog()
        assertNoteIntactAfterDialogClosed(id)
    }

    private fun searchFor(query: String) {
        val field = rule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput(query)
    }

    /** Search's debounce is a delay on the main thread, so it needs the looper's time moved along as well. */
    private fun awaitInSearch(condition: () -> Boolean) {
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            condition()
        }
        rule.waitForIdle()
    }

    @Test
    fun item214_deleteClosesTheEditorAndTheNoteIsGoneFromListAndSearch() {
        val keeper = seedNote("Keeper", DocBlock(text = "unrelated"))
        val id = seedNote("Doomed", DocBlock(text = "needle"))
        openNotesTab()
        await { exists(hasText("Doomed")) }

        // Control: Search finds it while it exists.
        tapTab(R.string.tab_search)
        await { routes.lastOrNull() == Routes.SEARCH }
        searchFor("needle")
        awaitInSearch { exists(hasText("Doomed")) }

        tapTab(R.string.tab_notes)
        await { routes.lastOrNull() == Routes.NOTES && exists(hasText("Doomed")) }
        openNote("Doomed", id)
        openDelete()
        rule.onNode(hasText(delete) and inDialog).performClick()
        rule.waitForIdle()

        // The list comes back showing its last snapshot until Room's flow delivers the delete, so Keeper alone proves nothing.
        await { routes.lastOrNull() == Routes.NOTES && exists(hasText("Keeper")) && !exists(hasText("Doomed")) }
        assertFalse("off the list", exists(hasText("Doomed")))
        assertFalse("no undo offered", exists(hasText("Undo", ignoreCase = true)))
        assertNull(page(id))
        assertTrue(page(keeper) != null)

        tapTab(R.string.tab_search)
        await { routes.lastOrNull() == Routes.SEARCH }
        searchFor("keeper")
        awaitInSearch { exists(hasText("Keeper")) }
        searchFor("needle")
        awaitInSearch { exists(hasText(str(R.string.sr_no_results))) }
        assertFalse("gone from Search", exists(hasText("Doomed")))
        assertEquals(emptyList<Long>(), runBlocking { container.pages.search("needle") }.map { it.id })
    }
}
