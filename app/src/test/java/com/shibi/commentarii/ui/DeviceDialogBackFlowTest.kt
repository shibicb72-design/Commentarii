package com.shibi.commentarii.ui

import android.app.Activity
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.DeviceStateTestBase
import com.shibi.commentarii.testutil.MutableClock
import com.shibi.commentarii.testutil.TestEnv
import com.shibi.commentarii.testutil.runWithLooper
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager

/**
 * Checklist #286: Back with a dialog open, delivered to the dialog's own window as the system does, on the real MainActivity. Each
 * test proves three things: the dialog closes, the screen under it stays, and a second Back then leaves that screen, which shows
 * the first one was used up by the dialog. BiometricPrompt is the system's own window, and Robolectric has no biometrics.
 */
class DeviceDialogBackFlowTest : DeviceStateTestBase() {
    @get:Rule val tmp = TemporaryFolder()

    private val pickDate get() = str(R.string.daily_pick_date)
    private val ok get() = str(R.string.action_ok)
    private val okInDialog get() = hasText(ok) and inDialog

    private val pageList = SemanticsMatcher("the page's vertical list") {
        SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.VerticalScrollAxisRange in it.config
    }

    private fun tapInList(text: String) {
        rule.onNode(pageList).performScrollToNode(hasText(text))
        rule.waitForIdle()
        rule.onNode(hasText(text)).performClick()
        rule.waitForIdle()
    }

    private fun data(): List<Any> = runBlocking {
        val d = container.db.pageDao()
        listOf(d.getAllTasks(), d.getAllSchedule(), d.getAllBlocks(), d.getAllMoods(), d.getAllFolders(), d.getAllTags(), d.getAllPageTags())
    }

    /** Only the dialog closes, nothing is saved, and [stillThere] holds; then the same Back from the page goes where it should. */
    private fun backClosesOnlyTheDialog(dialogShown: () -> Boolean, stillThere: () -> Boolean, afterwards: () -> Unit) {
        await(dialogShown)
        val before = data()

        pressBackInDialog()

        await { !dialogShown() }
        assertTrue("the screen under the dialog stays", stillThere())
        assertEquals("nothing was applied", before, data())
        settleFor(200)
        assertFalse("and the dialog stays closed", dialogShown())

        pressBack()
        afterwards()
    }

    /** On Today the page's own Back is leaving the app. */
    private fun thenTodayLeaves(): () -> Unit {
        val activity = rule.activity
        return { assertTrue("the second back leaves Today", activity.isFinishing) }
    }

    private fun onToday() = tabSelected(R.string.tab_today) && exists(hasText(dayTitle(today)))

    @Test
    fun item286_datePickerOnToday() {
        awaitToday()
        tapDescription(pickDate)
        backClosesOnlyTheDialog({ exists(okInDialog) }, ::onToday, thenTodayLeaves())
    }

    @Test
    fun item286_customTimePicker() {
        awaitToday()
        tapInList(str(R.string.daily_custom_time))
        backClosesOnlyTheDialog({ exists(okInDialog) }, { tabSelected(R.string.tab_today) }, thenTodayLeaves())
    }

    @Test
    fun item286_newFolderDialog() {
        awaitToday()
        tapInList(str(R.string.daily_no_folder))
        rule.onNode(hasText(str(R.string.daily_new_folder))).performClick()
        rule.waitForIdle()
        await { exists(okInDialog) }
        rule.onNode(hasSetTextAction() and inDialog).performTextInput("Typed")
        rule.waitForIdle()
        backClosesOnlyTheDialog({ exists(okInDialog) }, ::onToday, thenTodayLeaves())
    }

    @Test
    fun item286_newTagDialog() {
        awaitToday()
        tapInList(str(R.string.daily_add_tag))
        await { exists(okInDialog) }
        rule.onNode(hasSetTextAction() and inDialog).performTextInput("Typed")
        rule.waitForIdle()
        backClosesOnlyTheDialog({ exists(okInDialog) }, ::onToday, thenTodayLeaves())
    }

    private fun offerRollover() {
        awaitToday()
        val yesterday = today.minusDays(1)
        val page = runBlocking { container.pages.getOrCreateDaily(yesterday) }
        runBlocking {
            container.pages.saveTasks(
                page.id,
                TaskSection.TODO,
                listOf(TaskItemEntity(pageId = page.id, section = TaskSection.TODO, position = 0, text = "Old todo")),
            )
        }
        runWithLooper { container.settings.setLastOpenedDate(null) }
        openTab(R.string.tab_week)
        openTab(R.string.tab_today)
    }

    @Test
    fun item286_rolloverDialog() {
        offerRollover()
        val moveAll = hasText(str(R.string.rollover_move_all))
        await { exists(moveAll) }
        backClosesOnlyTheDialog({ exists(moveAll) }, ::onToday, thenTodayLeaves())
    }

