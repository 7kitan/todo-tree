// =============================================================================
//  TASK_TREE_SCREEN.KT
//  Main screen: custom scroll via Animatable, discrete gesture/wheel stepping,
//  keyboard navigation, auto-expand, cursor-centered viewport.
// =============================================================================

package com.example.todo_tree.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.todo_tree.model.TaskNode
import com.example.todo_tree.viewmodel.TaskViewModel
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val animDuration = 250

@Composable
fun TaskTreeScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    // ==== State ====

    val forest by viewModel.forest.collectAsState()
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var inputMode by remember { mutableStateOf<String?>(null) } // "addRoot" | "addSubtask" | "search" | null
    var inputText by remember { mutableStateOf("") }
    val effectiveQuery = remember(inputMode, inputText) { if (inputMode == "search") inputText else "" }
    val visibleOrder = remember(forest, expanded, effectiveQuery) { flattenVisible(forest, expanded, effectiveQuery) }
    var cursorIndex by remember { mutableIntStateOf(0) }
    if (visibleOrder.isNotEmpty() && cursorIndex >= visibleOrder.size) cursorIndex = visibleOrder.size - 1
    var editingId by remember { mutableStateOf<String?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editDoDate by remember { mutableStateOf<Long?>(null) }
    var editDueDate by remember { mutableStateOf<Long?>(null) }
    var doDateEdited by remember { mutableStateOf(false) }
    var dueDateEdited by remember { mutableStateOf(false) }
    var showDoPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    val cursorId = visibleOrder.getOrNull(cursorIndex)?.id
    var fabExpanded by remember { mutableStateOf(false) }
    var screenHeight by remember { mutableFloatStateOf(0f) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }

    // ==== Edit helpers ====

    fun startEdit(taskId: String?) {
        val task = taskId?.let { findTaskById(forest, it) } ?: return
        editingId = taskId
        editTitle = task.title
        editDoDate = task.doDate
        editDueDate = task.dueDate
        doDateEdited = false
        dueDateEdited = false
    }

    // ==== Scroll & animation constants ====

    val rowH = with(LocalDensity.current) { 40.dp.toPx() }
    val padPx = with(LocalDensity.current) { 8.dp.toPx() }
    var vpH by remember { mutableFloatStateOf(0f) }
    val scrollAnim = remember { Animatable(0f) }

    var pendingRemovals by remember { mutableStateOf(setOf<String>()) }

    // ==== Deferred collapse ====

    LaunchedEffect(pendingRemovals) {
        if (pendingRemovals.isEmpty()) return@LaunchedEffect
        delay(1000)
        val anchor = visibleOrder.getOrNull(cursorIndex)?.id
        expanded = expanded - pendingRemovals
        pendingRemovals = emptySet()
        if (anchor != null) {
            val nv = flattenVisible(forest, expanded, effectiveQuery)
            val idx = nv.indexOfFirst { it.id == anchor }
            if (idx >= 0) {
                scrollAnim.snapTo(idx * rowH + padPx + rowH / 2f - vpH * 0.5f)
                cursorIndex = idx
            }
        }
    }

    // ==== Cursor navigation ====

    fun moveUp() { if (cursorIndex > 0) cursorIndex-- }
    fun moveDown() { if (cursorIndex < visibleOrder.size - 1) cursorIndex++ }
    fun moveParent() { cursorId?.let { id -> findParent(forest, id)?.let { p -> visibleOrder.indexOfFirst { i -> i.id == p.id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } }
    fun moveChild() { cursorId?.let { id -> findTaskById(forest, id)?.let { t -> if (t.subtasks.isNotEmpty()) { if (t.id !in expanded) expanded = expanded + t.id; val nv = flattenVisible(forest, expanded, effectiveQuery); nv.indexOfFirst { it.id == t.subtasks.first().id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } } }
    fun moveLeft() { cursorId?.let { id -> getSiblings(forest, id).let { s -> val i = s.indexOfFirst { it.id == id }; if (i > 0) visibleOrder.indexOfFirst { it.id == s[i - 1].id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } }
    fun moveRight() { cursorId?.let { id -> getSiblings(forest, id).let { s -> val i = s.indexOfFirst { it.id == id }; if (i >= 0 && i < s.size - 1) visibleOrder.indexOfFirst { it.id == s[i + 1].id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } }

    // ==== Auto-scroll & auto-expand ====

    LaunchedEffect(cursorIndex, vpH) {
        if (vpH <= 0f || pendingRemovals.isNotEmpty()) return@LaunchedEffect
        scrollAnim.animateTo(cursorIndex * rowH + padPx + rowH / 2f - vpH * 0.5f, tween(200))
    }
    LaunchedEffect(cursorIndex) {
        val id = cursorId ?: return@LaunchedEffect; val task = findTaskById(forest, id) ?: return@LaunchedEffect
        delay(1000)
        if (task.subtasks.isNotEmpty() && task.id !in expanded) expanded = expanded + task.id
        val ne = expanded.filter { eid ->
            if (eid == id) return@filter true
            var cur = id; while (true) { if (cur == eid) break; val p = findParent(forest, cur) ?: break; cur = p.id }; cur == eid
        }
        if (ne.size < expanded.size) {
            pendingRemovals = pendingRemovals + (expanded - ne.toSet())
        }
    }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(inputMode) { if (inputMode != null) focusRequester.requestFocus() }

    // ==== Gesture thresholds ====

    var accY by remember { mutableStateOf(0f) }; var accX by remember { mutableStateOf(0f) }
    val thr = rowH * 0.3f

    // ==== Main layout ====

    Box(modifier = modifier.fillMaxSize().clipToBounds().onSizeChanged { screenHeight = it.height.toFloat() }) {
        if (fabExpanded) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)).clickable { fabExpanded = false })
        }
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).onPreviewKeyEvent { event ->
                handleKey(event, { moveUp() }, { moveDown() }, { moveLeft() }, { moveRight() },
                    { if (cursorId != null) viewModel.toggleCompleted(cursorId) }, { startEdit(cursorId) },
                    { if (cursorId != null) { deleteTargetId = cursorId } },
                    { if (cursorId != null) { inputMode = "addSubtask"; inputText = "" } })
            }.clipToBounds().onSizeChanged { vpH = it.height.toFloat() }) {
                if (visibleOrder.isEmpty()) {
                    Text("No tasks", Modifier.padding(horizontal = 16.dp, vertical = 40.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Column(Modifier.fillMaxWidth().wrapContentHeight().offset { IntOffset(0, -scrollAnim.value.roundToInt()) }
                    .pointerInput(visibleOrder.size) { detectDragGestures(onDragEnd = { accY = 0f; accX = 0f }, onDragCancel = { accY = 0f; accX = 0f }, onDrag = { ch, da -> ch.consume(); accY += da.y; accX += da.x; if (abs(accY) > abs(accX)) { if (accY > thr) { moveUp(); accY = 0f; accX = 0f }; if (accY < -thr) { moveDown(); accY = 0f; accX = 0f } } else { if (accX > thr) { moveChild(); accX = 0f; accY = 0f }; if (accX < -thr) { moveParent(); accX = 0f; accY = 0f } } }) }
                    .pointerInput(visibleOrder.size) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); val s = e.changes.firstOrNull()?.scrollDelta ?: continue; if (s.y < 0f) moveDown() else if (s.y > 0f) moveUp() } } }
                    .padding(top = 8.dp, bottom = 8.dp)) {
                    visibleOrder.forEachIndexed { i, item ->
                        val t = findTaskById(forest, item.id) ?: return@forEachIndexed
                        key(item.id) {
                            AnimatedVisibility(
                                visible = true,
                                enter = expandVertically(tween(animDuration)) + fadeIn(tween(animDuration)),
                            ) {
                                val d = (i - cursorIndex).coerceIn(-5, 5)
                                val strips = (0..item.depth).map { stripPalette[it % stripPalette.size] }
                                if (item.id == editingId) {
                                    EditingTaskRow(
                                        title = editTitle,
                                        onTitleChange = { value: String -> editTitle = value },
                                        visualTransformation = dateHighlightTransformation(),
                                        doDate = editDoDate,
                                        dueDate = editDueDate,
                                        onDoDateClick = { showDoPicker = true },
                                        onDueDateClick = { showDuePicker = true },
                                        onClearDoDate = { editDoDate = null; doDateEdited = true },
                                        onClearDueDate = { editDueDate = null; dueDateEdited = true },
                                        onSave = {
                                            if (editingId != null) {
                                                val p = parseTaskInput(editTitle)
                                                viewModel.updateTask(editingId!!, p.title,
                                                    if (doDateEdited) editDoDate else p.doDate,
                                                    if (dueDateEdited) editDueDate else p.dueDate)
                                                editingId = null
                                            }
                                        },
                                        onCancel = { editingId = null },
                                        strips = strips,
                                    )
                                } else {
                                    TaskRow(task = t, strips = strips, hasChildren = t.subtasks.isNotEmpty(), isExpanded = t.id in expanded,
                                    isCursor = i == cursorIndex, alpha = (1f - abs(d) * 0.25f).coerceIn(0f, 1f),
                                    onToggle = {
                                        val anchor = visibleOrder.getOrNull(cursorIndex)?.id
                                        val wasExpanded = t.id in expanded
                                        if (wasExpanded) {
                                            pendingRemovals = pendingRemovals + t.id
                                            if (anchor != null && isDescendant(forest, t.id, anchor)) {
                                                val idx = visibleOrder.indexOfFirst { it.id == t.id }
                                                if (idx >= 0) cursorIndex = idx
                                            }
                                        } else {
                                            expanded = expanded + t.id
                                        }
                                    },
                                    onToggleDone = { viewModel.toggleCompleted(t.id) }, onEdit = { startEdit(t.id) })
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==== Overlay: today bar / input row ====

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (screenHeight * 0.25f).roundToInt()) }
                .then(if (inputMode != null) Modifier.focusRequester(focusRequester) else Modifier),
        ) {
            if (inputMode != null) {
                val d = visibleOrder.find { it.id == cursorId }?.depth ?: 0
                InputTaskRow(
                    text = inputText,
                    onTextChange = { inputText = it },
                    visualTransformation = dateHighlightTransformation(),
                    onDone = {
                        when (inputMode) {
                            "addRoot" -> if (inputText.isNotBlank()) {
                                val p = parseTaskInput(inputText)
                                viewModel.addRootTask(p.title, p.doDate, p.dueDate)
                                inputMode = null; inputText = ""
                            }
                            "addSubtask" -> if (inputText.isNotBlank() && cursorId != null) {
                                val p = parseTaskInput(inputText)
                                viewModel.addSubtask(cursorId, p.title, p.doDate, p.dueDate)
                                inputMode = null; inputText = ""
                            }
                            else -> {}
                        }
                    },
                    onClose = { inputMode = null; inputText = "" },
                    depth = if (inputMode == "addSubtask") d + 1 else 0,
                    mode = inputMode ?: "",
                )
            } else {
                TodayBar(onSearchClick = { inputMode = "search"; inputText = "" })
            }
        }

        // ==== FAB with radial menu ====

        TaskFab(
            expanded = fabExpanded,
            onToggle = { fabExpanded = !fabExpanded },
            onAddRoot = { inputMode = "addRoot"; inputText = "" },
            onAddSubtask = { inputMode = "addSubtask"; inputText = "" },
            onDelete = { if (cursorId != null) deleteTargetId = cursorId },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(0, -(screenHeight * 0.25f).roundToInt()) }
                .padding(end = 12.dp),
        )

        val density = LocalDensity.current
        if (showDoPicker) datePicker(editDoDate, { editDoDate = it; doDateEdited = true }) { showDoPicker = false }
        if (showDuePicker) datePicker(editDueDate, { editDueDate = it; dueDateEdited = true }) { showDuePicker = false }
    }

    // ==== Dialogs ====

    if (deleteTargetId != null) {
        val task = findTaskById(forest, deleteTargetId!!)
        AlertDialog(
            onDismissRequest = { deleteTargetId = null },
            title = { Text("Delete task") },
            text = { Text("Remove \"${task?.title ?: "?"}\"?") },
            confirmButton = { TextButton(onClick = { viewModel.removeTask(deleteTargetId!!); deleteTargetId = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTargetId = null }) { Text("Cancel") } },
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete completed") },
            text = { Text("Remove all completed tasks?") },
            confirmButton = { TextButton(onClick = { viewModel.deleteCompleted(); showDeleteConfirmDialog = false }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancel") } },
        )
    }
}
