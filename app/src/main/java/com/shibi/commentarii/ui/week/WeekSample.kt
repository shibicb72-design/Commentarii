package com.shibi.commentarii.ui.week

import com.shibi.commentarii.ui.daily.TaskUi
import java.time.LocalDate

/** Fully filled-in state for screenshot tests and previews (week of Mon 2026-10-12). */
fun sampleWeekUiState(monday: LocalDate = LocalDate.of(2026, 10, 12)): WeekUiState {
    var k = 1L
    fun t(text: String, done: Boolean = false) = TaskUi(key = k++, text = text, done = done)
    val days = (0L..6L).map { monday.plusDays(it) }
    val priorities = listOf(
        listOf("Plan the sprint", "Gym"),
        listOf("Finish the report", "Call the bank", "Buy groceries"),
        listOf("Dentist"),
        emptyList(),
        listOf("Submit taxes", "Pack for the trip"),
        listOf("Farmers market"),
        emptyList(),
    )
    val todo = listOf(0 to 0, 4 to 2, 3 to 1, 0 to 0, 2 to 2, 1 to 0, 0 to 0)
    return WeekUiState(
        monday = monday,
        today = monday.plusDays(6),
        goals = listOf(t("Ship the v2 release", done = true), t("Run three times"), t("Read two chapters")),
        todo = listOf(t("Book flights", done = true), t("Renew the passport"), t("Tidy the desk"), t("Water the plants")),
        dontForget = listOf(t("Mom's birthday gift"), t("Library books due"), t("Take vitamins")),
        days = days.mapIndexed { i, d ->
            WeekDayUi(d, priorities[i], todoDone = todo[i].second, todoTotal = todo[i].first)
        },
        habits = listOf(
            WeekHabitUi(1, "Drink water", listOf(true, true, false, true, true, false, true)),
            WeekHabitUi(2, "Stretch", listOf(false, true, false, false, true, false, false)),
            WeekHabitUi(3, "Read 10 pages", listOf(true, true, true, false, true, true, false)),
        ),
        moods = listOf(4, 3, 5, null, 2, 4, 1),
        notes = "Busy week. Remember to follow up on the invoice and plan the weekend trip.",
        reviewWell = "Finished the report early and kept up the water habit.",
        reviewImprove = "Start the weekly plan on Sunday evening instead of Monday morning.",
    )
}
