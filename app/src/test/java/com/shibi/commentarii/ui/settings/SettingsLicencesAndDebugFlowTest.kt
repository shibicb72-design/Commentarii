package com.shibi.commentarii.ui.settings

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import com.shibi.commentarii.R
import com.shibi.commentarii.data.ScheduleEntryEntity
import com.shibi.commentarii.testutil.ThemeFlowTestBase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.shadows.ShadowToast

/** Checklist #269-#273: the Licences card and the debug log on the real MainActivity and container. */
class SettingsLicencesAndDebugFlowTest : ThemeFlowTestBase() {
    private val debugTitle get() = str(R.string.debug_title)
    private val openDebug get() = str(R.string.settings_debug_open)
    private val copyAll get() = str(R.string.debug_copy_all)
    private val clear get() = str(R.string.debug_clear)
    private val empty get() = str(R.string.debug_empty)
    private val backArrow get() = str(R.string.ed_back)

    // The log is the container's, which outlives a test.
    @Before
    fun clearLogBefore() = container.debugLog.clear()

    @After
    fun clearLogAfter() = container.debugLog.clear()

    private fun openDebugLog() {
        openSettings()
        scrollToText(openDebug)
        rule.onNode(onPage(openDebug)).performClick()
        await { exists(debugTitle) }
    }

    private fun tapClearAndAwaitEmpty() {
        rule.onNode(hasText(clear)).performClick()
        await { exists(empty) }
    }

    private fun pressSystemBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun clipboardText(): String {
        val clipboard = rule.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip!!.getItemAt(0).text.toString()
    }

    /** Checklist #269: the font line, then the button opens the whole asset and turns into Hide, which closes it again. */
    @Test
    fun item269_showLicenceTextOpensTheFullOflAndHideClosesIt() {
        val show = str(R.string.settings_licence_show)
        val hide = str(R.string.settings_licence_hide)
        val licence = rule.activity.assets.open("licenses/DancingScript-OFL.txt").bufferedReader().use { it.readText() }
        assertTrue("the asset is the real licence", licence.length > 2_000 && "SIL OPEN FONT LICENSE" in licence)
        val shownLicence = hasText("SIL OPEN FONT LICENSE", substring = true)

        openSettings()
        scrollToText(show)
        assertEquals("Dancing Script font, SIL Open Font License 1.1.", str(R.string.settings_licence_font))
        rule.onNode(onPage(str(R.string.settings_licence_font))).assertExists()
        assertFalse("closed at first", exists(shownLicence))
        assertFalse(exists(onPage(hide)))

        rule.onNode(onPage(show)).performClick()
        await { exists(shownLicence) }
        val text = rule.onNode(shownLicence).fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertEquals("the whole file is shown", licence.trim(), text.trim())
        assertTrue(exists(onPage(hide)))
        assertFalse(exists(onPage(show)))

        rule.onNode(onPage(hide)).performClick()
        await { !exists(shownLicence) }
        assertTrue(exists(onPage(show)))
        assertFalse(exists(onPage(hide)))
    }

    /** Checklist #270: back arrow, title, both buttons, and "The log is empty." once there are no lines. */
    @Test
    fun item270_emptyLogShowsItsControlsAndTheEmptyLine() {
        openDebugLog()
        rule.onNode(hasContentDescription(backArrow)).assertIsDisplayed()
        rule.onNode(hasText(debugTitle)).assertIsDisplayed()
        rule.onNode(hasText(copyAll)).assertIsDisplayed()
        rule.onNode(hasText(clear)).assertIsDisplayed()

        tapClearAndAwaitEmpty()
        rule.onNode(hasText(empty)).assertIsDisplayed()
    }

    /** Checklist #270: a log longer than the screen opens scrolled to its newest line. */
    @Test
    fun item270_aLongLogOpensScrolledToTheNewestLine() {
        for (i in 0 until 300) container.debugLog.log("TEST", "entry-%03d".format(i))
        openDebugLog()
        await { exists(hasText("entry-299", substring = true)) }

        rule.onNode(hasText("entry-299", substring = true)).assertIsDisplayed()
        assertFalse("the oldest line is far above the fold", exists(hasText("entry-000", substring = true)))
        assertFalse(exists(empty))
    }

