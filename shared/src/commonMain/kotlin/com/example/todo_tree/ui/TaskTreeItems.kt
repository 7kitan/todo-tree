// =============================================================================
//  TASK_TREE_ITEMS.KT
//  TaskRow composable: nesting strips, cursor highlight, checkbox, date labels.
// =============================================================================

package com.example.todo_tree.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.example.todo_tree.currentTimeMillis
import com.example.todo_tree.model.TaskNode

val stripPalette = listOf(
    Color(0xFF569CD6), Color(0xFF6A9955), Color(0xFFD7BA7D),
    Color(0xFFCE9178), Color(0xFFC586C0), Color(0xFFD16969),
)

@Composable
fun TaskRow(task: TaskNode, strips: List<Color>, hasChildren: Boolean, isExpanded: Boolean,
    isCursor: Boolean, alpha: Float, onToggle: () -> Unit, onToggleDone: () -> Unit, onEdit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp).graphicsLayer { this.alpha = alpha }
            .then(if (isCursor) Modifier.border(1.dp, Color(0xFF569CD6)) else Modifier)
            .background(if (isCursor) Color(0x18FFFFFF) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        strips.forEach { color -> Box(Modifier.width(4.dp).fillMaxHeight().background(color)) }
        Spacer(Modifier.width(6.dp))
        if (!hasChildren) {
            Box(Modifier.size(20.dp)
                .border(1.5.dp, if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                    .background(if (task.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                .clickable(onClick = onToggleDone), contentAlignment = Alignment.Center) {
                if (task.isCompleted) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
            }
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(task.title, modifier = Modifier.weight(1f).clickable(onClick = onEdit),
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (task.dueDate != null) { Spacer(Modifier.width(6.dp)); Text(relativeDate(task.dueDate), modifier = Modifier.clickable(onClick = onEdit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, maxLines = 1) }
        else if (task.doDate != null) { Spacer(Modifier.width(6.dp)); Text(relativeDate(task.doDate), modifier = Modifier.clickable(onClick = onEdit), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF6A9955), maxLines = 1) }
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

@Composable
fun EditingTaskRow(
    title: String,
    onTitleChange: (String) -> Unit,
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
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            strips.forEach { color -> Box(Modifier.width(4.dp).fillMaxHeight().background(color)) }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(20.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
            Spacer(Modifier.width(4.dp))
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

@Composable
fun TodayBar() {
    val now = currentTimeMillis()
    val todayDays = now / 86_400_000L
    val dayNames = arrayOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")
    val weekday = dayNames[(todayDays % 7).toInt()]
    Row(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(stripPalette[0]))
        Box(Modifier.width(4.dp).fillMaxHeight().background(stripPalette[1]))
        Spacer(Modifier.width(6.dp))
        Text(
            "Today, $weekday ${formatDate(now)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun InputTaskRow(
    text: String,
    onTextChange: (String) -> Unit,
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
        Box(Modifier.size(20.dp).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Spacer(Modifier.width(4.dp))
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
                if (text.isEmpty()) Text(
                    when (mode) {
                        "addRoot" -> "Add root task\u2026"
                        "addSubtask" -> "Add subtask\u2026"
                        "search" -> "Search tasks\u2026"
                        else -> ""
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
        Spacer(Modifier.width(4.dp))
        Box(Modifier.size(28.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
        }
    }
}
