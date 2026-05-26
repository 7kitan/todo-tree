// =============================================================================
//  TASK_SHEET.KT
//  Modal bottom sheet for editing task fields + date picker + date utilities.
// =============================================================================

package com.example.todo_tree.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import com.example.todo_tree.currentTimeMillis
import com.example.todo_tree.model.DAY_MS
import com.example.todo_tree.model.Item
import com.example.todo_tree.model.ItemNode
import com.example.todo_tree.model.doDate
import com.example.todo_tree.model.dueDate
// calendar math via shared DateHelpers (epochDaysToYmd, daysInMonth)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskSheet(task: ItemNode, onDismiss: () -> Unit, onSave: (String, Long?, Long?) -> Unit) {
    var title by remember { mutableStateOf(task.title) }
    var doMillis by remember { mutableStateOf<Long?>(task.doDate) }
    var dueMillis by remember { mutableStateOf<Long?>(task.dueDate) }
    var showDo by remember { mutableStateOf(false) }
    var showDue by remember { mutableStateOf(false) }
    val isProject = task.item is Item.Project
    val isCategory = task.item is Item.Category

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
internal fun datePicker(initial: Long?, onConfirm: (Long?) -> Unit, onDismiss: () -> Unit) {
    val todayStart = (currentTimeMillis() / DAY_MS) * DAY_MS
    var showCustom by remember { mutableStateOf(false) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayStart
            override fun isSelectableYear(year: Int): Boolean = true
        }
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(12.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("Select date", style = MaterialTheme.typography.titleSmall) },
        text = {
            Column {
                if (showCustom) {
                    DatePicker(state = state)
                } else {
                    DateRow("Today", onClick = { onConfirm(todayStart); onDismiss() })
                    DateRow("Tomorrow", onClick = { onConfirm(todayStart + DAY_MS); onDismiss() })
                    DateRow("Next week", onClick = { onConfirm(todayStart + 7 * DAY_MS); onDismiss() })
                    if (initial != null) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        DateRow("No date", onClick = { onConfirm(null); onDismiss() })
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    DateRow("Custom date", onClick = { showCustom = true })
                }
            }
        },
        confirmButton = { if (showCustom) TextButton(onClick = { onConfirm(state.selectedDateMillis); onDismiss() }) { Text("Done") } },
        dismissButton = { TextButton(onClick = if (showCustom) {{ showCustom = false }} else onDismiss) { Text(if (showCustom) "Back" else "Cancel") } }
    )
}

@Composable
private fun DateRow(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp)) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

fun relativeDate(epochMillis: Long): String {
    val today = currentTimeMillis() / DAY_MS
    val target = epochMillis / DAY_MS
    val days = (target - today).toInt()
    val dayNames = arrayOf("Thu", "Fri", "Sat", "Sun", "Mon", "Tue", "Wed")
    return when {
        days < 0 -> {
            val ago = -days
            when {
                ago == 1 -> "Overdue (Yesterday)"
                ago in 2..6 -> "Overdue ($ago days ago)"
                else -> "Overdue (${formatDate(epochMillis)})"
            }
        }
        days == 0 -> "Today"
        days == 1 -> "Tomorrow"
        days in 2..6 -> "this ${dayNames[(target % 7).toInt()]}"
        days in 7..13 -> "next ${dayNames[(target % 7).toInt()]}"
        else -> formatDate(epochMillis)
    }
}

fun formatDate(epochMillis: Long): String {
    val curYear = epochDaysToYmd(currentTimeMillis() / DAY_MS).year
    val ymd = epochDaysToYmd(epochMillis / DAY_MS)
    val mmdd = "${ymd.month.toString().padStart(2,'0')}-${ymd.day.toString().padStart(2,'0')}"
    return if (ymd.year == curYear) mmdd else "${ymd.year}-${mmdd}"
}
