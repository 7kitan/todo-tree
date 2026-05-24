package com.example.todo_tree.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.todo_tree.viewmodel.TaskViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun TaskTreeScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val forest by viewModel.forest.collectAsState()
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val visibleOrder = remember(forest, expanded) { flattenVisible(forest, expanded) }
    var cursorIndex by remember { mutableStateOf(0) }
    if (visibleOrder.isNotEmpty() && cursorIndex >= visibleOrder.size) cursorIndex = visibleOrder.size - 1
    var pendingEditId by remember { mutableStateOf<String?>(null) }
    var addContext by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    val cursorId = visibleOrder.getOrNull(cursorIndex)?.id

    fun moveUp() { if (cursorIndex > 0) cursorIndex-- }
    fun moveDown() { if (cursorIndex < visibleOrder.size - 1) cursorIndex++ }
    fun moveParent() { if (cursorId != null) findParent(forest, cursorId)?.let { p -> visibleOrder.indexOfFirst { i -> i.id == p.id }.takeIf { it >= 0 }?.let { cursorIndex = it } } }
    fun moveChild() { if (cursorId != null) findTaskById(forest, cursorId)?.let { t -> if (t.subtasks.isNotEmpty()) { if (t.id !in expanded) expanded = expanded + t.id; visibleOrder.indexOfFirst { it.id == t.subtasks.first().id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } }
    fun moveLeft() { if (cursorId != null) getSiblings(forest, cursorId).let { s -> val i = s.indexOfFirst { it.id == cursorId }; if (i > 0) visibleOrder.indexOfFirst { it.id == s[i - 1].id }.takeIf { it >= 0 }?.let { cursorIndex = it } } }
    fun moveRight() { if (cursorId != null) getSiblings(forest, cursorId).let { s -> val i = s.indexOfFirst { it.id == cursorId }; if (i >= 0 && i < s.size - 1) visibleOrder.indexOfFirst { it.id == s[i + 1].id }.takeIf { it >= 0 }?.let { cursorIndex = it } } }

    val rowH = with(LocalDensity.current) { 40.dp.toPx() }
    val padPx = with(LocalDensity.current) { 8.dp.toPx() }
    var vpH by remember { mutableFloatStateOf(0f) }
    val scrollAnim = remember { Animatable(0f) }

    LaunchedEffect(cursorIndex, vpH) {
        if (vpH <= 0f) return@LaunchedEffect
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
        if (ne.size < expanded.size) expanded = ne.toSet()
    }
    LaunchedEffect(pendingEditId) { if (pendingEditId != null) showEdit = true }

    var accY by remember { mutableStateOf(0f) }; var accX by remember { mutableStateOf(0f) }
    val thr = rowH * 0.3f

    Box(modifier = modifier.fillMaxSize().onPreviewKeyEvent { event ->
        handleKey(event, { moveUp() }, { moveDown() }, { moveLeft() }, { moveRight() },
            { if (cursorId != null) viewModel.toggleCompleted(cursorId) }, { pendingEditId = cursorId },
            { if (cursorId != null) viewModel.removeTask(cursorId) }, { moveChild() })
    }.clipToBounds().onSizeChanged { vpH = it.height.toFloat() }) {
        if (visibleOrder.isEmpty()) {
            Text("No tasks — add one below", Modifier.padding(horizontal = 16.dp, vertical = 40.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else Column(Modifier.fillMaxWidth().wrapContentHeight().offset { IntOffset(0, -scrollAnim.value.roundToInt()) }
            .pointerInput(Unit) { detectDragGestures(onDragEnd = { accY = 0f; accX = 0f }, onDragCancel = { accY = 0f; accX = 0f }, onDrag = { ch, da -> ch.consume(); accY += da.y; accX += da.x; if (accY > thr) { moveUp(); accY = 0f }; if (accY < -thr) { moveDown(); accY = 0f }; if (accX > thr) { moveChild(); accX = 0f }; if (accX < -thr) { moveParent(); accX = 0f } }) }
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); val s = e.changes.firstOrNull()?.scrollDelta ?: continue; if (s.y < 0f) moveDown() else if (s.y > 0f) moveUp() } } }
            .padding(top = 8.dp, bottom = 8.dp)) {
            visibleOrder.forEachIndexed { i, item ->
                val t = findTaskById(forest, item.id) ?: return@forEachIndexed
                val d = (i - cursorIndex).coerceIn(-5, 5)
                val strips = (0..item.depth).map { stripPalette[it % stripPalette.size] }
                TaskRow(task = t, strips = strips, hasChildren = t.subtasks.isNotEmpty(), isExpanded = t.id in expanded,
                    isCursor = i == cursorIndex, alpha = (1f - kotlin.math.abs(d) * 0.25f).coerceIn(0f, 1f),
                    onToggle = { expanded = if (t.id in expanded) expanded - t.id else expanded + t.id },
                    onToggleDone = { viewModel.toggleCompleted(t.id) }, onEdit = { pendingEditId = t.id })
            }
        }

        if (showEdit && pendingEditId != null) findTaskById(forest, pendingEditId!!)?.let { task ->
            EditTaskSheet(task, onDismiss = { showEdit = false; pendingEditId = null },
                onSave = { title, doDate, dueDate -> viewModel.updateTask(task.id, title, doDate, dueDate); showEdit = false; pendingEditId = null })
        }
    }
}
