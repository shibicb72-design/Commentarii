package com.shibi.commentarii.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.Lifecycle
import com.shibi.commentarii.R
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.data.TextSpan
import com.shibi.commentarii.domain.weekStart
import com.shibi.commentarii.testutil.DeviceStateTestBase
import com.shibi.commentarii.testutil.MutableClock
import com.shibi.commentarii.testutil.TestEnv
import com.shibi.commentarii.testutil.runWithLooper
import com.shibi.commentarii.ui.notes.FORMAT_TOOLBAR_TAG
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Checklist #276, #277, #278, #282, #284 and the back stack after a rotation (#285), on the real MainActivity. A recreate keeps the
 * activity's view models, as a rotation does; losing them as well is #292's job.
 */
class DeviceRecreateFlowTest : DeviceStateTestBase() {
    @get:Rule val tmp = TemporaryFolder()

    private val envs = mutableListOf<TestEnv>()

    @After
    fun closeEnvs() {
        envs.forEach { it.close() }
    }

    private val todoHint get() = str(R.string.daily_todo_hint)
    private val pickDate get() = str(R.string.daily_pick_date)
    private val ok get() = str(R.string.action_ok)
    private val cancel get() = str(R.string.action_cancel)

    private val pageList = SemanticsMatcher("the page's vertical list") {
        SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.VerticalScrollAxisRange in it.config
    }

    private fun scrollTo(matcher: SemanticsMatcher) {
        rule.onNode(pageList).performScrollToNode(matcher)
        rule.waitForIdle()
    }

    /** Waits for the row to arrive in the list, which after a recreate means scrolling to it as soon as the page has it. */
    private fun awaitInList(matcher: SemanticsMatcher) {
        await { runCatching { scrollTo(matcher) }.isSuccess && exists(matcher) }
    }

    private fun tapInList(text: String) {
        scrollTo(hasText(text))
        rule.onNode(hasText(text)).performClick()
        rule.waitForIdle()
    }

    private fun tasksOf(date: LocalDate, section: String) =
        runBlocking { container.pages.getTasks(container.pages.getOrCreateDaily(date).id, section) }

    private fun textsOf(date: LocalDate, section: String) = tasksOf(date, section).map { it.text }.filter { it.isNotBlank() }

    /** Everything a confirmation could change; not the pages, which record when they were opened. */
    private fun data(): List<Any> = runBlocking {
        val d = container.db.pageDao()
        listOf(d.getAllTasks(), d.getAllSchedule(), d.getAllBlocks(), d.getAllMoods(), d.getAllFolders(), d.getAllTags(), d.getAllPageTags())
    }

    private fun pageCount() = runBlocking { container.db.pageDao().getAllPages().size }

    /** Waits past the moment a page reloaded by the recreate could have written something or brought a dialog back. */
    private fun settle() = settleFor(400)

    // ---- #276 ----

    /**
     * Checklist #276. What a device must still show: the keyboard's own behaviour. What happens here: the text stays in one row
     * and in the database once, the row keeps taking input, and the rotation back is the same.
     */
    @Test
    fun item276_textTypedJustBeforeARotationStaysInOneRowAndIsSavedOnce() {
        awaitToday()
        scrollTo(hasText(todoHint))
        rule.onAllNodes(field(todoHint))[0].performTextInput("Buy milk")
        rule.onNode(field("Buy milk")).assertIsFocused()
        val emptyRows = count(field(todoHint))

        rotate()

        awaitDay(today)
        awaitInList(field("Buy milk"))
        assertOnlyOne("one row holds the text", field("Buy milk"))
        await { textsOf(today, TaskSection.TODO) == listOf("Buy milk") }
        val rowsSaved = tasksOf(today, TaskSection.TODO).size
        // Observed: focus does not come back by itself and the field's cursor is reset, so there is no stray cursor; a tap sets it.
        rule.onNode(field("Buy milk")).assertIsNotFocused()
        assertEquals(TextRange(0), selectionOf(field("Buy milk")))

        rule.onNode(field("Buy milk")).performClick()
        rule.waitForIdle()
        rule.onNode(field("Buy milk")).assertIsFocused()
        rule.onNode(field("Buy milk")).performTextInputSelection(TextRange(8))
        rule.onNode(field("Buy milk")).performTextInput(" now")
        rule.waitForIdle()
        await { textsOf(today, TaskSection.TODO) == listOf("Buy milk now") }

        rotate()

        awaitDay(today)
        awaitInList(field("Buy milk now"))
        assertOnlyOne("still one row", field("Buy milk now"))
        assertEquals("no row was added", rowsSaved, tasksOf(today, TaskSection.TODO).size)
        scrollTo(hasText(todoHint))
        assertEquals("the same number of empty rows", emptyRows, count(field(todoHint)))
    }

