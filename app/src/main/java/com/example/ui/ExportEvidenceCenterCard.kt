package com.example.ui

import android.content.Context
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
import com.example.engines.ieee.IntelligentExportEvidenceEngine
import com.example.engines.ieee.PrivacyFilterOptions
import com.example.viewmodel.BatteryViewModel
import com.example.viewmodel.LoggerState

@Composable
fun ExportEvidenceCenterCard(viewModel: BatteryViewModel? = null) {
    val context = LocalContext.current
    val exportHistory by IntelligentExportEvidenceEngine.exportHistoryFlow.collectAsStateWithLifecycle()
    val evidencePackages by IntelligentExportEvidenceEngine.evidencePackageFlow.collectAsStateWithLifecycle()
    val privacyOptions by IntelligentExportEvidenceEngine.privacyOptionsFlow.collectAsStateWithLifecycle()
    val verificationCert by IntelligentExportEvidenceEngine.verificationCertFlow.collectAsStateWithLifecycle()
    val auditLogs by IntelligentExportEvidenceEngine.auditLogsFlow.collectAsStateWithLifecycle()

    val loggerState = if (viewModel != null) {
        viewModel.loggerState.collectAsStateWithLifecycle().value
    } else {
        LoggerState.ACTIVE
    }

    val batteryEvents = if (viewModel != null) {
        viewModel.allBatteryEvents.collectAsStateWithLifecycle().value
    } else {
        emptyList()
    }

    val auditRecords = if (viewModel != null) {
        viewModel.allSystemAuditRecords.collectAsStateWithLifecycle().value
    } else {
        emptyList()
    }

    val appActivities = if (viewModel != null) {
        viewModel.allAppActivity.collectAsStateWithLifecycle().value
    } else {
        emptyList()
    }

    val totalRecords = batteryEvents.size + auditRecords.size + appActivities.size

    var isExpanded by remember { mutableStateOf(false) }
    var selectedSubSection by remember { mutableStateOf(0) } // 0: Quick Export, 1: Advanced Export, 2: Export History, 3: Evidence Package, 4: Share Center, 5: Data Verification

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("export_evidence_center_card"),
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
                        Icons.Filled.FolderZip,
                        contentDescription = null,
                        tint = Color(0xFF009688),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Export & Evidence Center (IEEE)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            val (badgeText, badgeColor) = when (loggerState) {
                                LoggerState.INITIALIZING -> "Logger: INITIALIZING" to Color(0xFFFFA000)
                                LoggerState.ERROR -> "Logger: ERROR · Persistence unavailable" to Color(0xFFD32F2F)
                                LoggerState.ACTIVE -> "Logger: ACTIVE · $totalRecords records" to Color(0xFF009688)
                                else -> "Logger: ACTIVE" to Color(0xFF009688)
                            }
                            
                            Surface(color = badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                Text(badgeText, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        
                        val subText = when (loggerState) {
                            LoggerState.INITIALIZING -> "Initializing secure audit trail..."
                            LoggerState.ERROR -> "No database log records can be retrieved."
                            LoggerState.ACTIVE -> "Packages: ${evidencePackages.size} • History: ${exportHistory.size} • Cert: ${verificationCert.integrityStatus}"
                            else -> "Logger operational."
                        }
                        Text(subText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                // Sub-Section Navigation
                val subSections = listOf("Quick Export", "Advanced Export", "Export History", "Evidence Package", "Share Center", "Data Verification")
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
                    0 -> { // Quick Export
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("1-Tap Quick Export Actions:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { IntelligentExportEvidenceEngine.performQuickExport(context, "Today's Report", "PDF") },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    Text("Today's Report", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { IntelligentExportEvidenceEngine.performQuickExport(context, "Today's Logs", "TXT") },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    Text("Today's Logs", fontSize = 10.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { IntelligentExportEvidenceEngine.performQuickExport(context, "Battery Report", "CSV") },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    Text("Battery Report", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { IntelligentExportEvidenceEngine.performQuickExport(context, "Diagnostic Report", "PDF") },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    Text("Diagnostic Report", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    1 -> { // Advanced Export
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Custom Filtered Dataset Export:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("• Date Filter: Last 7 Days (Configurable)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("• Categories: Power, Thermal, Recovery, Hardware, AI", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("• Severity Filter: All Levels (Info, Warning, Critical)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { IntelligentExportEvidenceEngine.performQuickExport(context, "Advanced_Filtered_Dataset", "CSV") },
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("Export Filtered Dataset (CSV)", fontSize = 11.sp)
                            }
                        }
                    }
                    2 -> { // Export History
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Recent Export History Log:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            exportHistory.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${item.exportName} [${item.format}]", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                        Text("${item.recordCount} records • ${item.fileSizeKb} KB • Duration: ${item.durationMs}ms", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(color = Color(0xFF4CAF50).copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                                        Text(item.status, color = Color(0xFF4CAF50), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                    3 -> { // Evidence Package
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Unified Diagnostic & Telemetry Evidence Packages:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            evidencePackages.forEach { pkg ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(pkg.title, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    Text("Modules: ${pkg.includedModules.joinToString(", ")}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text("Records: ${pkg.totalRecords} • Size: ${pkg.packageSizeKb} KB • SHA256: ${pkg.checksumSha256.take(16)}...", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { IntelligentExportEvidenceEngine.buildEvidencePackage(context) },
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("Generate New Evidence Package", fontSize = 11.sp)
                            }
                        }
                    }
                    4 -> { // Share Center & Privacy
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Privacy Protection Settings (Sanitized Sharing):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = privacyOptions.hideDeviceId,
                                    onCheckedChange = { IntelligentExportEvidenceEngine.updatePrivacyOptions(privacyOptions.copy(hideDeviceId = it)) }
                                )
                                Text("Sanitize Device Serial / Hardware Identifier", fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = privacyOptions.hideBluetoothNames,
                                    onCheckedChange = { IntelligentExportEvidenceEngine.updatePrivacyOptions(privacyOptions.copy(hideBluetoothNames = it)) }
                                )
                                Text("Sanitize Connected Bluetooth Device Names", fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = privacyOptions.hideApproxLocation,
                                    onCheckedChange = { IntelligentExportEvidenceEngine.updatePrivacyOptions(privacyOptions.copy(hideApproxLocation = it)) }
                                )
                                Text("Strip Cell Tower / Network Metadata", fontSize = 11.sp)
                            }
                        }
                    }
                    5 -> { // Data Verification
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("Data Integrity Verification Certificate:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Cert ID: ${verificationCert.certificateId}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("Integrity Status: ${verificationCert.integrityStatus}", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("Verified Records: ${verificationCert.checkedRecordsCount} • Corrupted: ${verificationCert.missingRecordsCount}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Checksum Test: ${verificationCert.checksumResult}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { IntelligentExportEvidenceEngine.verifyDataIntegrity(context) },
                                modifier = Modifier.fillMaxWidth().height(36.dp)
                            ) {
                                Text("Run Integrity Verification Check", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(6.dp))
                Text("Export & Evidence Audit Trail:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                auditLogs.take(3).forEach { log ->
                    Text("• [${log.eventType}] ${log.details}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
