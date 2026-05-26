// =============================================================================
//  DATE_HELPERS.KT
//  Shared calendar math: leap year, epoch↔date conversion, day-in-month.
//  Consolidates duplicate implementations from InputParser and TaskSheet.
// =============================================================================

package com.example.todo_tree.ui

import com.example.todo_tree.currentTimeMillis
import com.example.todo_tree.model.DAY_MS

private val MONTH_DAYS = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

internal fun isLeapYear(year: Long): Boolean =
    (year % 4L == 0L && year % 100L != 0L) || (year % 400L == 0L)

internal fun daysInMonth(year: Long, month: Int): Int =
    if (month == 2 && isLeapYear(year)) 29 else MONTH_DAYS[month - 1]

internal data class YearMonthDay(val year: Long, val month: Int, val day: Int)

internal fun epochDaysToYmd(epochDays: Long): YearMonthDay {
    var y = 1970L; var r = epochDays
    while (true) { val d = if (isLeapYear(y)) 366L else 365L; if (r < d) break; r -= d; y++ }
    val md = if (isLeapYear(y)) intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31) else MONTH_DAYS
    var m = 1; for (dm in md) { if (r < dm) break; r -= dm; m++ }
    return YearMonthDay(y, m, (r + 1).toInt())
}

internal fun ymdToEpochDays(year: Long, month: Int, day: Int): Long {
    var total = 0L
    for (y in 1970L until year) total += if (isLeapYear(y)) 366L else 365L
    val md = if (isLeapYear(year)) intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31) else MONTH_DAYS
    for (m in 0 until month - 1) total += md[m]
    return total + day - 1
}

internal fun nextDayMonth(day: Int, month: Int, todayStart: Long): Long? {
    if (day < 1 || day > 31 || month < 1 || month > 12) return null
    val nowDays = currentTimeMillis() / DAY_MS
    val (year, curMonth, curDay) = epochDaysToYmd(nowDays)
    if (day > daysInMonth(year, month)) return null
    if (month > curMonth || (month == curMonth && day >= curDay)) {
        return ymdToEpochDays(year, month, day) * DAY_MS
    }
    if (day > daysInMonth(year + 1, month)) return null
    return ymdToEpochDays(year + 1, month, day) * DAY_MS
}

internal fun nextDay(day: Int, todayStart: Long): Long? {
    if (day < 1 || day > 31) return null
    val nowDays = currentTimeMillis() / DAY_MS
    val (year, curMonth, curDay) = epochDaysToYmd(nowDays)
    val curMax = daysInMonth(year, curMonth)
    if (day <= curMax && day >= curDay) {
        return ymdToEpochDays(year, curMonth, day) * DAY_MS
    }
    for (m in (curMonth + 1)..12) {
        if (day <= daysInMonth(year, m)) return ymdToEpochDays(year, m, day) * DAY_MS
    }
    for (m in 1..12) {
        if (day <= daysInMonth(year + 1, m)) return ymdToEpochDays(year + 1, m, day) * DAY_MS
    }
    return null
}
