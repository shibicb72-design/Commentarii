package com.shibi.commentarii.ui.daily

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.text.font.FontWeight
import com.shibi.commentarii.R
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.testutil.DailyPageTestBase
import com.shibi.commentarii.ui.theme.ScriptFont
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Checklist #39-#45: the daily title, date strip, date picker and pin on the real screen. */
class DailyHeaderFlowTest : DailyPageTestBase() {
    /** A Wednesday well away from any real today, so the picker's day cells carry no "Today" prefix. */
    private val shown = LocalDate.of(2031, 3, 12)

    private val title get() = "Wednesday, Mar 12"

    private val strip = SemanticsMatcher("the date strip") {
        SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.HorizontalScrollAxisRange in it.config
    }

    private fun pill(day: LocalDate) = hasText(day.format(DateTimeFormatter.ofPattern("EEE", Locale.US))) and
        hasText(day.dayOfMonth.toString()) and hasAnyAncestor(strip)

    private fun scrollToPill(day: LocalDate) {
        rule.onNode(strip).performScrollToNode(pill(day))
        rule.waitForIdle()
    }

    private fun titleOf(day: LocalDate) = day.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US))

    private fun pickerCell(day: LocalDate) = hasText(day.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)))

    private val cancel get() = str(R.string.action_cancel)
    private val ok get() = str(R.string.action_ok)

    private fun openPicker() = tapDescription(str(R.string.daily_pick_date))

    @Test
    fun item039_titleIsTheDateInTheScriptFont() {
        open(LocalDate.of(2026, 11, 3))
        val node = rule.onNode(hasText("Tuesday, Nov 3")).fetchSemanticsNode()
        assertEquals(ScriptFont, layoutOf(node).layoutInput.style.fontFamily)
    }

    @Test
    @Config(qualifiers = "de-rDE-w412dp-h915dp-xxhdpi")
    fun item039_titleFollowsTheDeviceLocale() {
        val day = LocalDate.of(2026, 11, 3)
        open(day)
        val german = day.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.GERMANY))
        assertTrue("expected a German weekday in $german", german.startsWith("Dienstag"))
        rule.onNode(hasText(german)).assertExists()
    }

    @Test
    fun item040_stripHasFifteenPillsAroundTheShownDateAndOnlyItIsSelected() {
        open(shown)
        val info = rule.onNode(strip).fetchSemanticsNode().config[SemanticsProperties.CollectionInfo]
        assertEquals("15 pills", 15, info.columnCount)
        for (offset in -7L..7L) {
            val day = shown.plusDays(offset)
            scrollToPill(day)
            rule.onNode(pill(day)).assertExists()
            assertEquals("only the shown date is selected", offset == 0L, exists(pill(day) and isSelected()))
        }
    }

    @Test
    fun item040_shownDateIsBoldAndFilledTodayHasAnOutline() {
        val shownDay = today.minusDays(2)
        open(shownDay)

        fun numberWeight(day: LocalDate): FontWeight? = layoutOf(
            rule.onNode(hasText(day.dayOfMonth.toString()) and hasAnyAncestor(strip), useUnmergedTree = true)
                .fetchSemanticsNode(),
        ).layoutInput.style.fontWeight
        assertEquals(FontWeight.Bold, numberWeight(shownDay))
        assertEquals(FontWeight.Normal, numberWeight(today))

        assertSameColor("shown pill's fill", colors.primaryContainer, pixel(rule.onNode(pill(shownDay)), 26f, 3f))
        assertSameColor("other pill's fill", colors.surfaceContainer, pixel(rule.onNode(pill(today.minusDays(1))), 26f, 3f))
        assertSameColor("today's outline", colors.primary, pixel(rule.onNode(pill(today)), 0.7f, 30f))
        assertNotEquals("no outline on another day", colors.primary, pixel(rule.onNode(pill(today.minusDays(1))), 0.7f, 30f))
    }

    @Test
    fun item041_swipeThenTapOpensThatDayRecentredWithTodayTabStillSelected() {
        openApp()
        val start = today
        val selectedLeft = bounds(rule.onNode(pill(start))).left
        rule.onNode(tab(R.string.tab_today)).assertIsSelected()

        rule.onNode(strip).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        val day = start.plusDays(3)
        scrollToPill(day)
        assertTrue("the strip moved", !exists(pill(start)) || bounds(rule.onNode(pill(start))).left < selectedLeft)

        rule.onNode(pill(day)).performClick()
        await { exists(hasText(titleOf(day))) }
        awaitLoaded()

        rule.onNode(pill(day)).assertIsSelected()
        assertEquals("re-centred where the shown day always sits", selectedLeft, bounds(rule.onNode(pill(day))).left, 1f)
        rule.onNode(tab(R.string.tab_today)).assertIsSelected()
        assertEquals("daily/$day", routes.last())
        rule.onNode(hasText(str(R.string.daily_priority_hint, 1)) and hasSetTextAction()).assertExists()
        assertEquals("an empty page", 3, count(hasText(str(R.string.daily_todo_hint)) and hasSetTextAction()))
    }

    @Test
    fun item042_pickerOpensOnTheShownDateAndOkOpensThePickedDay() {
        open(shown)
        openPicker()
        rule.onNode(pickerCell(shown)).assertIsSelected()
        rule.onNode(hasText("Mar 12, 2031")).assertExists()

        val picked = LocalDate.of(2031, 3, 20)
        rule.onNode(pickerCell(picked)).performClick()
        rule.waitForIdle()
        rule.onNode(pickerCell(picked)).assertIsSelected()
        tapInDialog(ok)

        await { exists(hasText(titleOf(picked))) }
        assertFalse("dialog closed", exists(hasText(cancel)))
        assertFalse(exists(hasText(title)))
    }

    private fun changeSelection() {
        openPicker()
        rule.onNode(pickerCell(LocalDate.of(2031, 3, 20))).performClick()
        rule.waitForIdle()
        rule.onNode(pickerCell(LocalDate.of(2031, 3, 20))).assertIsSelected()
    }

    private fun assertSameDayAndClosed() {
        assertFalse("dialog closed", exists(hasText(cancel)))
        rule.onNode(hasText(title)).assertExists()
        assertFalse(exists(hasText(titleOf(LocalDate.of(2031, 3, 20)))))
    }

    @Test
    fun item043_cancelKeepsTheDay() {
        open(shown)
        changeSelection()
        tapInDialog(cancel)
        assertSameDayAndClosed()
    }

    @Test
    fun item043_tapOutsideKeepsTheDay() {
        open(shown)
        changeSelection()
        tapOutsideDialog()
        assertSameDayAndClosed()
    }

    private fun assertPickerOpensSeededPage(day: LocalDate) {
        open(today)
        goToDateWithPicker(day)

        await { exists(hasText(titleOf(day))) }
        awaitLoaded()
        for (i in 1..3) rule.onNode(hasText(str(R.string.daily_priority_hint, i)) and hasSetTextAction()).assertExists()
        assertEquals(3, count(hasText(str(R.string.daily_todo_hint)) and hasSetTextAction()))
        val rows = runBlocking { container.pages.getSchedule(pageId(day)) }
        assertEquals(17, rows.size)
        assertTrue(rows.all { it.text.isEmpty() })
    }

    @Test
    fun item044_aYearAgoOpensASeededEmptyPage() = assertPickerOpensSeededPage(today.minusYears(1))

    @Test
    fun item044_aYearAheadOpensASeededEmptyPage() = assertPickerOpensSeededPage(today.plusYears(1))

    private fun pinned() = runBlocking { container.pages.getPage(pageId(shown))!!.pinned }

    @Test
    fun item045_pinFillsAndTintsTheIconAndTappingAgainUnpins() {
        open(shown)
        val pinLabel = str(R.string.daily_pin)
        val unpinLabel = str(R.string.daily_unpin)
        assertFalse(pinned())
        assertNotEquals(colors.primary, pixel(rule.onNode(hasContentDescription(pinLabel)), 20f, 18f))

        tapDescription(pinLabel)
        await { exists(hasContentDescription(unpinLabel)) }
        assertTrue(pinned())
        settleAnimations()
        assertSameColor("filled, tinted pin", colors.primary, pixel(rule.onNode(hasContentDescription(unpinLabel)), 20f, 18f))

        tapDescription(unpinLabel)
        await { exists(hasContentDescription(pinLabel)) }
        assertFalse(pinned())
        settleAnimations()
        assertNotEquals(colors.primary, pixel(rule.onNode(hasContentDescription(pinLabel)), 20f, 18f))
    }

    private fun seedNotes(day: LocalDate, text: String) = runBlocking {
        container.pages.saveBlockDocument(pageId(day), SectionKind.NOTES, BlockDocument(listOf(DocBlock(text = text))))
    }

    private fun search(query: String, first: String, second: String) {
        tapTab(R.string.tab_search)
        val box = rule.onNode(hasSetTextAction())
        box.performTextClearance()
        box.performTextInput(query)
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            exists(hasText(first)) && exists(hasText(second))
        }
        rule.waitForIdle()
    }

    private fun topOf(text: String) = bounds(rule.onNode(hasText(text))).top

    @Test
    fun item045_pinnedDayShowsFirstInSearchResults() {
        val other = today.minusDays(3)
        seedNotes(today, "zebra")
        Thread.sleep(5)
        seedNotes(other, "zebra")
        val todayTitle = today.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        val otherTitle = other.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))

        openApp()
        search("zebra", todayTitle, otherTitle)
        assertTrue("control: most recently updated first", topOf(otherTitle) < topOf(todayTitle))

        tapTab(R.string.tab_today)
        await { exists(hasContentDescription(str(R.string.daily_pin))) }
        tapDescription(str(R.string.daily_pin))
        await { exists(hasContentDescription(str(R.string.daily_unpin))) }

        search("zebr", todayTitle, otherTitle)
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            topOf(todayTitle) < topOf(otherTitle)
        }
    }
}
