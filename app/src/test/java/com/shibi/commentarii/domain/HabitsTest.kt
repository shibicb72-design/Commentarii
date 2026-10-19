package com.shibi.commentarii.domain

import com.shibi.commentarii.data.SelfCareCheckEntity
import com.shibi.commentarii.data.SelfCareItemEntity
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HabitsTest {
    private val water = SelfCareItemEntity(id = 1, name = "Water", position = 0)
    private val stretch = SelfCareItemEntity(id = 2, name = "Stretch", position = 1)
    private val items = listOf(water, stretch)

    private fun check(item: Long, y: Int, m: Int, d: Int) = SelfCareCheckEntity(item, LocalDate.of(y, m, d))

    @Test
    fun weekGridAlignsChecksWithDays() {
        val days = weekDays(LocalDate.of(2026, 10, 12))
        val checks = listOf(check(1, 2026, 10, 12), check(1, 2026, 10, 14), check(2, 2026, 10, 18), check(1, 2026, 10, 19))
        val grid = weekGrid(items, checks, days)
        assertEquals(listOf(true, false, true, false, false, false, false), grid[1])
        assertEquals(listOf(false, false, false, false, false, false, true), grid[2])
    }

    @Test
    fun weekGridHasARowForEveryItemEvenWithoutChecks() {
        val grid = weekGrid(items, emptyList(), weekDays(LocalDate.of(2026, 10, 12)))
        assertEquals(setOf(1L, 2L), grid.keys)
        assertEquals(List(7) { false }, grid[1])
    }

    @Test
    fun pastMonthUsesTheWholeMonthAsDenominator() {
        val checks = listOf(check(1, 2026, 9, 1), check(1, 2026, 9, 30), check(2, 2026, 9, 15))
        val summary = monthSummary(items, checks, YearMonth.of(2026, 9), LocalDate.of(2026, 10, 18))
        assertEquals(listOf(HabitSummary(1, 2, 30), HabitSummary(2, 1, 30)), summary)
    }

    @Test
    fun currentMonthUsesDaysElapsedIncludingToday() {
        val checks = listOf(check(1, 2026, 10, 1), check(1, 2026, 10, 18), check(1, 2026, 10, 20))
        val summary = monthSummary(items, checks, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 18))
        assertEquals(HabitSummary(1, 2, 18), summary[0])
        assertEquals(HabitSummary(2, 0, 18), summary[1])
    }

    @Test
    fun futureMonthHasNoDenominator() {
        val checks = listOf(check(1, 2026, 11, 3))
        val summary = monthSummary(items, checks, YearMonth.of(2026, 11), LocalDate.of(2026, 10, 18))
        assertNull(summary[0].denominator)
        assertEquals(0, summary[0].done)
    }

    @Test
    fun checksOutsideTheMonthAreIgnored() {
        val checks = listOf(check(1, 2026, 9, 30), check(1, 2026, 11, 1), check(1, 2025, 10, 5), check(1, 2026, 10, 5))
        val summary = monthSummary(listOf(water), checks, YearMonth.of(2026, 10), LocalDate.of(2026, 10, 31))
        assertEquals(HabitSummary(1, 1, 31), summary.single())
    }
}
