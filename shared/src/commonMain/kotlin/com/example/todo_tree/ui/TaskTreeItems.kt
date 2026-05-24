// =============================================================================
//  TASK_TREE_ITEMS.KT
//  TaskRow composable: nesting strips, cursor highlight, date labels,
//  TaskState display, Category rules. No checkbox — tap title to edit.
// =============================================================================

package com.example.todo_tree.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import com.example.todo_tree.currentTimeMillis
import com.example.todo_tree.model.doDate
import com.example.todo_tree.model.dueDate
import com.example.todo_tree.model.isCategory
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.example.todo_tree.model.Item
import com.example.todo_tree.model.ItemNode
import com.example.todo_tree.model.TaskState

val stripPalette = listOf(
    Color(0xFF569CD6), Color(0xFF6A9955), Color(0xFFD7BA7D),
    Color(0xFFCE9178), Color(0xFFC586C0), Color(0xFFD16969),
)

// ==== Task Row ====

@Composable
fun TaskRow(node: ItemNode, strips: List<Color>, hasChildren: Boolean, isExpanded: Boolean,
    isCursor: Boolean, alpha: Float, onToggle: () -> Unit, onEdit: (() -> Unit)? = null,
    waitingBadge: @Composable (() -> Unit)? = null) {

    val titleColor = when {
        node.isCategory -> MaterialTheme.colorScheme.onSurfaceVariant
        node.item is Item.Project -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val isDone = when (val i = node.item) {
        is Item.Task -> i.state is TaskState.Done
        is Item.Project -> i.state is TaskState.Done
        is Item.Category -> false
    }
    val isWaiting = when (val i = node.item) {
        is Item.Task -> i.state is TaskState.Waiting
        else -> false
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).graphicsLayer { this.alpha = alpha }
            .then(if (isCursor) Modifier.border(1.dp, Color(0xFF569CD6)) else Modifier)
            .background(if (isCursor) Color(0x18FFFFFF) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        strips.forEach { color -> Box(Modifier.width(4.dp).fillMaxHeight().background(color)) }
        Spacer(Modifier.width(6.dp))
        val editMod = if (onEdit != null) Modifier.clickable(onClick = onEdit) else Modifier
        Text(node.title, modifier = Modifier.weight(1f).then(editMod),
            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
            color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else titleColor,
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (node.isCategory) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isWaiting) {
            Spacer(Modifier.width(4.dp))
            Box(Modifier.background(Color(0xFFFFC107), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text("Waiting", style = MaterialTheme.typography.labelSmall, color = Color(0xFF1C1B1F))
            }
        }
        val doDate = if (!node.isCategory) node.doDate else null
        val dueDate = node.dueDate
        if (doDate != null && dueDate != null) {
            Spacer(Modifier.width(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = editMod) {
                Text(relativeDate(doDate), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6A9955), maxLines = 1)
                Text(" | ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(relativeDate(dueDate), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, maxLines = 1)
            }
        } else if (dueDate != null) {
            Spacer(Modifier.width(6.dp))
            Text(relativeDate(dueDate), modifier = editMod,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, maxLines = 1)
        } else if (doDate != null) {
            Spacer(Modifier.width(6.dp))
            Text(relativeDate(doDate), modifier = editMod,
                style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6A9955), maxLines = 1)
        }
        Spacer(Modifier.width(4.dp))
        if (hasChildren) {
            Box(Modifier.size(28.dp).clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                Icon(if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    if (isExpanded) "Collapse" else "Expand", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
    }
}

// ==== Inline Edit Row ====

@Composable
fun EditingTaskRow(
    title: String,
    onTitleChange: (String) -> Unit,
    item: Item,
    doDate: Long?,
    dueDate: Long?,
    onDoDateClick: () -> Unit,
    onDueDateClick: () -> Unit,
    onClearDoDate: () -> Unit,
    onClearDueDate: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    strips: List<Color>,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val isCategory = item is Item.Category
    val isProject = item is Item.Project

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            strips.forEach { color -> Box(Modifier.width(4.dp).fillMaxHeight().background(color)) }
            Spacer(Modifier.width(6.dp))
            BasicTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.weight(1f).onKeyEvent { event ->
                    if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) { onSave(); true }
                    else if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) { onCancel(); true }
                    else false
                },
                singleLine = true,
                visualTransformation = visualTransformation,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(4.dp))
            Box(Modifier.size(28.dp).clickable(onClick = onSave), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, "Save", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.size(28.dp).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, "Cancel", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(4.dp))
        }
        Spacer(Modifier.width(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            strips.forEach { color -> Box(Modifier.width(4.dp).fillMaxHeight().background(color)) }
            Spacer(Modifier.width(6.dp))
            if (!isProject && !isCategory) {
                Text("Do:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = doDate != null,
                    onClick = onDoDateClick,
                    label = { Text(doDate?.let { formatDate(it) } ?: "none", style = MaterialTheme.typography.bodyMedium) },
                )
                if (doDate != null) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onClearDoDate, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("clear", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.width(12.dp))
            }
            if (!isCategory) {
                Text("Due:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = dueDate != null,
                    onClick = onDueDateClick,
                    label = { Text(dueDate?.let { formatDate(it) } ?: "due", style = MaterialTheme.typography.bodyMedium) },
                )
                if (dueDate != null) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = onClearDueDate, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("clear", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

// ==== Today Bar ====

@Composable
fun TodayBar(onSearchClick: () -> Unit = {}) {
    val now = currentTimeMillis()
    val todayDays = now / 86_400_000L
    val dayNames = arrayOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")
    val weekday = dayNames[(todayDays % 7).toInt()]
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).clickable(onClick = onSearchClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(stripPalette[0]))
        Box(Modifier.width(4.dp).fillMaxHeight().background(stripPalette[1]))
        Spacer(Modifier.width(6.dp))
        Text(
            "Today, $weekday ${formatDate(now)}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(Modifier.size(28.dp).clickable(onClick = onSearchClick), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Search, "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(4.dp))
    }
}

// ==== Add / Search Input Row ====

@Composable
fun InputTaskRow(
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit,
    depth: Int,
    mode: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val strips = (0..depth).map { stripPalette[it % stripPalette.size] }
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        strips.forEach { color -> Box(Modifier.width(4.dp).fillMaxHeight().background(color)) }
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f).onKeyEvent { event ->
                if (event.key == Key.Enter && event.type == KeyEventType.KeyDown) { onDone(); true }
                else false
            },
            singleLine = true,
            visualTransformation = visualTransformation,
            textStyle = MaterialTheme.typography.bodyMedium,
            decorationBox = { inner ->
                if (text.text.isEmpty()) Text(
                    when (mode) {
                        "search" -> "Search tasks\u2026"
                        else -> "Add / command\u2026"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                inner()
            },
        )
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(28.dp).clickable(onClick = onDone), contentAlignment = Alignment.Center) {
            Icon(
                if (mode == "search") Icons.Filled.Search else Icons.Filled.Add,
                "Submit",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        if (mode != "search") {
            Spacer(Modifier.width(4.dp))
            var showHelp by remember { mutableStateOf(false) }
            Box {
                Box(Modifier.size(28.dp).clickable(onClick = { showHelp = true }), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Info, "Help", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
                if (showHelp) {
                    Popup(
                        onDismissRequest = { showHelp = false },
                        alignment = Alignment.TopEnd,
                    ) {
                        Surface(
                            shadowElevation = 8.dp,
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Syntax", style = MaterialTheme.typography.labelLarge)
                                Spacer(Modifier.height(6.dp))
                                val entries = listOf(
                                    "<title>" to "Add as subtask of focused item",
                                    "#cat / #category" to "Category type",
                                    "#proj / #project" to "Project type",
                                    "#removecat / #rmcat" to "Delete a category",
                                    "#moveto / #mt" to "Move item to target",
                                    "#word" to "Parent reference",
                                    "do <date>" to "Do (start) date",
                                    "due <date>" to "Due date",
                                    "<date>" to "Do date (short)",
                                )
                                entries.forEach { (token, desc) ->
                                    Row {
                                        Text(token, modifier = Modifier.width(140.dp), style = MaterialTheme.typography.bodySmall)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(28.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
    }
}
