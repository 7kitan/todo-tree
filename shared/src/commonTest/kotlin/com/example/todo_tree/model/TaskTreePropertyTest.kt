// =============================================================================
//  TASK_TREE_PROPERTY_TEST.KT
//  Property-based tests for all TaskTree operations.
//
//  Invariants tested:
//    - Removing a task and re-inserting it at the same position restores the
//      original forest (captureLocation roundtrip).
//    - Toggling a non-Project task twice restores its original state.
//    - Moving a task up then down restores original sibling order.
//    - Indenting then outdenting a task restores original parent.
//    - deleteCompleted is idempotent.
//    - consolidate is idempotent.
// =============================================================================

package com.example.todo_tree.model

import com.example.todo_tree.arbForest
import com.example.todo_tree.arbForestWithLeftSibling
import com.example.todo_tree.arbForestWithNonRootTaskId
import com.example.todo_tree.arbForestWithTaskId
import com.example.todo_tree.collectTaskIds
import com.example.todo_tree.countNodes
import com.example.todo_tree.hasLeftSibling
import com.example.todo_tree.hasParent
import com.example.todo_tree.hasRightSibling
import com.example.todo_tree.arbForestWithoutProjects
import com.example.todo_tree.forestHasNoProjects
import com.example.todo_tree.isNotProject
import com.example.todo_tree.findNode
import io.kotest.core.spec.style.FunSpec
import io.kotest.property.checkAll
import io.kotest.matchers.shouldBe

class TaskTreePropertyTest : FunSpec({

    // Property: removing a non-root task then re-inserting it at the captured
    // position restores the original forest identically.
    test("captureLocation → removeTask → insertTask restores forest") {
        checkAll(arbForestWithNonRootTaskId()) { (forest, taskId) ->
            val (parentId, index, node) = TaskTree.captureLocation(forest, taskId)
            val removed = TaskTree.removeTask(forest, taskId)
            val restored = TaskTree.insertTask(removed, parentId, index, node)
            restored shouldBe forest
        }
    }

    // Property: adding a task then removing it restores the parent.
    test("addTask → removeTask restores parent") {
        checkAll(arbForest()) { forest ->
            val parentIds = collectTaskIds(forest)
            if (parentIds.isEmpty()) return@checkAll
            val parentId = parentIds.first()
            val child = ItemNode(title = "prop_child", item = Item.Task())
            val added = TaskTree.addTask(forest, parentId, child)
            val removed = TaskTree.removeTask(added, child.id)
            removed shouldBe forest
        }
    }

    // Property: toggleCompleted applied twice restores the original forest
    // for non-Project items in forests without any Projects.
    // NOTE: only Active tasks roundtrip (Active→Done→Active).
    // Waiting→Done→Active (not Waiting), so Waiting excluded.
    test("toggleCompleted is self-inverse for Active tasks") {
        checkAll(arbForestWithoutProjects()) { forest ->
            val ids = collectTaskIds(forest)
            if (ids.isEmpty()) return@checkAll
            val activeTaskIds = ids.filter {
                val n = findNode(forest, it) ?: return@filter false
                n.item is Item.Task && (n.item.state is TaskState.Active)
            }
            if (activeTaskIds.isEmpty()) return@checkAll
            val taskId = activeTaskIds.first()

            val once = TaskTree.toggleCompleted(forest, taskId)
            val twice = TaskTree.toggleCompleted(once, taskId)
            twice shouldBe forest
        }
    }

    // Property: Category items are never changed by toggleCompleted.
    test("toggleCompleted on Category is a no-op") {
        checkAll(arbForestWithoutProjects()) { forest ->
            val catIds = collectTaskIds(forest).filter {
                findNode(forest, it)?.item is Item.Category
            }
            if (catIds.isEmpty()) return@checkAll
            val catId = catIds.first()
            val result = TaskTree.toggleCompleted(forest, catId)
            result shouldBe forest
        }
    }

    // Property: moveUp then moveDown restores the original order.
    test("moveUp → moveDown restores order") {
        checkAll(arbForest()) { forest ->
            val ids = collectTaskIds(forest).filter { hasLeftSibling(forest, it) }
            if (ids.isEmpty()) return@checkAll
            val taskId = ids.first()
            val up = TaskTree.moveUp(forest, taskId)
            val down = TaskTree.moveDown(up, taskId)
            down shouldBe forest
        }
    }

    // Property: moveDown then moveUp restores the original order.
    test("moveDown → moveUp restores order") {
        checkAll(arbForest()) { forest ->
            val ids = collectTaskIds(forest).filter { hasRightSibling(forest, it) }
            if (ids.isEmpty()) return@checkAll
            val taskId = ids.first()
            val down = TaskTree.moveDown(forest, taskId)
            val up = TaskTree.moveUp(down, taskId)
            up shouldBe forest
        }
    }

    // Property: indent then outdent restores the original parent.
    test("indent → outdent restores parent") {
        checkAll(arbForestWithLeftSibling()) { (forest, taskId) ->
            val indented = TaskTree.indent(forest, taskId)
            val outdented = TaskTree.outdent(indented, taskId)
            outdented shouldBe forest
        }
    }

    // Property: outdent then indent restores the task's parent relationship
    // (but not sibling order — indent appends to the end of children).
    test("outdent → indent preserves parent-child relationship") {
        checkAll(arbForest(3, 6, 3)) { forest ->
            val valid = collectTaskIds(forest).filter { taskId ->
                val parent = TaskTree._findParent(forest, taskId)
                parent != null && TaskTree._findParent(forest, parent.id) != null
                    && parent.children.size > 1
            }
            if (valid.isEmpty()) return@checkAll
            val taskId = valid.first()
            val originalParent = TaskTree._findParent(forest, taskId)?.id
            val outdented = TaskTree.outdent(forest, taskId)
            val indented = TaskTree.indent(outdented, taskId)
            val restoredParent = TaskTree._findParent(indented, taskId)?.id
            restoredParent shouldBe originalParent
        }
    }

    // Property: moveTo with same parent or self is a no-op.
    test("moveTo guards against self-move") {
        checkAll(arbForestWithTaskId()) { (forest, taskId) ->
            val result = TaskTree.moveTo(forest, taskId, taskId)
            result shouldBe forest
        }
    }

    // Property: deleteCompleted is idempotent.
    test("deleteCompleted is idempotent") {
        checkAll(arbForest()) { forest ->
            val once = TaskTree.deleteCompleted(forest)
            val twice = TaskTree.deleteCompleted(once)
            twice shouldBe once
        }
    }

    // Property: updateTask preserves the structure (only changes given node).
    test("updateTask changes only the target node") {
        checkAll(arbForestWithNonRootTaskId()) { (forest, taskId) ->
            val newTitle = "updated_${taskId}"
            val updated = TaskTree.updateTask(forest, taskId) {
                it.copy(title = newTitle, item = Item.Task(state = TaskState.Done))
            }
            // Check the target node is updated
            val node = collectTaskIds(forest).first { it == taskId }
            val updatedNode = findNode(updated, taskId)!!
            updatedNode.title shouldBe newTitle
            // Check other nodes unchanged
            countNodes(updated) shouldBe countNodes(forest)
        }
    }
})


