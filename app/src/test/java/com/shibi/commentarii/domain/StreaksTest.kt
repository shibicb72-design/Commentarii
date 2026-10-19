package com.shibi.commentarii.domain

import com.shibi.commentarii.data.SelfCareCheckEntity
import com.shibi.commentarii.data.SelfCareItemEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StreaksTest {
    private val today = LocalDate.of(2026, 10, 18)
    private fun days(vararg back: Long) = back.map { today.minusDays(it) }.toSet()

    @Test
    fun todayChecked() = assertEquals(3, currentStreak(days(0, 1, 2), today))

    @Test
    fun onlyYesterdayChecked() = assertEquals(2, currentStreak(days(1, 2), today))

    @Test
    fun gapEndsStreak() = assertEquals(1, currentStreak(days(0, 2, 3), today))

    @Test
    fun gapBeforeYesterdayMeansZero() = assertEquals(0, currentStreak(days(2, 3), today))

    @Test
    fun noneIsZero() = assertEquals(0, currentStreak(emptySet(), today))

    @Test
    fun longStreak() = assertEquals(100, currentStreak((0L until 100L).map { today.minusDays(it) }.toSet(), today))

    @Test
    fun streaksForItems() {
        val items = listOf(SelfCareItemEntity(1, "water", 0), SelfCareItemEntity(2, "walk", 1), SelfCareItemEntity(3, "read", 2))
        val checks = listOf(
            SelfCareCheckEntity(1, today), SelfCareCheckEntity(1, today.minusDays(1)),
            SelfCareCheckEntity(2, today.minusDays(1)),
        )
        assertEquals(mapOf(1L to 2, 2L to 1, 3L to 0), streaksFor(items, checks, today))
    }
}
