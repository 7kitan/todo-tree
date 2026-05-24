// =============================================================================
//  TASK_VIEW_MODEL.KT
//  ViewModel holding forest StateFlow + ~60-item sample with due dates.
// =============================================================================

package com.example.todo_tree.viewmodel

import androidx.lifecycle.ViewModel
import com.example.todo_tree.currentTimeMillis
import com.example.todo_tree.model.Item
import com.example.todo_tree.model.ItemNode
import com.example.todo_tree.model.TaskState
import com.example.todo_tree.model.TaskTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskViewModel : ViewModel() {
    private val _forest = MutableStateFlow(sampleForest())
    val forest: StateFlow<List<ItemNode>> = _forest.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun addRootTask(title: String, item: Item = Item.Task()): String? { if (title.isBlank()) return null; val node = ItemNode(title = title.trim(), item = item); _forest.value = _forest.value + node; return node.id }
    fun addSubtask(parentId: String, title: String, item: Item = Item.Task()): String? { if (title.isBlank()) return null; val node = ItemNode(title = title.trim(), item = item); _forest.value = TaskTree.addTask(_forest.value, parentId, node); return node.id }
    fun removeTask(taskId: String) { _forest.value = TaskTree.removeTask(_forest.value, taskId) }
    fun toggleCompleted(taskId: String) { _forest.value = TaskTree.toggleCompleted(_forest.value, taskId) }
    fun updateTask(taskId: String, title: String, item: Item) {
        _forest.value = TaskTree.updateTask(_forest.value, taskId) { it.copy(title = title.trim(), item = item) }
    }
    fun deleteCompleted() { _forest.value = TaskTree.deleteCompleted(_forest.value) }
    fun moveUp(taskId: String) { _forest.value = TaskTree.moveUp(_forest.value, taskId) }
    fun moveDown(taskId: String) { _forest.value = TaskTree.moveDown(_forest.value, taskId) }
    fun indent(taskId: String) { _forest.value = TaskTree.indent(_forest.value, taskId) }
    fun outdent(taskId: String) { _forest.value = TaskTree.outdent(_forest.value, taskId) }
}

// ==== Sample data ====

private val epochDay: Long get() = currentTimeMillis() / 86_400_000L

private fun sampleForest(): List<ItemNode> {
    val today = epochDay * 86_400_000L
    fun d(n: Int) = today + n * 86_400_000L

    val t = 0; val tmw = 1; val p2 = -2; val d3 = 3; val d5 = 5; val d7 = 7; val d10 = 10; val d14 = 14; val d30 = 30

    fun task(title: String, due: Int, subs: List<ItemNode> = emptyList()) =
        ItemNode(title = title, item = Item.Task(dueDate = d(due)), children = subs)
    fun taskDo(title: String, doDay: Int, subs: List<ItemNode> = emptyList()) =
        ItemNode(title = title, item = Item.Task(doDate = d(doDay)), children = subs)
    fun taskBoth(title: String, doDay: Int, dueDay: Int, subs: List<ItemNode> = emptyList()) =
        ItemNode(title = title, item = Item.Task(doDate = d(doDay), dueDate = d(dueDay)), children = subs)
    fun waiting(title: String, dueDay: Int, subs: List<ItemNode> = emptyList()) =
        ItemNode(title = title, item = Item.Task(state = TaskState.Waiting, dueDate = d(dueDay)), children = subs)
    fun done(title: String, dueDay: Int, subs: List<ItemNode> = emptyList()) =
        ItemNode(title = title, item = Item.Task(state = TaskState.Done, dueDate = d(dueDay)), children = subs)
    fun project(title: String, dueDay: Int, subs: List<ItemNode> = emptyList()) =
        ItemNode(title = title, item = Item.Project(dueDate = d(dueDay)), children = subs)
    fun category(title: String, subs: List<ItemNode>) =
        ItemNode(title = title, item = Item.Category, children = subs)

    return listOf(
        category("Inbox", listOf(
            task("Pay electricity bill", p2),
            task("Buy groceries", t),
            taskDo("Schedule dentist", tmw),
            taskBoth("Plan weekend trip", tmw, d3),
            waiting("Wait for design review", t),
            done("Write Q1 report", p2),
        )),
        category("Work", listOf(
            project("Website redesign", d5, listOf(
                task("Design mockups", d3),
                taskBoth("Implement landing page", tmw, d5),
            )),
            task("Fix login bug", p2),
            taskDo("Review PR comments", tmw),
        )),
        category("Personal", listOf(
            project("Home renovation", d10, listOf(
                taskDo("Buy tiles", tmw),
                task("Paint bedroom", d10),
            )),
            task("Read 3 books", d30),
            waiting("Order new passport", d7),
        )),
        category("Deep nesting", listOf(
            task("L1", t, listOf(
                task("L2", t, listOf(
                    task("L3", t, listOf(
                        task("L4", t, listOf(
                            task("L5", t, listOf(
                                task("L6 leaf", t),
                            )),
                        )),
                    )),
                )),
            )),
        )),
    )
}
