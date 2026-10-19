package com.shibi.commentarii.domain

import com.shibi.commentarii.data.SelfCareCheckEntity
import com.shibi.commentarii.data.SelfCareItemEntity
import java.time.LocalDate

/** Consecutive checked days ending today, or ending yesterday if today is not checked yet; else 0. */
fun currentStreak(checkedDates: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in checkedDates) today else today.minusDays(1)
    var count = 0
    while (day in checkedDates) {
        count++
        day = day.minusDays(1)
    }
    return count
}

fun streaksFor(
    items: List<SelfCareItemEntity>,
    checks: List<SelfCareCheckEntity>,
    today: LocalDate,
): Map<Long, Int> {
    val byItem = checks.groupBy({ it.itemId }, { it.date }).mapValues { it.value.toSet() }
    return items.associate { it.id to currentStreak(byItem[it.id].orEmpty(), today) }
}