    private fun selectionOf(matcher: SemanticsMatcher): TextRange =
        rule.onNode(matcher).fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    // ---- #277 ----

    @Test
    fun item277_datePickerOnTodayIsLostAndPicksNothing() {
        awaitToday()
        val before = data()
        tapDescription(pickDate)
        await { exists(hasText(ok) and inDialog) }

        rotate()
        awaitToday()
        settle()

        assertFalse("the picker does not survive the rotation", exists(hasText(ok) and inDialog))
        assertTrue("still on today", exists(hasText(dayTitle(today))))
        assertEquals(before, data())
        // The page is alive: the same button still opens the picker, and Cancel closes it.
        tapDescription(pickDate)
        await { exists(hasText(ok) and inDialog) }
        rule.onNode(hasText(cancel) and inDialog).performClick()
        rule.waitForIdle()
        assertFalse(exists(hasText(ok) and inDialog))
    }

    @Test
    fun item277_datePickerOnWeekIsLostAndTheWeekStays() {
        awaitToday()
        openTab(R.string.tab_week)
        val title = weekTitle()
        await { exists(hasText(title)) }
        tapDescription(pickDate)
        await { exists(hasText(ok) and inDialog) }

        rotate()
        await { tabSelected(R.string.tab_week) && exists(hasText(title)) }
        settle()

        assertFalse("the picker does not survive the rotation", exists(hasText(ok) and inDialog))
        assertTrue("the same week", exists(hasText(title)))
    }

