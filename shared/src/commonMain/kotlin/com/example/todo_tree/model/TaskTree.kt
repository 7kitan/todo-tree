// =============================================================================
//  TASK_TREE.KT
//  Immutable tree operations: add, remove, update, toggle, consolidate.
// =============================================================================

package com.example.todo_tree.model

object TaskTree {

    // ==== Mutations ====

    fun addTask(forest: List<TaskNode>, parentId: String, child: TaskNode): List<TaskNode> =
        mapForest(forest, parentId) { it.copy(subtasks = it.subtasks + child) }

    fun removeTask(forest: List<TaskNode>, taskId: String): List<TaskNode> =
        removeFromForest(forest, taskId)

    fun updateTask(forest: List<TaskNode>, taskId: String, transform: (TaskNode) -> TaskNode): List<TaskNode> =
        mapForest(forest, taskId, transform)

    fun toggleCompleted(forest: List<TaskNode>, taskId: String): List<TaskNode> {
        val toggled = mapForest(forest, taskId) { it.copy(isCompleted = !it.isCompleted) }
        return consolidate(toggled)
    }

    // ==== Internal ====

    private fun mapForest(forest: List<TaskNode>, targetId: String, transform: (TaskNode) -> TaskNode): List<TaskNode> =
        forest.map { node ->
            if (node.id == targetId) transform(node)
            else node.copy(subtasks = mapForest(node.subtasks, targetId, transform))
        }

    private fun removeFromForest(forest: List<TaskNode>, targetId: String): List<TaskNode> =
        forest.filterNot { it.id == targetId }.map { node ->
            node.copy(subtasks = removeFromForest(node.subtasks, targetId))
        }

    private fun consolidate(forest: List<TaskNode>): List<TaskNode> = forest.map { node ->
        val kids = node.subtasks.map { consolidate(listOf(it)).first() }
        val allDone = kids.isNotEmpty() && kids.all { it.isCompleted }
        val anyUndone = kids.any { !it.isCompleted }
        node.copy(subtasks = kids, isCompleted = allDone || (node.isCompleted && !anyUndone))
    }
}
