package com.example.todo_tree.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GamePad(contextLabel: String, onSubmit: (String) -> Unit, onToggle: () -> Unit,
    onEdit: () -> Unit, onAddSub: () -> Unit, onDelete: () -> Unit, onNewRoot: () -> Unit, hasCursor: Boolean) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = onToggle, enabled = hasCursor) { Text("Toggle") }
            TextButton(onClick = onEdit, enabled = hasCursor) { Text("Edit") }
            TextButton(onClick = onAddSub, enabled = hasCursor) { Text("Sub") }
            TextButton(onClick = onDelete, enabled = hasCursor) { Text("Del") }
            TextButton(onClick = onNewRoot) { Text("New") }
        }
        Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(contextLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("Task title…") },
                        modifier = Modifier.weight(1f), singleLine = true, textStyle = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { val t = text.trim(); if (t.isNotEmpty()) { onSubmit(t); text = "" } },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), enabled = text.isNotBlank()) { Text("Add") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
