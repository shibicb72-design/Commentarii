package com.shibi.commentarii.ui

import android.os.Looper
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.DeviceStateTestBase
import java.time.Duration
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * Checklist #285 and #288: Back from every screen on the real MainActivity. A test takes each path once with the Back button and
 * once with the gesture's started/progress/committed events, which differ only in how they reach the dispatcher.
 */
class DeviceNavBackFlowTest : DeviceStateTestBase() {
    private val notesTitle get() = str(R.string.nt_title)
    private val newNote get() = str(R.string.nt_new)
    private val debugTitle get() = str(R.string.debug_title)
    private val backArrow get() = str(R.string.ed_back)

    private fun weekTitleShown() = exists(androidx.compose.ui.test.hasText("Week of ", substring = true))

    private fun monthTitleShown(): Boolean {
        val now = YearMonth.from(today)
        val name = now.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, java.util.Locale.getDefault())
        return exists(hasText(str(R.string.month_title_fmt, name, now.year)))
    }

    private fun onWeek() = tabSelected(R.string.tab_week) && weekTitleShown()

    private fun onMonth() = tabSelected(R.string.tab_month) && monthTitleShown()

    private fun onNotes() = tabSelected(R.string.tab_notes) && exists(hasContentDescription(newNote))

    private fun onSearch() = tabSelected(R.string.tab_search) && exists(hasText(str(R.string.sr_hint)) or hasSetTextAction())

    private fun onSettings() = tabSelected(R.string.tab_settings) && exists(str(R.string.settings_section_backup))

    private fun openSearch(query: String) {
        openTab(R.string.tab_search)
        await { onSearch() }
        rule.onNode(hasSetTextAction()).performTextInput(query)
        rule.waitForIdle()
    }

    /** The search debounce is a delay on the main looper, which only moves when told to. */
    private fun awaitResult(matcher: androidx.compose.ui.test.SemanticsMatcher) {
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            exists(matcher)
        }
        rule.waitForIdle()
    }

    /** From a tab root: back lands on Today, and from there the app leaves. */
    private fun tabRootGoesToTodayThenLeaves(label: Int, back: () -> Unit, isThere: () -> Boolean) {
        awaitToday()
        openTab(label)
        await(isThere)

        back()
        awaitToday()
        assertFalse("nothing left of the tab", isThere())

        leavesTheApp(back)
    }

    private fun leavesTheApp(back: () -> Unit) {
        val activity = rule.activity
        assertFalse("still in the app before the last back", activity.isFinishing)
        back()
        assertTrue("back from Today leaves the app", activity.isFinishing)
    }

    private fun noteIsOpen(title: String) = exists(hasContentDescription(str(R.string.ed_organize))) && exists(hasText(title) and hasSetTextAction())

    private fun seedNote(title: String) = runBlocking { container.pages.createNote(title).id }

    // ---- #285, gesture ----

    @Test fun item285_gestureFromWeekGoesToTodayThenLeaves() = tabRootGoesToTodayThenLeaves(R.string.tab_week, ::swipeBack, ::onWeek)

    @Test fun item285_gestureFromMonthGoesToTodayThenLeaves() = tabRootGoesToTodayThenLeaves(R.string.tab_month, ::swipeBack, ::onMonth)

    @Test fun item285_gestureFromNotesGoesToTodayThenLeaves() = tabRootGoesToTodayThenLeaves(R.string.tab_notes, ::swipeBack, ::onNotes)

    @Test fun item285_gestureFromSearchGoesToTodayThenLeaves() = tabRootGoesToTodayThenLeaves(R.string.tab_search, ::swipeBack, ::onSearch)

    @Test fun item285_gestureFromSettingsGoesToTodayThenLeaves() = tabRootGoesToTodayThenLeaves(R.string.tab_settings, ::swipeBack, ::onSettings)

    @Test fun item285_gestureFromEditorGoesToNotes() = editorGoesToNotes(::swipeBack)

    @Test fun item285_gestureFromDebugLogGoesToSettings() = debugLogGoesToSettings(::swipeBack)

    @Test fun item285_gestureFromDayOpenedFromWeekGoesToTheWeek() = dayFromWeekGoesToTheWeek(::swipeBack)

    @Test fun item285_gestureFromDayOpenedFromMonthGoesToTheMonth() = dayFromMonthGoesToTheMonth(::swipeBack)

    @Test fun item285_gestureFromPagesOpenedFromSearchGoesToSearch() = pagesFromSearchGoToSearch(::swipeBack)

    @Test fun item285_gestureFromAnotherDayLeavesTheApp() = anotherDayLeaves(::swipeBack)

    // ---- #288, the Back button ----

    @Test fun item288_buttonFromWeekGoesToTodayThenLeaves() = tabRootGoesToTodayThenLeaves(R.string.tab_week, ::pressBack, ::onWeek)

    @Test fun item288_buttonFromMonthGoesToTodayThenLeaves() = tabRootGoesToTodayThenLeaves(R.string.tab_month, ::pressBack, ::onMonth)

    @Test fun item288_buttonFromNotesGoesToTodayThenLeaves() = tabRootGoesToTodayThenLeaves(R.string.tab_notes, ::pressBack, ::onNotes)

    @Test fun item288_buttonFromSearchGoesToTodayThenLeaves() = tabRootGoesToTodayThenLeaves(R.string.tab_search, ::pressBack, ::onSearch)

    @Test fun item288_buttonFromSettingsGoesToTodayThenLeaves() = tabRootGoesToTodayThenLeaves(R.string.tab_settings, ::pressBack, ::onSettings)

    @Test fun item288_buttonFromEditorGoesToNotes() = editorGoesToNotes(::pressBack)

    @Test fun item288_buttonFromDebugLogGoesToSettings() = debugLogGoesToSettings(::pressBack)

    @Test fun item288_buttonFromDayOpenedFromWeekGoesToTheWeek() = dayFromWeekGoesToTheWeek(::pressBack)

    @Test fun item288_buttonFromDayOpenedFromMonthGoesToTheMonth() = dayFromMonthGoesToTheMonth(::pressBack)

    @Test fun item288_buttonFromPagesOpenedFromSearchGoesToSearch() = pagesFromSearchGoToSearch(::pressBack)

    @Test fun item288_buttonFromAnotherDayLeavesTheApp() = anotherDayLeaves(::pressBack)

    /** Home then back to the app stops and restarts the activity: the same screen, the same text, and Back still works from there. */
    @Test
    fun item288_homeAndReturnKeepsTheScreenAndTheBackStack() {
        val id = seedNote("Kept note")
        awaitToday()
        openTab(R.string.tab_notes)
        await { exists(hasText("Kept note")) }
        rule.onNode(card("Kept note")).performClick()
        rule.waitForIdle()
        await { noteIsOpen("Kept note") }
        rule.onNode(field("Kept note")).performTextInput("Still ")
        rule.waitForIdle()

        leaveAndReturn()

        await { noteIsOpen("Still Kept note") }
        await { runBlocking { container.pages.getPage(id) }?.title == "Still Kept note" }
        pressBack()
        await { onNotes() }
        pressBack()
        awaitToday()
        leavesTheApp(::pressBack)
    }

    // ---- The paths ----

    private fun editorGoesToNotes(back: () -> Unit) {
        seedNote("Back note")
        awaitToday()
        openTab(R.string.tab_notes)
        await { exists(hasText("Back note")) }
        rule.onNode(card("Back note")).performClick()
        rule.waitForIdle()
        await { noteIsOpen("Back note") }

        back()
        await { onNotes() && exists(hasText("Back note")) }
        assertFalse("the editor has gone", exists(hasContentDescription(str(R.string.ed_organize))))

        back()
        awaitToday()
        leavesTheApp(back)
    }

    private fun debugLogGoesToSettings(back: () -> Unit) {
        awaitToday()
        openTab(R.string.tab_settings)
        await { onSettings() }
        val open = str(R.string.settings_debug_open)
        rule.onNode(hasText(open)).performScrollTo().performClick()
        rule.waitForIdle()
        await { exists(debugTitle) && exists(hasContentDescription(backArrow)) }

        back()
        await { onSettings() }
        assertFalse("the log has gone", exists(debugTitle))

        back()
        awaitToday()
        leavesTheApp(back)
    }

    private fun dayFromWeekGoesToTheWeek(back: () -> Unit) {
        awaitToday()
        openTab(R.string.tab_week)
        await { onWeek() }
        val friday = com.shibi.commentarii.domain.weekStart(today).plusDays(4)
        await { exists(card("Fri")) }
        rule.onNode(card("Fri")).performClick()
        rule.waitForIdle()
        awaitDay(friday)
        assertTrue("a day belongs to Today", tabSelected(R.string.tab_today))

        back()
        await { onWeek() }
        assertFalse("the day has gone", exists(hasText("1.")))

        back()
        awaitToday()
        leavesTheApp(back)
    }

    private fun dayFromMonthGoesToTheMonth(back: () -> Unit) {
        awaitToday()
        openTab(R.string.tab_month)
        await { onMonth() }
        val theFifteenth = YearMonth.from(today).atDay(15)
        await { exists(card("15")) }
        rule.onNode(card("15")).performClick()
        rule.waitForIdle()
        awaitDay(theFifteenth)
        assertTrue("a day belongs to Today", tabSelected(R.string.tab_today))

        back()
        await { onMonth() }

        back()
        awaitToday()
        leavesTheApp(back)
    }

    private fun pagesFromSearchGoToSearch(back: () -> Unit) {
        val tomorrow = today.plusDays(1)
        runBlocking {
            container.pages.createNote("needle note")
            val day = container.pages.getOrCreateDaily(tomorrow)
            container.pages.saveTasks(
                day.id,
                com.shibi.commentarii.data.TaskSection.TODO,
                listOf(com.shibi.commentarii.data.TaskItemEntity(pageId = day.id, section = com.shibi.commentarii.data.TaskSection.TODO, position = 0, text = "needle task")),
            )
        }
        awaitToday()
        openSearch("needle")
        awaitResult(hasText("needle note"))
        val dayResult = tomorrow.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL))
        awaitResult(hasText(dayResult))

        rule.onNode(card("needle note")).performClick()
        rule.waitForIdle()
        await { noteIsOpen("needle note") }
        back()
        awaitResult(hasText("needle note"))
        assertTrue("back on Search", onSearch())
        assertTrue("with the query kept", exists(hasText("needle") and hasSetTextAction()))

        rule.onNode(card(dayResult)).performClick()
        rule.waitForIdle()
        awaitDay(tomorrow)
        back()
        awaitResult(hasText(dayResult))
        assertTrue("back on Search", onSearch())

        back()
        awaitToday()
        leavesTheApp(back)
    }

    /** The date strip replaces the page rather than stacking, so another day is the root: back leaves, as it does from Today. */
    private fun anotherDayLeaves(back: () -> Unit) {
        awaitToday()
        val tomorrow = today.plusDays(1)
        rule.onNode(hasText(tomorrow.dayOfMonth.toString()) and androidx.compose.ui.test.hasClickAction()).performClick()
        rule.waitForIdle()
        awaitDay(tomorrow)
        leavesTheApp(back)
    }
}
