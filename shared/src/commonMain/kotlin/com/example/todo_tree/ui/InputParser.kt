// =============================================================================
//  INPUT_PARSER.KT
//  Natural-language input parsing: #category/#project/#cat/#proj tokens,
//  parent refs via #word, #removecat/#rmcat, #moveto/#mt,
//  Todoist-style date expressions.
// =============================================================================

package com.example.todo_tree.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.todo_tree.currentTimeMillis
import com.example.todo_tree.model.DAY_MS
import com.example.todo_tree.model.Item

sealed class InputCommand {
    data class AddTask(
        val title: String,
        val doDate: Long? = null,
        val dueDate: Long? = null,
        val item: Item = Item.Task(),
        val parentRef: String? = null,
    ) : InputCommand()

    data class RemoveCategory(val title: String) : InputCommand()

    data class MoveTask(val targetTitle: String) : InputCommand()
}

private val epochDays: Long get() = currentTimeMillis() / DAY_MS

private val dow: Int get() = (epochDays % 7).toInt()

private fun dayOfWeek(): Int = when (dow) {
    4 -> 1  // Mon
    5 -> 2  // Tue
    6 -> 3  // Wed
    0 -> 4  // Thu
    1 -> 5  // Fri
    2 -> 6  // Sat
    3 -> 7  // Sun
    else -> 0
}

private val weekdays = mapOf(
    "monday" to 1, "mon" to 1,
    "tuesday" to 2, "tue" to 2,
    "wednesday" to 3, "wed" to 3,
    "thursday" to 4, "thu" to 4,
    "friday" to 5, "fri" to 5,
    "saturday" to 6, "sat" to 6,
    "sunday" to 7, "sun" to 7,
)

private val monthNames = listOf(
    "january", "jan", "february", "feb", "march", "mar",
    "april", "apr", "may", "june", "jun", "july", "jul",
    "august", "aug", "september", "sep", "sept", "october", "oct",
    "november", "nov", "december", "dec",
)

private val months = mapOf(
    "january" to 1, "jan" to 1,
    "february" to 2, "feb" to 2,
    "march" to 3, "mar" to 3,
    "april" to 4, "apr" to 4,
    "may" to 5,
    "june" to 6, "jun" to 6,
    "july" to 7, "jul" to 7,
    "august" to 8, "aug" to 8,
    "september" to 9, "sep" to 9, "sept" to 9,
    "october" to 10, "oct" to 10,
    "november" to 11, "nov" to 11,
    "december" to 12, "dec" to 12,
)

private val monthDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

// ==== Calendar math ====

private fun isLeapYear(y: Long) = (y % 4L == 0L && y % 100L != 0L) || (y % 400L == 0L)

// Break epoch days into (year, month, day)
private fun epochToYmd(days: Long): Triple<Long, Int, Int> {
    var y = 1970L; var r = days
    while (true) { val d = if (isLeapYear(y)) 366L else 365L; if (r < d) break; r -= d; y++ }
    val md = if (isLeapYear(y)) intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31) else monthDays
    var m = 1; for (dm in md) { if (r < dm) break; r -= dm; m++ }
    return Triple(y, m, (r + 1).toInt())
}

private fun ymdToEpochDays(year: Long, month: Int, day: Int): Long {
    var total = 0L
    for (y in 1970L until year) total += if (isLeapYear(y)) 366L else 365L
    val md = if (isLeapYear(year)) intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31) else monthDays
    for (m in 0 until month - 1) total += md[m]
    return total + day - 1
}

private fun nextDayMonth(day: Int, month: Int, todayStart: Long): Long? {
    if (day < 1 || day > 31 || month < 1 || month > 12) return null
    val (year, curMonth, curDay) = epochToYmd(epochDays)
    val maxDay = if (month == 2 && isLeapYear(year)) 29 else monthDays[month - 1]
    if (day > maxDay) return null
    if (month > curMonth || (month == curMonth && day >= curDay)) {
        return ymdToEpochDays(year, month, day) * DAY_MS
    }
    val nextMax = if (month == 2 && isLeapYear(year + 1)) 29 else monthDays[month - 1]
    if (day > nextMax) return null
    return ymdToEpochDays(year + 1, month, day) * DAY_MS
}

