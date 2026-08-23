package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.BatteryViewModel

@Composable
fun InsightsSection(viewModel: BatteryViewModel) {
    val state by viewModel.sanitizedBatteryState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        InsightCard("BATTERY HEALTH", state.health)
        Spacer(modifier = Modifier.height(16.dp))
        InsightCard("CHARGING ANALYSIS", "Average: ${state.avgWatt} W")
        Spacer(modifier = Modifier.height(16.dp))
        InsightCard("TEMPERATURE ANALYSIS", "Peak: ${state.highestTemp} °C")
        Spacer(modifier = Modifier.height(16.dp))
        InsightCard("POWER ANALYSIS", "Average: ${state.avgWatt} W")
    }
}

@Composable
fun InsightCard(title: String, insight: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray)
            Text(insight, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}
