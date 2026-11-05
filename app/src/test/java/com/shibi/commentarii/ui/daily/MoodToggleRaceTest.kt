package com.shibi.commentarii.ui.daily

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.testutil.MutableClock
import com.shibi.commentarii.testutil.resetToDefaults
import com.shibi.commentarii.testutil.runWithLooper
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Checklist #72, #310: a second tap on a face clears the mood even when it comes before Room's observer has told the view model
 * about the first. Each test taps inside one main-thread turn, so the observer cannot deliver between the taps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MoodToggleRaceTest {
    private val today = LocalDate.of(2031, 3, 12)
    private val marker = today.plusDays(1)
    private val clock = MutableClock(today.atTime(9, 0))
    private val container: AppContainer = ApplicationProvider.getApplicationContext<CommentariiApp>().container
    private val writers = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        runWithLooper { container.settings.resetToDefaults() }
    }

    // Off the main thread: the view model's settings edit may still be in flight and needs that thread.
    @After
    fun tearDown() {
        writers.cancel()
        runWithLooper { container.settings.resetToDefaults() }
    }

    private fun mood(date: LocalDate) = runBlocking { container.pages.getMood(date) }?.mood

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    /**
     * Taps in one turn, then waits for a write queued behind them. The result can be "no mood", which is also where it starts,
     * so waiting on the mood itself would pass before the taps had landed.
     */
    private fun tap(vararg faces: Int): Int? {
        val vm = DailyViewModel(container, today, clock)
        awaitUntil("the page") { vm.state.value.todo.isNotEmpty() }
        val onMood = vm.buildActions(onSelectDate = {}).onMood
        faces.forEach(onMood)
        writers.launch { container.pages.saveMood(marker, 1) }
        awaitUntil("the taps to land") { mood(marker) != null }
        return mood(today)
    }

    @Test
    fun item072_item310_secondTapClearsEvenIfTheFirstHasNotReachedTheState() {
        assertNull(tap(5, 5))
    }

    @Test
    fun item072_controlOneTapSetsTheMood() {
        assertEquals(5, tap(5))
    }

    @Test
    fun item072_controlTapsOnDifferentFacesKeepTheLast() {
        assertEquals(2, tap(5, 2))
    }

    @Test
    fun item072_controlThirdTapSetsItAgain() {
        assertEquals(5, tap(5, 5, 5))
    }
}
