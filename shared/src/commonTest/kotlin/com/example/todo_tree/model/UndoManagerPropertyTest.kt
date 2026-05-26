// =============================================================================
//  UNDO_MANAGER_PROPERTY_TEST.KT
//  Property-based tests for UndoManager stack behavior.
//
//  Invariants tested:
//    - execute pushes the inverse and clears the redo stack.
//    - undo restores the previous forest.
//    - redo restores the undone forest.
//    - undo on an empty stack is a no-op (returns null).
//    - redo on an empty stack is a no-op (returns null).
//    - new action after undo clears the redo stack.
//    - maxSize cap is enforced (>50 actions drop the oldest).
// =============================================================================

package com.example.todo_tree.model

import com.example.todo_tree.arbForest
import com.example.todo_tree.collectTaskIds
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.property.checkAll
import io.kotest.property.arbitrary.int

class UndoManagerPropertyTest : FunSpec({

    test("execute pushes inverse, clears redo") {
        checkAll(arbForest()) { forest ->
            val ids = collectTaskIds(forest)
            if (ids.isEmpty()) return@checkAll
            val mgr = UndoManager()
            mgr.canUndo shouldBe false
            mgr.canRedo shouldBe false

            mgr.execute(RemoveTask(ids.first()), forest)
            mgr.canUndo shouldBe true
            mgr.canRedo shouldBe false
        }
    }

    test("undo restores previous forest") {
        checkAll(arbForest()) { forest ->
            val ids = collectTaskIds(forest)
            if (ids.isEmpty()) return@checkAll
            val mgr = UndoManager()

            val afterExec = mgr.execute(
                UpdateTask(ids.first(), "undo_test", Item.Task()), forest
            )
            val afterUndo = mgr.undo(afterExec.forest)
            afterUndo.shouldNotBeNull()
            afterUndo.forest shouldBe forest
        }
    }

    test("redo restores undone state") {
        checkAll(arbForest()) { forest ->
            val ids = collectTaskIds(forest)
            if (ids.isEmpty()) return@checkAll
            val mgr = UndoManager()

            val afterExec = mgr.execute(
                UpdateTask(ids.first(), "redo_test", Item.Task()), forest
            )
            mgr.undo(afterExec.forest)
            val afterRedo = mgr.redo(forest)
            afterRedo.shouldNotBeNull()
            afterRedo.forest shouldBe afterExec.forest
        }
    }

    test("undo on empty stack returns null") {
        val mgr = UndoManager()
        mgr.undo(emptyList()).shouldBeNull()
    }

    test("redo on empty stack returns null") {
        val mgr = UndoManager()
        mgr.redo(emptyList()).shouldBeNull()
    }

    test("new action after undo clears redo stack") {
        checkAll(arbForest()) { forest ->
            val ids = collectTaskIds(forest)
            if (ids.size < 2) return@checkAll
            val mgr = UndoManager()

            val afterExec = mgr.execute(UpdateTask(ids.first(), "first", Item.Task()), forest)
            val afterUndo = mgr.undo(afterExec.forest)
            afterUndo.shouldNotBeNull()

            mgr.execute(UpdateTask(ids.first(), "second", Item.Task()), afterUndo.forest)
            mgr.canRedo shouldBe false
        }
    }

    test("maxSize cap — oldest entry dropped") {
        val mgr = UndoManager(maxSize = 3)
        var forest = emptyList<ItemNode>()

        // Execute 4 DeleteCompleted commands (works on empty forest, pushes inverse)
        repeat(4) { forest = mgr.execute(DeleteCompleted, forest).forest }

        // Undo 3 times — should get last 3 undone
        val u1 = mgr.undo(forest)
        u1.shouldNotBeNull()
        val u2 = mgr.undo(u1.forest)
        u2.shouldNotBeNull()
        val u3 = mgr.undo(u2.forest)
        u3.shouldNotBeNull()
        // First was dropped by maxSize cap — 4th undo returns null
        val u4 = mgr.undo(u3.forest)
        u4.shouldBeNull()
    }
})
