package com.shibi.commentarii.ui.theme

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import com.shibi.commentarii.R
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.testutil.ThemeFlowTestBase
import com.shibi.commentarii.ui.notes.FORMAT_TOOLBAR_TAG
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Checklist #283: the screens and dialogs [ThemePagesTest] does not reach, in Rose & Cream and an art theme, light and dark, on the real
 * MainActivity: Settings, the debug log, the note editor and its toolbar, the snackbar, and the delete, Folder and tags, date, time,
 * name and import dialogs. Each text's measured colour against what is really drawn behind it meets WCAG 4.5:1 (3:1 for large text).
 * Not judged here: how it looks to a person. The tall window keeps more of each page in one view.
 */
@Config(qualifiers = "en-rUS-w412dp-h1600dp-xxhdpi")
class DarkLightEveryScreenTest : ThemeFlowTestBase() {
    @get:Rule val tmp = TemporaryFolder()

    private val themes = listOf(AppThemes.Default, AppThemes.byId("washi_planner"))
    private val today: LocalDate get() = LocalDate.now(container.clock)
    private val noteTitle = "Every screen"
    private var noteId = 0L

    @Before
    fun seed() {
        container.debugLog.log("TEST", "a line of the log")
        runBlocking {
            noteId = container.pages.createNote(noteTitle).id
            container.pages.saveBlockDocument(noteId, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(text = "note body"))))
        }
    }

    @After
    fun clearLog() = container.debugLog.clear()

    private val pageColumn = SemanticsMatcher("the page's vertical list") {
        SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.VerticalScrollAxisRange in it.config
    }

    private fun barTop() = boundsOf(tab(R.string.tab_settings)).top

    private fun assertReadable(all: List<Reading>, what: String) {
        assertTrue("no text found: $what", all.isNotEmpty())
        val failures = all.filterNot { it.ok }.map { it.toString() }
        assertTrue("$what: ${failures.size} of ${all.size} below threshold:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    private fun forEachLook(check: (where: String) -> Unit) {
        for (theme in themes) for (dark in listOf(false, true)) {
            applyTheme(theme, dark)
            check("${theme.id}/${if (dark) "dark" else "light"}")
        }
    }

    private fun tapDialog(text: String) {
        rule.onNode(hasText(text) and hasAnyAncestor(isDialog())).performClick()
        await { !exists(isDialog()) }
    }

    /** Checklist #283, Settings: every section in turn, and the snackbar over it. */
    @Test
    fun item283_settingsIsReadableInEachLook() {
        val sections = listOf(
            R.string.settings_section_reminders,
            R.string.settings_section_backup,
            R.string.settings_section_theme,
            R.string.settings_section_licences,
            R.string.settings_debug_open,
        ).map { str(it) }
        openSettings()
        forEachLook { where ->
            for (section in sections) {
                scrollToText(section)
                rule.waitForIdle()
                assertReadable(readings("$where/Settings at $section", barTop()), section)
            }
        }
    }

    /** Checklist #283: the snackbar's message on the Settings page. */
    @Test
    fun item283_theSnackbarIsReadableInEachLook() {
        val send = str(R.string.settings_test_reminder)
        val message = str(R.string.settings_msg_test_scheduled)
        openSettings()
        forEachLook { where ->
            scrollToText(send)
            rule.onNode(onPage(send)).performClick()
            await { exists(message) }
            val found = readings("$where/snackbar", barTop()).filter { it.text == message.take(40) }
            assertReadable(found, "the snackbar")
            await { !exists(message) }
        }
    }

    /** Checklist #283: the debug log, and the note editor with its toolbar. */
    @Test
    fun item283_theDebugLogAndTheEditorAreReadableInEachLook() {
        val backArrow = str(R.string.ed_back)
        forEachLook { where ->
            openSettings()
            val open = str(R.string.settings_debug_open)
            scrollToText(open)
            rule.onNode(onPage(open)).performClick()
            await { exists(str(R.string.debug_title)) }
            assertReadable(readings("$where/debug log", barTop()), "the debug log")
            rule.onNode(hasContentDescription(backArrow)).performClick()
            await { exists(onPage(open)) }

            openTab(R.string.tab_notes)
            await { exists(noteTitle) }
            rule.onNode(hasText(noteTitle)).performClick()
            await { exists(hasText("note body")) }
            rule.onNode(hasText("note body")).performClick()
            await { exists(hasContentDescription(str(R.string.ed_bold))) }
            rule.onNodeWithTag(FORMAT_TOOLBAR_TAG).assertExists()
            val toolbarTop = boundsOf(hasContentDescription(str(R.string.ed_bold))).top
            assertReadable(readings("$where/editor", toolbarTop), "the editor")
            rule.onNode(hasContentDescription(backArrow)).performClick()
            await { exists(noteTitle) }
        }
    }

    /** Checklist #283: the editor's two dialogs. */
    @Test
    fun item283_theEditorDialogsAreReadableInEachLook() {
        forEachLook { where ->
            openTab(R.string.tab_notes)
            await { exists(noteTitle) }
            rule.onNode(hasText(noteTitle)).performClick()
            await { exists(hasContentDescription(str(R.string.ed_delete))) }

            rule.onNode(hasContentDescription(str(R.string.ed_delete))).performClick()
            await { exists(isDialog()) }
            assertReadable(dialogReadings("$where/delete dialog"), "the delete dialog")
            tapDialog(str(R.string.action_cancel))

            rule.onNode(hasContentDescription(str(R.string.ed_organize))).performClick()
            await { exists(isDialog()) }
            assertReadable(dialogReadings("$where/folder and tags dialog"), "the Folder and tags dialog")
            tapDialog(str(R.string.action_done))

            rule.onNode(hasContentDescription(str(R.string.ed_back))).performClick()
            await { exists(noteTitle) }
        }
    }

    /** Checklist #283: the date picker and the custom time picker on Today. */
    @Test
    fun item283_theDateAndTimePickersAreReadableInEachLook() {
        openTab(R.string.tab_today)
        forEachLook { where ->
            val pick = hasContentDescription(str(R.string.daily_pick_date)) and hasClickAction()
            await { exists(pick) }
            // This test is about the dialog's colours; opening it through the action does not depend on where the last look left the list.
            rule.onNode(pick).performSemanticsAction(SemanticsActions.OnClick)
            await { exists(isDialog()) }
            assertReadable(dialogReadings("$where/date picker"), "the date picker")
            tapDialog(str(R.string.action_cancel))

            val custom = str(R.string.daily_custom_time)
            rule.onNode(pageColumn).performScrollToNode(hasText(custom))
            rule.onNode(hasText(custom)).performClick()
            await { exists(isDialog()) }
            // The AM/PM toggle draws its divider over the text box in a software draw, so no paper can be read there: see
            // ThemeContrastTest.item283_theTimePickersPeriodToggleReadsInEveryThemeAndMode for those two.
            assertReadable(dialogReadings("$where/time picker").filter { it.text !in listOf("AM", "PM") }, "the time picker")
            tapDialog(str(R.string.action_cancel))
        }
    }

    /** Checklist #283: the import confirmation, reached by answering the real picker with a real file. */
    @Test
    fun item283_theImportDialogIsReadableInEachLook() {
        val file: File = tmp.newFile("backup.json").apply { writeText(runBlocking { container.exportImport.export() }) }
        val import = str(R.string.settings_import)
        openSettings()
        forEachLook { where ->
            scrollToText(import)
            rule.onNode(onPage(import)).performClick()
            rule.waitForIdle()
            val request = checkNotNull(shadowOf(rule.activity).nextStartedActivityForResult)
            shadowOf(rule.activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
            await { exists(isDialog()) }
            assertReadable(dialogReadings("$where/import dialog"), "the import dialog")
            tapDialog(str(R.string.action_cancel))
        }
    }
}
