package com.shibi.commentarii.domain

import com.shibi.commentarii.data.SelfCareCheckEntity
import com.shibi.commentarii.data.SelfCareItemEntity
import java.time.LocalDate
import java.time.YearMonth

/** Checked days of one habit in a month. [denominator] is null for a future month (shown as "-"). */
data class HabitSummary(val itemId: Long, val done: Int, val denominator: Int?)

/** For each item, one flag per entry of [days] telling whether it was checked that day. */
fun weekGrid(
    items: List<SelfCareItemEntity>,
    checks: List<SelfCareCheckEntity>,
    days: List<LocalDate>,
): Map<Long, List<Boolean>> {
    val checked = checks.map { it.itemId to it.date }.toSet()
    return items.associate { item -> item.id to days.map { (item.id to it) in checked } }
}

/**
 * Per-habit totals for [month]. Denominator: past month = its length, current month = days elapsed including
 * [today], future month = null. Checks outside the month (or after [today]) are not counted.
 */
fun monthSummary(
    items: List<SelfCareItemEntity>,
    checks: List<SelfCareCheckEntity>,
    month: YearMonth,
    today: LocalDate,
): List<HabitSummary> {
    val current = YearMonth.from(today)
    val denominator: Int? = when {
        month < current -> month.lengthOfMonth()
        month == current -> today.dayOfMonth
        else -> null
    }
    val last = if (month == current) today else month.atEndOfMonth()
    val inRange = checks.filter { YearMonth.from(it.date) == month && !it.date.isAfter(last) }
    return items.map { item ->
        HabitSummary(item.id, if (denominator == null) 0 else inRange.count { it.itemId == item.id }, denominator)
    }
}
