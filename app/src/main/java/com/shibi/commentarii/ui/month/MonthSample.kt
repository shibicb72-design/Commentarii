package com.shibi.commentarii.ui.month

import com.shibi.commentarii.domain.monthGrid
import com.shibi.commentarii.ui.daily.TaskUi
import java.time.LocalDate
import java.time.YearMonth

/** Fully filled-in state for screenshot tests and previews (October 2026, "today" is the 18th). */
fun sampleMonthUiState(month: YearMonth = YearMonth.of(2026, 10)): MonthUiState {
    var k = 1L
    fun t(text: String, done: Boolean = false) = TaskUi(key = k++, text = text, done = done)
    val moods = mapOf(1 to 4, 2 to 3, 3 to 5, 5 to 2, 6 to 4, 8 to 1, 9 to 3, 12 to 4, 13 to 5, 15 to 3, 16 to 2, 18 to 4)
    val withEntries = setOf(1, 2, 3, 5, 6, 8, 9, 12, 13, 14, 15, 16, 17, 18, 21, 25)
    val grid = monthGrid(month).map { cell ->
        val day = cell.date.dayOfMonth
        MonthDayUi(
            date = cell.date,
            inMonth = cell.inMonth,
            hasContent = cell.inMonth && day in withEntries,
            mood = if (cell.inMonth) moods[day] else null,
        )
    }
    val first = month.atDay(1)
    return MonthUiState(
        month = month,
        today = first.plusDays(17),
        grid = grid,
        goals = listOf(t("Finish the v2 release", done = true), t("Run a 10k"), t("Plan the winter trip")),
        events = listOf(
            EventUi(k++, first.plusDays(9).toEpochDay(), "Anniversary dinner"),
            EventUi(k++, first.plusDays(14).toEpochDay(), "Dentist check-up"),
            EventUi(k++, LocalDate.of(month.year, month.month, month.lengthOfMonth()).toEpochDay(), "Rent due"),
        ),
        todo = listOf(t("Renew the passport"), t("Book flights", done = true), t("Clean the garage")),
        habits = listOf(
            MonthHabitUi(1, "Drink water", 14, 18),
            MonthHabitUi(2, "Stretch", 6, 18),
            MonthHabitUi(3, "Read 10 pages", 11, 18),
        ),
        notes = "Quiet month so far. Keep the evenings free for the trip planning.",
        review = "Good momentum on the release; slow on exercise. Add a fixed running day next month.",
    )
}
