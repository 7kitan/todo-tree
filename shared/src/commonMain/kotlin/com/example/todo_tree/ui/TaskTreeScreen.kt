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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
fun TaskTreeScreen(viewModel: TaskViewModel, modifier: Modifier = Modifier, onThemeToggle: () -> Unit = {}) {
    // ==== State ====

    val forest by viewModel.forest.collectAsState()
    var expanded by remember { mutableStateOf(setOf("__inbox__")) }
    var inputMode by remember { mutableStateOf<String?>(null) } // "add" | "search" | null
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    val effectiveQuery = remember(inputMode, inputText.text) { if (inputMode == "search") inputText.text else "" }
    val visibleOrder = remember(forest, expanded, effectiveQuery) { flattenVisible(forest, expanded, effectiveQuery) }
    var cursorIndex by remember { mutableIntStateOf(0) }
    if (visibleOrder.isNotEmpty() && cursorIndex >= visibleOrder.size) cursorIndex = visibleOrder.size - 1
    var editingId by remember { mutableStateOf<String?>(null) }
    var pendingFocusId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingFocusId, forest, expanded) {
        val id = pendingFocusId ?: return@LaunchedEffect
        val idx = flattenVisible(forest, expanded, effectiveQuery).indexOfFirst { it.id == id }
        if (idx >= 0) { cursorIndex = idx; pendingFocusId = null }
    }
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
    var scrollAcc by remember { mutableFloatStateOf(0f) }
    var dragStartY by remember { mutableFloatStateOf(0f) }
    val thr = rowH * 0.5f
    val scrollThr = rowH * 0.4f

    // ==== Main layout ====

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clipToBounds().onSizeChanged { screenHeight = it.height.toFloat() }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = { swipeResetCounter++; editingId = null },
        )) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).onPreviewKeyEvent { event ->
                handleKey(event, { moveUp() }, { moveDown() }, { moveLeft() }, { moveRight() },
                    { if (cursorId != null) viewModel.toggleCompleted(cursorId) }, { startEdit(cursorId) },
                    { if (cursorId != null) { deleteTargetId = cursorId } },
                    { if (cursorId != null) { inputMode = "add"; inputText = TextFieldValue("") } })
            }.clipToBounds().onSizeChanged { vpH = it.height.toFloat() }) {
                if (visibleOrder.isEmpty()) {
                    Text("No tasks", Modifier.padding(horizontal = 16.dp, vertical = 40.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Column(Modifier.fillMaxWidth().wrapContentHeight().offset { IntOffset(0, -scrollAnim.value.roundToInt()) }
                    .pointerInput(visibleOrder.size) { detectDragGestures(onDragStart = { dragStartY = it.y }, onDragEnd = { if (abs(accY) < thr * 0.5f && dragStartY > screenHeight * 0.75f) swipeResetCounter++; accY = 0f }, onDragCancel = { accY = 0f }, onDrag = { ch, da -> ch.consume(); accY += da.y; if (abs(accY) > thr) { if (accY > thr) { moveUp(); accY = 0f }; if (accY < -thr) { moveDown(); accY = 0f } } }) }
                    .pointerInput(visibleOrder.size) { awaitPointerEventScope { while (true) { val e = awaitPointerEvent(); val s = e.changes.firstOrNull()?.scrollDelta ?: continue; scrollAcc += s.y; if (scrollAcc > scrollThr) { moveDown(); scrollAcc = 0f }; if (scrollAcc < -scrollThr) { moveUp(); scrollAcc = 0f } } } }
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

        // ==== Overlay: today bar / input row + fuzzy dropdown ====

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (screenHeight * 0.25f).roundToInt()) }
                .then(if (inputMode != null) Modifier.focusRequester(focusRequester) else Modifier),
        ) {
            Column {
                if (inputMode != null) {
                    val d = visibleOrder.find { it.id == cursorId }?.depth ?: 0
                    InputTaskRow(
                        text = inputText,
                        onTextChange = { inputText = it },
                        visualTransformation = dateHighlightTransformation(),
                        onDone = {
                            if (inputText.text.isNotBlank()) {
                                val p = parseTaskInput(inputText.text)
                                if (p.removeCatTitle != null) {
                                    val name = p.removeCatTitle.replace("_", " ")
                                    val cat = findTaskByTitle(forest, name)
                                        ?: findTasksByTitleFuzzy(forest, name, 2).singleOrNull()
                                    if (cat != null) deleteTargetId = cat.id
                                    inputMode = null; inputText = TextFieldValue("")
                                } else if (p.moveTarget != null && cursorId != null) {
                                    val target = p.moveTarget.replace("_", " ")
                                    val found = findTaskByTitle(forest, target)
                                        ?: findTasksByTitleFuzzy(forest, target, 2).singleOrNull()
                                    if (found != null && found.id != cursorId) {
                                        expanded = expanded + found.id
                                        viewModel.moveTo(cursorId, found.id)
                                    }
                                    inputMode = null; inputText = TextFieldValue("")
                                } else {
                                    val finalItem = when {
                                        p.item is Item.Category -> Item.Category
                                        p.item is Item.Project -> Item.Project(dueDate = p.dueDate)
                                        else -> Item.Task(doDate = p.doDate, dueDate = p.dueDate)
                                    }
                                    val ref = p.parentRef?.replace("_", " ")
                                    val newId = if (ref != null) {
                                        findTaskByTitle(forest, ref)?.let { parent ->
                                            expanded = expanded + parent.id
                                            viewModel.addSubtask(parent.id, p.title, finalItem)
                                        } ?: run { expanded = expanded + "__inbox__"; viewModel.addInboxChild(p.title, finalItem) }
                                    } else if (cursorId != null) {
                                        expanded = expanded + cursorId
                                        viewModel.addSubtask(cursorId, p.title, finalItem)
                                    } else {
                                        expanded = expanded + "__inbox__"
                                        viewModel.addInboxChild(p.title, finalItem)
                                    }
                                    inputMode = null; inputText = TextFieldValue("")
                                    pendingFocusId = newId
                                }
                            }
                        },
                        onClose = { inputMode = null; inputText = TextFieldValue("") },
                        depth = if (cursorId != null) (visibleOrder.find { it.id == cursorId }?.depth ?: 0) + 1 else 0,
                        mode = when (inputMode) {
                            "search" -> "search"
                            else -> if (cursorId != null) "addSubtask" else "addRoot"
                        },
                    )
                } else {
                    TodayBar(onSearchClick = { inputMode = "search"; inputText = TextFieldValue("") }, onThemeToggle = onThemeToggle)
                }

                // ==== Fuzzy dropdown for #removecat ====

                val catMatches = remember(inputText.text, forest) {
                    val p = parseTaskInput(inputText.text)
                    val q = p.removeCatTitle
                    if (q.isNullOrBlank()) emptyList()
                    else findTasksByTitleFuzzy(forest, q, 5).filter { it.item is Item.Category && it.id != "__inbox__" }
                }
                if (catMatches.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 4.dp,
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column {
                            catMatches.forEach { match ->
                                val ancestry = remember(match.id, forest) { breadcrumb(forest, match.id) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val t = "#removecat ${match.title.replace(" ", "_")}"
                                            inputText = TextFieldValue(t, selection = TextRange(t.length))
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(text = match.title, style = MaterialTheme.typography.bodyMedium)
                                    if (ancestry.size > 1) {
                                        Text(text = ancestry.dropLast(1).joinToString(" > "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // ==== Fuzzy dropdown for #moveto ====

                val moveTargetName = remember(inputText.text) {
                    val p = parseTaskInput(inputText.text)
                    p.moveTarget
                }
                val moveMatches = remember(inputText.text, forest) {
                    val q = moveTargetName ?: return@remember emptyList()
                    if (cursorId == null) return@remember emptyList()
                    val descendants = mutableSetOf<String>()
                    fun collect(nodes: List<ItemNode>) { for (n in nodes) { descendants.add(n.id); collect(n.children) } }
                    findTaskById(forest, cursorId)?.let { collect(it.children) }
                    descendants.add(cursorId)
                    findTasksByTitleFuzzy(forest, q, 5).filter { it.id !in descendants }
                }
                if (moveMatches.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 4.dp,
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column {
                            moveMatches.forEach { match ->
                                val ancestry = remember(match.id, forest) { breadcrumb(forest, match.id) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val t = "#moveto ${match.title.replace(" ", "_")}"
                                            inputText = TextFieldValue(t, selection = TextRange(t.length))
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(text = match.title, style = MaterialTheme.typography.bodyMedium)
                                    if (ancestry.size > 1) {
                                        Text(text = ancestry.dropLast(1).joinToString(" > "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // ==== Fuzzy dropdown for #word parent ref ====

                val refToken = remember(inputText.text) {
                    val m = Regex("""(?:^|\s+)#(\w+)""").find(inputText.text)
                    val t = m?.groupValues?.get(1)?.lowercase() ?: return@remember null
                    if (t in listOf("category", "cat", "project", "proj", "removecat", "rmcat", "moveto", "mt")) null else t
                }
                val refMatches = remember(inputText.text, forest) {
                    val t = refToken ?: return@remember emptyList()
                    findTasksByTitleFuzzy(forest, t, 5)
                }
                if (refMatches.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 4.dp,
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column {
                            refMatches.forEach { match ->
                                val ancestry = remember(match.id, forest) { breadcrumb(forest, match.id) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val hashPattern = Regex("""(?:^|\s+)#(\w+)""")
                                            val m = hashPattern.find(inputText.text) ?: return@clickable
                                            val prefix = inputText.text.substring(0, m.range.first)
                                            val suffix = inputText.text.substring(m.range.last + 1)
                                            val newText = "${prefix}#${match.title.replace(" ", "_")}$suffix "
                                            inputText = TextFieldValue(newText, selection = TextRange(newText.length))
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(text = match.title, style = MaterialTheme.typography.bodyMedium)
                                    if (ancestry.size > 1) {
                                        Text(text = ancestry.dropLast(1).joinToString(" > "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==== Bottom bar: undo / FAB / redo ====

        val canUndo by viewModel.canUndo.collectAsState()
        val canRedo by viewModel.canRedo.collectAsState()

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UndoButton(
                onClick = { viewModel.undo() },
                enabled = canUndo,
            )
            TaskFab(
                onClick = { inputMode = "add"; inputText = TextFieldValue("") },
            )
            RedoButton(
                onClick = { viewModel.redo() },
                enabled = canRedo,
            )
        }

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
