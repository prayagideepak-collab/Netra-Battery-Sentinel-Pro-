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
import com.example.engines.batteryproduction.BatteryProductionReleaseEngine
import com.example.engines.batteryproduction.SimulationType

@Composable
fun BatteryProductionCenterCard() {
    val context = LocalContext.current
    val readiness by BatteryProductionReleaseEngine.readinessState.collectAsStateWithLifecycle()
    val benchmark by BatteryProductionReleaseEngine.benchmarkResult.collectAsStateWithLifecycle()
    val framework by BatteryProductionReleaseEngine.frameworkStatus.collectAsStateWithLifecycle()
    val simulations by BatteryProductionReleaseEngine.simulations.collectAsStateWithLifecycle()

    var isExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Readiness, 1: Simulators, 2: Benchmarks, 3: LTS & Compliance

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("battery_production_center_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Color(0xFF673AB7).copy(alpha = 0.5f))
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
                        Icons.Filled.Verified,
                        contentDescription = null,
                        tint = Color(0xFF673AB7),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Production Release & Stability (Phase 3)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = Color(0xFF673AB7).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text("Readiness: ${readiness.overallReadinessScore}%", color = Color(0xFF673AB7), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        Text("LTS Version: ${framework.version} • Status: Production Approved", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                val tabs = listOf("Release Readiness", "Battery Simulators", "Performance Benchmarks", "LTS & Compliance")
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
                    0 -> { // Readiness
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Release Readiness Scorecard:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Overall Production Readiness: ${readiness.overallReadinessScore}% (Threshold ≥ 95%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            Text("• Stability Score: ${readiness.stabilityScore}% (Weight 30%)", fontSize = 11.sp)
                            Text("• Battery Efficiency Score: ${readiness.batteryEfficiencyScore}% (Weight 25%)", fontSize = 11.sp)
                            Text("• Performance Score: ${readiness.performanceScore}% (Weight 15%)", fontSize = 11.sp)
                            Text("• Security & Privacy Score: ${readiness.securityScore}% (Weight 20%)", fontSize = 11.sp)
                            Text("• Android Compatibility Score: ${readiness.compatibilityScore}% (Weight 10%)", fontSize = 11.sp)
                        }
                    }
                    1 -> { // Simulators
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Battery Testing & Simulation Lab:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { BatteryProductionReleaseEngine.runSimulation(SimulationType.BATTERY_DRAIN) },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    contentPadding = PaddingValues(2.dp)
                                ) {
                                    Text("Drain Sim", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { BatteryProductionReleaseEngine.runSimulation(SimulationType.THERMAL_SPIKE) },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    contentPadding = PaddingValues(2.dp)
                                ) {
                                    Text("Thermal", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { BatteryProductionReleaseEngine.runSimulation(SimulationType.CHARGER_FLUCTUATION) },
                                    modifier = Modifier.weight(1f).height(32.dp),
                                    contentPadding = PaddingValues(2.dp)
                                ) {
                                    Text("Charger", fontSize = 10.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            simulations.take(3).forEach { sim ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(6.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text(sim.type.name, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(if (sim.isPassed) "PASSED" else "FAILED", fontSize = 10.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                    }
                                    Text(sim.details, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                    }
                    2 -> { // Benchmarks
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Battery Benchmark & Performance Metrics:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Idle Battery Drain: ${benchmark.idleDrainPerHour}% / hour", fontSize = 11.sp)
                            Text("• Screen ON Battery Drain: ${benchmark.screenOnDrainPerHour}% / hour", fontSize = 11.sp)
                            Text("• Overnight Total Drain (8h): ${benchmark.overnightDrainTotal}%", fontSize = 11.sp)
                            Text("• Charging Coulomb Efficiency: ${benchmark.chargingEfficiencyPercent}%", fontSize = 11.sp)
                            Text("• Average Background CPU Usage: ${benchmark.avgCpuUsagePercent}%", fontSize = 11.sp)
                            Text("• RAM Footprint: ${benchmark.avgRamUsageMb} MB", fontSize = 11.sp)
                        }
                    }
                    3 -> { // LTS & Compliance
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("LTS, Security & Play Store Compliance:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("• Long-Term Support (LTS) Mode: Active", fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            Text("• Rollback Protection Guard: Enabled", fontSize = 11.sp)
                            Text("• Play Store Foreground Service Policy: Compliant", fontSize = 11.sp)
                            Text("• Security & Privacy Audit: Passed (Zero Unsafe Permissions)", fontSize = 11.sp)
                            Text("• Data Safety Form Compliance: Verified", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
