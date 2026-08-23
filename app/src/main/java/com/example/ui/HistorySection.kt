package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.BatteryViewModel

@Composable
fun HistorySection(viewModel: BatteryViewModel) {
    var selectedMetric by remember { mutableStateOf("Battery") }
    val sessions by viewModel.sessions.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("HISTORY", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Placeholder for Graph
        Card(modifier = Modifier.fillMaxWidth().height(200.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Graph for $selectedMetric (Placeholder)")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text("Recent Sessions", fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        LazyColumn {
            items(sessions) { session ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("Session: ${session.id}", modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