    @Test
    fun item286_rolloverPickList() {
        offerRollover()
        val moveAll = hasText(str(R.string.rollover_move_all))
        await { exists(moveAll) }
        rule.onNode(hasText(str(R.string.rollover_pick))).performClick()
        rule.waitForIdle()
        val pickTitle = hasText(str(R.string.rollover_pick_title))
        backClosesOnlyTheDialog({ exists(pickTitle) }, ::onToday, thenTodayLeaves())
        assertFalse("nor does the first step of the dialog come back", exists(moveAll))
    }

    @Test
    fun item286_datePickerOnWeekThenBackGoesToToday() {
        awaitToday()
        openTab(R.string.tab_week)
        await { exists(hasContentDescription(pickDate)) }
        tapDescription(pickDate)
        backClosesOnlyTheDialog({ exists(okInDialog) }, { tabSelected(R.string.tab_week) }) { awaitToday() }
    }

    @Test
    fun item286_datePickerOnMonthThenBackGoesToToday() {
        awaitToday()
        openTab(R.string.tab_month)
        await { exists(hasContentDescription(pickDate)) }
        tapDescription(pickDate)
        backClosesOnlyTheDialog({ exists(okInDialog) }, { tabSelected(R.string.tab_month) }) { awaitToday() }
    }

    private fun openNote(title: String) {
        runBlocking { container.pages.createNote(title) }
        awaitToday()
        openTab(R.string.tab_notes)
        await { exists(hasText(title)) }
        rule.onNode(card(title)).performClick()
        rule.waitForIdle()
        await { exists(hasContentDescription(str(R.string.ed_organize))) && exists(field(title)) }
    }

    private fun onEditor(title: String) = tabSelected(R.string.tab_notes) && exists(field(title))

    private fun thenOnNotes(): () -> Unit = { await { exists(hasContentDescription(str(R.string.nt_new))) } }

    @Test
    fun item286_noteOrganizeDialogThenBackGoesToNotes() {
        openNote("Organized")
        tapDescription(str(R.string.ed_organize))
        val done = hasText(str(R.string.action_done)) and inDialog
        backClosesOnlyTheDialog({ exists(done) }, { onEditor("Organized") }, thenOnNotes())
    }

    @Test
    fun item286_noteDeleteDialogKeepsTheNoteThenBackGoesToNotes() {
        openNote("Keep me")
        tapDescription(str(R.string.ed_delete))
        val title = hasText(str(R.string.ed_delete_title))
        backClosesOnlyTheDialog({ exists(title) }, { onEditor("Keep me") }, thenOnNotes())
        assertTrue("the note is still in the list", exists(hasText("Keep me")))
    }

    private fun onSettings() = tabSelected(R.string.tab_settings) && exists(str(R.string.settings_section_backup))

    @Test
    fun item286_importConfirmImportsNothingThenBackGoesToToday() {
        val env = TestEnv(tmp, MutableClock(today.atTime(9, 0)))
        try {
            runBlocking { env.pages.createNote("From the file") }
            val file = tmp.newFile().also { it.writeText(runBlocking { env.io.export() }) }
            awaitToday()
            openTab(R.string.tab_settings)
            await { onSettings() }
            rule.onNode(hasText(str(R.string.settings_import))).performScrollTo().performClick()
            rule.waitForIdle()
            val request = checkNotNull(shadowOf(rule.activity).nextStartedActivityForResult) { "tapping Import started no picker" }
            shadowOf(rule.activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
            rule.waitForIdle()
            val confirm = hasText(str(R.string.settings_import_confirm)) and inDialog
            backClosesOnlyTheDialog({ exists(confirm) }, ::onSettings) { awaitToday() }
            val titles = runBlocking { container.db.pageDao().getAllPages() }.map { it.title }
            assertFalse("the file was not imported", "From the file" in titles)
        } finally {
            env.close()
        }
    }

    /** With nothing enrolled, the lock switch opens the app's own "set up a screen lock" dialog instead of the system prompt. */
    @Test
    fun item286_lockHelpDialogLeavesTheLockOffThenBackGoesToToday() {
        Shadow.extract<ShadowBiometricManager>(rule.activity.getSystemService(BiometricManager::class.java)).setCanAuthenticate(false)
        leaveAndReturn()
        awaitToday()
        openTab(R.string.tab_settings)
        await { onSettings() }
        val lockTitle = hasText(str(R.string.settings_lock))
        rule.onNode(lockTitle).performScrollTo()
        rule.waitForIdle()
        val row = rule.onNode(lockTitle).fetchSemanticsNode().boundsInRoot.center.y
        val nodes = rule.onAllNodes(isToggleable()).fetchSemanticsNodes()
        rule.onAllNodes(isToggleable())[nodes.indices.minBy { abs(nodes[it].boundsInRoot.center.y - row) }].performClick()
        rule.waitForIdle()
        val help = hasText(str(R.string.lock_unavailable_title))
        backClosesOnlyTheDialog({ exists(help) }, ::onSettings) { awaitToday() }
        val enabled = runWithLooper { container.settings.lockEnabled.first() }
        assertEquals("the lock stays off", false, enabled)
    }
}
