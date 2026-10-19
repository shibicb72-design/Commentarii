package com.shibi.commentarii.ui.week

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.domain.weekStart
import com.shibi.commentarii.ui.daily.DailyViewModel
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HabitSyncTest {
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun habitToggledOnTheWeekPageShowsOnTheDayAndTheReverse() {
        val container = AppContainer(ApplicationProvider.getApplicationContext<Context>())
        val today = LocalDate.now(container.clock)
        val monday = weekStart(today)
        val dayIndex = today.toEpochDay().minus(monday.toEpochDay()).toInt()
        val habitId = runBlocking { container.selfCare.addItem("Water").id }

        val week = WeekViewModel(container, monday)
        val daily = DailyViewModel(container, today)
        val weekActions = week.buildActions({}, {})
        val dailyActions = daily.buildActions(onSelectDate = {})

        fun weekChecked() = week.state.value.habits.firstOrNull { it.id == habitId }?.checks?.getOrNull(dayIndex)
        fun dailyChecked() = daily.state.value.selfCare.firstOrNull { it.id == habitId }?.checked

        awaitUntil("both pages to load the habit") { weekChecked() == false && dailyChecked() == false }

        weekActions.onToggleHabit(habitId, today)
        awaitUntil("daily page to show the week toggle") { dailyChecked() == true }
        assertTrue(weekChecked() == true)

        dailyActions.onToggleSelfCare(habitId)
        awaitUntil("week page to show the daily toggle") { weekChecked() == false }
        assertEquals(false, dailyChecked())
    }
}
