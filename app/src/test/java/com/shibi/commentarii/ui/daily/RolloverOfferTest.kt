package com.shibi.commentarii.ui.daily

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.domain.weekStart
import com.shibi.commentarii.testutil.MutableClock
import com.shibi.commentarii.testutil.resetToDefaults
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Checklist #89, #91: the rollover offer through the real DailyViewModel, repository and settings. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RolloverOfferTest {
    private val today = LocalDate.of(2031, 3, 12)
    private val yesterday = today.minusDays(1)
    private val clock = MutableClock(today.atTime(9, 0))
    private val container: AppContainer = ApplicationProvider.getApplicationContext<CommentariiApp>().container
    private val collectors = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Read from a collector, never with runBlocking while polling. DataStore runs an edit's transform on its caller's context,
     * so a view model's edit needs the main thread, and blocking that thread behind it in the store's queue deadlocks.
     */
    private val lastOpened = AtomicReference<LocalDate?>()

    @Before
    fun setUp() {
        container.settings.resetToDefaults()
        assertFalse("each test needs a fresh database", runBlocking { container.pages.hasUserData() })
        collectors.launch { container.settings.lastOpenedDate.collect { lastOpened.set(it) } }
    }

    @After
    fun tearDown() {
        collectors.cancel()
        container.settings.resetToDefaults()
    }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun seed(date: LocalDate, section: String, vararg texts: String, done: Boolean = false) = runBlocking {
        val page = container.pages.getOrCreateDaily(date)
        seedPage(page.id, section, texts.toList(), done)
    }

    private suspend fun seedPage(pageId: Long, section: String, texts: List<String>, done: Boolean = false) {
        container.pages.saveTasks(
            pageId,
            section,
            texts.mapIndexed { i, text -> TaskItemEntity(pageId = pageId, section = "", position = i, text = text, done = done) },
        )
    }

    /** Opens today's page and waits until it has either offered rollover or recorded that there is nothing to offer. */
    private fun openToday(): DailyViewModel {
        val vm = DailyViewModel(container, today, clock)
        awaitUntil("the rollover check") { vm.rollover.value != null || lastOpened.get() == today }
        return vm
    }

    private fun open(date: LocalDate): DailyViewModel {
        val vm = DailyViewModel(container, date, clock)
        awaitUntil("page $date") { vm.state.value.todo.isNotEmpty() }
        return vm
    }

    @Test
    fun item089_moveAllLeavesOriginalsUntickedWithTheirTextAndNeverOffersThemAgain() {
        seed(yesterday, TaskSection.PRIORITY, "First priority")
        seed(yesterday, TaskSection.TODO, "Open todo")
        seed(today.minusDays(2), TaskSection.TODO, "Older page, not the latest")

        val vm = openToday()
        val offer = offerOf(vm)
        assertEquals(yesterday, offer.fromDate)
        vm.resolveRollover(offer.items.map { it.id }.toSet())
        awaitUntil("items to reach today") { vm.state.value.todo.any { it.text == "Open todo" } && lastOpened.get() == today }
        assertEquals("First priority", vm.state.value.priorities[0].text)

        val source = open(yesterday)
        assertEquals("First priority", source.state.value.priorities[0].text)
        assertFalse(source.state.value.priorities[0].done)
        assertEquals(today, source.state.value.priorities[0].carriedTo)
        val todo = source.state.value.todo.first { it.text == "Open todo" }
        assertFalse(todo.done)
        assertEquals(today, todo.carriedTo)

        // Even with today no longer marked as handled the carried items are not offered a second time.
        runBlocking { container.settings.setLastOpenedDate(null) }
        awaitUntil("today to be unmarked") { lastOpened.get() == null }
        assertNull(openToday().rollover.value)
    }

    @Test
    fun item091_positiveControlUnfinishedTodoOnTheLastDailyPageIsOffered() {
        seed(yesterday, TaskSection.TODO, "Open todo")
        assertEquals(listOf("Open todo"), offerOf(openToday()).items.map { it.text })
    }

    @Test
    fun item091_onlyDontForgetGivesNoOffer() {
        seed(yesterday, TaskSection.DONT_FORGET, "Buy stamps")
        assertNull(openToday().rollover.value)
        assertEquals(today, lastOpened.get())
    }

    /** The week and month pages are dated before today and newer than any daily page, so only the type filter excludes them. */
    private suspend fun seedWeekAndMonthPages() {
        val week = container.pages.getOrCreateWeekly(weekStart(today))
        val month = container.pages.getOrCreateMonthly(YearMonth.from(today).atDay(1))
        assertTrue(week.date!! < today && month.date!! < today)
        seedPage(week.id, TaskSection.TODO, listOf("Weekly todo"))
        seedPage(week.id, TaskSection.DONT_FORGET, listOf("Weekly note"))
        seedPage(month.id, TaskSection.TODO, listOf("Monthly todo"))
    }

    @Test
    fun item091_onlyWeekAndMonthPagesGiveNoOffer() {
        runBlocking { seedWeekAndMonthPages() }
        assertNull(openToday().rollover.value)
        assertEquals(today, lastOpened.get())
    }

    @Test
    fun item091_weekAndMonthItemsWithAnOlderBlankDailyPageGiveNoOffer() {
        runBlocking {
            seedWeekAndMonthPages()
            container.pages.getOrCreateDaily(today.minusDays(5))
        }
        assertNull(openToday().rollover.value)
        assertEquals(today, lastOpened.get())
    }

    @Test
    fun item091_weekPageNewerThanTheLastDailyDoesNotHideItsItems() = runBlocking {
        seedPage(container.pages.getOrCreateDaily(today.minusDays(5)).id, TaskSection.TODO, listOf("Daily todo"))
        seedPage(container.pages.getOrCreateWeekly(weekStart(today)).id, TaskSection.TODO, listOf("Weekly todo"))

        assertEquals(listOf("Daily todo"), offerOf(openToday()).items.map { it.text })
    }

    private fun offerOf(vm: DailyViewModel) = vm.rollover.value.also { assertNotNull("expected an offer", it) }!!
}
