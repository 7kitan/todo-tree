package com.example.todo_tree.viewmodel

import androidx.lifecycle.ViewModel
import com.example.todo_tree.currentTimeMillis
import com.example.todo_tree.model.TaskNode
import com.example.todo_tree.model.TaskTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskViewModel : ViewModel() {
    private val _forest = MutableStateFlow(sampleForest())
    val forest: StateFlow<List<TaskNode>> = _forest.asStateFlow()

    fun addRootTask(title: String) { if (title.isBlank()) return; _forest.value = _forest.value + TaskNode(title = title.trim()) }
    fun addSubtask(parentId: String, title: String) { if (title.isBlank()) return; _forest.value = TaskTree.addTask(_forest.value, parentId, TaskNode(title = title.trim())) }
    fun removeTask(taskId: String) { _forest.value = TaskTree.removeTask(_forest.value, taskId) }
    fun toggleCompleted(taskId: String) { _forest.value = TaskTree.toggleCompleted(_forest.value, taskId) }
    fun updateTask(taskId: String, title: String, doDate: Long?, dueDate: Long?) {
        _forest.value = TaskTree.updateTask(_forest.value, taskId) { it.copy(title = title, doDate = doDate, dueDate = dueDate) }
    }
}

private val epochDay: Long get() = currentTimeMillis() / 86_400_000L

private fun sampleForest(): List<TaskNode> {
    val today = epochDay * 86_400_000L
    fun d(n: Int) = today + n * 86_400_000L
    return (1..15).map { i -> TaskNode(
        title = "Task $i",
        dueDate = when (i) { 1 -> d(0); 2 -> d(1); 4 -> d(3); 7 -> d(10); 13 -> d(-1); else -> null },
        subtasks = if (i % 3 == 0) listOf(TaskNode(title = "Task ${i}.1"), TaskNode(title = "Task ${i}.2")) else emptyList(),
    ) }
}
