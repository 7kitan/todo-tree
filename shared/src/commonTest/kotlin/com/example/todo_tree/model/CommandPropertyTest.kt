// =============================================================================
//  COMMAND_PROPERTY_TEST.KT
//  Property-based tests verifying every Command variant's apply() returns an
//  inverse that restores the original forest.
//
//  Central invariant: for any forest and any valid command, applying the
//  command then applying its inverse yields the original forest.
// =============================================================================

package com.example.todo_tree.model

import com.example.todo_tree.arbForest
import com.example.todo_tree.arbForestWithLeftSibling
import com.example.todo_tree.arbForestWithNonRootTaskId
import com.example.todo_tree.arbForestWithoutProjects
import com.example.todo_tree.arbForestWithTaskId
import com.example.todo_tree.collectTaskIds
import com.example.todo_tree.findNode
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe

class CommandPropertyTest : FunSpec({

    test("AddSubtask → RemoveTask roundtrip") {
        checkAll(arbForestWithTaskId()) { (forest, parentId) ->
            val cmd = AddSubtask(parentId, "test", Item.Task())
            val r1 = cmd.apply(forest)
            val r2 = r1.inverse.apply(r1.forest)
            r2.forest shouldBe forest
        }
    }

    test("RemoveTask → InsertTask roundtrip") {
        checkAll(arbForestWithNonRootTaskId()) { (forest, taskId) ->
            val cmd = RemoveTask(taskId)
            val r1 = cmd.apply(forest)
            val r2 = r1.inverse.apply(r1.forest)
            r2.forest shouldBe forest
        }
    }

    test("ToggleCompleted → SetState roundtrip for Active tasks") {
        checkAll(arbForestWithoutProjects()) { forest ->
            val activeIds = collectTaskIds(forest).filter {
                val n = findNode(forest, it) ?: return@filter false
                n.item is Item.Task && (n.item.state is TaskState.Active)
            }
            if (activeIds.isEmpty()) return@checkAll
            val taskId = activeIds.first()
            val cmd = ToggleCompleted(taskId)
            val r1 = cmd.apply(forest)
            val r2 = r1.inverse.apply(r1.forest)
            r2.forest shouldBe forest
        }
    }

    test("SetState is symmetric") {
        checkAll(arbForestWithTaskId()) { (forest, taskId) ->
            val original = findNode(forest, taskId)?.item ?: return@checkAll
            val newItem = when (original) {
                is Item.Task -> original.copy(state = TaskState.Done)
                is Item.Project -> original.copy(state = TaskState.Done)
                is Item.Category -> Item.Task(state = TaskState.Waiting)
            }
            val cmd = SetState(taskId, newItem)
            val r1 = cmd.apply(forest)
            val r2 = r1.inverse.apply(r1.forest)
            r2.forest shouldBe forest
        }
    }

    test("UpdateTask is symmetric") {
        checkAll(arbForestWithTaskId()) { (forest, taskId) ->
            val cmd = UpdateTask(taskId, "new_title", Item.Task(state = TaskState.Done))
            val r1 = cmd.apply(forest)
            val r2 = r1.inverse.apply(r1.forest)
            r2.forest shouldBe forest
        }
    }

    test("MoveUp → MoveDown roundtrip") {
        checkAll(arbForestWithLeftSibling()) { (forest, taskId) ->
            val cmd = MoveUp(taskId)
            val r1 = cmd.apply(forest)
            val r2 = r1.inverse.apply(r1.forest)
            r2.forest shouldBe forest
        }
    }

    test("Indent → Outdent roundtrip") {
        checkAll(arbForestWithLeftSibling()) { (forest, taskId) ->
            val cmd = Indent(taskId)
            val r1 = cmd.apply(forest)
            val r2 = r1.inverse.apply(r1.forest)
            r2.forest shouldBe forest
        }
    }

    test("MoveTask roundtrip restores parent") {
        checkAll(arbForestWithNonRootTaskId()) { (forest, taskId) ->
            val otherIds = collectTaskIds(forest).filter { it != taskId && findNode(forest, it)?.item is Item.Category }
            if (otherIds.isEmpty()) return@checkAll
            val newParentId = otherIds.first()
            val originalParentId = TaskTree.findParentId(forest, taskId)
            val cmd = MoveTask(taskId, newParentId)
            val r1 = cmd.apply(forest)
            val r2 = r1.inverse.apply(r1.forest)
            val restoredParentId = TaskTree.findParentId(r2.forest, taskId)
            restoredParentId shouldBe originalParentId
        }
    }

    test("DeleteCompleted → RestoreForest roundtrip") {
        checkAll(arbForest()) { forest ->
            val r1 = DeleteCompleted.apply(forest)
            val r2 = r1.inverse.apply(r1.forest)
            r2.forest shouldBe forest
        }
    }
})
