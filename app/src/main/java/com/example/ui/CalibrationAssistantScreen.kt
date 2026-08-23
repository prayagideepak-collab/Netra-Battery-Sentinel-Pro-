package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.BatteryState
import com.example.util.DiagnosticLogEntry
import com.example.util.DiagnosticLogger
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationAssistantScreen(
    batteryState: BatteryState,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // 1: Discharge (<=5%), 2: Charge to 100%, 3: Top-off Trickle (30-60m), 4: Complete
    var currentStep by remember { mutableStateOf(1) }
    var topOffStartTime by remember { mutableStateOf(0L) }
    var showLogsDialog by remember { mutableStateOf(false) }

    // Auto-advance step logic based on real battery state
    LaunchedEffect(batteryState.percentage, batteryState.isCharging) {
        if (currentStep == 1 && batteryState.percentage in 1..5) {
            // Reached low discharge
        } else if (currentStep == 2 && batteryState.percentage >= 100 && batteryState.isCharging) {
            currentStep = 3
            if (topOffStartTime == 0L) {
                topOffStartTime = System.currentTimeMillis()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Battery Calibration Assistant", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("System fuel gauge recalibration wizard", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showLogsDialog = true }) {
                        Icon(Icons.Filled.ListAlt, contentDescription = "Diagnostic Logs")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Absolute Truth Header Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = "Absolute Truth",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Hardware Telemetry Baseline", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Level: ${batteryState.percentage}% | Voltage: ${batteryState.voltage} mV | Temp: ${batteryState.temperature}°C | Status: ${if (batteryState.isCharging) "Charging (${batteryState.chargingType})" else "Discharging"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Wizard Step Indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StepBadge(stepNumber = 1, title = "Discharge", active = currentStep == 1, completed = currentStep > 1)
                StepBadge(stepNumber = 2, title = "Full Charge", active = currentStep == 2, completed = currentStep > 2)
                StepBadge(stepNumber = 3, title = "Top-Off", active = currentStep == 3, completed = currentStep > 3)
                StepBadge(stepNumber = 4, title = "Complete", active = currentStep == 4, completed = currentStep > 4)
            }

            // Step Content Card
            when (currentStep) {
                1 -> StepDischargeCard(
                    batteryState = batteryState,
                    onNext = {
                        DiagnosticLogger.logEvent(
                            context,
                            "CALIBRATION",
                            "Discharge Phase Complete",
                            "User confirmed low battery level (${batteryState.percentage}%, ${batteryState.voltage}mV)",
                            batteryState.percentage,
                            batteryState.temperature,
                            batteryState.voltage.toFloat(),
                            "Discharging"
                        )
                        currentStep = 2
                    }
                )
                2 -> StepChargeCard(
                    batteryState = batteryState,
                    onNext = {
                        if (topOffStartTime == 0L) topOffStartTime = System.currentTimeMillis()
                        DiagnosticLogger.logEvent(
                            context,
                            "CALIBRATION",
                            "Full Charge Reached",
                            "Reached 100% full charge (${batteryState.voltage}mV)",
                            batteryState.percentage,
                            batteryState.temperature,
                            batteryState.voltage.toFloat(),
                            "Charging"
                        )
                        currentStep = 3
                    }
                )
                3 -> StepTopOffCard(
                    batteryState = batteryState,
                    startTime = topOffStartTime,
                    onNext = {
                        DiagnosticLogger.logEvent(
                            context,
                            "CALIBRATION",
                            "Calibration Finished",
                            "Top-off completed. Android battery stats fuel gauge offset recalibrated.",
                            batteryState.percentage,
                            batteryState.temperature,
                            batteryState.voltage.toFloat(),
                            "Calibrated"
                        )
                        currentStep = 4
                    }
                )
                4 -> StepCompleteCard(
                    batteryState = batteryState,
                    onResetWizard = {
                        currentStep = 1
                        topOffStartTime = 0L
                    }
                )
            }

            // Standard System Guidance Info
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Why Recalibrate?", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Over time, Android's internal software battery reporting (`batterystats` service) can drift from the battery chemical state due to partial charges. Performing a full 0-100% cycle allows the hardware fuel gauge controller to re-measure peak/floor voltage levels without requiring root access.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Diagnostics Log Action
            OutlinedButton(
                onClick = { showLogsDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Saved Diagnostic Logs File")
            }
        }
    }

    if (showLogsDialog) {
        DiagnosticLogsViewerDialog(
            onDismiss = { showLogsDialog = false }
        )
    }
}

@Composable
private fun StepBadge(stepNumber: Int, title: String, active: Boolean, completed: Boolean) {
    val bgColor = when {
        completed -> MaterialTheme.colorScheme.primary
        active -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        completed -> MaterialTheme.colorScheme.onPrimary
        active -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(bgColor, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (completed) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            } else {
                Text("$stepNumber", color = contentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Text(title, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StepDischargeCard(batteryState: BatteryState, onNext: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BatteryAlert, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Step 1: Deep Discharge Phase", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                "Use your device until the battery discharges down to 5% or lower. This allows the hardware fuel gauge to record the minimum voltage floor.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LinearProgressIndicator(
                progress = (batteryState.percentage / 100f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = if (batteryState.percentage <= 15) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Current Level: ${batteryState.percentage}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(if (batteryState.percentage <= 5) "Ready!" else "Discharging...", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            }

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Proceed to Charging Phase")
            }
        }
    }
}

@Composable
private fun StepChargeCard(batteryState: BatteryState, onNext: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Power, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Step 2: Uninterrupted Full Charge", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                "Connect your charger and charge uninterrupted until the battery reaches 100%. Avoid unplugging or using heavy apps during this phase.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LinearProgressIndicator(
                progress = (batteryState.percentage / 100f).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Current: ${batteryState.percentage}%", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(if (batteryState.isCharging) "Charging (${batteryState.chargingType})" else "Unplugged", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            }

            Button(
                onClick = onNext,
                enabled = batteryState.percentage >= 95,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(if (batteryState.percentage >= 100) "Proceed to Top-Off Stage" else "Waiting for 100% Charge...")
            }
        }
    }
}

@Composable
private fun StepTopOffCard(batteryState: BatteryState, startTime: Long, onNext: () -> Unit) {
    val elapsedMins = remember(System.currentTimeMillis()) {
        if (startTime > 0) ((System.currentTimeMillis() - startTime) / 60000).toInt() else 0
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Step 3: Post-100% Top-Off Stage", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                "Keep the device connected to the charger for 30-60 minutes after reaching 100%. This allows Android's kernel battery stats daemon to stabilize peak voltage and reset capacity metrics.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Top-Off Time Elapsed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$elapsedMins Minutes", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Voltage: ${batteryState.voltage} mV", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("Temp: ${batteryState.temperature}°C", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Complete Calibration Wizard")
            }
        }
    }
}

@Composable
private fun StepCompleteCard(batteryState: BatteryState, onResetWizard: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Calibration Complete!", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                "Android battery stats fuel gauge reference offset has been recalibrated based on real voltage thresholds. Unplug your charger and enjoy restored battery indicator precision.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Verified System Summary:", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("• Current Voltage: ${batteryState.voltage} mV", fontSize = 12.sp)
                Text("• Temperature: ${batteryState.temperature}°C", fontSize = 12.sp)
                Text("• Battery Health: ${batteryState.health}", fontSize = 12.sp)
                Text("• Calibration Timestamp: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = onResetWizard,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Run Calibration Again")
            }
        }
    }
}

@Composable
fun DiagnosticLogsViewerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var logs by remember { mutableStateOf<List<DiagnosticLogEntry>>(emptyList()) }
    var logText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        logs = DiagnosticLogger.readLogs(context)
        logText = DiagnosticLogger.getLogText(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("System Diagnostic Logs File")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Local Log Path: ${context.filesDir}/diagnostic_logs/battery_system_diagnostics.log",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                if (logs.isEmpty()) {
                    Text("No system-level diagnostic events logged yet.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    logs.forEach { entry ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("[${entry.category}] ${entry.title}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(entry.formattedTime, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (entry.details.isNotBlank()) {
                                    Text(entry.details, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text(
                                    "Level: ${entry.batteryLevel}% | Temp: ${entry.temperature}°C | Voltage: ${entry.voltage.toInt()}mV | ${entry.status}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    DiagnosticLogger.clearLogs(context)
                    logs = emptyList()
                    logText = ""
                }
            ) {
                Text("Clear Logs", color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