// Find the next occurrence of a given day, searching forward month by month.
private fun nextDay(day: Int, todayStart: Long): Long? {
    if (day < 1 || day > 31) return null
    val (year, curMonth, curDay) = epochToYmd(epochDays)
    // Try current month if day is today or future
    val curMax = if (curMonth == 2 && isLeapYear(year)) 29 else monthDays[curMonth - 1]
    if (day <= curMax && day >= curDay) {
        return ymdToEpochDays(year, curMonth, day) * DAY_MS
    }
    // Try remaining months this year
    for (m in (curMonth + 1)..12) {
        val max = if (m == 2 && isLeapYear(year)) 29 else monthDays[m - 1]
        if (day <= max) return ymdToEpochDays(year, m, day) * DAY_MS
    }
    // Try next year
    for (m in 1..12) {
        val max = if (m == 2 && isLeapYear(year + 1)) 29 else monthDays[m - 1]
        if (day <= max) return ymdToEpochDays(year + 1, m, day) * DAY_MS
    }
    return null
}

// ==== Date expression parsing ====

private fun parseDateExpression(expr: String, todayStart: Long): Long? {
    val lower = expr.lowercase().trim()

    when (lower) {
        "today", "t" -> return todayStart
        "tomorrow", "tmr", "tmrw" -> return todayStart + DAY_MS
        "next week", "nxt" -> return todayStart + 7 * DAY_MS
        "next month" -> return todayStart + 30 * DAY_MS
        "next year" -> return todayStart + 365 * DAY_MS
    }

    val nextDay = Regex("""(?:next|nxt)\s+(\w+)""", RegexOption.IGNORE_CASE).matchEntire(lower)
    if (nextDay != null) {
        val day = weekdays[nextDay.groupValues[1].lowercase()]
        if (day != null) {
            var diff = day - dayOfWeek()
            if (diff <= 0) diff += 7
            return todayStart + diff * DAY_MS
        }
        return null
    }

    val bare = weekdays[lower]
    if (bare != null) {
        var diff = bare - dayOfWeek()
        if (diff <= 0) diff += 7
        return todayStart + diff * DAY_MS
    }

    val inExpr = Regex("""in\s+(\d+)\s+(day|week|month)s?""", RegexOption.IGNORE_CASE).matchEntire(lower)
    if (inExpr != null) {
        val num = inExpr.groupValues[1].toIntOrNull() ?: return null
        val mult = when (inExpr.groupValues[2].lowercase()) {
            "day" -> 1
            "week" -> 7
            "month" -> 30
            else -> return null
        }
        return todayStart + num * mult * DAY_MS
    }

    val shortIn = Regex("""\+?(\d+)([dwm])(?:ays?|eeks?|onths?)?""", RegexOption.IGNORE_CASE).matchEntire(lower)
    if (shortIn != null) {
        val num = shortIn.groupValues[1].toIntOrNull() ?: return null
        val mult = when (shortIn.groupValues[2].lowercase()) {
            "d" -> 1
            "w" -> 7
            "m" -> 30
            else -> return null
        }
        return todayStart + num * mult * DAY_MS
    }

    val ordMatch = Regex("""(\d+)(?:st|nd|rd|th)?""", RegexOption.IGNORE_CASE).matchEntire(lower)
    if (ordMatch != null) {
        val day = ordMatch.groupValues[1].toIntOrNull()
        if (day != null && day in 1..31) {
            val (_, curMonth, curDay) = epochToYmd(epochDays)
            return nextDayMonth(day, curMonth, todayStart)
        }
    }

    val nextOrd = Regex("""(?:next|nxt)\s+(\d+)(?:st|nd|rd|th)?""", RegexOption.IGNORE_CASE).matchEntire(lower)
    if (nextOrd != null) {
        val day = nextOrd.groupValues[1].toIntOrNull()
        if (day != null && day in 1..31) {
            val (_, curMonth, _) = epochToYmd(epochDays)
            val nextMonth = if (curMonth == 12) 1 else curMonth + 1
            val nextYear = if (curMonth == 12) epochToYmd(epochDays).first + 1 else epochToYmd(epochDays).first
            val maxDay = if (nextMonth == 2 && isLeapYear(nextYear)) 29 else monthDays[nextMonth - 1]
            if (day <= maxDay) return ymdToEpochDays(nextYear, nextMonth, day) * DAY_MS
        }
    }

    val monthDay = Regex("""(${monthNames.joinToString("|")})\s+(\d+)(?:st|nd|rd|th)?""", RegexOption.IGNORE_CASE).matchEntire(lower)
    if (monthDay != null) {
        val month = months[monthDay.groupValues[1].lowercase()]
        val day = monthDay.groupValues[2].toIntOrNull()
        if (month != null && day != null) return nextDayMonth(day, month, todayStart)
    }
    val dayMonth = Regex("""(\d+)(?:st|nd|rd|th)?\s+(${monthNames.joinToString("|")})""", RegexOption.IGNORE_CASE).matchEntire(lower)
    if (dayMonth != null) {
        val day = dayMonth.groupValues[1].toIntOrNull()
        val month = months[dayMonth.groupValues[2].lowercase()]
        if (month != null && day != null) return nextDayMonth(day, month, todayStart)
    }

    return null
}

