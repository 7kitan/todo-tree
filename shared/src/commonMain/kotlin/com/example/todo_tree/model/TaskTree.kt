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

    // ==== New mutations (reorder, bulk) ====

    fun deleteCompleted(forest: List<TaskNode>): List<TaskNode> =
        forest.filterNot { it.isCompleted }.map { node ->
            node.copy(subtasks = deleteCompleted(node.subtasks))
        }

    fun moveUp(forest: List<TaskNode>, taskId: String): List<TaskNode> {
        val parent = _findParent(forest, taskId)
        val siblings = parent?.subtasks ?: forest
        val idx = siblings.indexOfFirst { it.id == taskId }
        if (idx <= 0) return forest
        val mutable = siblings.toMutableList()
        mutable[idx] = mutable[idx - 1].also { mutable[idx - 1] = mutable[idx] }
        return if (parent != null) mapForest(forest, parent.id) { it.copy(subtasks = mutable) } else mutable
    }

    fun moveDown(forest: List<TaskNode>, taskId: String): List<TaskNode> {
        val parent = _findParent(forest, taskId)
        val siblings = parent?.subtasks ?: forest
        val idx = siblings.indexOfFirst { it.id == taskId }
        if (idx < 0 || idx >= siblings.size - 1) return forest
        val mutable = siblings.toMutableList()
        mutable[idx] = mutable[idx + 1].also { mutable[idx + 1] = mutable[idx] }
        return if (parent != null) mapForest(forest, parent.id) { it.copy(subtasks = mutable) } else mutable
    }

    fun indent(forest: List<TaskNode>, taskId: String): List<TaskNode> {
        val parent = _findParent(forest, taskId)
        val siblings = parent?.subtasks ?: forest
        val idx = siblings.indexOfFirst { it.id == taskId }
        if (idx <= 0) return forest
        val task = siblings[idx]
        val newParent = siblings[idx - 1]
        val mutable = siblings.toMutableList()
        mutable.removeAt(idx)
        mutable[idx - 1] = newParent.copy(subtasks = newParent.subtasks + task)
        return if (parent != null) mapForest(forest, parent.id) { it.copy(subtasks = mutable) } else mutable
    }

    fun outdent(forest: List<TaskNode>, taskId: String): List<TaskNode> {
        val parent = _findParent(forest, taskId) ?: return forest
        val grandparent = _findParent(forest, parent.id)
        val parentSiblings = grandparent?.subtasks ?: forest
        val parentIdx = parentSiblings.indexOfFirst { it.id == parent.id }
        val taskIdx = parent.subtasks.indexOfFirst { it.id == taskId }
        if (taskIdx < 0) return forest
        val task = parent.subtasks[taskIdx]
        val newParent = parent.copy(subtasks = parent.subtasks.toMutableList().apply { removeAt(taskIdx) })
        val mutable = parentSiblings.toMutableList()
        mutable[parentIdx] = newParent
        mutable.add(parentIdx + 1, task)
        return if (grandparent != null) mapForest(forest, grandparent.id) { it.copy(subtasks = mutable) } else mutable
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

    private fun _findParent(forest: List<TaskNode>, childId: String): TaskNode? {
        for (node in forest) {
            if (node.subtasks.any { it.id == childId }) return node
            _findParent(node.subtasks, childId)?.let { return it }
        }
        return null
    }
}