    /**
     * Checklist #271: Copy all puts the log's lines on the clipboard and shows the toast. The one log line built from user data, an
     * alarm for a schedule row, is really produced here with a recognisable row text, and holds only its length.
     */
    @Test
    fun item271_copyAllCopiesTheLinesWithTheToastAndNoNoteOrRowText() {
        val secret = "secret row words"
        val noteSecret = "secret note words"
        runBlocking {
            assertTrue(container.pages.createNote(noteSecret).id > 0)
            val date = LocalDate.now(container.clock).plusDays(1)
            val page = container.pages.getOrCreateDaily(date)
            val saved = container.pages.saveSchedule(
                page.id,
                listOf(ScheduleEntryEntity(pageId = page.id, date = date, minuteOfDay = 600, text = secret)),
            )
            container.alarms.rescheduleAll(saved, "test")
        }
        container.debugLog.log("TEST", "second line")
        openDebugLog()

        rule.onNode(hasText(copyAll)).performClick()
        rule.waitForIdle()

        assertEquals(str(R.string.debug_copied), ShadowToast.getTextOfLatestToast())
        val pasted = clipboardText()
        assertEquals(container.debugLog.lines().joinToString("\n"), pasted)
        assertTrue("the schedule line is in the log: $pasted", "textLen=${secret.length}" in pasted)
        assertTrue("second line" in pasted)
        assertFalse("no row text", secret in pasted)
        assertFalse("no note text", noteSecret in pasted)
    }

    /** Checklist #272: Clear empties the list, a new event appears without reopening, and Send test reminder logs its line. */
    @Test
    fun item272_clearEmptiesTheListAndNewEventsAppendLive() {
        container.debugLog.log("TEST", "old-line")
        openDebugLog()
        await { exists(hasText("old-line", substring = true)) }

        tapClearAndAwaitEmpty()
        assertFalse(exists(hasText("old-line", substring = true)))
        assertEquals(emptyList<String>(), container.debugLog.lines())

        container.debugLog.log("TEST", "live-line")
        await { exists(hasText("live-line", substring = true)) }
        assertFalse(exists(empty))

        tapClearAndAwaitEmpty()
        rule.onNode(hasContentDescription(backArrow)).performClick()
        await { exists(onPage(openDebug)) }
        val send = str(R.string.settings_test_reminder)
        scrollToText(send)
        rule.onNode(onPage(send)).performClick()
        await { container.debugLog.lines().any { "SCHEDULE" in it } }
        await { exists(str(R.string.settings_msg_test_scheduled)) }
        // The snackbar can sit over the button after the scroll, so it has to go first.
        await { !exists(str(R.string.settings_msg_test_scheduled)) }
        scrollToText(openDebug)
        rule.onNode(onPage(openDebug)).performClick()
        await { exists(debugTitle) }
        await { exists(hasText("SCHEDULE", substring = true)) }
    }

    /** Checklist #273: arrow and system Back both return to Settings with its tab highlighted; Back again goes to Today. */
    @Test
    fun item273_backArrowAndSystemBackReturnToSettingsThenToday() {
        openDebugLog()
        assertTrue("Settings stays highlighted on the log", tabSelected(R.string.tab_settings))

        rule.onNode(hasContentDescription(backArrow)).performClick()
        await { exists(onPage(openDebug)) && !exists(debugTitle) }
        assertTrue(tabSelected(R.string.tab_settings))

        scrollToText(openDebug)
        rule.onNode(onPage(openDebug)).performClick()
        await { exists(debugTitle) }
        pressSystemBack()
        await { exists(onPage(openDebug)) && !exists(debugTitle) }
        assertTrue(tabSelected(R.string.tab_settings))
        assertFalse(rule.activity.isFinishing)

        pressSystemBack()
        await { tabSelected(R.string.tab_today) }
        assertFalse(exists(onPage(openDebug)))
        assertFalse("Today is on screen, not the launcher", rule.activity.isFinishing)
    }
}
