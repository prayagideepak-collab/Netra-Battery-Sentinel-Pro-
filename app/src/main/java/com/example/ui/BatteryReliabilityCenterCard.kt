package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.engines.batteryreliability.BatteryReliabilityManager

@Composable
fun BatteryReliabilityCenterCard() {
    val context = LocalContext.current
    val reliabilityState by BatteryReliabilityManager.reliabilityState.collectAsStateWithLifecycle()
    val engineHealth by BatteryReliabilityManager.engineHealthList.collectAsStateWithLifecycle()
    val aiInsights by BatteryReliabilityManager.aiInsights.collectAsStateWithLifecycle()
    val prediction by BatteryReliabilityManager.predictionModel.collectAsStateWithLifecycle()
    val perfMetrics by BatteryReliabilityManager.performanceMetrics.collectAsStateWithLifecycle()

    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Engine Health, 1: AI Insights, 2: Predictions, 3: Performance Monitor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("battery_reliability_center_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Color(0xFF009688).copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.HealthAndSafety,
                        contentDescription = null,
                        tint = Color(0xFF009688),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Battery Reliability & AI (Phase 2)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = Color(0xFF009688).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text("Score: ${reliabilityState.overallHealthScore}%", color = Color(0xFF009688), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        Text("Exception Shield Triggers: ${reliabilityState.exceptionShieldTriggers} • Recoveries: ${reliabilityState.totalRecoveriesPerformed}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                val tabs = listOf("Engine Health (BRM)", "Battery AI Insights", "Smart Predictions", "Performance Monitor")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tabs.size) { index ->
                        FilterChip(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            label = { Text(tabs[index], fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (selectedTab) {
                    0 -> { // Engine Health
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Smart Watchdog & Engine Health Status:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            engineHealth.forEach { eng ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${eng.engineName}: ${eng.statusSummary}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("Faults: ${eng.faultCount} • Last Level: ${eng.lastRecoveryLevel}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text("ACTIVE", color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // AI Insights
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Battery AI Evidence-Based Insights:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            aiInsights.forEach { ins ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(ins.title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        Text("Confidence: ${ins.confidence}", fontSize = 10.sp, color = Color(0xFF009688), fontWeight = FontWeight.Bold)
                                    }
                                    Text(ins.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    2 -> { // Predictions
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Battery Prediction Engine Model:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Estimated Remaining Battery Time: ${prediction.remainingBatteryTimeMinutes} minutes", fontSize = 11.sp)
                            Text("• Time to Full Charge: ${prediction.timeToFullChargeMinutes} minutes (${prediction.chargingCompletionEstimate})", fontSize = 11.sp)
                            Text("• Daily Battery Drain Trend: ${prediction.dailyDrainTrendPercent}% / day", fontSize = 11.sp)
                            Text("• Prediction Confidence Level: ${prediction.confidence}", fontSize = 11.sp, color = Color(0xFF009688), fontWeight = FontWeight.Bold)
                        }
                    }
                    3 -> { // Performance Monitor
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Performance Monitor & Resource Metrics:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Background CPU Usage: ${perfMetrics.cpuUsagePercent}%", fontSize = 11.sp)
                            Text("• RAM Footprint: ${perfMetrics.ramUsageMb} MB", fontSize = 11.sp)
                            Text("• Accumulated Background Runtime: ${perfMetrics.backgroundRuntimeHours} hours", fontSize = 11.sp)
                            Text("• Wakeups / Hour: ${perfMetrics.wakeupsPerHour}", fontSize = 11.sp)
                            Text("• Notification Latency: ${perfMetrics.notificationLatencyMs} ms", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
