// =============================================================================
//  TYPES.KT
//  Core data types: Item (sealed Task/Project/Category), TaskState,
//  ItemNode (tree node with immutable structural sharing), and domain
//  constants (GHOST_ROOT, INBOX_ID, DAY_MS).
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

// ==== Domain constants ====

// Ghost root: single synthetic root node that replaces a flat forest list.
// Previously, the forest was a list of independent root nodes, which required
// special-case handling everywhere (captureLocation, moveUp/down, indent/outdent,
// and separate AddRootTask/RemoveRootTask/InsertRootTask commands).
// Now every node has a parent — the ghost root — unifying all tree operations
// (add, remove, move, reorder) under a single code path.
const val GHOST_ROOT = "__ghost_root__"
const val INBOX_ID = "__inbox__"
const val DAY_MS = 86_400_000L
const val ROW_HEIGHT_DP = 40
const val MAX_FUZZY_RESULTS = 5

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
