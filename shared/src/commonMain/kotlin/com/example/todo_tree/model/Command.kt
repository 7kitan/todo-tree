// =============================================================================
//  COMMAND.KT
//  Algebraic data type (ADT) for undoable tree mutations.
//
//  Each command variant is a pure data class whose `apply()` returns both the
//  new forest and an inverse command. This makes the command history
//  self-contained — no snapshotting, no mutable state, and no coupling to the
//  UndoManager beyond the stack itself.
//
//  Why a sealed class ADT?
//  -----------------------
//  A sealed class (sum type) with data class variants (product types) is the
//  correct encoding of an algebraic data type in Kotlin. Each variant has typed
//  fields matching its specific operation, and the `when` dispatch in
//  UndoManager.execute/undo/redo is exhaustive at compile time.
//
//  Compared to alternatives:
//    - Flat enum with `Any` payload: loses type safety, requires unsafe casts.
//    - Single method + subclass per variant (polymorphism): dispatch is
//      scattered across files; adding a variant means finding and overriding.
//    - ADT: centralised `when`, typed fields per variant, the compiler proves
//      all call sites handle every variant. Extending means adding one data
//      class and one branch in each `when`.
// =============================================================================

package com.example.todo_tree.model

sealed class Command {
    abstract fun apply(forest: List<ItemNode>): ApplyResult
}

data class ApplyResult(
    val forest: List<ItemNode>,
    val inverse: Command,
    val newNodeId: String? = null,
)

// ==== Add / Remove / Insert ====

data class AddSubtask(
    val parentId: String,
    val title: String,
    val item: Item,
) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val node = ItemNode(title = title.trim(), item = item)
        val newForest = TaskTree.addTask(forest, parentId, node)
        return ApplyResult(newForest, RemoveTask(node.id), newNodeId = node.id)
    }
}

data class RemoveTask(val taskId: String) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val (parentId, index, node) = TaskTree.captureLocation(forest, taskId)
        val newForest = TaskTree.removeTask(forest, taskId)
        return ApplyResult(newForest, InsertTask(parentId, index, node))
    }
}

data class InsertTask(
    val parentId: String,
    val index: Int,
    val node: ItemNode,
) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val newForest = TaskTree.insertTask(forest, parentId, index.coerceAtLeast(0), node)
        return ApplyResult(newForest, RemoveTask(node.id))
    }
}

// ==== State toggling ====

data class ToggleCompleted(val taskId: String) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val oldItem = TaskTree.findById(forest, taskId)?.item
            ?: error("Task $taskId not found")
        val newForest = TaskTree.toggleCompleted(forest, taskId)
        return ApplyResult(newForest, SetState(taskId, oldItem))
    }
}

// Symmetric: SetState(X) -> SetState(oldX). Also used as inverse of ToggleCompleted.
data class SetState(val taskId: String, val item: Item) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val oldItem = TaskTree.findById(forest, taskId)?.item
            ?: error("Task $taskId not found")
        val newForest = TaskTree.updateTask(forest, taskId) { it.copy(item = item) }
        return ApplyResult(newForest, SetState(taskId, oldItem))
    }
}

// ==== Edit title / type ====

data class UpdateTask(
    val taskId: String,
    val title: String,
    val item: Item,
) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val old = TaskTree.findById(forest, taskId)
            ?: error("Task $taskId not found")
        val newForest = TaskTree.updateTask(forest, taskId) {
            it.copy(title = title.trim(), item = item)
        }
        return ApplyResult(newForest, UpdateTask(taskId, old.title, old.item))
    }
}

// ==== Reorder ====

data class MoveUp(val taskId: String) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val newForest = TaskTree.moveUp(forest, taskId)
        return ApplyResult(newForest, MoveDown(taskId))
    }
}

data class MoveDown(val taskId: String) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val newForest = TaskTree.moveDown(forest, taskId)
        return ApplyResult(newForest, MoveUp(taskId))
    }
}

data class MoveTask(
    val taskId: String,
    val newParentId: String,
) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val oldParentId = TaskTree.findParentId(forest, taskId)
            ?: error("Task $taskId has no parent")
        val newForest = TaskTree.moveTo(forest, taskId, newParentId)
        return ApplyResult(newForest, MoveTask(taskId, oldParentId))
    }
}

// ==== Indent / Outdent ====

data class Indent(val taskId: String) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val newForest = TaskTree.indent(forest, taskId)
        return ApplyResult(newForest, Outdent(taskId))
    }
}

data class Outdent(val taskId: String) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val newForest = TaskTree.outdent(forest, taskId)
        return ApplyResult(newForest, Indent(taskId))
    }
}

// ==== Root-level operations ====

data class AddRootTask(
    val title: String,
    val item: Item,
) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val node = ItemNode(title = title.trim(), item = item)
        return ApplyResult(forest + node, RemoveRootTask(node.id), newNodeId = node.id)
    }
}

data class RemoveRootTask(val taskId: String) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val idx = forest.indexOfFirst { it.id == taskId }
        require(idx >= 0) { "Root task $taskId not found" }
        val node = forest[idx]
        val newForest = forest.toMutableList().apply { removeAt(idx) }
        return ApplyResult(newForest, InsertRootTask(idx, node))
    }
}

data class InsertRootTask(
    val index: Int,
    val node: ItemNode,
) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val newForest = forest.toMutableList().apply {
            add(index.coerceIn(0..size), node)
        }
        return ApplyResult(newForest, RemoveRootTask(node.id))
    }
}

// ==== Batch deletion ====

// DeleteCompleted and RestoreForest form a pair. DeleteCompleted captures the
// full pre-deletion forest as its inverse — the one exception to the
// "no snapshots" rule, since batch operations don't have a compact inverse.
// RestoreForest.apply simply returns the stored snapshot; redo re-executes
// the deletion on whatever the current state is (semantic redo).
data object DeleteCompleted : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        val newForest = TaskTree.deleteCompleted(forest)
        return ApplyResult(newForest, RestoreForest(forest))
    }
}

data class RestoreForest(val oldForest: List<ItemNode>) : Command() {
    override fun apply(forest: List<ItemNode>): ApplyResult {
        return ApplyResult(oldForest, DeleteCompleted)
    }
}
