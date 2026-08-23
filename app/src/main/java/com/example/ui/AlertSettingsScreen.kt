package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.BatteryAlert
import com.example.viewmodel.BatteryViewModel

@Composable
fun AlertsDialog(viewModel: BatteryViewModel, onDismiss: () -> Unit) {
    val alerts by viewModel.allBatteryAlerts.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Battery Alerts") },
        text = {
            Column {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(alerts) { alert ->
                        ListItem(
                            headlineContent = { Text("Alert at ${alert.batteryLevel}%") },
                            supportingContent = { Text(alert.voicePrompt) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteBatteryAlert(alert) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        )
                    }
                }
                Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Add New Alert")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
    
    if (showAddDialog) {
        var level by remember { mutableStateOf("20") }
        var prompt by remember { mutableStateOf("") }
        var isBelow by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Battery Alert") },
            text = {
                Column {
                    OutlinedTextField(value = level, onValueChange = { level = it }, label = { Text("Level (%)") })
                    OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Voice Prompt") })
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Alert if below")
                        Checkbox(checked = isBelow, onCheckedChange = { isBelow = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val alertLevel = level.toIntOrNull() ?: 20
                    viewModel.addBatteryAlert(BatteryAlert(batteryLevel = alertLevel, isBelow = isBelow, voicePrompt = prompt))
                    showAddDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
