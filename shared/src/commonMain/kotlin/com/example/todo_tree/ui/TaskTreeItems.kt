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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        Box(Modifier.size(20.dp)
            .border(1.5.dp, if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                .background(if (task.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .clickable(onClick = onToggleDone), contentAlignment = Alignment.Center) {
            if (task.isCompleted) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(task.title, modifier = Modifier.weight(1f).clickable(onClick = onEdit),
            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (task.doDate != null) { Spacer(Modifier.width(6.dp)); Text(formatDate(task.doDate), modifier = Modifier.clickable(onClick = onEdit), style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
        if (task.dueDate != null) { Spacer(Modifier.width(6.dp)); Text(relativeDate(task.dueDate), modifier = Modifier.clickable(onClick = onEdit), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, maxLines = 1) }
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
