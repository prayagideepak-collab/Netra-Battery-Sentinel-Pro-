package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.BatteryColorEngine

@Composable
fun LiveMonitoringCard(
    isServiceRunning: Boolean,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onToggleService: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_anim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_monitoring_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isServiceRunning)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(
            1.dp,
            if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Sensors,
                            contentDescription = "Live Monitoring",
                            tint = if (isServiceRunning) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Live Monitoring",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Status Pill
                            Surface(
                                color = if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                shape = CircleShape
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                color = if (isServiceRunning) Color(0xFF00E676).copy(alpha = pulseAlpha) else Color.Gray,
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isServiceRunning) "ACTIVE" else "PAUSED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isServiceRunning) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (isServiceRunning) "24/7 real-time telemetry & voice alerts active" else "Tap toggle to activate background battery monitoring",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { onToggleService() },
                    modifier = Modifier.testTag("live_monitoring_switch")
                )
            }

            // Quick Status Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Background Service Chip
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isServiceRunning) Icons.Filled.CheckCircle else Icons.Filled.PauseCircle,
                            contentDescription = null,
                            tint = if (isServiceRunning) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isServiceRunning) "Service: Online" else "Service: Offline",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Notification Alerts Chip
                Surface(
                    color = if (!hasNotificationPermission) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        0.5.dp,
                        if (!hasNotificationPermission) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !hasNotificationPermission) { onRequestNotificationPermission() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (hasNotificationPermission) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                            contentDescription = null,
                            tint = if (hasNotificationPermission) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasNotificationPermission) "Alerts: Ready" else "Enable Alerts",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (hasNotificationPermission) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PrimaryBatteryCard(percentage: Int, status: String) {
    val isCharging = status.contains("Charging", ignoreCase = true)
    val animatedPct by animateFloatAsState(
        targetValue = percentage.coerceIn(0, 100).toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "PrimaryBatteryIndicatorAnim"
    )
    val batteryColor = BatteryColorEngine.getColor(animatedPct)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("primary_battery_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visual Battery Indicator Graphic
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(36.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(8.dp))
                        .padding(3.dp)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Active charged portion
                        val activeWeight = animatedPct.coerceIn(0.1f, 100f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(activeWeight)
                                .background(batteryColor, RoundedCornerShape(4.dp))
                        )
                        // Dark / Discharged portion
                        val dischargedWeight = (100f - animatedPct).coerceAtLeast(0.1f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(dischargedWeight)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        )
                    }
                }
                // Positive terminal nub
                Spacer(modifier = Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(2.dp))
                )
            }

            // Percentage Text
            Text(
                text = "${percentage}%",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = batteryColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isCharging) {
                    Icon(
                        imageVector = Icons.Filled.ElectricBolt,
                        contentDescription = "Charging",
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SmartChargingProtectionCard(
    isCharging: Boolean,
    batteryPercentage: Int,
    temperatureCelsius: Float,
    targetLimit: Int,
    onTargetLimitChange: (Int) -> Unit,
    fullBatteryAlarmEnabled: Boolean,
    onToggleFullAlarm: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isOverheated = temperatureCelsius >= 42.0f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_charging_protection_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(
            1.dp,
            if (isOverheated) Color(0xFFFF5252).copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isOverheated) Color(0xFFFF5252).copy(alpha = 0.15f) else Color(0xFF00E676).copy(alpha = 0.15f),
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ElectricBolt,
                            contentDescription = null,
                            tint = if (isOverheated) Color(0xFFFF5252) else Color(0xFF00E676),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart Charge & Thermal Shield",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isCharging) "Active Charging Stream Telemetry" else "Standby Protection Engine",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Thermal Badge
                Surface(
                    color = if (isOverheated) Color(0xFFFF5252).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, if (isOverheated) Color(0xFFFF5252) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "%.1f°C %s".format(temperatureCelsius, if (isOverheated) "🔥 HOT" else "✓ COOL"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverheated) Color(0xFFFF5252) else Color(0xFF00E676),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Charge Limit Selector (80% vs 85% vs 90% vs 100%)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Charge Limit Alert Target",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (targetLimit == 80) "80% (Battery Lifespan Mode)" else "$targetLimit% Target",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(80, 85, 90, 100).forEach { limit ->
                        val isSelected = targetLimit == limit
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onTargetLimitChange(limit) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = if (limit == 80) "80% ⚡" else "$limit%",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Full / Limit Alarm Toggle Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Audio & Voice Alarm at Target",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Triggers immediate audible announcement when charge reaches target",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = fullBatteryAlarmEnabled,
                    onCheckedChange = { onToggleFullAlarm(it) },
                    modifier = Modifier.testTag("switch_full_charge_alarm")
                )
            }
        }
    }
}

