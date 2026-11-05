package com.shibi.commentarii.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.shibi.commentarii.R
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.domain.weekStart
import com.shibi.commentarii.testutil.MainActivityTestBase
import com.shibi.commentarii.testutil.MutableClock
import com.shibi.commentarii.testutil.TestEnv
import com.shibi.commentarii.testutil.runWithLooper
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivity.IntentForResult

/**
 * Checklist #241-#245, #247, #249-#252, #258, #275: the Settings page on the real MainActivity and container. The system file
 * picker cannot open here, so what is asserted is the intent the app starts (shadowOf(activity).nextStartedActivityForResult) and
 * the answer is delivered with receiveResult on a real file in a temp folder. Everything the app does after the answer is real:
 * the view model, Room, the settings store, the snackbar and the other tabs.
 */
class SettingsAppFlowTest : MainActivityTestBase() {
    @get:Rule val tmp = TemporaryFolder()

    private val envs = mutableListOf<TestEnv>()

    @After
    fun closeEnvs() {
        envs.forEach { it.close() }
    }

    // ---- Strings ----

    private val settingsTitle get() = str(R.string.tab_settings)
    private val exportButton get() = str(R.string.settings_export)
    private val importButton get() = str(R.string.settings_import)
    private val exportReminder get() = str(R.string.settings_export_reminder)
    private val neverExported get() = str(R.string.settings_never_exported)
    private val exportOk get() = str(R.string.settings_msg_export_ok)
    private val exportFailed get() = str(R.string.settings_msg_export_failed)
    private val importOk get() = str(R.string.settings_msg_import_ok)
    private val importBad get() = str(R.string.settings_msg_import_bad_format)
    private val importFailed get() = str(R.string.settings_msg_import_failed)
    private val dialogTitle get() = str(R.string.settings_import_title)
    private val dialogMessage get() = str(R.string.settings_import_message)
    private val replaceAll get() = str(R.string.settings_import_confirm)
    private val cancel get() = str(R.string.action_cancel)
    private val inDialog = hasAnyAncestor(isDialog())

    // ---- The page ----

    private val scrollBox = SemanticsMatcher("the settings page's scroll area") {
        SemanticsProperties.VerticalScrollAxisRange in it.config && SemanticsActions.ScrollBy in it.config
    }

    /** Text on the page itself, not in the bottom bar or a dialog (the tab label "Settings" is also the page title). */
    private fun onPage(text: String) = hasText(text) and hasAnyAncestor(scrollBox)

    private fun openSettings() {
        openTab(R.string.tab_settings)
        await { exists(onPage(str(R.string.settings_section_backup))) }
    }

    /** Opens Settings once the app has settled (today's page is created on start), and returns the database as it is then. */
    private fun openSettingsAndSnapshot(): List<Any> {
        dailyId()
        openSettings()
        settleFor(300)
        return snapshot()
    }

    private fun scrollToText(text: String) {
        rule.onNode(onPage(text)).performScrollTo()
        rule.waitForIdle()
    }

    private fun scrollValue(): Float = rule.onNode(scrollBox).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun scrollMax(): Float = rule.onNode(scrollBox).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    /** Distance of the node's top from the top of the scrolled content, which is the same whatever the scroll position. */
    private fun contentY(text: String): Float {
        val node = rule.onNode(onPage(text)).fetchSemanticsNode()
        val viewport = rule.onNode(scrollBox).fetchSemanticsNode()
        return node.positionInRoot.y - viewport.positionInRoot.y + scrollValue()
    }

    private fun bottomOf(node: SemanticsNode) = node.positionInRoot.y + node.size.height

    private fun barTop() = boundsOf(tab(R.string.tab_settings)).top

    private fun dp(value: Int) = value * rule.activity.resources.displayMetrics.density

    private fun centerY(node: SemanticsNode) = node.positionInRoot.y + node.size.height / 2f

    private fun reminderSwitch(): SemanticsNodeInteraction {
        scrollToText(exportReminder)
        val row = centerY(rule.onNode(onPage(exportReminder)).fetchSemanticsNode())
        val nodes = rule.onAllNodes(isToggleable()).fetchSemanticsNodes()
        return rule.onAllNodes(isToggleable())[nodes.indices.minBy { abs(centerY(nodes[it]) - row) }]
    }

