// =============================================================================
//  TASK_VIEW_MODEL.KT
//  ViewModel holding forest StateFlow + ~60-item sample with due dates.
// =============================================================================

package com.example.todo_tree.viewmodel

import androidx.lifecycle.ViewModel
import com.example.todo_tree.currentTimeMillis
import com.example.todo_tree.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskViewModel : ViewModel() {
    private val _forest = MutableStateFlow(sampleForest())
    val forest: StateFlow<List<ItemNode>> = _forest.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val undoManager = UndoManager()
    private val __inbox__ = "__inbox__"

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    // ==== Private helpers ====

    private fun exec(cmd: Command) {
        _forest.value = undoManager.execute(cmd, _forest.value).forest
        _canUndo.value = undoManager.canUndo
        _canRedo.value = undoManager.canRedo
    }

    private fun execWithId(cmd: Command, title: String): String? {
        if (title.isBlank()) return null
        val r = undoManager.execute(cmd, _forest.value)
        _forest.value = r.forest
        _canUndo.value = undoManager.canUndo
        _canRedo.value = undoManager.canRedo
        return r.newNodeId
    }

    // ==== Public mutations (all go through commands) ====

    fun addRootTask(title: String, item: Item = Item.Task()): String? =
        execWithId(AddRootTask(title.trim(), item), title)

    fun addSubtask(parentId: String, title: String, item: Item = Item.Task()): String? =
        execWithId(AddSubtask(parentId, title.trim(), item), title)

    fun addInboxChild(title: String, item: Item = Item.Task()): String? =
        addSubtask(__inbox__, title, item)

    fun removeTask(taskId: String) {
        if (taskId == __inbox__) return; exec(RemoveTask(taskId))
    }

    fun toggleCompleted(taskId: String) {
        if (taskId == __inbox__) return; exec(ToggleCompleted(taskId))
    }

    fun updateTask(taskId: String, title: String, item: Item) {
        if (taskId == __inbox__) return; exec(UpdateTask(taskId, title.trim(), item))
    }

    fun deleteCompleted() { exec(DeleteCompleted) }

    fun moveUp(taskId: String) {
        if (taskId == __inbox__) return; exec(MoveUp(taskId))
    }

    fun moveDown(taskId: String) {
        if (taskId == __inbox__) return; exec(MoveDown(taskId))
    }

    fun moveTo(taskId: String, newParentId: String) {
        if (taskId == __inbox__ || newParentId == __inbox__) return
        exec(MoveTask(taskId, newParentId))
    }

    fun indent(taskId: String) {
        if (taskId == __inbox__) return; exec(Indent(taskId))
    }

    fun outdent(taskId: String) {
        if (taskId == __inbox__) return; exec(Outdent(taskId))
    }

    fun undo() {
        val r = undoManager.undo(_forest.value) ?: return
        _forest.value = r.forest
        _canUndo.value = undoManager.canUndo
        _canRedo.value = undoManager.canRedo
    }

    fun redo() {
        val r = undoManager.redo(_forest.value) ?: return
        _forest.value = r.forest
        _canUndo.value = undoManager.canUndo
        _canRedo.value = undoManager.canRedo
    }
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
    fun category(id: String, title: String, subs: List<ItemNode>) =
        ItemNode(id = id, title = title, item = Item.Category, children = subs)

    return listOf(
        category("__inbox__", "Inbox", listOf(
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
