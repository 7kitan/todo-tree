// =============================================================================
//  INPUT_PARSER_TEST.KT
//  Example-based + invariant tests for NLP input parsing and #command syntax.
//
//  InputParser is a complex NLP engine with specific grammar rules.
//  Property-based invariants cover broad categories (empty, plain text, known
//  tokens); example-based tests cover the specific date expression formats.
// =============================================================================

package com.example.todo_tree

import com.example.todo_tree.model.Item
import com.example.todo_tree.ui.InputCommand
import com.example.todo_tree.ui.dateHighlightTransformation
import com.example.todo_tree.ui.parseTaskInput
import androidx.compose.ui.text.buildAnnotatedString
import com.example.todo_tree.arbString
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.checkAll
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class InputParserTest : FunSpec({

    // ==== Property-based invariants ====

    test("empty and blank input produce empty AddTask") {
        checkAll(arbString(0, 0)) { input ->
            val r = parseTaskInput(input)
            r is InputCommand.AddTask
            if (r is InputCommand.AddTask) {
                r.title shouldBe ""
                r.doDate.shouldBeNull()
                r.dueDate.shouldBeNull()
                r.parentRef.shouldBeNull()
                (r.item is Item.Task).shouldBeTrue()
            }
        }
    }

    test("input without # or date tokens returns title unchanged") {
        checkAll(
            arbString(1, 30),
        ) { input ->
            val clean = input.trim().takeIf { it.isNotBlank() } ?: return@checkAll
            if ("#" in clean) return@checkAll
            if (clean.any { it.isDigit() }) return@checkAll
            val r = parseTaskInput(clean)
            check(r is InputCommand.AddTask)
            r.title.trim() shouldBe clean.trim()
            r.doDate.shouldBeNull()
            r.dueDate.shouldBeNull()
            (r.item is Item.Task).shouldBeTrue()
        }
    }

    test("#cat and #category produce Category item") {
        listOf("#cat", "#category", "test #cat", "buy milk #cat").forEach { input ->
            val r = parseTaskInput(input)
            check(r is InputCommand.AddTask)
            (r.item is Item.Category).shouldBeTrue()
        }
    }

    test("#proj and #project produce Project item") {
        listOf("#proj", "#project", "build app #proj").forEach { input ->
            val r = parseTaskInput(input)
            check(r is InputCommand.AddTask)
            (r.item is Item.Project).shouldBeTrue()
        }
    }

    test("do today sets doDate") {
        val r = parseTaskInput("test do today")
        check(r is InputCommand.AddTask)
        r.doDate.shouldNotBeNull()
        r.title.trim() shouldBe "test"
        (r.item is Item.Task).shouldBeTrue()
    }

    test("due tomorrow sets dueDate") {
        val r = parseTaskInput("test due tomorrow")
        check(r is InputCommand.AddTask)
        r.dueDate.shouldNotBeNull()
        r.title.trim() shouldBe "test"
    }

    test("do today sets doDate") {
        val r = parseTaskInput("do today")
        check(r is InputCommand.AddTask)
        r.doDate.shouldNotBeNull()
    }

    test("due fri sets dueDate") {
        val r = parseTaskInput("task due fri")
        check(r is InputCommand.AddTask)
        r.dueDate.shouldNotBeNull()
    }

    // ==== Example-based tests for date expression formats ====

    test("today alias: t") {
        val r1 = parseTaskInput("do t")
        val r2 = parseTaskInput("do today")
        check(r1 is InputCommand.AddTask && r2 is InputCommand.AddTask)
        r1.doDate.shouldNotBeNull()
        r2.doDate.shouldNotBeNull()
        r2.doDate shouldBe r1.doDate
    }

    test("tomorrow aliases: tmr, tmrw") {
        val r1 = parseTaskInput("do tmr")
        val r2 = parseTaskInput("do tmrw")
        val r3 = parseTaskInput("do tomorrow")
        check(r1 is InputCommand.AddTask && r2 is InputCommand.AddTask && r3 is InputCommand.AddTask)
        r1.doDate.shouldNotBeNull()
        r2.doDate.shouldNotBeNull()
        r3.doDate.shouldNotBeNull()
        r2.doDate shouldBe r1.doDate
        r3.doDate shouldBe r1.doDate
    }

    test("next week alias: next") {
        val r = parseTaskInput("do next week")
        check(r is InputCommand.AddTask)
        r.doDate.shouldNotBeNull()
    }

    test("relative: in 3 days, +5d, +2w") {
        val r1 = parseTaskInput("do in 3 days")
        val r2 = parseTaskInput("do +5d")
        val r3 = parseTaskInput("do +2w")
        check(r1 is InputCommand.AddTask && r2 is InputCommand.AddTask && r3 is InputCommand.AddTask)
        r1.doDate.shouldNotBeNull()
        r2.doDate.shouldNotBeNull()
        r3.doDate.shouldNotBeNull()
    }

    test("abbreviated relative: 3d, 1w") {
        val r1 = parseTaskInput("do 3d")
        val r2 = parseTaskInput("do 1w")
        check(r1 is InputCommand.AddTask && r2 is InputCommand.AddTask)
        r1.doDate.shouldNotBeNull()
        r2.doDate.shouldNotBeNull()
    }

    test("weekday names resolve") {
        val weekdays = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        weekdays.forEach { day ->
            val r = parseTaskInput("do $day")
            check(r is InputCommand.AddTask) { "Expected AddTask for 'do $day'" }
            r.doDate.shouldNotBeNull() { "doDate should be set for 'do $day'" }
        }
    }

    test("absolute date: mar 7, march 7th") {
        val r1 = parseTaskInput("do mar 7")
        val r2 = parseTaskInput("do march 7th")
        check(r1 is InputCommand.AddTask && r2 is InputCommand.AddTask)
        r1.doDate.shouldNotBeNull()
        r2.doDate.shouldNotBeNull()
    }

    test("reversed date: 7 mar, 7th march") {
        val r1 = parseTaskInput("do 7 mar")
        val r2 = parseTaskInput("do 7th march")
        check(r1 is InputCommand.AddTask && r2 is InputCommand.AddTask)
        r1.doDate.shouldNotBeNull()
        r2.doDate.shouldNotBeNull()
    }

    // ==== #command syntax tests ====

    test("#removecat returns RemoveCategory") {
        val r = parseTaskInput("#removecat Work")
        r shouldBe InputCommand.RemoveCategory("Work")
    }

    test("#rmcat is alias for #removecat") {
        val r = parseTaskInput("#rmcat Personal")
        r shouldBe InputCommand.RemoveCategory("Personal")
    }

    test("#moveto returns MoveTask") {
        val r = parseTaskInput("#moveto Inbox")
        r shouldBe InputCommand.MoveTask("Inbox")
    }

    test("#mt is alias for #moveto") {
        val r = parseTaskInput("#mt Inbox")
        r shouldBe InputCommand.MoveTask("Inbox")
    }

    test("#word sets parentRef") {
        val r = parseTaskInput("subtask #ProjectX")
        check(r is InputCommand.AddTask)
        r.parentRef shouldBe "ProjectX"
    }

    test("combined: title + category + date") {
        val r = parseTaskInput("My Task #cat do tomorrow")
        check(r is InputCommand.AddTask)
        r.title.trim() shouldBe "My Task"
        (r.item is Item.Category).shouldBeTrue()
        r.doDate.shouldNotBeNull()
    }

    test("date in middle of title is extracted") {
        val r = parseTaskInput("buy mar 7 tiles")
        check(r is InputCommand.AddTask)
        r.doDate.shouldNotBeNull()
    }

    test("bare trailing date as implicit do") {
        val r = parseTaskInput("finish report tomorrow")
        check(r is InputCommand.AddTask)
        r.doDate.shouldNotBeNull()
        r.title.trim() shouldBe "finish report"
    }

    test("input with only a #token") {
        val r = parseTaskInput("#cat")
        check(r is InputCommand.AddTask)
        r.title.trim() shouldBe ""
        (r.item is Item.Category).shouldBeTrue()
    }

    // ==== VisualTransformation ====

    test("dateHighlightTransformation produces valid output") {
        val transformation = dateHighlightTransformation()
        val input = buildAnnotatedString { append("test do tomorrow") }
        val result = transformation.filter(input)
        result.text.text.contains("test").shouldBeTrue()
        result.text.text.contains("tomorrow").shouldBeTrue()
    }
})
