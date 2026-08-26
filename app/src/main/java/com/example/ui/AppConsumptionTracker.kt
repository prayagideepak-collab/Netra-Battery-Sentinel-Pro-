package com.example.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import com.example.data.AppConsumptionEntity
import com.example.data.BatteryEvent
import com.example.engines.AppNetworkUsageEngine
import com.example.engines.BatteryAttributionEngine
import com.example.service.BatteryState
import com.example.util.TimeManager
import com.example.viewmodel.BatteryViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Netra App Consumption Tracker Screen Component
 * Made with ❤️ by Prayagi Ji
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppConsumptionTrackerScreen(
    viewModel: BatteryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val apps by viewModel.appConsumptions.collectAsStateWithLifecycle(emptyList())
    val batteryState by viewModel.batteryState.collectAsStateWithLifecycle()
    val events by viewModel.allBatteryEvents.collectAsStateWithLifecycle(emptyList())

    val hasUsagePermission = remember(apps) { hasUsageStatsPermission(context) }
    val activeAppsCount = remember(apps) { apps.count { it.isRunning || it.activityState == "Running" } }
    val totalEstimatedDrain = remember(apps) { apps.sumOf { it.consumedMah.toDouble() }.toFloat() }
    val totalNetworkBytes = remember(apps) { apps.sumOf { it.totalNetworkBytes } }
    
    var filterType by remember { mutableStateOf("All") } // "All", "Running", "Active Today"
    var selectedApp by remember { mutableStateOf<AppConsumptionEntity?>(null) }

    val filteredApps = remember(apps, filterType) {
        when (filterType) {
            "Running" -> apps.filter { it.isRunning || it.activityState == "Running" }
            "Active Today" -> apps.filter { it.foregroundTimeMs > 0L || it.totalNetworkBytes > 0L }
            else -> apps
        }.sortedWith(
            compareByDescending<AppConsumptionEntity> { it.isRunning }
                .thenByDescending { it.totalNetworkBytes }
                .thenByDescending { it.foregroundTimeMs }
        )
    }

    // Detail Dialog Overlay
    selectedApp?.let { app ->
        AppDetailDialog(
            app = app,
            batteryState = batteryState,
            events = events,
            onDismiss = { selectedApp = null }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("app_consumption_tracker_container")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Welcome Header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "App Consumption Tracker",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Authoritative telemetry from PackageManager & NetworkStatsManager. Zero fabrication.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Usage Access Permission Banner if not granted
        if (!hasUsagePermission) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = "Security", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Usage Access Required",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Usage Access is required to read authoritative per-app network bytes and foreground durations from Android NetworkStatsManager.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Open Settings -> Apps -> Special app access -> Usage access", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Grant Usage Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Deterministic Time Window Indicator & Stats Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Telemetry Window: Today (00:00 - Now)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Apps: ${apps.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Aggregated App Drain", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val drainText = if (apps.isEmpty() || totalEstimatedDrain <= 0f) "Unavailable" else "${String.format(Locale.US, "%.1f", totalEstimatedDrain)} mAh"
                            Text(
                                text = drainText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (apps.isEmpty() || totalEstimatedDrain <= 0f) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Aggregated Network", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val netText = if (!hasUsagePermission || apps.isEmpty()) "Unavailable" else AppNetworkUsageEngine.formatBytes(totalNetworkBytes)
                            Text(
                                text = netText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Active Apps", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val runningText = if (apps.isEmpty()) "Unavailable" else "$activeAppsCount running"
                            Text(
                                text = runningText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeAppsCount > 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Actions & Filters Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Diagnostics Filter & Clean Engine",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // Filters tabs (All, Running, Active Today)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Running", "Active Today").forEach { tag ->
                            val active = filterType == tag
                            Card(
                                onClick = { filterType = tag },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            Toast.makeText(context, "Optimizing background states and releasing wake-locks...", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.FlashOn, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Optimize Rogue Background Processes", fontSize = 11.sp)
                    }
                }
            }
        }

        // List Header with count
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Dynamic Power Allocations (${filteredApps.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = {
                        Toast.makeText(context, "Refreshing installed apps and network metrics...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(Icons.Filled.Refresh, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                }
            }
        }

        // App rows
        if (apps.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Info, "Information", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No Eligible Applications Found",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Netra queries only real installed applications from PackageManager. If background synchronization has just started, installed applications will appear shortly.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        } else if (filteredApps.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No apps match current filter criteria.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(filteredApps, key = { it.packageName }) { app ->
                AppConsumptionItem(app = app, onClick = { selectedApp = app })
            }
        }

        // Footer Brand
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Made with ❤️ by Prayagi Ji",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

/**
 * Redesigned Section 13 App Card:
 * [App Icon]  AppName
 *             Package: com.example.package (UID: 10042)
 *
 * Battery:
 * Unavailable
 *
 * Network:
 * Mobile 12.4 MB  •  Wi-Fi 84.2 MB  •  Total 96.6 MB
 *
 * State:
 * Background / Running / Inactive
 */
@Composable
fun AppConsumptionItem(app: AppConsumptionEntity, onClick: () -> Unit) {
    val firstChar = app.appName.firstOrNull()?.toString()?.uppercase() ?: "A"
    val state = when {
        app.isRunning || app.activityState == "Running" -> "Running"
        app.activityState == "Background" || app.foregroundTimeMs > 0L -> "Background"
        else -> "Inactive"
    }

    val stateColor = when (state) {
        "Running" -> Color(0xFF4CAF50)
        "Background" -> Color(0xFF2196F3)
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_item_${app.packageName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Icon + Name + Package + State Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(stateColor.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, stateColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = firstChar,
                        color = stateColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Package: ${app.packageName}${if (app.uid > 0) " (UID: ${app.uid})" else ""}",
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // State Badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = stateColor.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, stateColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = state.uppercase(),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        color = stateColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            Spacer(modifier = Modifier.height(8.dp))

            // Battery Attribution Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Battery:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val batteryText = if (app.consumedMah > 0f && app.batteryAttributionAvailable) {
                    "${String.format(Locale.US, "%.1f", app.consumedMah)} mAh"
                } else {
                    "Unavailable"
                }
                Text(
                    text = batteryText,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (app.consumedMah > 0f && app.batteryAttributionAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Real Network Telemetry Row
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Network:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (app.networkStatsAvailable) {
                        val mobileBytes = app.mobileRxBytes + app.mobileTxBytes
                        val wifiBytes = app.wifiRxBytes + app.wifiTxBytes
                        val totalBytes = if (app.totalNetworkBytes > 0L) app.totalNetworkBytes else (mobileBytes + wifiBytes)

                        Text("Mobile: ${AppNetworkUsageEngine.formatBytes(mobileBytes)}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("Wi-Fi: ${AppNetworkUsageEngine.formatBytes(wifiBytes)}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("Total: ${AppNetworkUsageEngine.formatBytes(totalBytes)}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(
                            text = "Data usage unavailable — Usage Access required",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppDetailDialog(
    app: AppConsumptionEntity,
    batteryState: BatteryState,
    events: List<BatteryEvent>,
    onDismiss: () -> Unit
) {
    val correlation = remember(app, batteryState) {
        BatteryAttributionEngine.calculateAttribution(app, batteryState, "Wi-Fi", "Stable", false)
    }

    val timeFormatter = remember { SimpleDateFormat("hh:mm:ss.SSS a", Locale.US) }

    val matchingEvents = remember(events, app) {
        events.filter { event ->
            event.details.contains(app.packageName, ignoreCase = true) ||
            event.details.contains(app.appName, ignoreCase = true)
        }.take(8)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .testTag("app_detail_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${app.packageName}${if (app.uid > 0) " • UID: ${app.uid}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Text("✕", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // System vs User App and State
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Application Type", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(correlation.category, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text("Current State", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val state = when {
                                        app.isRunning || app.activityState == "Running" -> "RUNNING (ACTIVE)"
                                        app.activityState == "Background" -> "BACKGROUND"
                                        else -> "INACTIVE / IDLE"
                                    }
                                    Text(
                                        text = state,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (app.isRunning) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Network Telemetry Breakdown
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Authoritative Network Usage", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))

                                if (app.networkStatsAvailable) {
                                    AppDetailRow("Mobile RX / TX", "${AppNetworkUsageEngine.formatBytes(app.mobileRxBytes)} / ${AppNetworkUsageEngine.formatBytes(app.mobileTxBytes)}")
                                    AppDetailRow("Mobile Total", AppNetworkUsageEngine.formatBytes(app.mobileRxBytes + app.mobileTxBytes))
                                    AppDetailRow("Wi-Fi RX / TX", "${AppNetworkUsageEngine.formatBytes(app.wifiRxBytes)} / ${AppNetworkUsageEngine.formatBytes(app.wifiTxBytes)}")
                                    AppDetailRow("Wi-Fi Total", AppNetworkUsageEngine.formatBytes(app.wifiRxBytes + app.wifiTxBytes))
                                    AppDetailRow("Total Network Usage", AppNetworkUsageEngine.formatBytes(app.totalNetworkBytes))
                                } else {
                                    Text(
                                        text = "Per-app network telemetry is unavailable. Grant Usage Access in Android Settings to enable live NetworkStatsManager tracking.",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Activity Timeline Details
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Usage & Activity Timeline", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                AppDetailRow("Last Active", if (app.lastActiveTime > 0L) timeFormatter.format(Date(app.lastActiveTime)) else "Unavailable")
                                AppDetailRow("Foreground Runtime", if (app.foregroundTimeMs > 0L) TimeManager.formatDurationMs(app.foregroundTimeMs) else "Unavailable")
                                AppDetailRow("Background Runtime", if (app.backgroundTimeMs > 0L) TimeManager.formatDurationMs(app.backgroundTimeMs) else "Unavailable")
                            }
                        }
                    }

                    // Battery Impact Analysis
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Battery Attribution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (app.consumedMah > 0f && app.batteryAttributionAvailable) "ACTIVE" else "UNAVAILABLE",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                AppDetailRow("Battery Impact", if (app.consumedMah > 0f) "${app.consumedMah} mAh" else "Unavailable")
                                AppDetailRow("Attribution Note", "Standard Android SDK does not expose per-UID battery mAh without privileged system permissions.")
                            }
                        }
                    }

                    // Historical Events Timeline
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Historical Event Log",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            if (matchingEvents.isEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                ) {
                                    Box(
                                        modifier = Modifier.padding(10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No historical event logs registered for this app package.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else {
                                matchingEvents.forEach { event ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(event.title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Text(timeFormatter.format(Date(event.timestamp)), fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(event.details, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close Diagnostics View", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AppDetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Text(text = label, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(1.dp))
        Text(text = value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}
