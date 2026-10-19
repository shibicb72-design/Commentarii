package com.shibi.commentarii.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodsTest {
    @Test
    fun yearEndWeekIsIsoWeek53() {
        val monday = LocalDate.of(2026, 12, 28)
        assertEquals(monday, weekStart(LocalDate.of(2026, 12, 31)))
        assertEquals(IsoWeek(2026, 53), isoWeek(LocalDate.of(2026, 12, 31)))
        assertEquals(monday, weekStart(LocalDate.of(2027, 1, 3)))
        assertEquals(IsoWeek(2026, 53), isoWeek(LocalDate.of(2027, 1, 3)))
        assertEquals(LocalDate.of(2027, 1, 4), weekStart(LocalDate.of(2027, 1, 4)))
        assertEquals(IsoWeek(2027, 1), isoWeek(LocalDate.of(2027, 1, 4)))
    }

    @Test
    fun sundayMapsToThePreviousMonday() {
        val sunday = LocalDate.of(2026, 10, 18)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 12), weekStart(sunday))
        assertEquals(LocalDate.of(2026, 10, 12), weekStart(LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun weekDaysRunMondayToSunday() {
        val days = weekDays(LocalDate.of(2026, 10, 12))
        assertEquals(7, days.size)
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 18), days.last())
    }

    @Test
    fun leapFebruaryHasFiveRows() {
        val grid = monthGrid(YearMonth.of(2028, 2))
        assertEquals(35, grid.size)
        assertEquals(29, grid.count { it.inMonth })
        assertEquals(LocalDate.of(2028, 1, 31), grid.first().date)
        assertFalse(grid.first().inMonth)
        assertEquals(LocalDate.of(2028, 3, 5), grid.last().date)
        assertTrue(grid.contains(GridCell(LocalDate.of(2028, 2, 29), true)))
    }

    @Test
    fun monthStartingOnMondayHasNoLeadingDays() {
        val grid = monthGrid(YearMonth.of(2026, 6))
        assertEquals(LocalDate.of(2026, 6, 1), grid.first().date)
        assertTrue(grid.first().inMonth)
        assertEquals(35, grid.size)
        assertEquals(LocalDate.of(2026, 7, 5), grid.last().date)
        assertFalse(grid.last().inMonth)
    }

    @Test
    fun monthStartingOnSundayHasSixLeadingDaysAndSixRows() {
        val grid = monthGrid(YearMonth.of(2026, 11))
        assertEquals(42, grid.size)
        assertEquals(6, grid.takeWhile { !it.inMonth }.size)
        assertEquals(LocalDate.of(2026, 10, 26), grid.first().date)
        assertEquals(30, grid.count { it.inMonth })
        assertEquals(6, grid.takeLastWhile { !it.inMonth }.size)
        assertEquals(LocalDate.of(2026, 12, 6), grid.last().date)
        assertTrue(grid.chunked(7).all { it.first().date.dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test
    fun everyGridHasWholeWeeks() {
        for (m in 1..12) {
            val grid = monthGrid(YearMonth.of(2026, m))
            assertEquals(0, grid.size % 7)
            assertTrue(grid.size / 7 in 4..6)
        }
    }

    @Test
    fun weekRouteSnapsToMondayAndFallsBackOnBadInput() {
        val today = LocalDate.of(2026, 10, 18)
        assertEquals(LocalDate.of(2026, 10, 12), parseWeekRoute("2026-10-14", today))
        assertEquals(LocalDate.of(2026, 10, 12), parseWeekRoute("2026-10-12", today))
        assertEquals(LocalDate.of(2026, 10, 12), parseWeekRoute("garbage", today))
        assertEquals(LocalDate.of(2026, 10, 12), parseWeekRoute("2026-13-40", today))
        assertEquals(LocalDate.of(2026, 10, 12), parseWeekRoute(null, today))
    }

    @Test
    fun monthRouteFallsBackOnBadInput() {
        val today = LocalDate.of(2026, 10, 18)
        assertEquals(YearMonth.of(2027, 2), parseMonthRoute("2027-02", today))
        assertEquals(YearMonth.of(2026, 10), parseMonthRoute("2027-2", today))
        assertEquals(YearMonth.of(2026, 10), parseMonthRoute("2027-13", today))
        assertEquals(YearMonth.of(2026, 10), parseMonthRoute("nope", today))
        assertEquals(YearMonth.of(2026, 10), parseMonthRoute(null, today))
    }
}