@Composable
fun VoiceEngineProfileCard(
    currentVoice: String,
    speechPitch: Float,
    speechSpeed: Float,
    onVoiceSelect: (String, Float, Float) -> Unit,
    onTestAnnouncement: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_engine_profile_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Voice Engine & Profiles",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Instant 1-sec audio telemetry presets",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onTestAnnouncement,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("btn_test_voice_engine")
                ) {
                    Text("Test ⚡", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Presets Row
            val profiles = listOf(
                Triple("CYBERPUNK", "🛡️ Sentinel Cyber", Pair(1.3f, 1.35f)),
                Triple("MINIMALIST", "🎙️ Minimalist", Pair(1.0f, 1.15f)),
                Triple("TURBO", "⚡ Turbo Speed", Pair(1.1f, 1.5f)),
                Triple("BILINGUAL", "🌐 Bilingual", Pair(1.0f, 1.2f))
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                profiles.forEach { (id, label, audioParams) ->
                    val isSelected = currentVoice.equals(id, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onVoiceSelect(id, audioParams.first, audioParams.second) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChargingSpeedGraphCard(
    isCharging: Boolean,
    currentMa: Int,
    speedPercentPerHour: Float,
    chargingType: String,
    modifier: Modifier = Modifier
) {
    val history by com.example.telemetry.AuthoritativeTelemetryRepository.historicalSamples.collectAsStateWithLifecycle()
    val liveSample by com.example.telemetry.AuthoritativeTelemetryRepository.liveSample.collectAsStateWithLifecycle()

    var selectedWindowMinutes by remember { mutableStateOf(1) } // Default is 1m

    val windowMillis = selectedWindowMinutes * 60 * 1000L
    val now = System.currentTimeMillis()
    val cutoff = now - windowMillis
    val filteredSamples = remember(history, cutoff) {
        val f = history.filter { it.timestamp >= cutoff }
        if (f.isEmpty() && history.isNotEmpty()) listOf(history.last()) else f
    }

    val voltV = liveSample?.voltageV ?: 4.0f
    val curMa = liveSample?.currentMa ?: currentMa
    val tempC = liveSample?.temperature ?: 25f
    val batPct = liveSample?.batteryLevel ?: 50f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("charging_speed_graph_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = if (isCharging) Color(0xFF00E676).copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = null,
                            tint = if (isCharging) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Real-Time Telemetry Streams",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Status: ${if (isCharging) "Charging ($chargingType)" else "Discharging / Standby"}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Time Window selector chips: 1m (default), 5m, 10m, 30m, 1h
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1 to "1m", 5 to "5m", 10 to "10m", 30 to "30m", 60 to "1h").forEach { (min, label) ->
                        val isSelected = selectedWindowMinutes == min
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedWindowMinutes = min },
                            label = {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // 2x2 Grid of 4 Compact Square Cards
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMetricBox(
                        title = "Current (mA)",
                        currentValueText = "${curMa} mA",
                        samples = filteredSamples,
                        valueSelector = { it.currentMa.toFloat() },
                        lineColor = Color(0xFF00E676),
                        timeWindowMinutes = selectedWindowMinutes,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricBox(
                        title = "Voltage (V)",
                        currentValueText = "%.3f V".format(voltV),
                        samples = filteredSamples,
                        valueSelector = { it.voltageV },
                        lineColor = Color(0xFF29B6F6),
                        timeWindowMinutes = selectedWindowMinutes,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMetricBox(
                        title = "Temperature (°C)",
                        currentValueText = "%.1f °C".format(tempC),
                        samples = filteredSamples,
                        valueSelector = { it.temperature },
                        lineColor = Color(0xFFFFCA28),
                        timeWindowMinutes = selectedWindowMinutes,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricBox(
                        title = "Battery Level (%)",
                        currentValueText = "%.1f %%".format(batPct),
                        samples = filteredSamples,
                        valueSelector = { it.batteryLevel },
                        lineColor = Color(0xFFAB47BC),
                        timeWindowMinutes = selectedWindowMinutes,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryMetricBox(
    title: String,
    currentValueText: String,
    samples: List<com.example.telemetry.AuthoritativeTelemetrySample>,
    valueSelector: (com.example.telemetry.AuthoritativeTelemetrySample) -> Float,
    lineColor: Color,
    timeWindowMinutes: Int = 1,
    modifier: Modifier = Modifier
) {
    val animDurationMs = when (timeWindowMinutes) {
        5 -> 850
        10 -> 900
        in 30..60 -> 1000
        else -> 800
    }

    val targetPoints = remember(samples, timeWindowMinutes) {
        if (samples.isEmpty()) emptyList()
        else {
            val raw = samples.map(valueSelector)
            when {
                timeWindowMinutes >= 30 -> {
                    val count = 8
                    val step = (raw.size.toFloat() / count).coerceAtLeast(1f)
                    val res = mutableListOf<Float>()
                    for (i in 0 until count) {
                        val idx = (i * step).toInt().coerceIn(0, raw.lastIndex)
                        res.add(raw[idx])
                    }
                    res
                }
                timeWindowMinutes >= 10 -> {
                    val count = 12
                    val step = (raw.size.toFloat() / count).coerceAtLeast(1f)
                    val res = mutableListOf<Float>()
                    for (i in 0 until count) {
                        val idx = (i * step).toInt().coerceIn(0, raw.lastIndex)
                        res.add(raw[idx])
                    }
                    res
                }
                timeWindowMinutes >= 5 -> {
                    val count = 16
                    val step = (raw.size.toFloat() / count).coerceAtLeast(1f)
                    val res = mutableListOf<Float>()
                    for (i in 0 until count) {
                        val idx = (i * step).toInt().coerceIn(0, raw.lastIndex)
                        res.add(raw[idx])
                    }
                    res
                }
                else -> raw.takeLast(24)
            }
        }
    }

    var prevPoints by remember { mutableStateOf<List<Float>>(emptyList()) }
    var currentPoints by remember { mutableStateOf<List<Float>>(emptyList()) }
    val animProgress = remember { Animatable(1f) }

    LaunchedEffect(targetPoints) {
        if (targetPoints.isNotEmpty()) {
            prevPoints = if (currentPoints.isNotEmpty()) currentPoints else targetPoints
            currentPoints = targetPoints
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = animDurationMs, easing = LinearOutSlowInEasing)
            )
        }
    }

    Card(
        modifier = modifier.height(150.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0A0C14)
        ),
        border = BorderStroke(0.5.dp, lineColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Title & Current Value
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = currentValueText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = lineColor
                )
            }

            // Graph Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(85.dp),
                contentAlignment = Alignment.Center
            ) {
                if (currentPoints.isEmpty()) {
                    Text(
                        text = "Collecting data…",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                } else {
                    val progress = animProgress.value
                    val fillColor = lineColor.copy(alpha = 0.2f)

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val totalPoints = currentPoints.size
                        val interpolatedValues = FloatArray(totalPoints)
                        for (i in 0 until totalPoints) {
                            val currVal = currentPoints[i]
                            val prevVal = if (i < prevPoints.size) prevPoints[i] else currVal
                            interpolatedValues[i] = prevVal + (currVal - prevVal) * progress
                        }

                        val minVal = interpolatedValues.minOrNull() ?: 0f
                        val maxVal = interpolatedValues.maxOrNull() ?: 1f
                        val span = (maxVal - minVal)
                        val padding = if (span == 0f) 1f else span * 0.15f
                        val adjustedMin = minVal - padding
                        val adjustedMax = maxVal + padding
                        val range = (adjustedMax - adjustedMin).coerceAtLeast(0.001f)

                        val stepX = if (totalPoints > 1) size.width / (totalPoints - 1) else size.width

                        val path = Path()
                        val fillPath = Path()

                        for (i in 0 until totalPoints) {
                            val value = interpolatedValues[i]
                            val x = i * stepX
                            val normalizedY = 1f - ((value - adjustedMin) / range).coerceIn(0f, 1f)
                            val y = normalizedY * (size.height - 8.dp.toPx()) + 4.dp.toPx()

                            if (i == 0) {
                                path.moveTo(x, y)
                                fillPath.moveTo(x, size.height)
                                fillPath.lineTo(x, y)
                            } else {
                                path.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                        }

                        if (totalPoints > 0) {
                            val lastX = (totalPoints - 1) * stepX
                            fillPath.lineTo(lastX, size.height)
                            fillPath.close()

                            drawPath(path = fillPath, brush = Brush.verticalGradient(listOf(fillColor, Color.Transparent)))
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                }
            }

            // Footer: Live status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Stream",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
                val lastTs = samples.lastOrNull()?.timestamp ?: 0L
                val timeStr = if (lastTs > 0L) java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(lastTs)) else "--:--"
                Text(
                    text = timeStr,
                    fontSize = 9.sp,
                    color = lineColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun BatteryWearEstimatorCard(
    cycleCount: Int,
    healthPercentage: Int,
    averageTemp: Float,
    modifier: Modifier = Modifier
) {
    // STRICT DATA AVAILABILITY POLICY:
    // Only display this card if genuine, verifiable battery cycle and health data exist.
    // If data is unavailable, incomplete, or placeholder ("N/A", "Collecting..."), hide the card completely.
    val hasValidCycles = cycleCount > 0
    val hasValidHealth = healthPercentage in 1..100
    if (!hasValidCycles || !hasValidHealth) {
        return
    }

    val estimatedMonthsRemaining = maxOf(6, 36 - (cycleCount / 30) - if (averageTemp > 35f) 6 else 0)

    Card(
        modifier = modifier.fillMaxWidth().testTag("battery_wear_estimator_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).background(Color(0xFFFF9800).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = "Battery Wear & Lifespan Forecast", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = "Measured Degradation & Cycle Audit", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    color = Color(0xFFFF9800).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(text = "~$estimatedMonthsRemaining mo left", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "Cycles Recorded: $cycleCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Health Capacity: $healthPercentage%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )
            }
        }
    }
}

@Composable
fun NighttimeDeepSleepCard(
    isDeepSleepEnabled: Boolean,
    startTime: String = "09:00 PM",
    endTime: String = "06:00 AM",
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isCurrentlyWindow = com.example.engines.deepsleep.DeepSleepEngine.isTimeInWindow(startTime, endTime)
    val isModeActiveNow = isDeepSleepEnabled && isCurrentlyWindow

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("nighttime_deep_sleep_card")
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isModeActiveNow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(
            1.dp,
            if (isModeActiveNow) Color(0xFF00E676).copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (isModeActiveNow) Color(0xFF00E676).copy(alpha = 0.15f)
                            else Color(0xFF3F51B5).copy(alpha = 0.15f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PauseCircle,
                        contentDescription = null,
                        tint = if (isModeActiveNow) Color(0xFF00E676) else Color(0xFF7986CB),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Nighttime Deep Sleep",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = if (isModeActiveNow) Color(0xFF00E676).copy(alpha = 0.15f)
                            else if (isDeepSleepEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isModeActiveNow) "ACTIVE"
                                else if (isDeepSleepEnabled) "SCHEDULED"
                                else "OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isModeActiveNow) Color(0xFF00E676)
                                else if (isDeepSleepEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isDeepSleepEnabled) "Throttles polling $startTime–$endTime • Thermal safe 🔒"
                        else "Deep Sleep disabled in Settings",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Manage in Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AppPowerDrainInspectorCard(
    measuredEnergyConsumers: List<Pair<String, Float>> = emptyList(),
    modifier: Modifier = Modifier
) {
    // STRICT DATA AVAILABILITY & NO-GUESSWORK POLICY:
    // If real measured per-process energy drain telemetry is not available from Android OS,
    // NEVER show fake precision or hardcoded rankings (e.g. 0.4%/hr, 0.2%/hr).
    // Hide the card completely when genuine measurement data is unavailable.
    if (measuredEnergyConsumers.isEmpty()) {
        return
    }

    Card(
        modifier = modifier.fillMaxWidth().testTag("app_power_drain_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).background(Color(0xFFE91E63).copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Filled.Sensors, contentDescription = null, tint = Color(0xFFE91E63), modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = "Top Energy & Wake-Lock Inspector", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Measured process power rankings", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            measuredEnergyConsumers.take(5).forEachIndexed { index, (name, drainRatePctHr) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "${index + 1}. $name", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", drainRatePctHr)}% / hr",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                }
            }
        }
    }
}


