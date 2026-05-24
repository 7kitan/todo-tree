// =============================================================================
//  TASK_NODE.KT
//  Core data class with auto-increment id generator.
// =============================================================================

package com.example.todo_tree.model

data class TaskNode(
    val id: String = generateId(),
    val title: String,
    val isCompleted: Boolean = false,
    val doDate: Long? = null,
    val dueDate: Long? = null,
    val subtasks: List<TaskNode> = emptyList(),
) {
    companion object {
        private var counter = 0L
        fun generateId() = "task_${++counter}"
    }
}
