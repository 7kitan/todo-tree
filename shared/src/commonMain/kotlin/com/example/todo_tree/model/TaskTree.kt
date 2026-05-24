// =============================================================================
//  TASK_TREE.KT
//  Immutable tree operations: add, remove, update, toggle, consolidate.
// =============================================================================

package com.example.todo_tree.model

object TaskTree {

    // ==== Core mutations ====

    fun addTask(forest: List<ItemNode>, parentId: String, child: ItemNode): List<ItemNode> =
        mapForest(forest, parentId) { it.copy(children = it.children + child) }

    fun removeTask(forest: List<ItemNode>, taskId: String): List<ItemNode> =
        removeFromForest(forest, taskId)

    fun updateTask(forest: List<ItemNode>, taskId: String, transform: (ItemNode) -> ItemNode): List<ItemNode> =
        mapForest(forest, taskId, transform)

    fun toggleCompleted(forest: List<ItemNode>, taskId: String): List<ItemNode> {
        val toggled = mapForest(forest, taskId) { node ->
            val newItem = when (val i = node.item) {
                is Item.Task -> i.copy(
                    state = when (i.state) {
                        is TaskState.Active -> TaskState.Done
                        is TaskState.Done -> TaskState.Active
                        is TaskState.Waiting -> TaskState.Done
                    }
                )
                is Item.Project -> i.copy(
                    state = when (i.state) {
                        is TaskState.Active -> TaskState.Done
                        is TaskState.Done -> TaskState.Active
                        is TaskState.Waiting -> TaskState.Done
                    }
                )
                is Item.Category -> i
            }
            node.copy(item = newItem)
        }
        return consolidate(toggled)
    }

    // ==== Reorder / bulk ====

    fun deleteCompleted(forest: List<ItemNode>): List<ItemNode> =
        forest.filterNot { node ->
            when (val i = node.item) {
                is Item.Task -> i.state is TaskState.Done
                is Item.Project -> i.state is TaskState.Done
                is Item.Category -> false
            }
        }.map { node ->
            node.copy(children = deleteCompleted(node.children))
        }

    fun moveUp(forest: List<ItemNode>, taskId: String): List<ItemNode> {
        val parent = _findParent(forest, taskId)
        val siblings = parent?.children ?: forest
        val idx = siblings.indexOfFirst { it.id == taskId }
        if (idx <= 0) return forest
        val mutable = siblings.toMutableList()
        mutable[idx] = mutable[idx - 1].also { mutable[idx - 1] = mutable[idx] }
        return if (parent != null) mapForest(forest, parent.id) { it.copy(children = mutable) } else mutable
    }

    fun moveDown(forest: List<ItemNode>, taskId: String): List<ItemNode> {
        val parent = _findParent(forest, taskId)
        val siblings = parent?.children ?: forest
        val idx = siblings.indexOfFirst { it.id == taskId }
        if (idx < 0 || idx >= siblings.size - 1) return forest
        val mutable = siblings.toMutableList()
        mutable[idx] = mutable[idx + 1].also { mutable[idx + 1] = mutable[idx] }
        return if (parent != null) mapForest(forest, parent.id) { it.copy(children = mutable) } else mutable
    }

    fun indent(forest: List<ItemNode>, taskId: String): List<ItemNode> {
        val parent = _findParent(forest, taskId)
        val siblings = parent?.children ?: forest
        val idx = siblings.indexOfFirst { it.id == taskId }
        if (idx <= 0) return forest
        val task = siblings[idx]
        val newParent = siblings[idx - 1]
        val mutable = siblings.toMutableList()
        mutable.removeAt(idx)
        mutable[idx - 1] = newParent.copy(children = newParent.children + task)
        return if (parent != null) mapForest(forest, parent.id) { it.copy(children = mutable) } else mutable
    }

    fun outdent(forest: List<ItemNode>, taskId: String): List<ItemNode> {
        val parent = _findParent(forest, taskId) ?: return forest
        val grandparent = _findParent(forest, parent.id)
        val parentSiblings = grandparent?.children ?: forest
        val parentIdx = parentSiblings.indexOfFirst { it.id == parent.id }
        val taskIdx = parent.children.indexOfFirst { it.id == taskId }
        if (taskIdx < 0) return forest
        val task = parent.children[taskIdx]
        val newParent = parent.copy(children = parent.children.toMutableList().apply { removeAt(taskIdx) })
        val mutable = parentSiblings.toMutableList()
        mutable[parentIdx] = newParent
        mutable.add(parentIdx + 1, task)
        return if (grandparent != null) mapForest(forest, grandparent.id) { it.copy(children = mutable) } else mutable
    }

    // ==== Move to new parent ====

    fun moveTo(forest: List<ItemNode>, itemId: String, newParentId: String): List<ItemNode> {
        if (itemId == newParentId) return forest
        if (isDescendantOf(forest, itemId, newParentId)) return forest
        val item = findById(forest, itemId) ?: return forest
        val removed = removeFromForest(forest, itemId)
        return addTask(removed, newParentId, item)
    }

    private fun findById(forest: List<ItemNode>, targetId: String): ItemNode? {
        for (node in forest) {
            if (node.id == targetId) return node
            findById(node.children, targetId)?.let { return it }
        }
        return null
    }

    private fun isDescendantOf(forest: List<ItemNode>, ancestorId: String, nodeId: String): Boolean {
        for (node in forest) {
            if (node.id == ancestorId) {
                return node.children.any { it.id == nodeId || isDescendantOf(it.children, ancestorId, nodeId) }
            }
            if (isDescendantOf(node.children, ancestorId, nodeId)) return true
        }
        return false
    }

    // ==== Internal ====

    private fun mapForest(forest: List<ItemNode>, targetId: String, transform: (ItemNode) -> ItemNode): List<ItemNode> =
        forest.map { node ->
            if (node.id == targetId) transform(node)
            else node.copy(children = mapForest(node.children, targetId, transform))
        }

    private fun removeFromForest(forest: List<ItemNode>, targetId: String): List<ItemNode> =
        forest.filterNot { it.id == targetId }.map { node ->
            node.copy(children = removeFromForest(node.children, targetId))
        }

    private fun consolidate(forest: List<ItemNode>): List<ItemNode> = forest.map { node ->
        val kids = node.children.map { consolidate(listOf(it)).first() }
        val newItem = when (val i = node.item) {
            is Item.Project -> {
                val allDone = kids.isNotEmpty() && kids.all { child ->
                    when (val ci = child.item) {
                        is Item.Task -> ci.state is TaskState.Done || ci.state is TaskState.Waiting
                        is Item.Project -> ci.state is TaskState.Done
                        is Item.Category -> true
                    }
                }
                i.copy(state = if (allDone) TaskState.Done else i.state)
            }
            else -> node.item
        }
        node.copy(children = kids, item = newItem)
    }

    private fun _findParent(forest: List<ItemNode>, childId: String): ItemNode? {
        for (node in forest) {
            if (node.children.any { it.id == childId }) return node
            _findParent(node.children, childId)?.let { return it }
        }
        return null
    }
}
