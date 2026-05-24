package com.example.todo_tree.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.todo_tree.currentTimeMillis
import com.example.todo_tree.model.TaskNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskSheet(task: TaskNode, onDismiss: () -> Unit, onSave: (String, Long?, Long?) -> Unit) {
    var title by remember { mutableStateOf(task.title) }
    var doMillis by remember { mutableStateOf(task.doDate) }
    var dueMillis by remember { mutableStateOf(task.dueDate) }
    var showDo by remember { mutableStateOf(false) }
    var showDue by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Edit Task", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Do:", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.width(12.dp))
                FilterChip(selected = doMillis != null, onClick = { showDo = true }, label = { Text(doMillis?.let { formatDate(it) } ?: "none") })
                if (doMillis != null) { Spacer(Modifier.width(8.dp)); TextButton(onClick = { doMillis = null }) { Text("clear", color = MaterialTheme.colorScheme.error) } }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Due:", style = MaterialTheme.typography.bodyMedium); Spacer(Modifier.width(8.dp))
                FilterChip(selected = dueMillis != null, onClick = { showDue = true }, label = { Text(dueMillis?.let { formatDate(it) } ?: "none") })
                if (dueMillis != null) { Spacer(Modifier.width(8.dp)); TextButton(onClick = { dueMillis = null }) { Text("clear", color = MaterialTheme.colorScheme.error) } }
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = { onSave(title, doMillis, dueMillis) }, modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 14.dp)) { Text("Save") }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (showDo) datePicker(doMillis, { doMillis = it }) { showDo = false }
    if (showDue) datePicker(dueMillis, { dueMillis = it }) { showDue = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun datePicker(initial: Long?, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { onConfirm(state.selectedDateMillis); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }, text = { DatePicker(state = state) })
}

fun relativeDate(epochMillis: Long): String {
    val today = currentTimeMillis() / 86_400_000L
    val target = epochMillis / 86_400_000L
    val days = (target - today).toInt()
    return when { days < 0 -> "Overdue"; days == 0 -> "Today"; days == 1 -> "Tomorrow"; else -> "in $days days" }
}

fun formatDate(epochMillis: Long): String {
    var y = 1970L; var r = epochMillis / 86_400_000L
    while (true) { val d = if (isLeap(y)) 366L else 365L; if (r < d) break; r -= d; y++ }
    val md = if (isLeap(y)) intArrayOf(31,29,31,30,31,30,31,31,30,31,30,31) else intArrayOf(31,28,31,30,31,30,31,31,30,31,30,31)
    var m = 1; for (dm in md) { if (r < dm) break; r -= dm; m++ }
    return "$y-${m.toString().padStart(2,'0')}-${(r+1).toString().padStart(2,'0')}"
}

private fun isLeap(y: Long) = (y % 4L == 0L && y % 100L != 0L) || (y % 400L == 0L)
