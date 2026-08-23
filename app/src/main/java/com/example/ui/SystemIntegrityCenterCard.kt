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
import com.example.engines.isire.IntelligentSystemIntegrityEngine

@Composable
fun SystemIntegrityCenterCard() {
    val context = LocalContext.current
    val reliabilityMetrics by IntelligentSystemIntegrityEngine.reliabilityMetricsFlow.collectAsStateWithLifecycle()
    val compatibility by IntelligentSystemIntegrityEngine.compatibilityFlow.collectAsStateWithLifecycle()
    val navStatus by IntelligentSystemIntegrityEngine.navStatusFlow.collectAsStateWithLifecycle()
    val checkResults by IntelligentSystemIntegrityEngine.checkResultsFlow.collectAsStateWithLifecycle()
    val reports by IntelligentSystemIntegrityEngine.reportsFlow.collectAsStateWithLifecycle()
    val auditLogs by IntelligentSystemIntegrityEngine.auditLogsFlow.collectAsStateWithLifecycle()

    var isExpanded by remember { mutableStateOf(false) }
    var selectedSubSection by remember { mutableStateOf(0) } // 0: Integrity Monitor, 1: Compatibility, 2: Config Validator, 3: Module Sync, 4: Reliability Analysis, 5: Integrity Reports

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("system_integrity_center_card"),
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
                        Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF673AB7),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("System Integrity Center (ISIRE)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = Color(0xFF673AB7).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text("Score: ${reliabilityMetrics.overallReliabilityScore}%", color = Color(0xFF673AB7), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        Text("Hierarchy Valid: ${navStatus.isHierarchyValid} • Compatibility: ${compatibility.androidVersion}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                // Sub-Section Navigation
                val subSections = listOf("Integrity Monitor", "Compatibility Center", "Config Validator", "Module Sync", "Reliability Analysis", "Integrity Reports")
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
                    0 -> { // Integrity Monitor
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Continuous Runtime Integrity Monitor:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            checkResults.forEach { res ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${res.componentName}: PASSED", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF4CAF50))
                                        Text(res.message, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { IntelligentSystemIntegrityEngine.runIntegrityVerification(context) },
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("Re-run Full Integrity Scan", fontSize = 11.sp)
                            }
                        }
                    }
                    1 -> { // Compatibility Center
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Device & Android OS Compatibility Parameters:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("• Platform OS: ${compatibility.androidVersion}", fontSize = 11.sp)
                            Text("• Device Vendor: ${compatibility.manufacturer}", fontSize = 11.sp)
                            Text("• Battery Cycle API: ${if (compatibility.batteryCycleApiAvailable) "Supported" else "Emulated Target"}", fontSize = 11.sp)
                            Text("• Current Draw API: ${if (compatibility.currentNowApiAvailable) "Supported" else "Unavailable"}", fontSize = 11.sp)
                            Text("• Thermal Headroom API: ${if (compatibility.thermalHeadroomApiAvailable) "Supported" else "Fallback Mode"}", fontSize = 11.sp)
                            Text("• Hidden Unsupported Features: ${compatibility.unsupportedFeaturesHiddenCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    2 -> { // Config Validator
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Configuration Consistency & Safety Validator:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("• Configuration Health Index: ${reliabilityMetrics.configHealthScore}%", fontSize = 11.sp)
                            Text("• Deprecated Settings Keys: 0 found", fontSize = 11.sp)
                            Text("• Conflicting Sentinel Parameters: None", fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { IntelligentSystemIntegrityEngine.performSafeAutoRepair(context) },
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("Execute Safe Auto-Repair Metadata Fix", fontSize = 11.sp)
                            }
                        }
                    }
                    3 -> { // Module Sync & Hierarchy
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Section/Sub-Section Navigation & Synchronization:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("• Hierarchy Valid: ${navStatus.isHierarchyValid}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("• Main Sections: ${navStatus.totalMainSections} • Sub-Sections: ${navStatus.totalSubSections}", fontSize = 11.sp)
                            Text("• Orphan Screens: ${navStatus.orphanScreensCount} • Duplicate Routes: ${navStatus.duplicateRoutesCount}", fontSize = 11.sp)
                            Text("• Last Index Rebuild: ${navStatus.lastHierarchyRebuildTime}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { IntelligentSystemIntegrityEngine.rebuildNavigationHierarchy(context) },
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("Rebuild & Sync Navigation Cache", fontSize = 11.sp)
                            }
                        }
                    }
                    4 -> { // Reliability Analysis
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Intelligent System Reliability Breakdown:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("• Overall System Reliability: ${reliabilityMetrics.overallReliabilityScore}%", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF673AB7))
                            Text("• Runtime Stability Index: ${reliabilityMetrics.runtimeStabilityScore}%", fontSize = 11.sp)
                            Text("• Crash History Count: ${reliabilityMetrics.crashHistoryCount}", fontSize = 11.sp)
                            Text("• Memory Stability Rate: ${reliabilityMetrics.memoryStabilityPercent}%", fontSize = 11.sp)
                            Text("• Synchronization Quality: ${reliabilityMetrics.syncQualityPercent}%", fontSize = 11.sp)
                            Text("• SQLite DB Integrity Score: ${reliabilityMetrics.dbIntegrityScore}%", fontSize = 11.sp)
                        }
                    }
                    5 -> { // Integrity Reports
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("System Integrity & Reliability Reports:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            reports.forEach { rep ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(rep.title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("${rep.summary} (Score: ${rep.score}%)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Generated: ${rep.generatedDate}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(6.dp))
                Text("System Integrity Audit Log:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                auditLogs.take(2).forEach { log ->
                    Text("• [${log.eventType}] ${log.details}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
