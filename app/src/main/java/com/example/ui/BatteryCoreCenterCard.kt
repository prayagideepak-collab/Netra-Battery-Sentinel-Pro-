package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import com.example.engines.batterycore.BatteryCoordinator

@Composable
fun BatteryCoreCenterCard() {
    val context = LocalContext.current
    val status by BatteryCoordinator.statusFlow.collectAsStateWithLifecycle()
    val capability by BatteryCoordinator.capabilityFlow.collectAsStateWithLifecycle()
    val baseline by BatteryCoordinator.baselineFlow.collectAsStateWithLifecycle()

    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("battery_core_center_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Color(0xFF2196F3).copy(alpha = 0.5f))
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
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Battery Core Architecture (Phase 1)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = Color(0xFF2196F3).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text("OEM: ${capability.detectedManufacturer}", color = Color(0xFF2196F3), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        Text("Active Modules: ${status.activeModulesCount} • Event-Driven Runtime Active", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Core Architecture & Runtime Foundation:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Battery Coordinator: Active & Synchronized", fontSize = 11.sp)
                    Text("• Event-Driven Polling: Enabled (Zero Polling Spam)", fontSize = 11.sp)
                    Text("• Duplicate Service / Receiver Protection: Enforced", fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                    Text("• Thermal Headroom API: ${if (capability.isThermalHeadroomSupported) "Supported" else "Fallback Mode"}", fontSize = 11.sp)
                    Text("• Charging Optimization: ${if (capability.isChargingOptimizationSupported) "Supported" else "Standard"}", fontSize = 11.sp)

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Runtime Performance Baseline:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("• Cold Startup Latency: ${baseline.startupTimeMs} ms (Target < 300ms)", fontSize = 11.sp)
                    Text("• Charging Detection Latency: ${baseline.chargingDetectionLatencyMs} ms", fontSize = 11.sp)
                    Text("• Thermal Callback Latency: ${baseline.thermalCallbackLatencyMs} ms", fontSize = 11.sp)
                    Text("• Baseline Memory Footprint: ${baseline.baselineMemoryMb} MB", fontSize = 11.sp)
                }
            }
        }
    }
}
