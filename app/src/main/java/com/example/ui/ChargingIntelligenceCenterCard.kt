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
import com.example.engines.charging.ChargingIntelligenceEngine

@Composable
fun ChargingIntelligenceCenterCard() {
    val context = LocalContext.current
    val chargingState by ChargingIntelligenceEngine.chargingState.collectAsStateWithLifecycle()
    val chargingMode by com.example.engines.charging.ChargingStateManager.currentMode.collectAsStateWithLifecycle()

    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Live Charging, 1: Targets & Overcharge, 2: History & Summary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("charging_intelligence_center_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f))
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
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Charging Intelligence Upgrade", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = if (chargingState.isCharging) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    if (chargingState.isCharging) chargingMode.name else "NOT CHARGING",
                                    color = if (chargingState.isCharging) Color(0xFF4CAF50) else Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text("ETA: ${chargingState.timeToFullChargeMinutes}m • Overcharge: ${chargingState.overchargeSeconds}s", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                val tabs = listOf("Live Charging", "Targets & Overcharge", "History & Summary")
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
                    0 -> { // Live Charging
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Live Charging Status & Intelligence:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Charging Status: ${if (chargingState.isCharging) "Charging (${chargingState.chargingType})" else "Not Charging"}", fontSize = 11.sp)
                            Text("• Charging Current: ${chargingState.chargingCurrentMa} mA (${String.format("%.2f", chargingState.chargingPowerW)} W)", fontSize = 11.sp)
                            Text("• Time to Full Charge (ETA): ${chargingState.timeToFullChargeMinutes} minutes", fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("USB Data Transfer Mode (Suppress Slow Warning)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Switch(
                                    checked = chargingState.isUsbDataTransferActive,
                                    onCheckedChange = { ChargingIntelligenceEngine.toggleUsbDataTransfer(it) }
                                )
                            }
                        }
                    }
                    1 -> { // Targets & Overcharge
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Charging Target & Overcharge Timer:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Select Target Charge Level:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(80, 85, 90, 95, 100).forEach { target ->
                                    FilterChip(
                                        selected = chargingState.targetChargePercent == target,
                                        onClick = { ChargingIntelligenceEngine.setTargetCharge(target) },
                                        label = { Text("$target%", fontSize = 10.sp) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("• Target Reached: ${if (chargingState.targetReached) "YES (Overcharge Timer Active)" else "NO"}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (chargingState.targetReached) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface)
                            Text("• Overcharge Duration: ${chargingState.overchargeSeconds} seconds", fontSize = 11.sp)
                        }
                    }
                    2 -> { // History & Summary
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Last Disconnect Summary:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(chargingState.lastDisconnectSummary, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Charging Session History (${chargingState.sessionHistory.size} sessions):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            if (chargingState.sessionHistory.isEmpty()) {
                                Text("No recorded charging sessions yet.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                chargingState.sessionHistory.take(3).forEach { sess ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                            .padding(6.dp)
                                    ) {
                                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                            Text("Type: ${sess.chargingType}", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                            Text("${sess.initialBatteryPercent}% → ${sess.finalBatteryPercent}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("Duration: ${sess.durationSeconds / 60}m • Overcharge: ${sess.overchargeDurationSeconds}s", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
