// =============================================================================
//  ITEM_NODE.KT
//  Core data types: Item (sealed Task/Project/Category), TaskState, and
//  ItemNode (tree node with immutable structural sharing).
// =============================================================================

package com.example.todo_tree.model

sealed class TaskState {
    data object Active : TaskState()
    data object Done : TaskState()
    data object Waiting : TaskState()
}

sealed class Item {
    data class Task(
        val state: TaskState = TaskState.Active,
        val doDate: Long? = null,
        val dueDate: Long? = null,
    ) : Item()

    data class Project(
        val state: TaskState = TaskState.Active,
        val dueDate: Long? = null,
    ) : Item()

    data object Category : Item()
}

data class ItemNode(
    val id: String = generateId(),
    val title: String,
    val item: Item = Item.Task(),
    val children: List<ItemNode> = emptyList(),
) {
    companion object {
        private var counter = 0L
        fun generateId() = "task_${++counter}"
    }
}

val ItemNode.dueDate: Long? get() = when (val i = item) {
    is Item.Task -> i.dueDate
    is Item.Project -> i.dueDate
    is Item.Category -> null
}

val ItemNode.doDate: Long? get() = (item as? Item.Task)?.doDate

val ItemNode.isCategory: Boolean get() = item is Item.Category
