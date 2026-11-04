package com.shibi.commentarii.testutil

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.R
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.ui.AppNav
import com.shibi.commentarii.ui.month.MonthPageScreen
import com.shibi.commentarii.ui.month.MonthViewModel
import com.shibi.commentarii.ui.theme.AppColors
import com.shibi.commentarii.ui.theme.CommentariiTheme
import com.shibi.commentarii.ui.week.WeekPageScreen
import com.shibi.commentarii.ui.week.WeekViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.runBlocking

/**
 * The real week and month pages, either inside AppNav on a fixed date or alone with a view model holding a test clock. AppNav's
 * clock decides where the tabs go but does not reach the pages, so a date far from the real one is never "today" there; the
 * highlight tests use [showWeek] and [showMonth], which create the view model first with the same key the screen uses.
 */
abstract class PeriodPageTestBase : DailyPageTestBase() {
    protected val day: LocalDate = LocalDate.of(2031, 3, 12)
    protected val monday: LocalDate = LocalDate.of(2031, 3, 10)
    protected val month: YearMonth = YearMonth.of(2031, 3)

    protected val goalsTitle get() = str(R.string.section_goals)
    protected val todoTitle get() = str(R.string.section_todo)
    protected val dontForgetTitle get() = str(R.string.section_dont_forget)
    protected val notesTitle get() = str(R.string.section_notes)
    protected val todoHint get() = str(R.string.daily_todo_hint)
    protected val notesHint get() = str(R.string.daily_notes_hint)
    protected val reviewHint get() = str(R.string.review_hint)
    protected val addItem get() = str(R.string.daily_add_item)
    protected val remove get() = str(R.string.daily_remove)
    protected val moveUp get() = str(R.string.daily_move_up)
    protected val moveDown get() = str(R.string.daily_move_down)
    protected val pickDate get() = str(R.string.daily_pick_date)

