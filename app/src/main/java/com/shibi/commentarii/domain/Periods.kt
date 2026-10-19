package com.shibi.commentarii.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

/** ISO week-based year and week number (2026-12-31 is week 53 of 2026, 2027-01-04 is week 1 of 2027). */
data class IsoWeek(val year: Int, val week: Int)

/** One square of the month calendar; [inMonth] is false for the padding days of the neighbouring months. */
data class GridCell(val date: LocalDate, val inMonth: Boolean)

/** The Monday of the week containing [d] (a Sunday maps to the Monday before it). */
fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

/** Monday..Sunday of the week starting on [monday]. */
fun weekDays(monday: LocalDate): List<LocalDate> = (0L..6L).map { monday.plusDays(it) }

fun isoWeek(d: LocalDate): IsoWeek = IsoWeek(d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))

/** Monday-first calendar of [month], padded with neighbouring days to whole weeks (4 to 6 rows of 7). */
fun monthGrid(month: YearMonth): List<GridCell> {
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val start = weekStart(first)
    val end = last.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val days = end.toEpochDay() - start.toEpochDay() + 1
    return (0 until days).map { i ->
        val d = start.plusDays(i)
        GridCell(d, YearMonth.from(d) == month)
    }
}

/** Route argument `yyyy-MM-dd` snapped to its Monday; missing or invalid input falls back to the week of [today]. */
fun parseWeekRoute(arg: String?, today: LocalDate): LocalDate =
    weekStart(arg?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today)

/** Route argument `yyyy-MM`; missing or invalid input falls back to the month of [today]. */
fun parseMonthRoute(arg: String?, today: LocalDate): YearMonth =
    arg?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: YearMonth.from(today)