// ==== #token scanning ====

private data class HashScan(val stripped: String, val item: Item, val parentRef: String?, val removeCatTitle: String?, val moveTarget: String?)

private fun scanHashTokens(input: String): HashScan {
    var text = input
    var item: Item = Item.Task()
    var parentRef: String? = null
    var removeCatTitle: String? = null
    var moveTarget: String? = null
    val hashPattern = Regex("""(?:^|\s+)#(\w+)""")
    while (true) {
        val m = hashPattern.find(text) ?: break
        val token = m.groupValues[1].lowercase()
        when (token) {
            "category", "cat" -> item = Item.Category
            "project", "proj" -> item = Item.Project()
            "removecat", "rmcat" -> removeCatTitle = text.removeRange(m.range).trim()
            "moveto", "mt" -> moveTarget = text.removeRange(m.range).trim()
            else -> if (parentRef == null) parentRef = m.groupValues[1]
        }
        text = text.removeRange(m.range)
        text = text.trim()
    }
    return HashScan(text, item, parentRef, removeCatTitle, moveTarget)
}

// ==== Full input parsing ====

fun parseTaskInput(input: String): InputCommand {
    val trimmed = input.trim()
    if (trimmed.isBlank()) return InputCommand.AddTask(trimmed)

    val scanned = scanHashTokens(trimmed)
    val text = scanned.stripped
    if (scanned.removeCatTitle != null) return InputCommand.RemoveCategory(scanned.removeCatTitle)
    if (scanned.moveTarget != null) return InputCommand.MoveTask(scanned.moveTarget)
    if (text.isBlank()) return InputCommand.AddTask(text, item = scanned.item, parentRef = scanned.parentRef)

    val todayStart = epochDays * DAY_MS

    val doMatch = Regex("""^(.+?)\s+do\s+(.+)$""", RegexOption.IGNORE_CASE).matchEntire(text)
    if (doMatch != null) {
        val title = doMatch.groupValues[1].trim()
        val dateExpr = doMatch.groupValues[2].trim()
        val date = parseDateExpression(dateExpr, todayStart)
        if (date != null) return InputCommand.AddTask(title, doDate = date, item = scanned.item, parentRef = scanned.parentRef)
    }

    val dueMatch = Regex("""^(.+?)\s+due\s+(.+)$""", RegexOption.IGNORE_CASE).matchEntire(text)
    if (dueMatch != null) {
        val title = dueMatch.groupValues[1].trim()
        val dateExpr = dueMatch.groupValues[2].trim()
        val date = parseDateExpression(dateExpr, todayStart)
        if (date != null) return InputCommand.AddTask(title, dueDate = date, item = scanned.item, parentRef = scanned.parentRef)
    }

    val wordEnd = Regex(
        """^(.+?)\s+(today|tomorrow|next\s+\w+|in\s+\d+\s+\w+|mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)$""",
        RegexOption.IGNORE_CASE,
    ).find(text)
    if (wordEnd != null) {
        val date = parseDateExpression(wordEnd.groupValues[2].trim(), todayStart)
        if (date != null) return InputCommand.AddTask(wordEnd.groupValues[1].trim(), doDate = date, item = scanned.item, parentRef = scanned.parentRef)
    }

    val shortEnd = Regex(
        """^(.+?)\s+(t(?:oday)?|tmr?w?|nxt(?:\s+\w+)?|\+?\d+[dwm](?:ays?|eeks?|onths?)?)$""",
        RegexOption.IGNORE_CASE,

    ).find(text)
    if (shortEnd != null) {
        val date = parseDateExpression(shortEnd.groupValues[2].trim(), todayStart)
        if (date != null) return InputCommand.AddTask(shortEnd.groupValues[1].trim(), doDate = date, item = scanned.item, parentRef = scanned.parentRef)
    }

    val monthDayEnd = Regex(
        """^(.+?)\s+(${monthNames.joinToString("|")})\s+(\d+)(?:st|nd|rd|th)?$""",
        RegexOption.IGNORE_CASE,
    ).find(text)
    if (monthDayEnd != null) {
        val month = months[monthDayEnd.groupValues[2].lowercase()]
        val day = monthDayEnd.groupValues[3].toIntOrNull()
        if (month != null && day != null) {
            val date = nextDayMonth(day, month, todayStart)
            if (date != null) return InputCommand.AddTask(monthDayEnd.groupValues[1].trim(), doDate = date, item = scanned.item, parentRef = scanned.parentRef)
        }
    }

    val dayMonthEnd = Regex(
        """^(.+?)\s+(\d+)(?:st|nd|rd|th)?\s+(${monthNames.joinToString("|")})$""",
        RegexOption.IGNORE_CASE,
    ).find(text)
    if (dayMonthEnd != null) {
        val day = dayMonthEnd.groupValues[2].toIntOrNull()
        val month = months[dayMonthEnd.groupValues[3].lowercase()]
        if (month != null && day != null) {
            val date = nextDayMonth(day, month, todayStart)
            if (date != null) return InputCommand.AddTask(dayMonthEnd.groupValues[1].trim(), doDate = date, item = scanned.item, parentRef = scanned.parentRef)
        }
    }

    val monthDayMiddle = Regex(
        """^(.+?)\s+(${monthNames.joinToString("|")})\s+(\d+)(?:st|nd|rd|th)?\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    ).find(text)
    if (monthDayMiddle != null) {
        val month = months[monthDayMiddle.groupValues[2].lowercase()]
        val day = monthDayMiddle.groupValues[3].toIntOrNull()
        val title = "${monthDayMiddle.groupValues[1].trim()} - ${monthDayMiddle.groupValues[4].trim()}"
        if (month != null && day != null) {
            val date = nextDayMonth(day, month, todayStart)
            if (date != null) return InputCommand.AddTask(title, doDate = date, item = scanned.item, parentRef = scanned.parentRef)
        }
    }

    val dayMonthMiddle = Regex(
        """^(.+?)\s+(\d+)(?:st|nd|rd|th)?\s+(${monthNames.joinToString("|")})\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    ).find(text)
    if (dayMonthMiddle != null) {
        val day = dayMonthMiddle.groupValues[2].toIntOrNull()
        val month = months[dayMonthMiddle.groupValues[3].lowercase()]
        val title = "${dayMonthMiddle.groupValues[1].trim()} - ${dayMonthMiddle.groupValues[4].trim()}"
        if (month != null && day != null) {
            val date = nextDayMonth(day, month, todayStart)
            if (date != null) return InputCommand.AddTask(title, doDate = date, item = scanned.item, parentRef = scanned.parentRef)
        }
    }

    val monthDayMid = Regex(
        """^(${monthNames.joinToString("|")})\s+(\d+)(?:st|nd|rd|th)?\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    ).find(text)
    if (monthDayMid != null) {
        val month = months[monthDayMid.groupValues[1].lowercase()]
        val day = monthDayMid.groupValues[2].toIntOrNull()
        if (month != null && day != null) {
            val date = nextDayMonth(day, month, todayStart)
            if (date != null) return InputCommand.AddTask(monthDayMid.groupValues[3].trim(), doDate = date, item = scanned.item, parentRef = scanned.parentRef)
        }
    }

    val dayMonthMid = Regex(
        """^(\d+)(?:st|nd|rd|th)?\s+(${monthNames.joinToString("|")})\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    ).find(text)
    if (dayMonthMid != null) {
        val day = dayMonthMid.groupValues[1].toIntOrNull()
        val month = months[dayMonthMid.groupValues[2].lowercase()]
        if (month != null && day != null) {
            val date = nextDayMonth(day, month, todayStart)
            if (date != null) return InputCommand.AddTask(dayMonthMid.groupValues[3].trim(), doDate = date, item = scanned.item, parentRef = scanned.parentRef)
        }
    }

    val ordEnd = Regex(
        """^(.+?)\s+(\d+)(?:st|nd|rd|th)?$""",
        RegexOption.IGNORE_CASE,
    ).find(text)
    if (ordEnd != null) {
        val day = ordEnd.groupValues[2].toIntOrNull()
        if (day != null && day in 1..31) {
            val date = nextDay(day, todayStart)
            if (date != null) return InputCommand.AddTask(ordEnd.groupValues[1].trim(), doDate = date, item = scanned.item, parentRef = scanned.parentRef)
        }
    }

    return InputCommand.AddTask(text, item = scanned.item, parentRef = scanned.parentRef)
}

