package com.example.devices

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Netra PC / Laptop / Tablet Battery Sentinel Reference Config
 * Made with ❤️ by Prayagi Ji
 */

object DevicePCSentinelConfig {
    fun getReferenceModel(): String = "Netra Crossplatform PC-Core Client"
}

@Composable
fun DevicePCBatterySentinelView(
    isUnlocked: Boolean,
    onPurchaseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var optionsCount by remember { mutableStateOf(4) } // Default 4 options
    var isSyncing by remember { mutableStateOf(false) }
    var syncProgress by remember { mutableStateOf(0f) }
    var syncLogs = remember { mutableStateListOf<String>() }

    var syncRate by remember { mutableStateOf("Every 5 Mins") }
    var backgroundPersistence by remember { mutableStateOf("Low CPU Core (Optimal)") }
    var alertOnFullCharge by remember { mutableStateOf("Enabled (Alert on PC)") }
    var telemetryLoggingDepth by remember { mutableStateOf("Standard Network Payload") }

    // Coroutine logic to simulate PC Synchronization progress and logs
    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            syncProgress = 0f
            syncLogs.clear()
            syncLogs.add("🔌 Checking local network subnet for active PC daemons...")
            delay(800)
            syncProgress = 0.5f
            syncLogs.add("⚠️ Verified data is currently unavailable. No external PC desktop software client connected.")
            delay(800)
            syncProgress = 1f
            isSyncing = false
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isUnlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Computer,
                    contentDescription = null,
                    tint = if (isUnlocked) Color(0xFF00bcd4) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PC & Tablet Battery Sentinel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "Cross-platform PC Sync • Multi-Device Sentinel",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Box(
                    modifier = Modifier
                        .background(
                            if (isUnlocked) Color(0xFF00bcd4).copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isUnlocked) "UNLOCKED" else "LOCKED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (isUnlocked) Color(0xFF00bcd4) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(14.dp))

            if (!isUnlocked) {
                // Locked Gated Screen
                Text(
                    text = "Requires Netra PC Desktop License. Once activated, your PC/Laptop battery status will be fully synchronized here with real-time remote telemetry.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onPurchaseClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unlock PC Sync License (100 Credits)", fontSize = 12.sp)
                }
            } else {
                // Unlocked Config view
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Options Configured:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Option stepper
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Options: ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(
                            onClick = { if (optionsCount > 1) optionsCount-- },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("-", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("$optionsCount", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        IconButton(
                            onClick = { if (optionsCount < 4) optionsCount++ },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (optionsCount < 3) {
                    // Option hidden constraint
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "⚠️ Telemetry is hidden! At least 3 options must be configured in your reference file to activate active PC synchronization.",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                } else {
                    // Fully displayed options
                    PCSpecRow(label = "Sync Rate Duty Cycle", value = syncRate)
                    PCSpecRow(label = "Service Core Persistence", value = backgroundPersistence)
                    PCSpecRow(label = "Alert Host Client Option", value = alertOnFullCharge)
                    if (optionsCount == 4) {
                        PCSpecRow(label = "Desktop Sync Logging", value = telemetryLoggingDepth)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // "Their PC will be synchronized there." Active Synchronization Panel Window
                    Text(
                        text = "PC Sync & Connection Window",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF00bcd4)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Laptop,
                                        contentDescription = null,
                                        tint = if (syncProgress == 1f) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Workstation-PC (ThinkPad)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                
                                Button(
                                    onClick = { isSyncing = true },
                                    enabled = !isSyncing,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00bcd4)),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Filled.Sync, null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sync Now", fontSize = 10.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (isSyncing || syncProgress > 0) {
                                LinearProgressIndicator(
                                    progress = syncProgress,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFF00bcd4)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black, RoundedCornerShape(6.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    if (syncLogs.isEmpty()) {
                                        Text(
                                            text = "$ Terminal Ready... Tap Sync Now to calibrate dynamic PC Sentinel telemetry.",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            color = Color.LightGray
                                        )
                                    } else {
                                        syncLogs.forEach { log ->
                                            Text(
                                                text = "> $log",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                color = if (log.contains("✅")) Color(0xFF4CAF50) else Color.LightGray,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            )
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
}

@Composable
fun PCSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}
