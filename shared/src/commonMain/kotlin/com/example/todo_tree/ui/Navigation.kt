// =============================================================================
//  NAVIGATION.KT
//  Tree walk helpers: visible-order flattening, find, parent, siblings,
//  fuzzy search, earliest due date, keyboard dispatch.
// =============================================================================

package com.example.todo_tree.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.example.todo_tree.model.GHOST_ROOT
import com.example.todo_tree.model.Item
import com.example.todo_tree.model.ItemNode
import com.example.todo_tree.model.dueDate

data class VisibleItem(val id: String, val depth: Int)

// ==== Tree flattening ====

fun flattenVisible(forest: List<ItemNode>, expanded: Set<String>, searchQuery: String = ""): List<VisibleItem> {
    val query = searchQuery.lowercase().trim()
    val result = mutableListOf<VisibleItem>()

    if (query.isEmpty()) {
        fun walk(nodes: List<ItemNode>, depth: Int) {
            for (node in nodes) {
                result.add(VisibleItem(node.id, depth))
                if (node.id == GHOST_ROOT) {
                    walk(node.children, depth)
                } else if (node.id in expanded) {
                    walk(node.children, depth + 1)
                }
            }
        }
        walk(forest, 0)
        return result
    }

    val matchIds = mutableSetOf<String>()
    fun collectMatches(nodes: List<ItemNode>) {
        for (node in nodes) {
            if (node.title.lowercase().contains(query)) matchIds.add(node.id)
            collectMatches(node.children)
        }
    }
    collectMatches(forest)

    val ancestorIds = mutableSetOf<String>()
    for (id in matchIds) {
        var current = id
        while (true) {
            val p = findParent(forest, current) ?: break
            ancestorIds.add(p.id)
            current = p.id
        }
    }

    fun walk(nodes: List<ItemNode>, depth: Int, showAll: Boolean = false) {
        for (node in nodes) {
            if (node.id == GHOST_ROOT) {
                walk(node.children, depth, showAll)
            } else {
                val show = showAll || node.id in matchIds || node.id in ancestorIds
                if (show) {
                    result.add(VisibleItem(node.id, depth))
                    walk(node.children, depth + 1, showAll || node.id in matchIds)
                }
            }
        }
    }
    walk(forest, 0)
    return result
}

// ==== Tree search helpers ====

fun findTaskById(forest: List<ItemNode>, id: String): ItemNode? {
    for (node in forest) {
        if (node.id == id) return node
        findTaskById(node.children, id)?.let { return it }
    }
    return null
}

fun findParent(forest: List<ItemNode>, childId: String): ItemNode? {
    for (node in forest) {
        if (node.children.any { it.id == childId }) return node
        findParent(node.children, childId)?.let { return it }
    }
    return null
}

fun getSiblings(forest: List<ItemNode>, taskId: String): List<ItemNode> {
    val parent = findParent(forest, taskId)
    return parent?.children ?: forest
}

fun isDescendant(forest: List<ItemNode>, ancestorId: String, descendantId: String): Boolean {
    val ancestor = findTaskById(forest, ancestorId) ?: return false
    fun walk(nodes: List<ItemNode>): Boolean {
        for (node in nodes) {
            if (node.id == descendantId) return true
            if (walk(node.children)) return true
        }
        return false
    }
    return walk(ancestor.children)
}

fun findTaskByTitle(forest: List<ItemNode>, title: String): ItemNode? {
    val q = title.trim().lowercase()
    for (node in forest) {
        if (node.title.lowercase() == q) return node
        findTaskByTitle(node.children, title)?.let { return it }
    }
    return null
}

fun findTasksByTitleFuzzy(forest: List<ItemNode>, query: String, maxResults: Int = 5): List<ItemNode> {
    val q = query.trim().lowercase()
    val result = mutableListOf<ItemNode>()
    fun walk(nodes: List<ItemNode>) {
        for (node in nodes) {
            if (result.size >= maxResults) return
            if (node.title.lowercase().contains(q)) result.add(node)
            walk(node.children)
        }
    }
    walk(forest)
    return result
}

fun breadcrumb(forest: List<ItemNode>, nodeId: String): List<String> {
    fun walk(nodes: List<ItemNode>, path: List<String>): List<String>? {
        for (node in nodes) {
            if (node.id == nodeId) return path + node.title
            walk(node.children, path + node.title)?.let { return it }
        }
        return null
    }
    return walk(forest, emptyList()) ?: emptyList()
}

fun earliestDueDate(node: ItemNode): Long? {
    var best = node.dueDate
    for (child in node.children) {
        val childBest = earliestDueDate(child)
        if (childBest != null && (best == null || childBest < best)) best = childBest
    }
    return best
}

// ==== Keyboard dispatch ====

fun handleKey(event: KeyEvent, onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit,
    onRight: () -> Unit, onToggleDone: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onAdd: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionUp -> { onUp(); true }
        Key.DirectionDown -> { onDown(); true }
        Key.DirectionLeft -> { onLeft(); true }
        Key.DirectionRight -> { onRight(); true }
        Key.Spacebar -> { onToggleDone(); true }
        Key.Enter -> { onEdit(); true }
        Key.Delete, Key.Backspace -> { onDelete(); true }
        Key.A -> { onAdd(); true }
        else -> false
    }
}