// ==== Live highlight support ====

private val dateSuffixPatterns = listOf(
    Regex("""\s+(do|due)\s+(.+?)\s*$""", RegexOption.IGNORE_CASE),
    Regex("""\s+(today|tomorrow|next\s+\w+|mon(?:day)?|tue(?:sday)?|wed(?:nesday)?|thu(?:rsday)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\s*$""", RegexOption.IGNORE_CASE),
    Regex("""\s+(t(?:oday)?|tmr?w?|nxt(?:\s+\w+)?|\+?\d+[dwm](?:ays?|eeks?|onths?)?)\s*$""", RegexOption.IGNORE_CASE),
    Regex("""\s+(${monthNames.joinToString("|")})\s+\d+(?:st|nd|rd|th)?\s*$""", RegexOption.IGNORE_CASE),
    Regex("""\s+\d+(?:st|nd|rd|th)?\s+(${monthNames.joinToString("|")})\s*$""", RegexOption.IGNORE_CASE),
    Regex("""\s+\d+(?:st|nd|rd|th)?\s*$"""),
)

private val dateMidPatterns = listOf(
    Regex("""(?:^|\s+)(${monthNames.joinToString("|")})\s+\d+(?:st|nd|rd|th)?(?=\s|$)""", RegexOption.IGNORE_CASE),
    Regex("""(?:^|\s+)(\d+(?:st|nd|rd|th)?)\s+(${monthNames.joinToString("|")})(?=\s|$)""", RegexOption.IGNORE_CASE),
)