    protected fun fixedClock(date: LocalDate): Clock = Clock.fixed(date.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    protected fun openAppAt(date: LocalDate, width: WindowWidthSizeClass = WindowWidthSizeClass.Compact, dark: Boolean = false) {
        rule.setContent {
            CommentariiTheme(dark = dark) {
                colors = MaterialTheme.colorScheme
                appColors = AppColors.current
                AppNav(container, width, onRoutesChanged = { routes = it }, clock = fixedClock(date))
            }
        }
        awaitLoaded()
    }

    /** Days the page asked to open, for pages hosted without AppNav. */
    protected val openedDays = mutableListOf<LocalDate>()

    private var shownMonth by mutableStateOf(YearMonth.of(2000, 1))

    protected fun showWeek(mondayDate: LocalDate, clock: Clock) {
        rule.setContent {
            CommentariiTheme(dark = false) {
                colors = MaterialTheme.colorScheme
                appColors = AppColors.current
                viewModel<WeekViewModel>(key = "week-$mondayDate", factory = WeekViewModel.factory(container, mondayDate, clock))
                WeekPageScreen(container, mondayDate, {}, { openedDays += it }, WindowWidthSizeClass.Compact)
            }
        }
        awaitWeekLoaded()
    }

    /** The month arrows work here: [start] is only the first month shown. */
    protected fun showMonth(start: YearMonth, clock: Clock) {
        shownMonth = start
        rule.setContent {
            CommentariiTheme(dark = false) {
                colors = MaterialTheme.colorScheme
                appColors = AppColors.current
                val shown = shownMonth
                viewModel<MonthViewModel>(key = "month-$shown", factory = MonthViewModel.factory(container, shown, clock))
                MonthPageScreen(container, shown, { shownMonth = it }, { openedDays += it }, WindowWidthSizeClass.Compact)
            }
        }
        awaitMonthLoaded()
    }

    /** A week's goal rows only exist once it has loaded, and only then is a field enabled. */
    protected fun awaitWeekLoaded() = await { exists(hasSetTextAction() and isEnabled()) }

    /** A month has no goal rows to wait for, but its Add item button is disabled until it has loaded. */
    protected fun awaitMonthLoaded() {
        await { runCatching { scrollTo(hasText(goalsTitle)) }.isSuccess && exists(hasText(addItem) and isEnabled()) }
    }

    protected fun openWeekTab() {
        tapTab(R.string.tab_week)
        awaitWeekLoaded()
    }

    protected fun openMonthTab() {
        tapTab(R.string.tab_month)
        awaitMonthLoaded()
    }

    protected fun awaitTitle(title: String) = await { exists(hasText(title)) }

    protected fun titleText(title: String) = rule.onNode(hasText(title))

    /** Section titles sit in the page's list; the rail and the bottom bar can carry the same words. */
    protected fun sectionTitle(title: String) = hasText(title) and hasAnyAncestor(pageList)

    /** The [index]th node matching [matcher] below the section title, counted down the screen. */
    protected fun inSection(title: String, matcher: SemanticsMatcher, index: Int = 0): SemanticsNodeInteraction {
        scrollTo(hasText(title))
        val top = rule.onNode(sectionTitle(title)).fetchSemanticsNode().positionInRoot.y
        val nodes = rule.onAllNodes(matcher).fetchSemanticsNodes()
        val below = nodes.indices.filter { nodes[it].positionInRoot.y > top }.sortedBy { nodes[it].positionInRoot.y }
        return rule.onAllNodes(matcher)[below[index]]
    }

    /** The first node matching [matcher] on the same row as [fieldNode]: rows hold no semantic link, only a position. */
    protected fun onRowOf(fieldNode: SemanticsNodeInteraction, matcher: SemanticsMatcher): SemanticsNodeInteraction {
        val row = centerY(fieldNode.fetchSemanticsNode())
        val nodes = rule.onAllNodes(matcher).fetchSemanticsNodes()
        return rule.onAllNodes(matcher)[nodes.indices.minBy { abs(centerY(nodes[it]) - row) }]
    }

    protected fun tapControl(description: String, rowText: String) {
        onRowOf(field(rowText), hasContentDescription(description)).performClick()
        rule.waitForIdle()
    }

    /** Types into the first empty row showing [hint], which moves on to the next such row each time. */
    protected fun typeInFirst(hint: String, text: String) {
        scrollTo(hasText(hint))
        rule.onAllNodes(hasText(hint) and hasSetTextAction())[0].performTextInput(text)
        rule.waitForIdle()
    }

    /** Fills in the date picker that is already open, through its text mode (en-US format), and confirms. */
    protected fun pickInOpenDialog(date: LocalDate) {
        typeInOpenDialog(date)
        tapInDialog(str(R.string.action_ok))
    }

    protected fun typeInOpenDialog(date: LocalDate) {
        tapDescription("Switch to text input mode")
        val input = rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))
        input.performTextClearance()
        input.performTextInput(date.format(DateTimeFormatter.ofPattern("MMddyyyy", Locale.US)))
        rule.waitForIdle()
    }

    protected fun tasksOf(pageId: Long, section: String): List<TaskItemEntity> = runBlocking { container.pages.getTasks(pageId, section) }

    protected fun textsOf(pageId: Long, section: String) = tasksOf(pageId, section).map { it.text }.filter { it.isNotBlank() }

    protected fun documentOf(pageId: Long, kind: String) =
        runBlocking { container.pages.getBlockDocument(pageId, kind) }.blocks.joinToString("\n") { it.text }

    protected fun weekPageId(mondayDate: LocalDate = monday) = runBlocking { container.pages.getOrCreateWeekly(mondayDate).id }

    protected fun monthPageId(of: YearMonth = month) = runBlocking { container.pages.getOrCreateMonthly(of.atDay(1)).id }

    protected fun seedTasksOn(pageId: Long, section: String, vararg texts: String) {
        runBlocking {
            container.pages.saveTasks(
                pageId,
                section,
                texts.mapIndexed { i, text -> TaskItemEntity(pageId = pageId, section = section, position = i, text = text) },
            )
        }
    }

    protected fun seedMood(date: LocalDate, mood: Int) = runBlocking { container.pages.saveMood(date, mood) }

    protected fun windowBitmap(): Bitmap {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        return bitmap
    }

    /** How many pixels inside [area] differ from its top-left pixel. */
    protected fun inkIn(bitmap: Bitmap, area: Rect): Int {
        val background = bitmap.getPixel(area.left.toInt(), area.top.toInt())
        var ink = 0
        for (y in area.top.toInt() until area.bottom.toInt()) {
            for (x in area.left.toInt() until area.right.toInt()) if (bitmap.getPixel(x, y) != background) ink++
        }
        return ink
    }

    /** Checks each title in [titles] sits in one column, one below the other, and returns that column's left edge. */
    protected fun assertColumn(titles: List<String>): Float {
        val nodes = titles.map { rule.onNode(sectionTitle(it)).fetchSemanticsNode().positionInRoot }
        assertEqualsIn(titles.toString(), 1, nodes.map { it.x }.distinct().size)
        assertEqualsIn("$titles run top to bottom", nodes.map { it.y }.sorted(), nodes.map { it.y })
        return nodes.first().x
    }

    private fun assertEqualsIn(what: String, expected: Any, actual: Any) = org.junit.Assert.assertEquals(what, expected, actual)
}
