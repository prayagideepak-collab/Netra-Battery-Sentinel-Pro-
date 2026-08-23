package com.example.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.SystemAuditRecord
import com.example.service.SystemSelfAuditEngine.ComponentStatus
import com.example.viewmodel.BatteryViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SystemSelfAuditScreen(viewModel: BatteryViewModel) {
    val context = LocalContext.current
    val isAuditing by viewModel.isAuditing.collectAsStateWithLifecycle()
    val auditComponents by viewModel.auditComponents.collectAsStateWithLifecycle()
    val lastReport by viewModel.lastAuditReport.collectAsStateWithLifecycle()
    val auditHistory by viewModel.allSystemAuditRecords.collectAsStateWithLifecycle()

    var selectedComponent by remember { mutableStateOf<ComponentStatus?>(null) }
    var activeHistoryFilter by remember { mutableStateOf("All") } // "All", "Failed", "Restarted", "Critical"

    val filteredHistory = remember(auditHistory, activeHistoryFilter) {
        when (activeHistoryFilter) {
            "Failed" -> auditHistory.filter { it.failedServices > 0 }
            "Restarted" -> auditHistory.filter { it.restartedServices > 0 }
            "Critical" -> auditHistory.filter { it.healthScore < 90 }
            else -> auditHistory
        }
    }

    // Trigger an initial audit check if empty
    LaunchedEffect(auditComponents) {
        if (auditComponents.isEmpty()) {
            viewModel.triggerSelfAudit(context)
        }
    }

    // Main scrollable content
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero / Overall Health Score Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("health_score_hero_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "System Health Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Continuous Self-Audit Engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.triggerSelfAudit(context) },
                            enabled = !isAuditing,
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("manual_audit_button")
                        ) {
                            if (isAuditing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Filled.Refresh, contentDescription = "Run System Audit")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Big circle score
                    val score = lastReport?.healthScore ?: 100
                    val scoreColor = when {
                        score >= 90 -> Color(0xFF4CAF50)
                        score >= 80 -> Color(0xFFFFC107)
                        else -> Color(0xFFF44336)
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(140.dp)
                            .background(scoreColor.copy(alpha = 0.1f), CircleShape)
                            .border(3.dp, scoreColor, CircleShape)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$score%",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = scoreColor,
                                fontSize = 36.sp
                            )
                            Text(
                                text = when {
                                    score >= 90 -> "INTEGRITY SECURE"
                                    score >= 80 -> "WARNING ACTIVE"
                                    else -> "BREACH DETECTED"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Horizontal breakdown of counts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MetricsColumn(
                            value = "${lastReport?.healthyServices ?: 0}",
                            label = "Healthy",
                            icon = Icons.Filled.CheckCircle,
                            color = Color(0xFF4CAF50)
                        )
                        MetricsColumn(
                            value = "${lastReport?.restartedServices ?: 0}",
                            label = "Recovered",
                            icon = Icons.Filled.Autorenew,
                            color = Color(0xFFFF9800)
                        )
                        MetricsColumn(
                            value = "${lastReport?.failedServices ?: 0}",
                            label = "Failed",
                            icon = Icons.Filled.Error,
                            color = Color(0xFFF44336)
                        )
                        MetricsColumn(
                            value = "${lastReport?.unsupportedComponents ?: 0}",
                            label = "Unsupported",
                            icon = Icons.Filled.Block,
                            color = Color(0xFF9E9E9E)
                        )
                    }

                    if (lastReport?.recoveryActions != "None" && lastReport?.recoveryActions != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Build,
                                    contentDescription = "Repairs Attempted",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Recovery Actions: ${lastReport?.recoveryActions}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Autonomous Power Policy Engine Card
        item {
            val policyState by com.example.engines.power.AutonomousPowerPolicyEngine.policyState.collectAsStateWithLifecycle()
            val capRegistry by com.example.engines.capability.CapabilityFeatureEngine.registryState.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("autonomous_power_policy_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = "Autonomous Power Engine",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "Autonomous Power Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Capability-Aware (${capRegistry.activeFeaturesCount} Active)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "Device-Adaptive capability registry enforcing truth-based UI visibility. Non-supported features are dynamically hidden:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 5 Autonomous Independent Layers (Dynamically rendered based on Capability Registry)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.SCREEN_OFF_CONSERVATION)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (policyState.isLayerAScreenConservationActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (policyState.isLayerAScreenConservationActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text("Layer A: Screen", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(if (policyState.isLayerAScreenConservationActive) "Conservation (OFF)" else "Normal (ON)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                                }
                            }
                        }

                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.LOW_BATTERY_PROTECTION)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (policyState.isLayerBLowBatteryProtectionActive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (policyState.isLayerBLowBatteryProtectionActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text("Layer B: Low Batt", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(if (policyState.isLayerBLowBatteryProtectionActive) "Active (≤30%)" else "Normal (>31%)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                                }
                            }
                        }

                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.THERMAL_PROTECTION)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (policyState.isLayerCThermalProtectionActive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (policyState.isLayerCThermalProtectionActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text("Layer C: Thermal", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(if (policyState.isLayerCThermalProtectionActive) "Critical (≥40°C)" else "Normal (<40°C)", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                                }
                            }
                        }

                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.ROAMING_POWER_SAVE)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (policyState.isLayerDRoamingPowerSaveActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (policyState.isLayerDRoamingPowerSaveActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text("Layer D: Roaming", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(if (policyState.isLayerDRoamingPowerSaveActive) "Roaming Save ON" else "Home Network", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                                }
                            }
                        }

                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.MANUAL_POWER_SAVE)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (policyState.isLayerEManualPowerSaveActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (policyState.isLayerEManualPowerSaveActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text("Layer E: Manual Override", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Text(if (policyState.isLayerEManualPowerSaveActive) "Persistent ON" else "Disabled", style = MaterialTheme.typography.bodySmall, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Manual Power Save Switch Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Manual Power Saving Override",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Persistent state: stays active until explicitly turned off (Screen & Charging events do not auto-disable)",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = policyState.isManualPowerSaveActive,
                            onCheckedChange = { checked ->
                                com.example.engines.power.AutonomousPowerPolicyEngine.setManualPowerSave(context, checked)
                            },
                            modifier = Modifier.testTag("manual_power_save_switch")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Engine Status Breakdown (Dynamic Capability Filter)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.SCREEN_OFF_CONSERVATION)) {
                            PolicyChip(
                                label = "1. Screen Conservation",
                                value = if (policyState.isScreenOn) "Screen ON (Normal)" else "Screen OFF (Conservation)",
                                icon = Icons.Outlined.PhoneAndroid,
                                active = !policyState.isScreenOn
                            )
                        }
                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.LOW_BATTERY_PROTECTION)) {
                            PolicyChip(
                                label = "2. Low Batt Protection",
                                value = policyState.lowBatteryPolicySummary,
                                icon = Icons.Outlined.BatterySaver,
                                active = policyState.isLayerBLowBatteryProtectionActive
                            )
                        }
                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.ADAPTIVE_NETWORK_SYNC)) {
                            PolicyChip(
                                label = "3. Adaptive Sync",
                                value = policyState.syncStrategy.name,
                                icon = Icons.Outlined.Sync,
                                active = policyState.syncStrategy != com.example.engines.power.NetworkSyncStrategy.RESPONSIVE
                            )
                            PolicyChip(
                                label = "4. Network Radio",
                                value = if (policyState.isUnmeteredNetwork) "Unmetered Batching" else "Metered Deferral",
                                icon = Icons.Outlined.Wifi,
                                active = !policyState.isUnmeteredNetwork
                            )
                        }
                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.THERMAL_PROTECTION)) {
                            PolicyChip(
                                label = "5. Thermal Workload",
                                value = policyState.thermalWorkloadSummary,
                                icon = Icons.Outlined.Thermostat,
                                active = policyState.deviceTemperature >= 38f
                            )
                        }
                        PolicyChip(
                            label = "6. Task Priority Filter",
                            value = "Do Less Mode: ${policyState.lowestAllowedTaskPriority.name}",
                            icon = Icons.Outlined.FilterList,
                            active = policyState.lowestAllowedTaskPriority != com.example.engines.power.NetraTaskPriority.OPTIONAL
                        )
                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.SENSOR_DUTY_CYCLING)) {
                            PolicyChip(
                                label = "7. Sensor Duty-Cycle",
                                value = policyState.sensorMode.name,
                                icon = Icons.Outlined.Sensors,
                                active = policyState.sensorMode == com.example.engines.power.SensorSamplingMode.DUTY_CYCLED
                            )
                        }
                        if (com.example.engines.capability.CapabilityFeatureEngine.isFeatureVisible(com.example.engines.capability.NetraFeature.SELF_BATTERY_AUDIT)) {
                            PolicyChip(
                                label = "8. Self-Battery Audit",
                                value = if (policyState.selfAuditMetrics.isSelfThrottled) "Auto-Throttled" else "Impact <0.3%/hr",
                                icon = Icons.Outlined.Speed,
                                active = policyState.selfAuditMetrics.isSelfThrottled
                            )
                        }
                    }
                }
            }
        }

        // Health + Policy + Recovery Watchdog Ledger Card
        item {
            val watchdogAudit by com.example.engines.WatchdogEngine.auditState.collectAsStateWithLifecycle()
            val watchdogLedger by com.example.engines.WatchdogEngine.ledgerHistory.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("health_policy_watchdog_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HealthAndSafety,
                                contentDescription = "Watchdog Engine",
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "Health, Policy & Recovery Watchdog",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Conflict-Free",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = "Real-time capability, screen-off, 30% low-battery, thermal watchdog, and self-battery footprint governor:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        WatchdogCheckItem(label = "Capability Watch", status = watchdogAudit.capabilityWatchStatus)
                        WatchdogCheckItem(label = "Manifest Verification", status = watchdogAudit.manifestVerificationStatus)
                        WatchdogCheckItem(label = "Screen-Off Watch", status = watchdogAudit.screenPolicyWatchStatus)
                        WatchdogCheckItem(label = "30% Low-Batt Watch", status = watchdogAudit.lowBatteryWatchStatus)
                        WatchdogCheckItem(label = "Thermal Watchdog", status = watchdogAudit.thermalWatchStatus)
                        WatchdogCheckItem(label = "Conflict Resolver", status = watchdogAudit.conflictResolutionStatus)
                        WatchdogCheckItem(label = "Self-Battery Governor", status = watchdogAudit.selfBatteryHealthStatus)
                        WatchdogCheckItem(label = "Recovery Escalation", status = watchdogAudit.recoveryEscalationStatus)
                    }

                    if (watchdogLedger.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Text(
                            text = "Recent Watchdog State Ledger (${watchdogLedger.size} Entries):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        val latestRecord = watchdogLedger.first()
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Latest Entry: ${latestRecord.activePolicyLayers}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Batt: ${latestRecord.batteryPercentage}% | Temp: ${latestRecord.temperature}°C | Screen: ${if (latestRecord.isScreenOn) "ON" else "OFF"} | Status: ${latestRecord.recoveryStatus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Component Diagnostics Title
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Component Diagnostics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${auditComponents.size} Checked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // List of Active Components Grid-like Rows
        items(auditComponents) { component ->
            val statusColor = when {
                component.status.contains("Normally") -> Color(0xFF4CAF50)
                component.status.contains("Warning") -> Color(0xFFFFC107)
                component.status.contains("Failed") -> Color(0xFFF44336)
                else -> Color(0xFF9E9E9E)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedComponent = component }
                    .testTag("component_status_item_${component.name.lowercase().replace(" ", "_")}"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Column {
                            Text(
                                text = component.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Last active: ${component.lastSuccessfulActivity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatusBadge(statusText = component.status, color = statusColor)
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = "Details",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Audit History Header & Filter Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Audit Logs & Historical Reports",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (auditHistory.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.clearAuditHistory() },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Text("Clear", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Filter Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "Failed", "Restarted", "Critical").forEach { filter ->
                        FilterChip(
                            selected = activeHistoryFilter == filter,
                            onClick = { activeHistoryFilter = filter },
                            label = { Text(filter) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // Audit Logs list
        if (filteredHistory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Empty History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No historical logs matching filter",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        } else {
            items(filteredHistory) { record ->
                val dateStr = remember(record.timestamp) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
                }
                val scoreColor = when {
                    record.healthScore >= 90 -> Color(0xFF4CAF50)
                    record.healthScore >= 80 -> Color(0xFFFFC107)
                    else -> Color(0xFFF44336)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("audit_history_item_${record.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Audit #${record.id}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            Text(
                                text = "${record.healthScore}% Score",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scoreColor
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Services Checked: ${record.totalServicesChecked}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Failures: ${record.failedServices}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (record.failedServices > 0) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Recoveries: ${record.restartedServices}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (record.restartedServices > 0) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (record.recoveryActions != "None") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Actions: ${record.recoveryActions}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Detail BottomSheet/Dialog when selecting any component
    selectedComponent?.let { component ->
        AlertDialog(
            onDismissRequest = { selectedComponent = null },
            confirmButton = {
                TextButton(onClick = { selectedComponent = null }) {
                    Text("Close")
                }
            },
            title = {
                Text(
                    text = component.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val badgeColor = when {
                        component.status.contains("Normally") -> Color(0xFF4CAF50)
                        component.status.contains("Warning") -> Color(0xFFFFC107)
                        component.status.contains("Failed") -> Color(0xFFF44336)
                        else -> Color(0xFF9E9E9E)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Operational Status:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        StatusBadge(statusText = component.status, color = badgeColor)
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    DetailRow(label = "Initialization Time", value = component.startTime)
                    DetailRow(label = "Last Checked", value = component.lastSuccessfulActivity)
                    DetailRow(label = "Restart Count", value = "${component.restartCount}")
                    DetailRow(label = "JVM Memory Allocation", value = component.memoryUsage)
                    DetailRow(label = "Active System Threads", value = component.threadInfo)

                    component.lastError?.let { error ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = "Last Diagnostic Error:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun MetricsColumn(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun StatusBadge(statusText: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = statusText.substringAfter(" "),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun PolicyChip(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean
) {
    val containerBg = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerBg,
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WatchdogCheckItem(label: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
            modifier = Modifier.weight(0.65f),
            textAlign = TextAlign.End
        )
    }
}

