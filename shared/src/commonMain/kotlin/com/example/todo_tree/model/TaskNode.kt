// =============================================================================
//  ITEM_NODE.KT
//  Core data types: Item (sealed Task/Project/Category), TaskState, and
//  ItemNode (tree node with immutable structural sharing).
//
//  Persistence decision: @Serializable enables kotlinx-serialization-json
//  for full-tree save/load. UUID IDs are used instead of counter-based IDs
//  so they remain stable across app restarts (required for persistence and
//  future online sync).
// =============================================================================

package com.example.todo_tree.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.serialization.Serializable

@Serializable
sealed class TaskState {
    @Serializable data object Active : TaskState()
    @Serializable data object Done : TaskState()
    @Serializable data object Waiting : TaskState()
}

@Serializable
sealed class Item {
    @Serializable data class Task(
        val state: TaskState = TaskState.Active,
        val doDate: Long? = null,
        val dueDate: Long? = null,
    ) : Item()

    @Serializable data class Project(
        val state: TaskState = TaskState.Active,
        val dueDate: Long? = null,
    ) : Item()

    @Serializable data object Category : Item()
}

@Serializable
data class ItemNode(
    val id: String = generateId(),
    val title: String,
    val item: Item = Item.Task(),
    val children: List<ItemNode> = emptyList(),
) {
    companion object {
        @OptIn(ExperimentalUuidApi::class)
        fun generateId() = Uuid.random().toString()
    }
}

val ItemNode.dueDate: Long? get() = when (val i = item) {
    is Item.Task -> i.dueDate
    is Item.Project -> i.dueDate
    is Item.Category -> null
}

val ItemNode.doDate: Long? get() = (item as? Item.Task)?.doDate

val ItemNode.isCategory: Boolean get() = item is Item.Category

const val GHOST_ROOT = "__ghost_root__"
