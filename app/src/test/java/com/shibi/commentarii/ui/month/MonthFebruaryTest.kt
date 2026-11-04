package com.shibi.commentarii.ui.month

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.waitUntilWithLooper
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Checklist #160: February on the real MonthPageScreen, from the view model's grid to the rows drawn on screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class MonthFebruaryTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val container get() = (rule.activity.application as CommentariiApp).container
    private val cellFormat = DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())

    private fun show(month: YearMonth): MonthViewModel {
        lateinit var vm: MonthViewModel
        rule.setContent {
            CommentariiTheme(dark = false) {
                vm = viewModel(key = "month-$month", factory = MonthViewModel.factory(container, month))
                MonthPageScreen(container, month, {}, {}, WindowWidthSizeClass.Compact)
            }
        }
        rule.waitUntilWithLooper { vm.state.value.loaded }
        val calendar = rule.activity.getString(R.string.section_calendar)
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(calendar))
        rule.waitForIdle()
        return vm
    }

    private fun cellBounds(date: LocalDate) =
        rule.onNode(hasContentDescription(date.format(cellFormat))).fetchSemanticsNode().boundsInRoot

    /** Cells of a row can sit a pixel apart from rounding seven equal widths, so a new row starts half a cell further down. */
    private fun rowsOf(dates: List<LocalDate>): Int {
        val halfCell = cellBounds(dates.first()).height / 2
        return 1 + dates.map { cellBounds(it).top }.sorted().zipWithNext().count { (a, b) -> b - a > halfCell }
    }

    private fun assertFebruary(
        month: YearMonth,
        vm: MonthViewModel,
        days: Int,
        rows: Int,
        firstCell: LocalDate,
        lastCell: LocalDate,
    ) {
        val grid = vm.state.value.grid
        assertEquals(rows * 7, grid.size)
        assertEquals(days, grid.count { it.inMonth })
        assertEquals((1..days).map { month.atDay(it) }, grid.filter { it.inMonth }.map { it.date })
        assertEquals(firstCell, grid.first().date)
        assertEquals(DayOfWeek.MONDAY, grid.first().date.dayOfWeek)
        assertEquals(lastCell, grid.last().date)
        assertEquals(DayOfWeek.SUNDAY, grid.last().date.dayOfWeek)

        val inMonth = (1..days).map { month.atDay(it) }
        assertEquals("rows drawn", rows, rowsOf(inMonth))
        val weeks = inMonth.groupBy { it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) }.values.toList()
        weeks.forEach { assertEquals("one row per week: $it", 1, rowsOf(it)) }
        assertEquals("weeks run down the page", weeks.indices.toList(), weeks.indices.sortedBy { cellBounds(weeks[it].first()).top })
        weeks.forEach { week ->
            val lefts = week.map { cellBounds(it).left }
            assertEquals("days run left to right: $week", lefts.sorted(), lefts)
        }
    }

    @Test
    fun item160_february2028HasTwentyNineDaysInFiveRows() {
        val month = YearMonth.of(2028, 2)
        val vm = show(month)
        assertFebruary(month, vm, 29, 5, LocalDate.of(2028, 1, 31), LocalDate.of(2028, 3, 5))
        rule.onNode(hasContentDescription(LocalDate.of(2028, 2, 29).format(cellFormat))).assertExists()
    }

    /** Feb 1 2027 is a Monday and 28 days fill exactly four rows, so no neighbouring day is padded in. */
    @Test
    fun item160_february2027HasTwentyEightDaysInFourRows() {
        val month = YearMonth.of(2027, 2)
        val vm = show(month)
        assertFebruary(month, vm, 28, 4, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28))
        rule.onNode(hasContentDescription(LocalDate.of(2027, 2, 28).format(cellFormat))).assertExists()
        assertEquals("no 29th on screen", 0, rule.onAllNodes(hasText("29")).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodes(hasText("31")).fetchSemanticsNodes().size)
    }
}