fun findDateSuffix(text: String): IntRange? {
    val t = text.trimEnd()
    if (t.isEmpty()) return null
    val offset = text.length - t.length
    for (pattern in dateSuffixPatterns) {
        val m = pattern.find(t) ?: continue
        return (offset + m.range.first)..(offset + m.range.last)
    }
    return null
}

private fun findAllDateRanges(text: String): List<IntRange> {
    val result = mutableListOf<IntRange>()
    // Suffix patterns (end of string) — return at most one
    val t = text.trimEnd()
    if (t.isNotEmpty()) {
        val offset = text.length - t.length
        for (pattern in dateSuffixPatterns) {
            val m = pattern.find(t) ?: continue
            result.add((offset + m.range.first)..(offset + m.range.last))
            break
        }
    }
    // Mid/prefix patterns — return all matches not overlapping with suffix
    if (result.isEmpty()) {
        for (pattern in dateMidPatterns) {
            var start = 0
            while (true) {
                val m = pattern.find(text, start) ?: break
                result.add(m.range)
                start = m.range.last + 1
            }
        }
    }
    return result
}

private fun findAllHashTokens(text: String): List<IntRange> {
    val result = mutableListOf<IntRange>()
    val pattern = Regex("""(?:^|\s+)#(\w+)""")
    var m = pattern.find(text)
    while (m != null) {
        result.add(m.range)
        m = pattern.find(text, m.range.last + 1)
    }
    return result
}

private val highlightColor = Color(0xFF569CD6)

fun dateHighlightTransformation(): VisualTransformation = VisualTransformation { text ->
    val dateRanges = findAllDateRanges(text.text)
    val hashRanges = findAllHashTokens(text.text)
    val allRanges = dateRanges + hashRanges
    if (allRanges.isNotEmpty()) {
        val annotated = buildAnnotatedString {
            append(text.text)
            for (range in allRanges) {
                addStyle(SpanStyle(color = highlightColor), range.first, range.last + 1)
            }
        }
        TransformedText(annotated, OffsetMapping.Identity)
    } else {
        TransformedText(text, OffsetMapping.Identity)
    }
}
