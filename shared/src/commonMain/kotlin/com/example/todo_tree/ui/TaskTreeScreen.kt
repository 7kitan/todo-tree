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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val animDuration = 250

@Composable
fun TaskTreeScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier) {
    val forest by viewModel.forest.collectAsState()
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val visibleOrder = remember(forest, expanded) { flattenVisible(forest, expanded) }
    var cursorIndex by remember { mutableIntStateOf(0) }
    if (visibleOrder.isNotEmpty() && cursorIndex >= visibleOrder.size) cursorIndex = visibleOrder.size - 1
    var pendingEditId by remember { mutableStateOf<String?>(null) }
    var showEdit by remember { mutableStateOf(false) }
    val cursorId = visibleOrder.getOrNull(cursorIndex)?.id

    val rowH = with(LocalDensity.current) { 40.dp.toPx() }
    val padPx = with(LocalDensity.current) { 8.dp.toPx() }
    var vpH by remember { mutableFloatStateOf(0f) }
    val scrollAnim = remember { Animatable(0f) }

    // ==== Deferred collapse (toggle + pruning) ====
    // Both manual toggle and auto-pruning write to pendingRemovals instead of
    // mutating expanded directly. This gives the 1s cursor-settle window before
    // the collapse takes effect.  Without this window, the cursor would land on
    // a stale index after the visible list shrinks — the scroll tween would
    // fight the layout shift, producing a visible "snap" when jumping to a
    // subtree further down the list (where pruned branches lie above the
    // cursor). With the 1s defer, the cursor finishes its 200ms scroll tween,
    // sits at the target for ~800ms, then the collapse applies instantly with a
    // scrollAnim.snapTo (no tween) — no fight, no snap.
    var pendingRemovals by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(pendingRemovals) {
        if (pendingRemovals.isEmpty()) return@LaunchedEffect
        delay(1000)
        val anchor = visibleOrder.getOrNull(cursorIndex)?.id
        expanded = expanded - pendingRemovals
        pendingRemovals = emptySet()
        if (anchor != null) {
            val nv = flattenVisible(forest, expanded)
            val idx = nv.indexOfFirst { it.id == anchor }
            if (idx >= 0) {
                scrollAnim.snapTo(idx * rowH + padPx + rowH / 2f - vpH * 0.5f)
                cursorIndex = idx
            }
        }
    }

    fun moveUp() { if (cursorIndex > 0) cursorIndex-- }
    fun moveDown() { if (cursorIndex < visibleOrder.size - 1) cursorIndex++ }
    fun moveParent() { cursorId?.let { id -> findParent(forest, id)?.let { p -> visibleOrder.indexOfFirst { i -> i.id == p.id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } }
    fun moveChild() { cursorId?.let { id -> findTaskById(forest, id)?.let { t -> if (t.subtasks.isNotEmpty()) { if (t.id !in expanded) expanded = expanded + t.id; val nv = flattenVisible(forest, expanded); nv.indexOfFirst { it.id == t.subtasks.first().id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } } }
    fun moveLeft() { cursorId?.let { id -> getSiblings(forest, id).let { s -> val i = s.indexOfFirst { it.id == id }; if (i > 0) visibleOrder.indexOfFirst { it.id == s[i - 1].id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } }
    fun moveRight() { cursorId?.let { id -> getSiblings(forest, id).let { s -> val i = s.indexOfFirst { it.id == id }; if (i >= 0 && i < s.size - 1) visibleOrder.indexOfFirst { it.id == s[i + 1].id }.takeIf { it >= 0 }?.let { cursorIndex = it } } } }

    // Cursor-centered scroll: suppressed during pendingRemovals so the scroll
    // tween doesn't fight the pending layout change.  The removal handler below
    // uses snapTo after the defer — no tween needed since the cursor has already
    // settled.
    LaunchedEffect(cursorIndex, vpH) {
        if (vpH <= 0f || pendingRemovals.isNotEmpty()) return@LaunchedEffect
        scrollAnim.animateTo(cursorIndex * rowH + padPx + rowH / 2f - vpH * 0.5f, tween(200))
    }
    // Auto-expand: after cursor rests 1s on an unexpanded task, open it.
    // Pruning: mark non-ancestor branches for animated removal.
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
    // Deferred show to let composition settle before opening the edit sheet
    LaunchedEffect(pendingEditId) { if (pendingEditId != null) showEdit = true }

    var accY by remember { mutableStateOf(0f) }; var accX by remember { mutableStateOf(0f) }
    val thr = rowH * 0.3f

    Box(modifier = modifier.fillMaxSize().onPreviewKeyEvent { event ->
        handleKey(event, { moveUp() }, { moveDown() }, { moveLeft() }, { moveRight() },
            { if (cursorId != null) viewModel.toggleCompleted(cursorId) }, { pendingEditId = cursorId },
            { if (cursorId != null) viewModel.removeTask(cursorId) }, { moveChild() })
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
                    // Enter animation for expansion (items appear smoothly when a
                    // parent is expanded or auto-expanded).  No exit animation:
                    // collapse is handled by pendingRemovals — items stay fully
                    // visible during the 1s defer, then are removed atomically
                    // when expanded changes (no gradual shrink/fade).
                    AnimatedVisibility(
                        visible = true,
                        enter = expandVertically(tween(animDuration)) + fadeIn(tween(animDuration)),
                    ) {
                        val d = (i - cursorIndex).coerceIn(-5, 5)
                        val strips = (0..item.depth).map { stripPalette[it % stripPalette.size] }
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
                            onToggleDone = { viewModel.toggleCompleted(t.id) }, onEdit = { pendingEditId = t.id })
                    }
                }
            }
        }

        if (showEdit && pendingEditId != null) findTaskById(forest, pendingEditId!!)?.let { task ->
            EditTaskSheet(task, onDismiss = { showEdit = false; pendingEditId = null },
                onSave = { title, doDate, dueDate -> viewModel.updateTask(task.id, title, doDate, dueDate); showEdit = false; pendingEditId = null })
        }
    }
}
