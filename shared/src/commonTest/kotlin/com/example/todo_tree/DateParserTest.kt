// =============================================================================
//  DATE_PARSER_TEST.KT
//  Example-based + invariant tests for NLP date parsing and #command syntax.
//
//  The DateParser is a complex NLP engine with specific grammar rules.
//  Property-based invariants cover broad categories (empty, plain text, known
//  tokens); example-based tests cover the specific date expression formats.
// =============================================================================

package com.example.todo_tree

import com.example.todo_tree.model.Item
import androidx.compose.ui.text.buildAnnotatedString
import com.example.todo_tree.arbString
import com.example.todo_tree.ui.dateHighlightTransformation
import com.example.todo_tree.ui.parseTaskInput
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.checkAll
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DateParserTest : FunSpec({

    // ==== Property-based invariants ====

    test("empty and blank input produce empty title") {
        checkAll(arbString(0, 0)) { input ->
            val r = parseTaskInput(input)
            assertEquals("", r.title)
            assertNull(r.doDate)
            assertNull(r.dueDate)
            assertNull(r.parentRef)
            assertNull(r.removeCatTitle)
            assertNull(r.moveTarget)
            assertTrue(r.item is Item.Task)
        }
    }

    test("input without # or date tokens returns title unchanged") {
        checkAll(
            arbString(1, 30),
        ) { input ->
            val clean = input.trim().takeIf { it.isNotBlank() } ?: return@checkAll
            // Skip inputs that happen to contain hash or date-looking content
            if ("#" in clean) return@checkAll
            if (clean.any { it.isDigit() }) return@checkAll
            val r = parseTaskInput(clean)
            // The parser may lowercase, trim, etc.
            assertEquals(clean.trim(), r.title.trim())
            assertNull(r.doDate)
            assertNull(r.dueDate)
            assertTrue(r.item is Item.Task)
        }
    }

    test("#cat and #category produce Category item") {
        listOf("#cat", "#category", "test #cat", "buy milk #cat").forEach { input ->
            val r = parseTaskInput(input)
            assertTrue(r.item is Item.Category, "Expected Category for: $input")
        }
    }

    test("#proj and #project produce Project item") {
        listOf("#proj", "#project", "build app #proj").forEach { input ->
            val r = parseTaskInput(input)
            assertTrue(r.item is Item.Project, "Expected Project for: $input")
        }
    }

    test("do today sets doDate") {
        val r = parseTaskInput("test do today")
        assertNotNull(r.doDate, "doDate should be set with 'do today'")
        assertEquals("test", r.title.trim())
        assertTrue(r.item is Item.Task)
    }

    test("due tomorrow sets dueDate") {
        val r = parseTaskInput("test due tomorrow")
        assertNotNull(r.dueDate, "dueDate should be set with 'due tomorrow'")
        assertEquals("test", r.title.trim())
    }

    test("do today sets doDate") {
        val r = parseTaskInput("do today")
        assertNotNull(r.doDate, "doDate should be set")
    }

    test("due fri sets dueDate") {
        val r = parseTaskInput("task due fri")
        assertNotNull(r.dueDate, "dueDate should be set with 'task due fri'")
    }

    // ==== Example-based tests for date expression formats ====

    test("today alias: t") {
        val r1 = parseTaskInput("do t")
        val r2 = parseTaskInput("do today")
        assertNotNull(r1.doDate)
        assertNotNull(r2.doDate)
        assertEquals(r1.doDate, r2.doDate)
    }

    test("tomorrow aliases: tmr, tmrw") {
        val r1 = parseTaskInput("do tmr")
        val r2 = parseTaskInput("do tmrw")
        val r3 = parseTaskInput("do tomorrow")
        assertNotNull(r1.doDate)
        assertNotNull(r2.doDate)
        assertNotNull(r3.doDate)
        assertEquals(r1.doDate, r2.doDate)
        assertEquals(r1.doDate, r3.doDate)
    }

    test("next week alias: next") {
        val r = parseTaskInput("do next week")
        assertNotNull(r.doDate)
    }

    test("relative: in 3 days, +5d, +2w") {
        val r1 = parseTaskInput("do in 3 days")
        val r2 = parseTaskInput("do +5d")
        val r3 = parseTaskInput("do +2w")
        assertNotNull(r1.doDate)
        assertNotNull(r2.doDate)
        assertNotNull(r3.doDate)
    }

    test("abbreviated relative: 3d, 1w") {
        val r1 = parseTaskInput("do 3d")
        val r2 = parseTaskInput("do 1w")
        assertNotNull(r1.doDate)
        assertNotNull(r2.doDate)
    }

    test("weekday names resolve") {
        val weekdays = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        weekdays.forEach { day ->
            val r = parseTaskInput("do $day")
            assertNotNull(r.doDate) { "doDate should be set for 'do $day'" }
        }
    }

    test("absolute date: mar 7, march 7th") {
        val r1 = parseTaskInput("do mar 7")
        val r2 = parseTaskInput("do march 7th")
        assertNotNull(r1.doDate)
        assertNotNull(r2.doDate)
    }

    test("reversed date: 7 mar, 7th march") {
        val r1 = parseTaskInput("do 7 mar")
        val r2 = parseTaskInput("do 7th march")
        assertNotNull(r1.doDate)
        assertNotNull(r2.doDate)
    }

    // ==== #command syntax tests ====

    test("#removecat sets removeCatTitle") {
        val r = parseTaskInput("#removecat Work")
        assertEquals("Work", r.removeCatTitle)
    }

    test("#rmcat is alias for #removecat") {
        val r = parseTaskInput("#rmcat Personal")
        assertEquals("Personal", r.removeCatTitle)
    }

    test("#moveto sets moveTarget") {
        val r = parseTaskInput("#moveto Inbox")
        assertEquals("Inbox", r.moveTarget)
    }

    test("#mt is alias for #moveto") {
        val r = parseTaskInput("#mt Inbox")
        assertEquals("Inbox", r.moveTarget)
    }

    test("#word sets parentRef") {
        val r = parseTaskInput("subtask #ProjectX")
        assertEquals("ProjectX", r.parentRef)
    }

    test("combined: title + category + date") {
        val r = parseTaskInput("My Task #cat do tomorrow")
        assertEquals("My Task", r.title.trim())
        assertTrue(r.item is Item.Category)
        assertNotNull(r.doDate)
    }

    test("date in middle of title is extracted") {
        val r = parseTaskInput("buy mar 7 tiles")
        assertNotNull(r.doDate, "doDate should be extracted from middle of title")
    }

    test("bare trailing date as implicit do") {
        val r = parseTaskInput("finish report tomorrow")
        // Should interpret trailing "tomorrow" as do date
        assertNotNull(r.doDate)
        assertEquals("finish report", r.title.trim())
    }

    test("input with only a #token") {
        val r = parseTaskInput("#cat")
        assertEquals("", r.title.trim())
        assertTrue(r.item is Item.Category)
    }

    // ==== VisualTransformation ====

    test("dateHighlightTransformation produces valid output") {
        val transformation = dateHighlightTransformation()
        val input = buildAnnotatedString { append("test do tomorrow") }
        val result = transformation.filter(input)
        assertTrue(result.text.text.contains("test"))
        assertTrue(result.text.text.contains("tomorrow"))
    }
})
