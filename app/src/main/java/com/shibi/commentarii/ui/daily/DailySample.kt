package com.shibi.commentarii.ui.daily

import java.time.LocalDate

/** Fully filled-in state for screenshot tests and previews. */
fun sampleDailyUiState(date: LocalDate = LocalDate.of(2026, 10, 18)): DailyUiState {
    var k = 1L
    fun t(text: String, done: Boolean = false) = TaskUi(key = k++, text = text, done = done)
    val hours = (6..22).map { h ->
        val text = when (h) {
            7 -> "Morning walk"
            9 -> "Team stand-up"
            12 -> "Lunch with Maya"
            15 -> "Dentist"
            18 -> "Yoga class"
            else -> ""
        }
        ScheduleRowUi(key = k++, minuteOfDay = h * 60, text = text)
    }
    val custom = ScheduleRowUi(key = k++, minuteOfDay = 7 * 60 + 35, text = "Catch the bus", isCustom = true)
    return DailyUiState(
        date = date,
        today = date,
        pinned = true,
        folders = listOf(ChipUi(1, "Personal"), ChipUi(2, "Work")),
        folderId = 1,
        tags = listOf(ChipUi(1, "focus"), ChipUi(2, "family")),
        priorities = listOf(t("Finish the report", done = true), t("Call the bank"), t("Buy groceries")),
        todo = listOf(t("Reply to emails", done = true), t("Water the plants"), t("Book flights"), t("Tidy the desk")),
        schedule = (hours + custom).sortedWith(compareBy({ it.minuteOfDay }, { it.isCustom })),
        dontForget = listOf(t("Mom's birthday gift"), t("Library books due"), t("Take vitamins")),
        mood = 4,
        notes = "Slept well. Remember to follow up on the invoice and plan the weekend trip.",
    )
}
