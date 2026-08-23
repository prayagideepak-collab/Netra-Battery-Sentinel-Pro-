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
import com.example.engines.release.IntelligentReleaseEngine

@Composable
fun ProductionReleaseCenterCard() {
    val context = LocalContext.current
    val releaseState by IntelligentReleaseEngine.releaseStateFlow.collectAsStateWithLifecycle()
    val modules by IntelligentReleaseEngine.moduleRegistryFlow.collectAsStateWithLifecycle()
    val startupValidation by IntelligentReleaseEngine.startupValidationFlow.collectAsStateWithLifecycle()
    val migrationInfo by IntelligentReleaseEngine.migrationInfoFlow.collectAsStateWithLifecycle()
    val backupSync by IntelligentReleaseEngine.backupSyncFlow.collectAsStateWithLifecycle()
    val healthMetrics by IntelligentReleaseEngine.healthMetricsFlow.collectAsStateWithLifecycle()
    val stabilityTrend by IntelligentReleaseEngine.stabilityTrendFlow.collectAsStateWithLifecycle()
    val baseline by IntelligentReleaseEngine.performanceBaselineFlow.collectAsStateWithLifecycle()
    val suiteResults by IntelligentReleaseEngine.suiteResultsFlow.collectAsStateWithLifecycle()
    val auditEntries by IntelligentReleaseEngine.auditEntriesFlow.collectAsStateWithLifecycle()

    var isExpanded by remember { mutableStateOf(false) }
    var selectedSubSection by remember { mutableStateOf(0) } // 0: Module Registry, 1: Validation Suite, 2: Migration & Backups, 3: Health & Baseline, 4: Stability & Crash, 5: Audit Log

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("production_release_center_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, Color(0xFFE91E63).copy(alpha = 0.5f))
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
                        Icons.Filled.RocketLaunch,
                        contentDescription = null,
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Production Release Center (PRRLSF)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = Color(0xFFE91E63).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text("Health: ${healthMetrics.overallHealthScore}%", color = Color(0xFFE91E63), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        Text("Ver: ${releaseState.versionName} • Channel: ${releaseState.channel.name} • Architecture Frozen", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                // Sub-Section Chips
                val subSections = listOf("Module Registry", "Validation Suite", "Migration & Backup", "Health & Baseline", "Crash & Stability", "Release Audit Log")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(subSections.size) { index ->
                        FilterChip(
                            selected = selectedSubSection == index,
                            onClick = { selectedSubSection = index },
                            label = { Text(subSections[index], fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (selectedSubSection) {
                    0 -> { // Module Registry
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Central Netra Module Registry (${modules.size} Core Engines):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            modules.forEach { mod ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${mod.moduleName} (v${mod.version})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("Category: ${mod.category.name} • State: ${mod.runtimeState}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text(mod.integrityStatus, color = Color(0xFF4CAF50), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // Validation Suite
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("14-Point Automated Release Validation Suite:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            suiteResults.forEach { test ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("• ${test.testName} (${test.category}): PASSED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                        Text(test.details, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("${test.executionTimeMs}ms", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { IntelligentReleaseEngine.runReleaseValidationSuite(context) },
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("Run Full 14-Point Release Suite Test", fontSize = 11.sp)
                            }
                        }
                    }
                    2 -> { // Migration & Backup
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Version Migration & Backup Synchronization:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("• Migration State: ${migrationInfo.details}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("• Local Backup Available: ${backupSync.localBackupAvailable} (Last: ${backupSync.lastLocalBackupTime})", fontSize = 11.sp)
                            Text("• Google Drive Cloud Sync: ${if (backupSync.cloudBackupEnabled) "Enabled (Opt-In)" else "Disabled"}", fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { IntelligentReleaseEngine.toggleCloudBackup(!backupSync.cloudBackupEnabled) },
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Text(if (backupSync.cloudBackupEnabled) "Disable Drive Sync" else "Enable Drive Sync", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { IntelligentReleaseEngine.performVersionMigration("2.9.8", "3.0.0-PROD") },
                                    modifier = Modifier.weight(1f).height(36.dp)
                                ) {
                                    Text("Simulate Migration", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    3 -> { // Health & Baseline
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Production Health Score & Performance Baseline:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("• Overall Production Health: ${healthMetrics.overallHealthScore}%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFE91E63))
                            Text("• Cold Startup Baseline: ${baseline.startupTimeMs} ms (Target: < 300 ms)", fontSize = 11.sp)
                            Text("• Memory Footprint: ${baseline.memoryUsageMb} MB", fontSize = 11.sp)
                            Text("• Background CPU Usage: ${baseline.cpuUsagePercent}%", fontSize = 11.sp)
                            Text("• Battery Impact Baseline: ${baseline.batteryConsumptionPercentPerHour}% / hr", fontSize = 11.sp)
                            Text("• DB Query Latency: ${baseline.dbQueryTimeMs} ms", fontSize = 11.sp)
                        }
                    }
                    4 -> { // Crash & Stability
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Intelligent Crash Protection & Stability Monitor:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("• Crash Rate: ${stabilityTrend.crashFrequency} crashes (${healthMetrics.crashRatePercent}%)", fontSize = 11.sp)
                            Text("• ANR Frequency: ${stabilityTrend.anrFrequency} instances", fontSize = 11.sp)
                            Text("• Service Uptime Rate: ${stabilityTrend.serviceUptimePercent}%", fontSize = 11.sp, color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            Text("• Memory Growth Rate: ${stabilityTrend.memoryGrowthRate}", fontSize = 11.sp)
                            Text("• SQLite DB Growth Rate: ${stabilityTrend.dbGrowthRate}", fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { IntelligentReleaseEngine.executeStartupValidation(context) },
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("Re-Validate Cold Startup State", fontSize = 11.sp)
                            }
                        }
                    }
                    5 -> { // Audit Log
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Release Audit Trail:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            auditEntries.take(4).forEach { entry ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(6.dp)
                                ) {
                                    Text("[${entry.actionType}] ${entry.details}", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
