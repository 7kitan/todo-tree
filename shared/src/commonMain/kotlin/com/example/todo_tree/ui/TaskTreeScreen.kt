// =============================================================================
//  TASK_TREE_SCREEN.KT
//  Main screen: custom scroll via Animatable, keyboard nav, auto-expand,
//  swipe actions per row, unified add mode, inline edit with type awareness.
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
import com.example.todo_tree.model.Item
import com.example.todo_tree.model.ItemNode
import com.example.todo_tree.model.TaskState
import com.example.todo_tree.model.doDate
import com.example.todo_tree.model.dueDate
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
    var inputMode by remember { mutableStateOf<String?>(null) } // "add" | "search" | null
    var inputText by remember { mutableStateOf("") }
    val effectiveQuery = remember(inputMode, inputText) { if (inputMode == "search") inputText else "" }
    val visibleOrder = remember(forest, expanded, effectiveQuery) { flattenVisible(forest, expanded, effectiveQuery) }
    var cursorIndex by remember { mutableIntStateOf(0) }
    if (visibleOrder.isNotEmpty() && cursorIndex >= visibleOrder.size) cursorIndex = visibleOrder.size - 1
    var editingId by remember { mutableStateOf<String?>(null) }
    var editTitle by remember { mutableStateOf("") }
    var editDoDate by remember { mutableStateOf<Long?>(null) }
    var editDueDate by remember { mutableStateOf<Long?>(null) }
    var editItem by remember { mutableStateOf<Item>(Item.Task()) }
    var doDateEdited by remember { mutableStateOf(false) }
    var dueDateEdited by remember { mutableStateOf(false) }
    var showDoPicker by remember { mutableStateOf(false) }
    var showDuePicker by remember { mutableStateOf(false) }
    val cursorId = visibleOrder.getOrNull(cursorIndex)?.id
    var screenHeight by remember { mutableFloatStateOf(0f) }
    var deleteTargetId by remember { mutableStateOf<String?>(null) }
    var swipeResetCounter by remember { mutableIntStateOf(0) }

    // ==== Edit helpers ====

    fun startEdit(taskId: String?) {
        val node = taskId?.let { findTaskById(forest, it) } ?: return
        editingId = taskId
        editTitle = node.title
        editDoDate = node.doDate
        editDueDate = node.dueDate
        editItem = node.item
        doDateEdited = false
        dueDateEdited = false
    }

    fun buildItemFromEdit(p: ParsedTaskInput, currentItem: Item?, finalDoDate: Long?, finalDueDate: Long?): Item = when {
        currentItem is Item.Category -> Item.Category
        p.item is Item.Category -> Item.Category
        p.item is Item.Project -> Item.Project(
            state = (currentItem as? Item.Project)?.state ?: TaskState.Active,
            dueDate = finalDueDate,
        )
        currentItem is Item.Project -> Item.Project(
            state = currentItem.state,
            dueDate = finalDueDate,
        )
        else -> Item.Task(doDate = finalDoDate, dueDate = finalDueDate)
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

    // ==== Reset swipe/edit on cursor change ====

    LaunchedEffect(cursorIndex) {
        if (cursorIndex >= 0) {
            swipeResetCounter++
            editingId = null
        }
    }

    // ==== Cursor navigation ====

    fun moveUp() { if (cursorIndex > 0) cursorIndex-- }
    fun moveDown() { if (cursorIndex < visibleOrder.size - 1) cursorIndex++ }
    fun moveParent() { cursorId?.let { id -> findParent(forest, id)?.let { p -> visibleOrder.indexOfFirst { i -> i.id == p.id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } }
    fun moveChild() { cursorId?.let { id -> findTaskById(forest, id)?.let { t -> if (t.children.isNotEmpty()) { if (t.id !in expanded) expanded = expanded + t.id; val nv = flattenVisible(forest, expanded, effectiveQuery); nv.indexOfFirst { it.id == t.children.first().id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } } }
    fun moveLeft() { cursorId?.let { id -> getSiblings(forest, id).let { s -> val i = s.indexOfFirst { it.id == id }; if (i > 0) visibleOrder.indexOfFirst { it.id == s[i - 1].id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } }
    fun moveRight() { cursorId?.let { id -> getSiblings(forest, id).let { s -> val i = s.indexOfFirst { it.id == id }; if (i >= 0 && i < s.size - 1) visibleOrder.indexOfFirst { it.id == s[i + 1].id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } }

    // ==== Auto-scroll & auto-expand ====

    LaunchedEffect(cursorIndex, vpH) {
        if (vpH <= 0f || pendingRemovals.isNotEmpty()) return@LaunchedEffect
        scrollAnim.animateTo(cursorIndex * rowH + padPx + rowH / 2f - vpH * 0.5f, tween(200))
        delay(100)
        val id = cursorId ?: return@LaunchedEffect; val node = findTaskById(forest, id) ?: return@LaunchedEffect
        if (node.children.isNotEmpty() && node.id !in expanded) expanded = expanded + node.id
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

    var accY by remember { mutableStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    val thr = rowH * 0.3f

    // ==== Main layout ====

    Box(modifier = modifier.fillMaxSize().clipToBounds().onSizeChanged { screenHeight = it.height.toFloat() }) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).onPreviewKeyEvent { event ->
                handleKey(event, { moveUp() }, { moveDown() }, { moveLeft() }, { moveRight() },
                    { if (cursorId != null) viewModel.toggleCompleted(cursorId) }, { startEdit(cursorId) },
                    { if (cursorId != null) { deleteTargetId = cursorId } },
                    { if (cursorId != null) { inputMode = "add"; inputText = "" } })
            }.clipToBounds().onSizeChanged { vpH = it.height.toFloat() }) {
                if (visibleOrder.isEmpty()) {
                    Text("No tasks", Modifier.padding(horizontal = 16.dp, vertical = 40.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Column(Modifier.fillMaxWidth().wrapContentHeight().offset { IntOffset(0, -scrollAnim.value.roundToInt()) }
                    .pointerInput(visibleOrder.size) { detectDragGestures(onDragStart = { dragStartY = it.y }, onDragEnd = { if (abs(accY) < thr * 0.5f && dragStartY > screenHeight * 0.75f) swipeResetCounter++; accY = 0f }, onDragCancel = { accY = 0f }, onDrag = { ch, da -> ch.consume(); accY += da.y; if (abs(accY) > thr) { if (accY > thr) { moveUp(); accY = 0f }; if (accY < -thr) { moveDown(); accY = 0f } } }) }
                    .pointerInput(visibleOrder.size) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); val s = e.changes.firstOrNull()?.scrollDelta ?: continue; if (s.y < 0f) moveDown() else if (s.y > 0f) moveUp() } } }
                    .padding(top = 8.dp, bottom = 8.dp)) {
                    visibleOrder.forEachIndexed { i, item ->
                        val node = findTaskById(forest, item.id) ?: return@forEachIndexed
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
                                        item = editItem,
                                        doDate = editDoDate,
                                        dueDate = editDueDate,
                                        onDoDateClick = { showDoPicker = true },
                                        onDueDateClick = { showDuePicker = true },
                                        onClearDoDate = { editDoDate = null; doDateEdited = true },
                                        onClearDueDate = { editDueDate = null; dueDateEdited = true },
                                        onSave = {
                                            if (editingId != null) {
                                                val p = parseTaskInput(editTitle)
                                                val task = findTaskById(forest, editingId!!)
                                                val finalDoDate = if (doDateEdited) editDoDate else p.doDate
                                                val finalDueDate = if (dueDateEdited) editDueDate else p.dueDate
                                                val newItem = buildItemFromEdit(p, task?.item, finalDoDate, finalDueDate)
                                                viewModel.updateTask(editingId!!, p.title, newItem)
                                                editingId = null
                                            }
                                        },
                                        onCancel = { editingId = null },
                                        strips = strips,
                                    )
                                } else {
                                    val isTask = node.item is Item.Task
                                    val rowContent = @Composable {
                                        TaskRow(node = node, strips = strips, hasChildren = node.children.isNotEmpty(), isExpanded = node.id in expanded,
                                            isCursor = i == cursorIndex, alpha = (1f - abs(d) * 0.25f).coerceIn(0f, 1f),
                                            onToggle = {
                                                val anchor = visibleOrder.getOrNull(cursorIndex)?.id
                                                val wasExpanded = node.id in expanded
                                                if (wasExpanded) {
                                                    pendingRemovals = pendingRemovals + node.id
                                                    if (anchor != null && isDescendant(forest, node.id, anchor)) {
                                                        val idx = visibleOrder.indexOfFirst { it.id == node.id }
                                                        if (idx >= 0) cursorIndex = idx
                                                    }
                                                } else {
                                                    expanded = expanded + node.id
                                                }
                                            },
                                            onEdit = if (i == cursorIndex) ({ startEdit(node.id) }) else null)
                                    }

                                    if (isTask) {
                                        val taskItem = node.item
                                        SwipeActionsRow(
                                            onDone = { viewModel.toggleCompleted(node.id) },
                                            onWaiting = {
                                                val newState = when (taskItem.state) {
                                                    is TaskState.Waiting -> TaskState.Active
                                                    else -> TaskState.Waiting
                                                }
                                                viewModel.updateTask(node.id, node.title, taskItem.copy(state = newState))
                                            },
                                            onDelete = { deleteTargetId = node.id },
                                            resetCount = swipeResetCounter,
                                            enabled = i == cursorIndex,
                                            modifier = Modifier.fillMaxWidth(),
                                            content = rowContent,
                                        )
                                    } else {
                                        Box(Modifier.fillMaxWidth()) { rowContent() }
                                    }
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
                        if (inputText.isNotBlank()) {
                            val p = parseTaskInput(inputText)
                            val finalItem = when {
                                p.item is Item.Category -> Item.Category
                                p.item is Item.Project -> Item.Project(dueDate = p.dueDate)
                                else -> Item.Task(doDate = p.doDate, dueDate = p.dueDate)
                            }
                            if (p.parentRef != null) {
                                findTaskByTitle(forest, p.parentRef)?.let { parent ->
                                    viewModel.addSubtask(parent.id, p.title, finalItem)
                                } ?: viewModel.addRootTask(p.title, finalItem)
                            } else if (cursorId != null) {
                                viewModel.addSubtask(cursorId, p.title, finalItem)
                            } else {
                                viewModel.addRootTask(p.title, finalItem)
                            }
                            inputMode = null; inputText = ""
                        }
                    },
                    onClose = { inputMode = null; inputText = "" },
                    depth = if (cursorId != null) (visibleOrder.find { it.id == cursorId }?.depth ?: 0) + 1 else 0,
                    mode = inputMode ?: "",
                )
            } else {
                TodayBar(onSearchClick = { inputMode = "search"; inputText = "" })
            }
        }

        // ==== FAB (single button) ====

        TaskFab(
            onClick = { inputMode = "add"; inputText = "" },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 12.dp),
        )

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
}
