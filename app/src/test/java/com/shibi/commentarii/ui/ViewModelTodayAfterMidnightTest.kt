package com.shibi.commentarii.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.R
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.MutableClock
import com.shibi.commentarii.testutil.resetToDefaults
import com.shibi.commentarii.testutil.waitUntilWithLooper
import com.shibi.commentarii.ui.daily.DailyPageScreen
import com.shibi.commentarii.ui.daily.DailyViewModel
import com.shibi.commentarii.ui.month.MonthPageScreen
import com.shibi.commentarii.ui.month.MonthViewModel
import com.shibi.commentarii.ui.theme.CommentariiTheme
import com.shibi.commentarii.ui.week.WeekPageScreen
import com.shibi.commentarii.ui.week.WeekViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checklist #294, page level: a page left open across midnight re-reads the date when it resumes. TodayAfterMidnightTest
 * is the evidence for the tabs. Here the real screens are hosted, and the host creates each page's view model first with
 * the same `viewModel(key = ...)` the screen uses, so the screen gets the one holding the test clock. Refresh only runs
 * through the real lifecycle. The highlights (#40, #142, #156) have no semantics and take `state.today` straight from
 * these states, so that is what is asserted. Also touches the rollover offer (#84, #88, #90) and banner (#92-95).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class ViewModelTodayAfterMidnightTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val clock = MutableClock(day.atTime(23, 59))
    private val container: AppContainer get() = (rule.activity.application as CommentariiApp).container

    private var shownDay by mutableStateOf(day)
    private lateinit var dailyVm: DailyViewModel

    @Before
    fun setUp() {
        container.settings.resetToDefaults()
        assertFalse("each test needs a fresh database", runBlocking { container.pages.hasUserData() })
    }

    @After
    fun tearDown() {
        container.settings.resetToDefaults()
    }

    private fun host(page: @Composable () -> Unit) {
        rule.setContent { CommentariiTheme(dark = false) { page() } }
        rule.waitForIdle()
    }

    private fun showDaily(date: LocalDate): DailyViewModel {
        shownDay = date
        host {
            val shown = shownDay
            dailyVm = viewModel(key = "daily-$shown", factory = DailyViewModel.factory(container, shown, clock))
            DailyPageScreen(container, shown, {}, WindowWidthSizeClass.Compact)
        }
        rule.waitUntilWithLooper { dailyVm.state.value.todo.isNotEmpty() }
        return dailyVm
    }

    private fun showWeek(monday: LocalDate): WeekViewModel {
        lateinit var vm: WeekViewModel
        host {
            vm = viewModel(key = "week-$monday", factory = WeekViewModel.factory(container, monday, clock))
            WeekPageScreen(container, monday, {}, {}, WindowWidthSizeClass.Compact)
        }
        rule.waitUntilWithLooper { vm.state.value.loaded }
        return vm
    }

    private fun showMonth(month: YearMonth): MonthViewModel {
        lateinit var vm: MonthViewModel
        host {
            vm = viewModel(key = "month-$month", factory = MonthViewModel.factory(container, month, clock))
            MonthPageScreen(container, month, {}, {}, WindowWidthSizeClass.Compact)
        }
        rule.waitUntilWithLooper { vm.state.value.loaded }
        return vm
    }

    /** The system pausing and resuming the app, e.g. the notification shade or a brief trip to another app. */
    private fun pauseAndResume() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle()
    }

    /** The screen going off, the clock reaching [wakeAt] meanwhile, and the screen coming back. ON_STOP also flushes autosave. */
    private fun screenOffUntil(wakeAt: LocalDateTime) {
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        clock.set(wakeAt)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        settle()
    }

    /**
     * Gives a stray reload started by the resume time to finish, so the "nothing changed" checks after it can see one.
     * Short of the 500 ms autosave debounce, so text typed just before the resume is still unsaved at those checks.
     */
    private fun settle() {
        repeat(10) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        rule.waitForIdle()
    }

    private fun waitUntilSaved(pageId: Long, text: String) {
        rule.waitUntilWithLooper { runBlocking { container.pages.getTasks(pageId) }.any { it.text == text } }
    }

    private fun seed(date: LocalDate, text: String, done: Boolean = false) = runBlocking {
        val page = container.pages.getOrCreateDaily(date)
        container.pages.saveTasks(
            page.id,
            TaskSection.TODO,
            listOf(TaskItemEntity(id = 0, pageId = page.id, section = TaskSection.TODO, position = 0, text = text, done = done, carriedTo = null)),
        )
        Unit
    }

    private fun tasksOn(date: LocalDate): List<TaskItemEntity> = runBlocking {
        container.pages.getTasks(container.pages.getOrCreateDaily(date).id)
    }

    /** Writes through the same DataStore first, so a write already queued by the page has landed before the read. */
    private fun lastOpenedDate(): LocalDate? = runBlocking {
        container.settings.setExportReminderEnabled(true)
        container.settings.lastOpenedDate.first()
    }

    private fun str(id: Int) = rule.activity.getString(id)

    private fun scrollToText(text: String) {
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        rule.waitForIdle()
    }

    private fun typeInFirstTodo(text: String) {
        val hint = str(R.string.daily_todo_hint)
        scrollToText(hint)
        rule.onAllNodes(hasSetTextAction() and hasText(hint)).onFirst().performTextInput(text)
        rule.waitForIdle()
    }

    private fun assertDailyPageShowing() {
        rule.onNode(hasContentDescription(str(R.string.daily_pick_date))).assertExists()
    }

    private fun assertOfferDialogShowing() {
        rule.onNode(hasText(str(R.string.rollover_skip))).assertExists()
    }

    private fun assertOfferDialogGone() {
        rule.onNode(hasText(str(R.string.rollover_skip))).assertDoesNotExist()
    }

    private val banner: SemanticsMatcher get() = hasText(str(R.string.reminder_text))

    private fun waitForBanner() {
        rule.waitUntilWithLooper { rule.onAllNodes(banner).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForBannerGone() {
        rule.waitUntilWithLooper { rule.onAllNodes(banner).fetchSemanticsNodes().isEmpty() }
    }

    // ---- Daily ----

    /** #294 case (a), with #84 and #88: an offer pending at midnight goes away without copying or marking anything. */
    @Test
    fun item294_pendingOfferIsDismissedAfterMidnightWithoutCopying() {
        seed(day.minusDays(1), "carry me")
        runBlocking { container.settings.setLastOpenedDate(day.minusDays(1)) }
        val vm = showDaily(day)

        rule.waitUntilWithLooper { vm.rollover.value != null }
        assertEquals(day, vm.state.value.today)
        assertEquals(listOf("carry me"), vm.rollover.value!!.items.map { it.text })
        rule.waitForIdle()
        assertOfferDialogShowing()

        clock.set(day.plusDays(1).atTime(0, 0, 30))
        assertNotNull("the date is only re-read on resume", vm.rollover.value)
        pauseAndResume()

        assertDailyPageShowing()
        assertEquals(day.plusDays(1), vm.state.value.today)
        assertNull(vm.rollover.value)
        assertOfferDialogGone()
        assertEquals("not marked as handled", day.minusDays(1), lastOpenedDate())
        assertTrue("nothing copied", tasksOn(day).none { it.text == "carry me" })
        assertTrue("nothing copied", vm.state.value.todo.none { it.text == "carry me" })
        assertEquals("original not marked as carried", listOf<LocalDate?>(null), tasksOn(day.minusDays(1)).map { it.carriedTo })
    }

    /** #294 case (a) again, the way midnight usually arrives: the screen is off, so the app goes through ON_STOP. */
    @Test
    fun item294_pendingOfferIsDismissedWhenTheScreenWasOffOverMidnight() {
        seed(day.minusDays(1), "carry me")
        runBlocking { container.settings.setLastOpenedDate(day.minusDays(1)) }
        val vm = showDaily(day)
        rule.waitUntilWithLooper { vm.rollover.value != null }
        rule.waitForIdle()
        assertOfferDialogShowing()

        screenOffUntil(day.plusDays(1).atTime(7, 0))

        assertDailyPageShowing()
        assertEquals(day.plusDays(1), vm.state.value.today)
        assertNull(vm.rollover.value)
        assertOfferDialogGone()
        assertEquals(day.minusDays(1), lastOpenedDate())
        assertTrue(tasksOn(day).none { it.text == "carry me" })
    }

    /** #294 case (b), with #92: the banner is only for today's page, so it goes once the page is yesterday's. */
    @Test
    fun item294_exportBannerGoesAfterMidnight() {
        seed(day.minusDays(1), "finished", done = true)
        val vm = showDaily(day)
        waitForBanner()

        clock.set(day.plusDays(1).atTime(0, 0, 30))
        pauseAndResume()

        assertDailyPageShowing()
        assertEquals(day.plusDays(1), vm.state.value.today)
        waitForBannerGone()
        rule.onNode(banner).assertDoesNotExist()
        assertNull("hidden, not dismissed", runBlocking { container.settings.reminderDismissedAt.first() })
    }

    /**
     * #294 case (c), regression guard: refresh must not reload the page, because load() would replace rows that autosave
     * has not written yet. New row keys would show a reload even if the save happened to land first.
     */
    @Test
    fun item294_resumeAfterMidnightKeepsUnsavedTextAndDoesNotReload() {
        val vm = showDaily(day)
        typeInFirstTodo("typed at 23:59")
        assertEquals("typed at 23:59", vm.state.value.todo[0].text)
        val todoBefore = vm.state.value.todo
        val prioritiesBefore = vm.state.value.priorities

        clock.set(day.plusDays(1).atTime(0, 0, 30))
        pauseAndResume()

        assertEquals("typed at 23:59", vm.state.value.todo[0].text)
        assertEquals(todoBefore.map { it.key }, vm.state.value.todo.map { it.key })
        assertEquals(prioritiesBefore.map { it.key }, vm.state.value.priorities.map { it.key })

        rule.waitUntilWithLooper { tasksOn(day).any { it.text == "typed at 23:59" } }
        assertEquals("saved to the page it was typed on", 1, tasksOn(day).count { it.text == "typed at 23:59" })
        assertEquals(todoBefore.map { it.key }, vm.state.value.todo.map { it.key })
        assertEquals(day.plusDays(1), vm.state.value.today)
    }

    /** #294 case (c) through ON_STOP: the flush there and the refresh on the way back must not disturb the edit. */
    @Test
    fun item294_screenOffOverMidnightKeepsTheEdit() {
        val vm = showDaily(day)
        typeInFirstTodo("typed at 23:59")
        val keysBefore = vm.state.value.todo.map { it.key }

        screenOffUntil(day.plusDays(1).atTime(7, 0))

        assertEquals("typed at 23:59", vm.state.value.todo[0].text)
        assertEquals(keysBefore, vm.state.value.todo.map { it.key })
        rule.waitUntilWithLooper { tasksOn(day).any { it.text == "typed at 23:59" } }
        assertEquals(keysBefore, vm.state.value.todo.map { it.key })
        assertEquals(day.plusDays(1), vm.state.value.today)
    }

    /** #294 case (d), regression guard: opening the new day creates a new page, which asks about the day before. */
    @Test
    fun item294_newDayPageShowsTheOffer() {
        seed(day.minusDays(1), "older item")
        seed(day, "unfinished on the old day")
        runBlocking { container.settings.setLastOpenedDate(day.minusDays(1)) }
        val oldPage = showDaily(day)
        rule.waitUntilWithLooper { oldPage.rollover.value != null }

        clock.set(day.plusDays(1).atTime(0, 0, 30))
        pauseAndResume()
        shownDay = day.plusDays(1)
        rule.waitForIdle()

        rule.waitUntilWithLooper { dailyVm.date == day.plusDays(1) && dailyVm.rollover.value != null }
        assertEquals(day, dailyVm.rollover.value!!.fromDate)
        assertEquals(listOf("unfinished on the old day"), dailyVm.rollover.value!!.items.map { it.text })
        assertEquals(day.plusDays(1), dailyVm.state.value.today)
        rule.waitForIdle()
        assertOfferDialogShowing()
    }

    /**
     * #294, known limitation, with #90 and #92: a page that becomes today while open gets its highlight and banner but no
     * offer, because applying one reloads the task sections and would drop unsaved text. The next load of the page asks.
     */
    @Test
    fun item294_pageThatBecomesTodayGetsBannerButNoOfferUntilNextLoad() {
        seed(day.minusDays(1), "carry me")
        runBlocking { container.settings.setLastOpenedDate(day.minusDays(1)) }
        val tomorrow = day.plusDays(1)
        val vm = showDaily(tomorrow)
        typeInFirstTodo("typed the night before")
        val keysBefore = vm.state.value.todo.map { it.key }
        assertEquals(day, vm.state.value.today)
        assertNull("only today's page asks", vm.rollover.value)
        rule.onNode(banner).assertDoesNotExist()

        clock.set(tomorrow.atTime(0, 0, 30))
        pauseAndResume()

        assertEquals(tomorrow, vm.state.value.today)
        waitForBanner()
        assertNull(vm.rollover.value)
        assertOfferDialogGone()
        assertEquals("typed the night before", vm.state.value.todo[0].text)
        assertEquals(keysBefore, vm.state.value.todo.map { it.key })
        assertEquals(day.minusDays(1), lastOpenedDate())

        val nextLoad = DailyViewModel(container, tomorrow, clock)
        rule.waitUntilWithLooper { nextLoad.rollover.value != null }
        assertEquals(listOf("carry me"), nextLoad.rollover.value!!.items.map { it.text })
    }

    /**
     * #294 rule (2): midnight passes while the page is still loading. The view model exists before the screen does, so its
     * load is still waiting on the main looper when the screen's resume refreshes it. Folders are only observed after the
     * rollover check, so seeing them means that check has had its chance.
     */
    @Test
    fun item294_midnightDuringLoadDoesNotOffer() {
        seed(day.minusDays(1), "carry me")
        runBlocking {
            container.settings.setLastOpenedDate(day.minusDays(1))
            container.pages.createFolder("folder")
        }
        val vm = ViewModelProvider(rule.activity, DailyViewModel.factory(container, day, clock))["daily-$day", DailyViewModel::class.java]

        clock.set(day.plusDays(1).atTime(0, 0, 30))
        host { DailyPageScreen(container, day, {}, WindowWidthSizeClass.Compact) }
        rule.waitUntilWithLooper { vm.state.value.folders.isNotEmpty() }

        assertDailyPageShowing()
        assertEquals(day.plusDays(1), vm.state.value.today)
        assertNull(vm.rollover.value)
        assertOfferDialogGone()
        assertEquals(day.minusDays(1), lastOpenedDate())
    }

    /** #294 with #90: an older page stays out of the offer and banner; only its date strip's today moves. */
    @Test
    fun item294_pastPageOnlyUpdatesItsToday() {
        seed(day.minusDays(3), "carry me")
        val vm = showDaily(day.minusDays(2))
        assertEquals(day, vm.state.value.today)

        clock.set(day.plusDays(1).atTime(0, 0, 30))
        pauseAndResume()

        assertDailyPageShowing()
        assertEquals(day.plusDays(1), vm.state.value.today)
        assertNull(vm.rollover.value)
        assertOfferDialogGone()
        rule.onNode(banner).assertDoesNotExist()
        assertNull(lastOpenedDate())
    }

    /** #294 rule (1), regression guard: resuming on the same date leaves the page and a pending offer alone. */
    @Test
    fun item294_resumeOnTheSameDayChangesNothing() {
        seed(day.minusDays(1), "carry me")
        runBlocking { container.settings.setLastOpenedDate(day.minusDays(1)) }
        val vm = showDaily(day)
        rule.waitUntilWithLooper { vm.rollover.value != null }
        rule.waitForIdle()
        val offerBefore = vm.rollover.value
        val keysBefore = vm.state.value.todo.map { it.key }

        clock.set(day.atTime(23, 59, 30))
        pauseAndResume()

        assertEquals(day, vm.state.value.today)
        assertEquals(offerBefore, vm.rollover.value)
        assertOfferDialogShowing()
        assertEquals(keysBefore, vm.state.value.todo.map { it.key })
        assertEquals(day.minusDays(1), lastOpenedDate())
    }

    /** #294 rule (4): the banner's Dismiss stamps the same clock the page reads, not the system one. */
    @Test
    fun item294_bannerDismissUsesThePageClock() {
        seed(day.minusDays(1), "finished", done = true)
        showDaily(day)
        waitForBanner()

        rule.onNode(hasText(str(R.string.reminder_dismiss))).performClick()
        waitForBannerGone()

        assertEquals(clock.instant(), runBlocking { container.settings.reminderDismissedAt.first() })
    }

    // ---- Week ----

    /** #294 with #142: Sunday night to Monday morning, the week page's today moves out of the week. */
    @Test
    fun item294_weekPageSundayToMonday() {
        val sunday = LocalDate.parse("2031-03-16")
        clock.set(sunday.atTime(23, 59))
        val vm = showWeek(LocalDate.parse("2031-03-10"))
        assertEquals(sunday, vm.state.value.today)

        clock.set(sunday.plusDays(1).atTime(0, 0, 30))
        assertEquals("the date is only re-read on resume", sunday, vm.state.value.today)
        pauseAndResume()

        assertEquals(sunday.plusDays(1), vm.state.value.today)
    }

    /** #294 with #142: within one week the highlight moves to the next card, including after the screen was off. */
    @Test
    fun item294_weekPageMovesToTheNextDayAfterTheScreenWasOff() {
        clock.set(day.atTime(23, 59))
        val vm = showWeek(LocalDate.parse("2031-03-10"))
        screenOffUntil(day.plusDays(1).atTime(7, 0))
        assertEquals(day.plusDays(1), vm.state.value.today)
    }

    /** #294 rule (1) for the week page: unsaved text survives the refresh, with no reload. */
    @Test
    fun item294_weekPageKeepsUnsavedTextOverMidnight() {
        val monday = LocalDate.parse("2031-03-10")
        val vm = showWeek(monday)
        typeInFirstTodo("typed at 23:59")
        val keysBefore = vm.state.value.todo.map { it.key }

        clock.set(day.plusDays(1).atTime(0, 0, 30))
        pauseAndResume()

        assertEquals("typed at 23:59", vm.state.value.todo[0].text)
        assertEquals(keysBefore, vm.state.value.todo.map { it.key })
        waitUntilSaved(runBlocking { container.pages.getOrCreateWeekly(monday).id }, "typed at 23:59")
        assertEquals(keysBefore, vm.state.value.todo.map { it.key })
        assertEquals(day.plusDays(1), vm.state.value.today)
    }

    // ---- Month ----

    /** #294 with #156: last day of the month to the 1st, the month page's today moves out of the month. */
    @Test
    fun item294_monthPageLastDayToFirst() {
        val lastDay = LocalDate.parse("2031-03-31")
        clock.set(lastDay.atTime(23, 59))
        val vm = showMonth(YearMonth.parse("2031-03"))
        assertEquals(lastDay, vm.state.value.today)

        clock.set(lastDay.plusDays(1).atTime(0, 0, 30))
        assertEquals("the date is only re-read on resume", lastDay, vm.state.value.today)
        pauseAndResume()

        assertEquals(lastDay.plusDays(1), vm.state.value.today)
    }

    /** #294 rule (1) for the month page: unsaved text survives the refresh, with no reload. */
    @Test
    fun item294_monthPageKeepsUnsavedTextOverMidnight() {
        val month = YearMonth.parse("2031-03")
        val vm = showMonth(month)
        typeInFirstTodo("typed at 23:59")
        val keysBefore = vm.state.value.todo.map { it.key }

        clock.set(day.plusDays(1).atTime(0, 0, 30))
        pauseAndResume()

        assertEquals("typed at 23:59", vm.state.value.todo[0].text)
        assertEquals(keysBefore, vm.state.value.todo.map { it.key })
        waitUntilSaved(runBlocking { container.pages.getOrCreateMonthly(month.atDay(1)).id }, "typed at 23:59")
        assertEquals(keysBefore, vm.state.value.todo.map { it.key })
        assertEquals(day.plusDays(1), vm.state.value.today)
    }

    /** #294 rule (4): Add event defaults to the new today once midnight has passed inside the month. */
    @Test
    fun item294_monthAddEventUsesTheNewDate() {
        val vm = showMonth(YearMonth.parse("2031-03"))

        clock.set(day.plusDays(1).atTime(0, 0, 30))
        pauseAndResume()
        tapAddEvent()

        assertEquals(listOf(day.plusDays(1).toEpochDay()), vm.state.value.events.map { it.epochDay })
    }

    /** #294 rule (4): once the shown month is over, Add event falls back to its 1st, not its last "today". */
    @Test
    fun item294_monthAddEventAfterTheMonthEndedUsesItsFirstDay() {
        clock.set(LocalDate.parse("2031-03-31").atTime(23, 59))
        val vm = showMonth(YearMonth.parse("2031-03"))

        clock.set(LocalDate.parse("2031-04-01").atTime(0, 0, 30))
        pauseAndResume()
        tapAddEvent()

        assertEquals(listOf(LocalDate.parse("2031-03-01").toEpochDay()), vm.state.value.events.map { it.epochDay })
    }

    private fun tapAddEvent() {
        val add = str(R.string.event_add)
        scrollToText(add)
        rule.onNode(hasText(add)).performClick()
        rule.waitForIdle()
    }

    private companion object {
        /** A Wednesday, far from the real date so the system clock cannot make a page count as today by accident. */
        val day: LocalDate = LocalDate.parse("2031-03-12")
    }
}
