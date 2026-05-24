package com.example.todo_tree.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.example.todo_tree.model.TaskNode

data class VisibleItem(val id: String, val depth: Int)

fun flattenVisible(forest: List<TaskNode>, expanded: Set<String>): List<VisibleItem> {
    val result = mutableListOf<VisibleItem>()
    fun walk(nodes: List<TaskNode>, depth: Int) {
        for (node in nodes) {
            result.add(VisibleItem(node.id, depth))
            if (node.id in expanded) walk(node.subtasks, depth + 1)
        }
    }
    walk(forest, 0)
    return result
}

fun findTaskById(forest: List<TaskNode>, id: String): TaskNode? {
    for (node in forest) {
        if (node.id == id) return node
        findTaskById(node.subtasks, id)?.let { return it }
    }
    return null
}

fun findParent(forest: List<TaskNode>, childId: String): TaskNode? {
    for (node in forest) {
        if (node.subtasks.any { it.id == childId }) return node
        findParent(node.subtasks, childId)?.let { return it }
    }
    return null
}

fun getSiblings(forest: List<TaskNode>, taskId: String): List<TaskNode> {
    val parent = findParent(forest, taskId)
    return parent?.subtasks ?: forest
}

fun handleKey(event: KeyEvent, onUp: () -> Unit, onDown: () -> Unit, onLeft: () -> Unit,
    onRight: () -> Unit, onToggleDone: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onAddSubtask: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key) {
        Key.DirectionUp -> { onUp(); true }
        Key.DirectionDown -> { onDown(); true }
        Key.DirectionLeft -> { onLeft(); true }
        Key.DirectionRight -> { onRight(); true }
        Key.Spacebar -> { onToggleDone(); true }
        Key.Enter -> { onEdit(); true }
        Key.Delete, Key.Backspace -> { onDelete(); true }
        Key.A -> { onAddSubtask(); true }
        else -> false
    }
}
