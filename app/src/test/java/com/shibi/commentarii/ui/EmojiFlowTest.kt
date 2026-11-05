package com.shibi.commentarii.ui

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.notify.AlarmReceiver
import com.shibi.commentarii.notify.AlarmScheduler
import com.shibi.commentarii.notify.Notifications
import com.shibi.commentarii.testutil.MutableClock
import com.shibi.commentarii.testutil.NotesTestBase
import com.shibi.commentarii.testutil.TestEnv
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.Shadows.shadowOf

/**
 * Checklist #301: a ZWJ sequence and a flag, typed into the real fields and read back from the database. Whether the glyphs look right
 * and what a real keyboard's backspace sends are for a device; the hardware Backspace path is what runs here.
 */
class EmojiFlowTest : NotesTestBase() {
    @get:Rule val tmp = TemporaryFolder()

    private val zwj = "👩‍💻"
    private val flag = "🇮🇳"
    private val both = zwj + flag

    /** Checklist #301: priority, schedule row and daily notes keep the exact code points in the fields and the database. */
    @Test
    fun item301_emojiInADailyPageIsShownAndSaved() {
        open(today)
        rule.onNode(hasText(str(R.string.daily_priority_hint, 1)) and hasSetTextAction()).performTextInput(both)
        await { texts(today, TaskSection.PRIORITY) == listOf(both) }
        assertTrue(exists(hasText(both) and hasSetTextAction()))

        val plan = hasText(str(R.string.daily_schedule_hint)) and hasSetTextAction()
        rule.onNode(pageList).performScrollToNode(plan)
        rule.onAllNodes(plan)[0].performTextInput("Plan $both")
        await { runBlocking { container.pages.getSchedule(pageId(today)) }.any { it.text == "Plan $both" } }

        val notes = hasText(str(R.string.daily_notes_hint)) and hasSetTextAction()
        rule.onNode(pageList).performScrollToNode(notes)
        rule.onNode(notes).performTextInput("Notes $both")
        await { runBlocking { container.pages.getBlockDocument(pageId(today), SectionKind.NOTES) }.blocks.any { it.text == "Notes $both" } }

        // A fresh composition reads it all back from the database.
        reopen()
        rule.onNode(pageList).performScrollToNode(hasText(both))
        assertTrue(exists(hasText(both) and hasSetTextAction()))
    }

    /** Checklist #301: a note's title and body, a folder and a tag, each saved exactly, and shown again on the list after leaving. */
    @Test
    fun item301_emojiInANoteItsFolderAndItsTagIsShownAndSaved() {
        openNotesTab()
        val id = createNoteWithFab()
        titleField().performTextInput("Title $both")
        type(0, "Body $both")
        await { page(id)?.title == "Title $both" && savedTexts(id) == listOf("Body $both") }

        openOrganize()
        addFolder("Folder $both")
        addTag("tag$both")
        await { page(id)?.folderId != null && savedTags(id) == listOf("tag$both") }
        assertEquals(listOf("Folder $both"), runBlocking { container.pages.getFolders() }.map { it.name })
        assertTrue(exists(hasText("Folder $both") and inDialog))
        tapDone()
        await { exists(hasText("Folder $both")) && exists(hasText("#tag$both")) }

        leaveEditor()
        await { exists(hasText("Title $both")) }
        openNote("Title $both", id)
        assertEquals("Title $both", textOf(fields()[0]))
        assertEquals(listOf("Body $both"), shownBlocks())
    }

    /**
     * Checklist #301: Backspace removes a whole emoji, not half of its surrogate pair. Only a single code point is checked here: the
     * field asks java.text.BreakIterator for the break, and the JVM's (JDK 17) splits ZWJ sequences and flags where Android's ICU
     * does not (boundaries 0 1 3 4 6 for "a" + woman + ZWJ + laptop), so those two need a device.
     */
    @Test
    fun item301_backspaceDeletesAWholeSingleCodePointEmoji() {
        val grin = "😀"
        val id = seedNote("Note", DocBlock(text = ""))
        openNotesTab()
        await { exists(hasText("Note")) }
        openNote("Note", id)
        tapBlock(0)

        type(0, "a$grin")
        assertEquals("a$grin", shownBlocks().single())
        pressBackspace(0)
        assertEquals("both halves of the pair", "a", shownBlocks().single())
    }

    /** Checklist #301: the reminder fired for a row with emoji shows the same characters in its notification text. */
    @Test
    fun item301_theReminderNotificationKeepsTheEmoji() {
        val context = rule.activity.applicationContext
        AlarmReceiver().onReceive(
            context,
            Intent(AlarmScheduler.ACTION_REMINDER)
                .putExtra(Notifications.EXTRA_ENTRY_ID, 7L)
                .putExtra(Notifications.EXTRA_MINUTE, 9 * 60)
                .putExtra(Notifications.EXTRA_DATE, "2031-03-12")
                .putExtra(Notifications.EXTRA_TEXT, both),
        )
        val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        val text = posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue("the text \"$text\"", both in text)
    }

    /** Checklist #301: export then import into an empty app keeps the emoji everywhere it was typed. */
    @Test
    fun item301_exportAndImportKeepTheEmoji() {
        val date = LocalDate.of(2031, 3, 12)
        val clock = MutableClock(date.atTime(9, 0))
        val from = TestEnv(tmp, clock)
        val into = TestEnv(tmp, clock)
        try {
            runBlocking {
                val note = from.pages.createNote("Title $both")
                from.pages.saveBlockDocument(note.id, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(text = "Body $both"))))
                val folder = from.pages.createFolder("Folder $both")
                from.pages.moveToFolder(note.id, folder.id)
                from.pages.addTag(note.id, from.pages.getOrCreateTag("tag$both").id)

                into.io.import(from.io.export())

                val folders = into.pages.getFolders()
                assertEquals(listOf("Folder $both"), folders.map { it.name })
                val restored = into.pages.getPage(note.id)!!
                assertEquals("Title $both", restored.title)
                assertEquals(folders.single().id, restored.folderId)
                assertEquals(listOf("Body $both"), into.pages.getBlockDocument(note.id, SectionKind.RICH_TEXT).blocks.map { it.text })
                assertEquals(listOf("tag$both"), into.pages.getTagsForPage(note.id).map { it.name })
            }
        } finally {
            from.close()
            into.close()
        }
    }
}