    private fun tapOnPage(text: String) {
        scrollToText(text)
        rule.onNode(onPage(text)).performClick()
        rule.waitForIdle()
    }

    /** Runs the looper and compose for [millis] of real time, for checks that something does not arrive. */
    private fun settleFor(millis: Long) {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            Thread.sleep(10)
        }
    }

    // ---- Data ----

    private fun seedNote(title: String, body: String): Long = runBlocking {
        val id = container.pages.createNote(title).id
        container.pages.saveBlockDocument(id, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(text = body))))
        id
    }

    private fun setTasks(pageId: Long, section: String, vararg texts: String) = runBlocking {
        container.pages.saveTasks(
            pageId,
            section,
            texts.mapIndexed { i, t -> TaskItemEntity(pageId = pageId, section = section, position = i, text = t) },
        )
    }

    private val today: LocalDate get() = LocalDate.now(container.clock)
    private fun dailyId() = runBlocking { container.pages.getOrCreateDaily(today).id }
    private fun weeklyId() = runBlocking { container.pages.getOrCreateWeekly(weekStart(today)).id }
    private fun monthlyId() = runBlocking { container.pages.getOrCreateMonthly(YearMonth.from(today).atDay(1)).id }

    private fun snapshot(): List<Any> = runBlocking {
        val d = container.db.pageDao()
        listOf(d.getAllPages(), d.getAllTasks(), d.getAllSchedule(), d.getAllBlocks(), d.getAllMoods())
    }

    private fun titles(): Set<String> = runBlocking { container.db.pageDao().getAllPages() }.map { it.title }.toSet()

    private fun noteTitles(): Set<String> = runBlocking { container.db.pageDao().getAllPages() }.filter { it.type == "NOTE" }.map { it.title }.toSet()

    private fun taskTexts(): Set<String> = runBlocking { container.db.pageDao().getAllTasks() }.map { it.text }.filter { it.isNotBlank() }.toSet()

    private fun lastExportAt(): Instant? = runWithLooper { container.settings.lastExportAt.first() }

    /** A valid backup of other data, from a separate store (see [TestEnv]); its settings block is the store's defaults. */
    private fun backupOfOtherData(noteTitle: String, todo: String? = null): File {
        val env = TestEnv(tmp, MutableClock(today.atTime(9, 0))).also { envs += it }
        runBlocking {
            env.pages.createNote(noteTitle)
            if (todo != null) {
                val page = env.pages.getOrCreateDaily(today)
                env.db.pageDao().insertAllTasks(listOf(TaskItemEntity(pageId = page.id, section = TaskSection.TODO, position = 0, text = todo)))
            }
        }
        return tmp.newFile().also { it.writeText(runBlocking { env.io.export() }) }
    }

    // ---- The pickers ----

    private fun startedPicker(): IntentForResult? = shadowOf(rule.activity).nextStartedActivityForResult

    private fun answer(request: IntentForResult, file: File?) {
        if (file == null) {
            shadowOf(rule.activity).receiveResult(request.intent, Activity.RESULT_CANCELED, null)
        } else {
            shadowOf(rule.activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        }
        rule.waitForIdle()
    }

    private fun exportPicker(): IntentForResult {
        val before = suggestedName()
        tapOnPage(exportButton)
        val request = checkNotNull(startedPicker()) { "tapping Export started no picker" }
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.intent.action)
        assertEquals("application/json", request.intent.type)
        assertTrue(request.intent.getStringExtra(Intent.EXTRA_TITLE) in setOf(before, suggestedName()))
        return request
    }

    private fun importPicker(): IntentForResult {
        tapOnPage(importButton)
        val request = checkNotNull(startedPicker()) { "tapping Import started no picker" }
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
        assertEquals("*/*", request.intent.type)
        return request
    }

    private fun suggestedName() = backupFileName(today)

    private fun exportTo(file: File) {
        answer(exportPicker(), file)
        await { exists(exportOk) }
    }

    private fun importFrom(file: File) {
        answer(importPicker(), file)
        await { exists(dialogTitle) }
        rule.onNode(hasText(replaceAll) and inDialog).performClick()
        rule.waitForIdle()
        await { exists(importOk) }
    }

    private fun assertNoSettingsMessage() {
        for (text in listOf(exportOk, exportFailed, importOk, importBad, importFailed)) {
            assertFalse("unexpected message: $text", exists(text))
        }
    }

    private fun assertNoImportDialog() {
        assertFalse(exists(dialogTitle))
        assertFalse(exists(dialogMessage))
        assertFalse(exists(hasText(replaceAll)))
    }

    private fun noteTitlesIn(root: JSONObject): List<String> = root.getJSONArray("pages").let { pages ->
        (0 until pages.length()).map { pages.getJSONObject(it) }.filter { it.getString("type") == "NOTE" }.map { it.getString("title") }
    }

    // ---- #241 ----

    /**
     * Checklist #241: title first, then Privacy, Reminders, Backup, Theme, Debugging, Licences, in that order down the page (read
     * after scrolling to each); the whole page scrolls; and its last content comes to rest above the bottom bar when scrolled to the
     * end, having been below it before.
     */
    @Test
    fun item241_titleThenSixCardsInOrderAndTheWholePageScrollsAboveTheBottomBar() {
        openSettings()
        val cards = listOf(
            R.string.settings_section_privacy, R.string.settings_section_reminders, R.string.settings_section_backup,
            R.string.settings_section_theme, R.string.settings_debugging, R.string.settings_section_licences,
        ).map { str(it) }
        assertEquals(listOf("Privacy", "Reminders", "Backup", "Theme", "Debugging", "Licences"), cards)
        assertEquals("one page title", 1, count(onPage(settingsTitle)))
        val licenceButton = str(R.string.settings_licence_show)

        assertEquals("starts at the top", 0f, scrollValue(), 0.5f)
        assertTrue("the page is longer than the window, so it must scroll", scrollMax() > 0f)
        assertTrue("last content starts below the bar before scrolling", bottomOf(rule.onNode(onPage(licenceButton)).fetchSemanticsNode()) > barTop())

        val positions = (listOf(settingsTitle) + cards).map { name ->
            scrollToText(name)
            val node = rule.onNode(onPage(name)).fetchSemanticsNode()
            assertTrue("$name is on screen after scrolling to it", node.boundsInRoot.height > 0f)
            assertTrue("$name is not behind the bottom bar", bottomOf(node) <= barTop() + 1f)
            contentY(name)
        }
        assertEquals("title and cards run top to bottom", positions.sorted(), positions)
        assertEquals("no two share a row", positions.size, positions.distinct().size)

        var swipes = 0
        while (scrollValue() < scrollMax() - 0.5f && swipes++ < 20) {
            rule.onNode(scrollBox).performTouchInput { swipeUp() }
            rule.waitForIdle()
        }
        assertEquals("scrolled to the very end", scrollMax(), scrollValue(), 0.5f)
        val last = rule.onNode(onPage(licenceButton)).fetchSemanticsNode()
        assertTrue("last content above the bar: bottom ${bottomOf(last)} bar ${barTop()}", bottomOf(last) <= barTop())
        assertTrue("with the page padding to spare", barTop() - bottomOf(last) >= dp(16) - 1f)
    }

    // ---- #242 ----

    /** Checklist #242: the real switch, off and on, and what the settings store holds each time. */
    @Test
    fun item242_exportReminderSwitchGoesOffAndOnAndTheStoreFollows() {
        openSettings()
        reminderSwitch().assertIsOn()
        assertTrue(runWithLooper { container.settings.exportReminderEnabled.first() })

        reminderSwitch().performClick()
        await { runWithLooper { container.settings.exportReminderEnabled.first() }.not() }
        rule.waitForIdle()
        reminderSwitch().assertIsOff()

        reminderSwitch().performClick()
        await { runWithLooper { container.settings.exportReminderEnabled.first() } }
        rule.waitForIdle()
        reminderSwitch().assertIsOn()
    }

    /** Checklist #242: a stored Off is what the screen shows on a fresh start of the screen (the switch reads the store). */
    @Test
    fun item242_aStoredOffIsShownWhenTheScreenOpensAgain() {
        runWithLooper { container.settings.setExportReminderEnabled(false) }
        openSettings()
        reminderSwitch().assertIsOff()
        assertEquals(ToggleableState.Off, reminderSwitch().fetchSemanticsNode().config[SemanticsProperties.ToggleableState])
    }

    // ---- #243 ----

    /** Checklist #243, with its control: never exported shows the line under the two buttons; an export date replaces it. */
    @Test
    fun item243_neverExportedLineSitsUnderTheButtonsAndAnExportDateReplacesIt() {
        assertNull(lastExportAt())
        openSettings()
        scrollToText(neverExported)
        assertTrue(exists(onPage(neverExported)))
        assertFalse(exists(hasText("Last export", substring = true)))
        val buttons = listOf(exportButton, importButton).map { rule.onNode(onPage(it)).fetchSemanticsNode() }
        val line = rule.onNode(onPage(neverExported)).fetchSemanticsNode()
        buttons.forEach { assertTrue("under the buttons", line.positionInRoot.y >= bottomOf(it)) }

        runWithLooper { container.settings.setLastExportAt(Instant.parse("2031-03-12T15:00:00Z")) }
        await { exists(hasText("Last export: 2031-03-12")) }
        assertFalse(exists(neverExported))
    }

    // ---- #244 ----

    /**
     * Checklist #244: Export starts the Save picker with the suggested name from backupFileName; once the picker answers, the snackbar
     * says "Backup exported.", the line becomes "Last export: yyyy-MM-dd", and the file's bytes are a backup that imports into a
     * separate database with the seeded note in it. The system picker's own window is not exercised.
     */
    @Test
    fun item244_exportPicksTheSuggestedNameThenSnackbarDateLineAndAValidFile() {
        seedNote("Alpha note", "alpha body")
        val file = tmp.newFile("picked.json")
        assertEquals(0L, file.length())
        openSettings()
        scrollToText(neverExported)

        val picker = exportPicker()
        assertEquals("commentarii-backup-$today.json", picker.intent.getStringExtra(Intent.EXTRA_TITLE))
        assertNull("nothing is written before the picker answers", lastExportAt())
        assertEquals(0L, file.length())

        answer(picker, file)
        await { exists(exportOk) }
        assertEquals("Backup exported.", exportOk)
        await { exists(hasText("Last export", substring = true)) }
        assertFalse(exists(neverExported))
        val line = rule.onNode(hasText("Last export", substring = true)).fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        assertTrue(line, Regex("""Last export: \d{4}-\d{2}-\d{2}""").matches(line))
        val shown = LocalDate.parse(line.removePrefix("Last export: "))
        assertEquals("the date is the local day, as in the file name", today, shown)
        assertNotNull(lastExportAt())

        val text = file.readText(Charsets.UTF_8)
        val root = JSONObject(text)
        assertEquals(1, root.getInt("formatVersion"))
        val env = TestEnv(tmp, MutableClock(today.atTime(9, 0))).also { envs += it }
        runBlocking { env.io.import(text) }
        assertTrue("Alpha note" in runBlocking { env.db.pageDao().getAllPages() }.map { it.title })
    }

    /**
     * Checklist #244, the date in the line: the line shows a stored instant's date in the device's time zone, as the suggested file
     * name does (it uses LocalDate.now(clock)). The instant is 23:30 local time on one day and 00:30 local on the next, so that in
     * any zone but UTC one of them falls on a different UTC day.
     */
    @Test
    fun item244_lastExportLineShowsTheLocalDayOfTheStoredInstant() {
        val zone = container.zone
        assertTrue("needs a zone other than UTC to tell the two days apart", zone.rules.getOffset(Instant.parse("2031-03-12T12:00:00Z")).totalSeconds != 0)
        openSettings()
        for ((local, time) in listOf(LocalDate.of(2031, 3, 12) to "23:30", LocalDate.of(2031, 3, 15) to "00:30")) {
            val instant = local.atTime(java.time.LocalTime.parse(time)).atZone(zone).toInstant()
            runWithLooper { container.settings.setLastExportAt(instant) }
            await { exists(hasText("Last export", substring = true)) }
            scrollToText(exportButton)
            val line = rule.onNode(hasText("Last export", substring = true)).fetchSemanticsNode().config[SemanticsProperties.Text].single().text
            assertEquals("stored $instant is $local at $time in $zone", "Last export: $local", line)
        }
    }

    // ---- #245 ----

    /** Checklist #245: the picker was started, then backed out of; nothing shows, nothing is written, the date is unchanged. Control: the same tap answered with a file does show it. */
    @Test
    fun item245_backingOutOfTheSavePickerChangesNothing() {
        seedNote("Alpha note", "alpha body")
        val file = tmp.newFile("never-written.json")
        file.writeText("sentinel")
        val before = openSettingsAndSnapshot()
        scrollToText(neverExported)

        answer(exportPicker(), null)
        settleFor(400)

        assertNoSettingsMessage()
        assertTrue("still never exported", exists(onPage(neverExported)))
        assertFalse(exists(hasText("Last export", substring = true)))
        assertNull(lastExportAt())
        assertEquals("sentinel", file.readText())
        assertEquals(before, snapshot())
        assertNull("no second picker", startedPicker())

        // Control: the same button, answered with a file, writes it and shows everything the cancel did not.
        exportTo(file)
        await { exists(hasText("Last export", substring = true)) }
        assertTrue(file.readText() != "sentinel")
        assertNotNull(lastExportAt())
    }

    // ---- #247 ----

    /**
     * Checklist #247: the target file already holds longer junk, and a larger earlier export; exporting onto it leaves exactly the
     * new backup, which parses and contains neither. Whether Robolectric's file path honours the "wt" truncation is asserted by
     * [item247_truncatingIsReallyExercised]; whether the mode string reaches a real provider is in SettingsExportFileModeTest.
     */
    @Test
    fun item247_exportOntoALongerExistingFileLeavesOnlyTheNewBackup() {
        val file = tmp.newFile("existing.json")
        file.writeText("JUNK-".repeat(40_000))
        val junkLength = file.length()
        seedNote("Small note", "small")
        openSettings()

        exportTo(file)
        val first = file.readText(Charsets.UTF_8)
        assertFalse("junk left behind", first.contains("JUNK"))
        assertTrue("file shorter than the junk it replaced: ${file.length()} vs $junkLength", file.length() < junkLength)
        assertEquals(listOf("Small note"), noteTitlesIn(JSONObject(first)))

        // Second export onto the same file, from less data than the first: the tail of the first must not survive.
        val big = seedNote("Big note", "x".repeat(20_000))
        runBlocking { container.pages.deletePage(big) }
        exportTo(file)
        val second = file.readText(Charsets.UTF_8)
        assertFalse(second.contains("xxxx"))
        assertTrue(second.endsWith("}"))
        val root = JSONObject(second)
        assertEquals(listOf("Small note"), noteTitlesIn(root))
        assertEquals("exactly one JSON document in the file", second.length, root.toString().length)
    }

    /** Control for the above: the junk file really was longer than the export, so an untruncated write would show it. */
    @Test
    fun item247_truncatingIsReallyExercised() {
        val file = tmp.newFile("existing.json")
        val junk = "JUNK-".repeat(40_000)
        file.writeText(junk)
        seedNote("Small note", "small")
        val exportLength = runBlocking { container.exportImport.export() }.toByteArray().size
        assertTrue("fixture: junk ${junk.length} must be longer than the export $exportLength", junk.length > exportLength)
        openSettings()
        exportTo(file)
        assertTrue("file is ${file.length()} bytes, an untruncated write would leave at least ${junk.length}", file.length() < junk.length)
    }

    // ---- #249 ----

    /** Checklist #249: Import starts the Open picker; after a file is picked, the dialog asks "Import backup?" with both buttons, and nothing has changed yet. */
    @Test
    fun item249_pickingAFileAsksBeforeReplacingAnything() {
        seedNote("Alpha note", "alpha body")
        val file = backupOfOtherData("From the file")
        val before = openSettingsAndSnapshot()
        assertNoImportDialog()

        answer(importPicker(), file)
        await { exists(dialogTitle) }

        assertEquals("Import backup?", dialogTitle)
        assertEquals("Importing replaces all data currently in the app. This cannot be undone.", dialogMessage)
        assertTrue(exists(hasText("Importing replaces all data currently in the app. This cannot be undone.") and inDialog))
        assertTrue(exists(hasText("Replace all data") and inDialog))
        assertTrue(exists(hasText("Cancel") and inDialog))
        assertEquals("Replace all data", replaceAll)
        assertEquals("Cancel", cancel)
        assertNoSettingsMessage()
        assertEquals("asking changes nothing", before, snapshot())
    }

    // ---- #250 ----

    /** Checklist #250: Import started, picker backed out of: no dialog, nothing changed. Control: the same tap answered with a file does show the dialog. */
    @Test
    fun item250_backingOutOfTheOpenPickerShowsNoDialogAndChangesNothing() {
        seedNote("Alpha note", "alpha body")
        val file = backupOfOtherData("From the file")
        val fileBytes = file.readBytes()
        val before = openSettingsAndSnapshot()

        answer(importPicker(), null)
        settleFor(400)

        assertNoImportDialog()
        assertNoSettingsMessage()
        assertEquals(before, snapshot())
        assertTrue(fileBytes.contentEquals(file.readBytes()))
        assertNull(lastExportAt())

        // Control: the dialog is visible to this test when the picker does answer.
        answer(importPicker(), file)
        await { exists(dialogTitle) }
        assertTrue(exists(hasText(replaceAll) and inDialog))
        assertEquals("still nothing imported", before, snapshot())
    }

    // ---- #251 ----

    /** Checklist #251: pick a file, Cancel in the dialog: the dialog is gone and the data, in the database and on the Notes tab, is untouched. Control: confirming the same file does replace it. */
    @Test
    fun item251_cancelInTheDialogLeavesTheDataUntouched() {
        seedNote("Alpha note", "alpha body")
        setTasks(dailyId(), TaskSection.TODO, "Original todo")
        val file = backupOfOtherData("From the file", todo = "File todo")
        val before = openSettingsAndSnapshot()

        answer(importPicker(), file)
        await { exists(dialogTitle) }
        rule.onNode(hasText(cancel) and inDialog).performClick()
        rule.waitForIdle()
        await { !exists(dialogTitle) }
        settleFor(300)

        assertNoImportDialog()
        assertNoSettingsMessage()
        assertEquals(before, snapshot())
        assertEquals(setOf("Alpha note"), noteTitles())
        assertEquals(setOf("Original todo"), taskTexts())
        openTab(R.string.tab_notes)
        await { exists("Alpha note") }
        assertFalse(exists("From the file"))

        // Control: the same file, confirmed, replaces the data, so Cancel is what saved it.
        openSettings()
        importFrom(file)
        assertTrue("From the file" in titles())
        assertFalse("Alpha note" in titles())
        assertEquals(setOf("File todo"), taskTexts())
    }

    // ---- #252 ----

    /** Types [query] and waits until [condition] holds: the search debounce is a delay on the main looper, which only idleFor moves. */
    private fun searchUntil(query: String, condition: () -> Boolean) {
        val field = rule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput(query)
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
            condition()
        }
        rule.waitForIdle()
    }

    private val nothingFound get() = str(R.string.sr_no_results)

    /**
     * The #252 story up to the import: original data, Export to a file, then a new note and a changed day, week and month; the tabs
     * in [visit] are opened after the changes (so their view models already hold the new data, which also shows the checks below can
     * see it), then Import of the earlier file is confirmed. Leaves the app on Settings with "Backup imported." shown.
     */
    private fun exportChangeAndImportBack(visit: List<Int> = emptyList(), beforeImport: () -> Unit = {}) {
        seedNote("Alpha note", "alpha body")
        setTasks(dailyId(), TaskSection.TODO, "Original todo")
        setTasks(weeklyId(), TaskSection.GOALS, "Original week goal")
        setTasks(monthlyId(), TaskSection.GOALS, "Original month goal")
        val file = tmp.newFile("backup.json")
        openSettings()
        exportTo(file)

        seedNote("Beta note", "beta body")
        setTasks(dailyId(), TaskSection.TODO, "Changed todo")
        setTasks(weeklyId(), TaskSection.GOALS, "Changed week goal")
        setTasks(monthlyId(), TaskSection.GOALS, "Changed month goal")

        for (tab in visit) {
            openTab(tab)
            when (tab) {
                R.string.tab_notes -> await { exists("Beta note") && exists("Alpha note") }
                R.string.tab_search -> {
                    await { exists(hasSetTextAction()) }
                    searchUntil("Beta") { exists("Beta note") }
                    assertFalse(exists(nothingFound))
                }
                R.string.tab_week -> await { exists("Changed week goal") }
                R.string.tab_month -> await { exists("Changed month goal") }
                R.string.tab_today -> await { exists("Changed todo") }
            }
        }
        beforeImport()

        openSettings()
        importFrom(file)
        assertEquals("Backup imported.", importOk)
    }

    private fun assertEventuallyShows(where: String, shown: String, gone: String) {
        runCatching { await { exists(shown) && !exists(gone) } }
        val state = "$where: '$shown' shown=${exists(shown)}, '$gone' shown=${exists(gone)}"
        assertTrue("after the import $state", exists(shown) && !exists(gone))
    }

    /** Checklist #252, the data itself: the new note is gone and every day, week and month holds what the backup had. */
    @Test
    fun item252_databaseHoldsTheBackupAndTheNewNoteIsGone() {
        exportChangeAndImportBack()
        assertEquals(setOf("Alpha note"), noteTitles())
        assertEquals(setOf("Original todo", "Original week goal", "Original month goal"), taskTexts())
    }

    /** Checklist #252: Notes and Search, both already opened with the new note in them, show the backup's notes after the import. */
    @Test
    fun item252_notesAndSearchShowTheRestoredNotes() {
        exportChangeAndImportBack(visit = listOf(R.string.tab_notes, R.string.tab_search))

        openTab(R.string.tab_notes)
        await { exists("Alpha note") }
        settleFor(300)
        assertFalse("the new note is gone from Notes", exists("Beta note"))

        openTab(R.string.tab_search)
        await { exists(hasSetTextAction()) }
        assertFalse("the card from before the import is gone without retyping", exists("Beta note"))
        searchUntil("Beta") { exists(nothingFound) }
        assertFalse("Beta is no longer found", exists("Beta note"))
        searchUntil("Alpha") { exists("Alpha note") }
        assertFalse(exists(nothingFound))
    }

    /** Checklist #252: the Week tab, opened before the import, shows the backup's week. */
    @Test
    fun item252_weekShowsTheRestoredWeekAfterBeingOpenedBeforeTheImport() {
        exportChangeAndImportBack(visit = listOf(R.string.tab_week))
        openTab(R.string.tab_week)
        assertEventuallyShows("Week", "Original week goal", "Changed week goal")
    }

    /** Checklist #252: the Month tab, opened before the import, shows the backup's month. */
    @Test
    fun item252_monthShowsTheRestoredMonthAfterBeingOpenedBeforeTheImport() {
        exportChangeAndImportBack(visit = listOf(R.string.tab_month))
        openTab(R.string.tab_month)
        assertEventuallyShows("Month", "Original month goal", "Changed month goal")
    }

    /**
     * Checklist #252: a page kept from before the import must not save its old rows over the restored data. Before the fix, typing on
     * this Week page replaced the restored goal with "EDITED Changed week goal".
     */
    @Test
    fun item252_anEditOnAWeekOpenedBeforeTheImportKeepsTheRestoredData() {
        exportChangeAndImportBack(visit = listOf(R.string.tab_week))
        openTab(R.string.tab_week)
        await { exists(hasText("Original week goal") and hasSetTextAction()) }

        rule.onNode(hasText("Original week goal") and hasSetTextAction()).performTextInput("EDITED ")
        rule.waitForIdle()

        await { "EDITED Original week goal" in taskTexts() }
        assertEquals(setOf("Original todo", "EDITED Original week goal", "Original month goal"), taskTexts())
    }

    /** Checklist #252: Back from Settings lands on the page under it, which was composed before the import and must show the backup's day. */
    @Test
    fun item252_backFromSettingsShowsTheRestoredDay() {
        exportChangeAndImportBack(visit = listOf(R.string.tab_today))
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        assertTrue("left Settings for Today", tabSelected(R.string.tab_today))
        assertEventuallyShows("Today after Back", "Original todo", "Changed todo")
    }

    /** Checklist #252: a note left open in the Notes tab comes back showing the backup's text, not what it held before the import. */
    @Test
    fun item252_aNoteLeftOpenInTheNotesTabShowsTheRestoredText() {
        val alpha = "alpha body"
        val changed = "changed body"
        exportChangeAndImportBack(
            beforeImport = {
                val id = runBlocking { container.db.pageDao().getAllPages() }.single { it.title == "Alpha note" }.id
                runBlocking { container.pages.saveBlockDocument(id, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(text = changed)))) }
                openTab(R.string.tab_notes)
                await { exists("Alpha note") }
                rule.onNode(hasText("Alpha note")).performClick()
                await { exists(hasText(changed) and hasSetTextAction()) }
            },
        )
        openTab(R.string.tab_notes)
        assertEventuallyShows("the open note", alpha, changed)
    }

    /** Checklist #252: Today, opened before the import, shows the backup's day. */
    @Test
    fun item252_todayShowsTheRestoredDayAfterBeingOpenedBeforeTheImport() {
        exportChangeAndImportBack(visit = listOf(R.string.tab_today))
        openTab(R.string.tab_today)
        assertEventuallyShows("Today", "Original todo", "Changed todo")
    }

    /** Control for the two tests above: Week and Month first opened after the import do show the backup's data. */
    @Test
    fun item252_weekAndMonthFirstOpenedAfterTheImportShowTheBackup() {
        exportChangeAndImportBack()
        openTab(R.string.tab_week)
        assertEventuallyShows("Week", "Original week goal", "Changed week goal")
        openTab(R.string.tab_month)
        assertEventuallyShows("Month", "Original month goal", "Changed month goal")
    }

    // ---- #258 ----

    /**
     * Checklist #258, a note-behaviour item: with the dialog open, rotating recreates the activity. What happens: the dialog is gone
     * (pendingImport is a plain remember), the Settings page itself is back, and nothing was imported. Control: before the rotation
     * the dialog was showing; after it, no import has run and no message is shown.
     */
    @Test
    fun item258_rotatingWithTheDialogOpenLosesTheDialogAndImportsNothing() {
        seedNote("Alpha note", "alpha body")
        val file = backupOfOtherData("From the file")
        val before = openSettingsAndSnapshot()
        answer(importPicker(), file)
        await { exists(dialogTitle) }
        assertTrue("dialog open before the rotation", exists(hasText(replaceAll) and inDialog))

        rotate()
        await { exists(onPage(str(R.string.settings_section_backup))) }
        settleFor(400)

        assertFalse("the dialog does not survive the rotation", exists(dialogTitle))
        assertFalse(exists(hasText(replaceAll)))
        assertNoSettingsMessage()
        assertEquals("nothing was imported", before, snapshot())
        assertTrue("Alpha note" in titles())
        assertFalse("From the file" in titles())
        assertNull(lastExportAt())
    }

    private fun rotate() {
        RuntimeEnvironment.setQualifiers("+land")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertEquals(android.content.res.Configuration.ORIENTATION_LANDSCAPE, rule.activity.resources.configuration.orientation)
    }

    // ---- #275 ----

    /**
     * Checklist #275, a note-behaviour item: scroll the page part way, set the reminder switch off and Dark on, rotate. What
     * happens, as observed: the switch and radio states come back from the store, the page is still Settings, and the scroll position
     * is NOT kept (back at the top, 0 of the new maximum), (the cause was not investigated).
     */
    @Test
    fun item275_rotatingKeepsTheSwitchesAndReportsTheScrollPosition() {
        openSettings()
        reminderSwitch().performClick()
        await { runWithLooper { container.settings.exportReminderEnabled.first() }.not() }
        tapOnPage(str(R.string.settings_theme_dark))
        await { runWithLooper { container.settings.darkMode.first() } == DarkMode.DARK }
        scrollToText(str(R.string.settings_debugging))
        val before = scrollValue()
        val beforeMax = scrollMax()
        assertTrue("halfway: scrolled but not to the end ($before of $beforeMax)", before > 0f && before < beforeMax)

        rotate()
        await { exists(onPage(str(R.string.settings_section_backup))) }
        rule.waitForIdle()

        val after = scrollValue()
        val afterMax = scrollMax()
        reminderSwitch().assertIsOff()
        assertTrue("the Dark radio is selected", rule.onNode(onPage(str(R.string.settings_theme_dark))).fetchSemanticsNode().config[SemanticsProperties.Selected])
        assertFalse("the System radio is not", rule.onNode(onPage(str(R.string.settings_theme_system))).fetchSemanticsNode().config[SemanticsProperties.Selected])
        println("item275: scroll before rotation $before of $beforeMax, after $after of $afterMax")
        assertTrue("the relayout is real: the page is taller in landscape ($beforeMax -> $afterMax)", afterMax != beforeMax)
        assertTrue("still on the Settings tab", tabSelected(R.string.tab_settings))
        assertEquals("note behaviour: the scroll position is not kept, the page is back at the top", 0f, after, 0.5f)
    }
}
