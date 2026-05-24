// =============================================================================
//  TASK_VIEW_MODEL.KT
//  ViewModel holding forest StateFlow + ~60-item sample with due dates.
// =============================================================================

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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun addRootTask(title: String, doDate: Long? = null, dueDate: Long? = null) { if (title.isBlank()) return; _forest.value = _forest.value + TaskNode(title = title.trim(), doDate = doDate, dueDate = dueDate) }
    fun addSubtask(parentId: String, title: String, doDate: Long? = null, dueDate: Long? = null) { if (title.isBlank()) return; _forest.value = TaskTree.addTask(_forest.value, parentId, TaskNode(title = title.trim(), doDate = doDate, dueDate = dueDate)) }
    fun removeTask(taskId: String) { _forest.value = TaskTree.removeTask(_forest.value, taskId) }
    fun toggleCompleted(taskId: String) { _forest.value = TaskTree.toggleCompleted(_forest.value, taskId) }
    fun updateTask(taskId: String, title: String, doDate: Long?, dueDate: Long?) {
        _forest.value = TaskTree.updateTask(_forest.value, taskId) { it.copy(title = title, doDate = doDate, dueDate = dueDate) }
    }
    fun deleteCompleted() { _forest.value = TaskTree.deleteCompleted(_forest.value) }
    fun moveUp(taskId: String) { _forest.value = TaskTree.moveUp(_forest.value, taskId) }
    fun moveDown(taskId: String) { _forest.value = TaskTree.moveDown(_forest.value, taskId) }
    fun indent(taskId: String) { _forest.value = TaskTree.indent(_forest.value, taskId) }
    fun outdent(taskId: String) { _forest.value = TaskTree.outdent(_forest.value, taskId) }
}

private val epochDay: Long get() = currentTimeMillis() / 86_400_000L

private fun sampleForest(): List<TaskNode> {
    val today = epochDay * 86_400_000L
    fun d(n: Int) = today + n * 86_400_000L
    fun task(title: String, due: Int, subs: List<TaskNode> = emptyList()) = TaskNode(title = title, dueDate = d(due), subtasks = subs)
    return listOf(
        task("Work", 0, listOf(
            task("Sprint review", 0),
            task("Code cleanup", 1),
            task("Meeting prep", 1),
            task("Ship feature v2.1", 2),
            task("Retro notes", 2),
        )),
        task("Personal", 1, listOf(
            task("Gym session", 1),
            task("Meal planning", 2),
            task("Reading time", 3),
            task("Journal entry", 3),
            task("Stretch routine", 0),
        )),
        task("Shopping", 3, listOf(
            task("Groceries", 2, listOf(
                task("Milk & eggs", 2),
                task("Whole wheat bread", 2),
                task("Fresh vegetables", 3),
            )),
            task("Hardware store", 5),
            task("Online returns", 6),
        )),
        task("House", 5, listOf(
            task("Clean garage", 5),
            task("Fix bathroom leak", 7, listOf(
                task("Buy sealant", 6),
                task("Remove old caulk", 7),
                task("Apply new layer", 7),
            )),
            task("Paint shed", 10),
        )),
        task("Finance", 7, listOf(
            task("Pay electricity bill", 8),
            task("Tax documents scan", 14),
            task("Monthly budget review", 7),
            task("Investment rebalance", 10),
        )),
        task("Health", 10, listOf(
            task("Dentist appointment", 12),
            task("Blood test results", 13),
            task("Prescription refill", 10),
            task("Eye exam", 20),
        )),
        task("Learning", 12, listOf(
            task("Kotlin course module 3", 14),
            task("Compose gestures deep dive", 16, listOf(
                task("Pointer input chapter", 16),
                task("Animation chapter", 18),
            )),
            task("Coding kata practice", 12),
            task("Blog post draft", 15),
        )),
        task("Travel planning", 14, listOf(
            task("Book flights", 14),
            task("Reserve hotel", 16),
            task("Packing list", 18),
            task("Research restaurants", 19),
        )),
        task("Social", 2, listOf(
            task("Call mom", 2),
            task("Plan dinner party", 7),
            task("RSVP wedding", 14),
        )),
        task("Side project", 8, listOf(
            task("Deploy backend", 8),
            task("Write test suite", 9, listOf(
                task("Unit tests", 9),
                task("Integration tests", 10),
                task("Snapshot tests", 10),
            )),
            task("Update README", 11),
            task("Polish UI states", 12),
        )),
        task("Deep nesting", 0, listOf(
            task("L1", 0, listOf(
                task("L2", 0, listOf(
                    task("L3", 0, listOf(
                        task("L4", 0, listOf(
                            task("L5", 0, listOf(
                                task("L6 leaf", 0),
                            )),
                        )),
                    )),
                )),
            )),
        )),
    )
}