    private fun weekTitle(): String {
        val monday = weekStart(today)
        val format = DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault())
        return str(R.string.week_title_fmt, monday.format(format), monday.plusDays(6).format(format))
    }

    @Test
    fun item277_customTimePickerIsLostAndAddsNoRow() {
        awaitToday()
        val before = data()
        tapInList(str(R.string.daily_custom_time))
        await { exists(hasText(ok) and inDialog) }

        rotate()
        // The page comes back scrolled to where it was, so the header may be out of the list's window.
        await { tabSelected(R.string.tab_today) }
        awaitInList(hasText(str(R.string.daily_custom_time)))
        settle()

        assertFalse("the picker does not survive the rotation", exists(hasText(ok) and inDialog))
        assertEquals("no custom time was added", before, data())
        awaitInList(hasText(dayTitle(today)))
    }

    @Test
    fun item277_newFolderDialogIsLostWithItsTextAndCreatesNothing() {
        awaitToday()
        val before = data()
        tapInList(str(R.string.daily_no_folder))
        rule.onNode(hasText(str(R.string.daily_new_folder))).performClick()
        rule.waitForIdle()
        await { exists(hasText(ok) and inDialog) }
        rule.onNode(hasSetTextAction() and inDialog).performTextInput("Half typed")
        rule.waitForIdle()

        rotate()
        awaitToday()
        settle()

        assertFalse("the dialog does not survive the rotation", exists(hasText(ok) and inDialog))
        assertFalse("nor does its text", exists(hasText("Half typed")))
        assertEquals("no folder was created", before, data())
    }

    @Test
    fun item277_newTagDialogIsLostWithItsTextAndCreatesNothing() {
        awaitToday()
        val before = data()
        tapInList(str(R.string.daily_add_tag))
        await { exists(hasText(ok) and inDialog) }
        rule.onNode(hasSetTextAction() and inDialog).performTextInput("Half typed")
        rule.waitForIdle()

        rotate()
        awaitToday()
        settle()

        assertFalse("the dialog does not survive the rotation", exists(hasText(ok) and inDialog))
        assertFalse("nor does its text", exists(hasText("Half typed")))
        assertEquals("no tag was created", before, data())
    }

    private val yesterday get() = today.minusDays(1)
    private val moveAll get() = str(R.string.rollover_move_all)
    private val pick get() = str(R.string.rollover_pick)
    private val skip get() = str(R.string.rollover_skip)
    private val pickTitle get() = str(R.string.rollover_pick_title)

    /** The activity opened Today before anything was seeded, so leaving and coming back is what makes a new page ask. */
    private fun offerRollover() {
        awaitToday()
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
        await { exists(hasText(moveAll)) }
    }

    /** Observed: the offer lives in the view model, which a rotation keeps, so it is still there, and nothing has moved. */
    @Test
    fun item277_rolloverDialogSurvivesAndMovesNothingUntilConfirmed() {
        offerRollover()

        rotate()
        awaitDay(today)
        await { exists(hasText(moveAll)) }
        settle()

        assertOnlyOne("one dialog", hasText(moveAll))
        assertEquals("nothing moved", emptyList<String>(), textsOf(today, TaskSection.TODO))
        assertEquals("still on yesterday, not carried", listOf<Long?>(null), tasksOf(yesterday, TaskSection.TODO).map { it.carriedTo })

        rule.onNode(hasText(moveAll)).performClick()
        rule.waitForIdle()
        await { textsOf(today, TaskSection.TODO) == listOf("Old todo") }
        assertFalse("the dialog closed", exists(hasText(moveAll)))
    }

    /** Observed: the choice of mode is saveable, so the pick list comes back, and Skip still moves nothing. */
    @Test
    fun item277_rolloverPickListSurvivesAndSkipMovesNothing() {
        offerRollover()
        rule.onNode(hasText(pick)).performClick()
        rule.waitForIdle()
        await { exists(hasText(pickTitle)) }

        rotate()
        awaitDay(today)
        await { exists(hasText(pickTitle)) }
        assertEquals("nothing moved", emptyList<String>(), textsOf(today, TaskSection.TODO))

        rule.onNode(hasText(skip)).performClick()
        rule.waitForIdle()
        await { !exists(hasText(pickTitle)) }
        settle()
        assertEquals("nothing moved", emptyList<String>(), textsOf(today, TaskSection.TODO))
    }

    private fun backupOfOtherData(noteTitle: String): File {
        val env = TestEnv(tmp, MutableClock(today.atTime(9, 0))).also { envs += it }
        runBlocking { env.pages.createNote(noteTitle) }
        return tmp.newFile().also { it.writeText(runBlocking { env.io.export() }) }
    }

    /** Observed: the file waiting for confirmation is in plain remember, so a rotation drops it, and nothing is imported. */
    @Test
    fun item277_importConfirmIsLostAndImportsNothing() {
        runBlocking { container.pages.createNote("Mine") }
        val file = backupOfOtherData("From the file")
        awaitToday()
        openTab(R.string.tab_settings)
        val importButton = hasText(str(R.string.settings_import))
        await { exists(importButton) }
        rule.onNode(importButton).performScrollTo().performClick()
        rule.waitForIdle()
        val request = checkNotNull(shadowOf(rule.activity).nextStartedActivityForResult) { "tapping Import started no picker" }
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, request.intent.action)
        shadowOf(rule.activity).receiveResult(request.intent, Activity.RESULT_OK, Intent().setData(Uri.fromFile(file)))
        rule.waitForIdle()
        val confirm = hasText(str(R.string.settings_import_confirm)) and inDialog
        await { exists(confirm) }
        val before = data()

        rotate()
        await { tabSelected(R.string.tab_settings) && exists(importButton) }
        settle()

        assertFalse("the confirmation does not survive the rotation", exists(confirm))
        assertEquals("nothing imported", before, data())
        val titles = runBlocking { container.db.pageDao().getAllPages() }.map { it.title }
        assertTrue("my note is there", "Mine" in titles)
        assertFalse("the file's is not", "From the file" in titles)
    }

    // ---- #278 ----

    private fun boldRanges(node: SemanticsNode): List<Pair<Int, Int>> {
        val results = mutableListOf<TextLayoutResult>()
        checkNotNull(node.config[SemanticsActions.GetTextLayoutResult].action).invoke(results)
        return results.single().layoutInput.text.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }.map { it.start to it.end }
    }

    private fun savedBlocks(id: Long) = runBlocking { container.pages.getBlockDocument(id, SectionKind.RICH_TEXT) }.blocks

    private val blocksField get() = hasSetTextAction() and !inDialog

    /** The editor's fields top to bottom, without the title. */
    private fun shownBlocks() = rule.onAllNodes(blocksField).fetchSemanticsNodes().drop(1).map { it.config[SemanticsProperties.EditableText].text }

    private fun checks() = rule.onAllNodes(isToggleable()).fetchSemanticsNodes().sortedBy { it.positionInRoot.y }
        .map { it.config[SemanticsProperties.ToggleableState] == ToggleableState.On }

    private fun openSeededNote(title: String, vararg blocks: DocBlock): Long {
        val id = runBlocking {
            val page = container.pages.createNote(title)
            container.pages.saveBlockDocument(page.id, SectionKind.RICH_TEXT, BlockDocument(blocks.toList()))
            page.id
        }
        awaitToday()
        openTab(R.string.tab_notes)
        await { exists(hasText(title)) }
        rule.onNode(card(title)).performClick()
        rule.waitForIdle()
        await { count(blocksField) == blocks.size + 1 }
        return id
    }

    /**
     * Checklist #278, with an injected keyboard inset and the cursor in a checklist row. What a device must still show: the real
     * keyboard coming back. All blocks, checks and bold are asserted on screen and in the database after each of the two rotations.
     */
    @Test
    fun item278_twoRotationsKeepEveryBlockCheckAndBoldWithNoDuplicates() {
        val blocks = listOf(
            DocBlock(type = BlockType.CHECKBOX, text = "Milk", checked = true),
            DocBlock(type = BlockType.CHECKBOX, text = "Bread"),
            DocBlock(text = "Plain bold end", spans = listOf(TextSpan(6, 10))),
        )
        val id = openSeededNote("Shopping", *blocks.toTypedArray())
        rule.onNode(field("Bread")).performClick()
        rule.waitForIdle()
        rule.onNode(field("Bread")).performTextInputSelection(TextRange(5))
        rule.onNode(field("Bread")).performTextInput(" rolls")
        rule.waitForIdle()
        injectInsets(navBarDp = 24, imeDp = 280)
        val expected = listOf("Milk", "Bread rolls", "Plain bold end")

        fun assertAllThere(after: String) {
            await { shownBlocks() == expected }
            assertEquals("$after: the checks", listOf(true, false), checks())
            assertEquals("$after: the bold", listOf(6 to 10), boldRanges(rule.onNode(field("Plain bold end")).fetchSemanticsNode()))
            await { savedBlocks(id).map { it.text } == expected }
            assertEquals("$after: saved checks", listOf(true, false, false), savedBlocks(id).map { it.checked })
            assertEquals("$after: saved bold", listOf(TextSpan(6, 10)), savedBlocks(id)[2].spans)
            assertTrue("$after: the title", exists(field("Shopping")))
        }

        rotate()
        assertAllThere("first rotation")
        injectInsets(navBarDp = 24, imeDp = 280)

        rotate()
        assertAllThere("second rotation")
        assertTrue("still the note, in the Notes tab", tabSelected(R.string.tab_notes))

        // The rows still work: Back goes to the list, not to a copy of the note under this one.
        pressBack()
        await { exists(hasContentDescription(str(R.string.nt_new))) }
        pressBack()
        awaitToday()
    }

    // ---- #282 ----

    private fun assertBottomBarAlongTheBottom(what: String) {
        assertTrue("$what: a bottom bar", isBottomBar())
        val barBottom = tabNodes().maxOf { it.positionInRoot.y + it.size.height }
        assertEquals("$what: along the bottom of the window", rule.activity.window.decorView.height.toFloat(), barBottom, 2f)
    }

    private fun assertRailOnTheLeft(what: String) {
        assertTrue("$what: a rail", isRail())
        assertTrue("$what: on the left", tabNodes().maxOf { it.positionInRoot.x + it.size.width } < rule.activity.window.decorView.width / 2f)
    }

    /** Checklist #282, on the real calculateWindowSizeClass: the sizes on each side of the 600dp line, both ways, typed text kept. */
    @Test
    fun item282_narrowWindowHasTheBottomBarAndResizingSwitchesLayoutsKeepingTheScreen() {
        resizeTo("w599dp-h640dp")
        val id = openSeededNote("Split", DocBlock(text = "Body"))
        rule.onNode(field("Body")).performTextInputSelection(TextRange(0))
        rule.onNode(field("Body")).performTextInput("Typed ")
        rule.waitForIdle()
        assertBottomBarAlongTheBottom("599dp wide")

        resizeTo("w600dp-h640dp")
        await { exists(field("Typed Body")) }
        assertRailOnTheLeft("600dp wide")
        assertTrue("the same tab", tabSelected(R.string.tab_notes))

        resizeTo("w320dp-h480dp")
        await { exists(field("Typed Body")) }
        assertBottomBarAlongTheBottom("a small pop-up window")
        assertTrue("the same tab", tabSelected(R.string.tab_notes))

        resizeTo("w673dp-h747dp")
        await { exists(field("Typed Body")) }
        assertRailOnTheLeft("the Fold's inner screen")
        await { savedBlocks(id).single().text == "Typed Body" }
    }

    /** Checklist #282: the daily page follows the same class from MainActivity: one column narrow, two wide. */
    @Test
    fun item282_dailyPageHasOneColumnInANarrowWindowAndTwoInAWideOne() {
        resizeTo("w412dp-h915dp")
        awaitToday()
        assertBottomBarAlongTheBottom("narrow")
        val todo = hasText(str(R.string.section_todo))
        val priorities = hasText(str(R.string.section_priorities))
        val schedule = hasText(str(R.string.section_schedule))
        scrollTo(priorities)
        val narrowLeft = rule.onNode(priorities).fetchSemanticsNode().positionInRoot.x
        scrollTo(schedule)
        assertEquals("one column", narrowLeft, rule.onNode(schedule).fetchSemanticsNode().positionInRoot.x, 1f)

        resizeTo("w840dp-h915dp")
        awaitToday()
        assertRailOnTheLeft("wide")
        assertTrue(exists(todo))
        val left = rule.onNode(priorities).fetchSemanticsNode().positionInRoot.x
        val right = rule.onNode(schedule).fetchSemanticsNode().positionInRoot.x
        assertTrue("two columns: $left and $right", right > left + 100f)
    }

    // ---- #284 ----

    private fun backgroundLuminance(): Double {
        val toolbarTop = rule.onNodeWithTag(FORMAT_TOOLBAR_TAG).fetchSemanticsNode().boundsInRoot.top
        val bitmap = windowBitmap()
        val x = bitmap.width - (4 * rule.activity.resources.displayMetrics.density).toInt()
        return ColorUtils.calculateLuminance(bitmap.getPixel(x, toolbarTop.toInt() - 8))
    }

    private fun textLuminance(text: String): Float {
        val results = mutableListOf<TextLayoutResult>()
        checkNotNull(rule.onNode(field(text)).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action).invoke(results)
        return results.single().layoutInput.style.color.luminance()
    }

    private fun isNight() = rule.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    /**
     * Checklist #284 with Follow system (the setting's default after the reset), an injected keyboard inset, and a real recreate:
     * uiMode is not in configChanges. What a device must still show: the real keyboard during the change.
     */
    @Test
    fun item284_followSystemRecoloursTheEditorBothWaysAndKeepsTheText() {
        assertFalse("starts light", isNight())
        val id = openSeededNote("Theme note", DocBlock(type = BlockType.CHECKBOX, text = "Ink"))
        rule.onNode(field("Ink")).performClick()
        rule.waitForIdle()
        rule.onNode(field("Ink")).performTextInputSelection(TextRange(0))
        rule.onNode(field("Ink")).performTextInput("Dark ")
        rule.waitForIdle()
        rule.onNode(field("Dark Ink")).assertIsFocused()
        injectInsets(navBarDp = 24, imeDp = 280)
        val lightBackground = backgroundLuminance()
        val lightText = textLuminance("Dark Ink")
        assertTrue("a light page: $lightBackground", lightBackground > 0.5)

        RuntimeEnvironment.setQualifiers("+night")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertTrue("the system is dark now", isNight())

        await { exists(field("Dark Ink")) }
        assertTrue("the title is kept", exists(field("Theme note")))
        val darkBackground = backgroundLuminance()
        assertTrue("a dark page: $darkBackground", darkBackground < 0.2)
        assertTrue("lighter text than in light mode: $lightText then ${textLuminance("Dark Ink")}", textLuminance("Dark Ink") > lightText)
        await { savedBlocks(id).single().text == "Dark Ink" }
        // Observed: focus does not come back by itself, and the field takes it again with a tap.
        rule.onNode(field("Dark Ink")).assertIsNotFocused()
        rule.onNode(field("Dark Ink")).performClick()
        rule.waitForIdle()
        rule.onNode(field("Dark Ink")).assertIsFocused()

        RuntimeEnvironment.setQualifiers("+notnight")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertFalse("the system is light again", isNight())

        await { exists(field("Dark Ink")) }
        assertTrue("light again", backgroundLuminance() > 0.5)
        assertEquals("one block, the same", listOf("Dark Ink"), savedBlocks(id).map { it.text })
    }

    // ---- #285, after a rotation ----

    /** A rotation brings the back stack back as it was: a day opened from the week still goes back to the week, then Today, then out. */
    @Test
    fun item285_backAfterARotationGoesTheSameWayAsBefore() {
        awaitToday()
        openTab(R.string.tab_week)
        await { exists(hasText(weekTitle())) && exists(card("Fri")) }
        val friday = weekStart(today).plusDays(4)
        rule.onNode(card("Fri")).performClick()
        rule.waitForIdle()
        awaitDay(friday)

        rotate()
        awaitDay(friday)
        assertTrue("a day still belongs to Today", tabSelected(R.string.tab_today))

        pressBack()
        await { tabSelected(R.string.tab_week) && exists(hasText(weekTitle())) }

        pressBack()
        awaitToday()

        val activity = rule.activity
        pressBack()
        assertTrue("and Today leaves the app, with nothing stacked under it", activity.isFinishing)
    }

    /** Two rotations must not add to the stack either, which would be worse each time. */
    @Test
    fun item285_twoRotationsOnADayOpenedFromTheWeekStackNothing() {
        awaitToday()
        openTab(R.string.tab_week)
        await { exists(hasText(weekTitle())) && exists(card("Fri")) }
        val friday = weekStart(today).plusDays(4)
        rule.onNode(card("Fri")).performClick()
        rule.waitForIdle()
        awaitDay(friday)

        rotate()
        awaitDay(friday)
        rotate()
        awaitDay(friday)

        pressBack()
        await { tabSelected(R.string.tab_week) && exists(hasText(weekTitle())) }
        pressBack()
        awaitToday()
        val activity = rule.activity
        pressBack()
        assertTrue("nothing stacked under Today", activity.isFinishing)
    }

    // ---- The replay after an unlock, which the rotation fix must leave working ----

    private val unlockText get() = str(R.string.lock_unlock)

    private fun enableLockAndUnlock() {
        runWithLooper { container.settings.setLockEnabled(true) }
        await { exists(unlockText) }
        container.lock.unlock()
        await { !exists(unlockText) && tabSelected(R.string.tab_today) }
    }

    /** Home for over a minute: the app comes back locked, and AppNav is out of composition. */
    private fun comeBackLocked() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        container.lock.onBackground()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(61))
        container.onAppStart()
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        await { exists(unlockText) }
    }

    private fun openFridayFromTheWeek(): LocalDate {
        openTab(R.string.tab_week)
        await { exists(hasText(weekTitle())) && exists(card("Fri")) }
        val friday = weekStart(today).plusDays(4)
        rule.onNode(card("Fri")).performClick()
        rule.waitForIdle()
        awaitDay(friday)
        return friday
    }

    private fun assertBackGoesWeekTodayOut() {
        pressBack()
        await { tabSelected(R.string.tab_week) && exists(hasText(weekTitle())) }
        pressBack()
        awaitToday()
        val activity = rule.activity
        pressBack()
        assertTrue("nothing stacked under Today", activity.isFinishing)
    }

    @Test
    fun item285_rotationThenLockAndUnlockComesBackToTheSameStack() {
        enableLockAndUnlock()
        val friday = openFridayFromTheWeek()
        rotate()
        awaitDay(friday)

        comeBackLocked()
        assertFalse("the app is hidden", exists(tab(R.string.tab_today)))
        container.lock.unlock()
        awaitDay(friday)

        assertBackGoesWeekTodayOut()
    }

    @Test
    fun item285_rotationWhileLockedThenUnlockComesBackToTheSameStack() {
        enableLockAndUnlock()
        val friday = openFridayFromTheWeek()

        comeBackLocked()
        rotate()
        await { exists(unlockText) }
        assertFalse("the app is still hidden", exists(tab(R.string.tab_today)))
        container.lock.unlock()
        awaitDay(friday)

        assertBackGoesWeekTodayOut()
    }
}
