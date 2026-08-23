package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.BatteryState

/**
 * Netra Smart Heat Source Analyzer Screen
 * Made with ❤️ by Prayagi Ji
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartHeatSourceAnalyzerScreen(
    state: BatteryState,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val temp = state.temperature

    val color = when {
        temp < 38f -> Color(0xFF4CAF50)
        temp < 42f -> Color(0xFFFBC02D)
        temp < 45f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    val tempStatus = when {
        temp < 38f -> "Cool & Healthy"
        temp < 42f -> "Moderate Warmth"
        temp < 45f -> "Warning Threshold"
        else -> "🔴 CRITICAL OVERHEAT"
    }

    val possibleCauses = remember(temp, state.isPlugged, state.currentNow) {
        val list = mutableListOf<String>()
        if (state.isPlugged) {
            if (state.currentNow > 2000) {
                list.add("🔌 Fast Charging Intake (PPS Protocol Active)")
            } else {
                list.add("🔌 Standard USB Charging Flow")
            }
        }
        val currentDrain = -state.currentNow
        if (currentDrain > 800) {
            list.add("🎮 Heavy 3D GPU Rendering (Active Gaming)")
            list.add("🧠 High Thread Count Multiprocessing")
        } else if (currentDrain > 450) {
            list.add("🧠 Active CPU Scheduling")
            list.add("📷 Hardware Video Encoding / Active Camera")
        } else if (currentDrain > 250) {
            list.add("📍 Global GPS Constellation Tracking")
            list.add("📡 Weak LTE/5G Cellular Baseband Hunting")
        } else if (temp > 38f && !state.isPlugged) {
            list.add("📲 Rogue Background Activity Wake-Locks")
            list.add("🌞 Hot Ambient Temperature (Solar Radiation)")
        }
        if (list.isEmpty()) {
            list.add("❓ Ambient Absorption / Chassis Heat Dissipation Index")
        }
        list
    }

    val recommendations = remember(possibleCauses) {
        val list = mutableListOf<String>()
        possibleCauses.forEach { cause ->
            when {
                cause.contains("Charging") -> list.add("Disconnect the fast charger immediately.")
                cause.contains("GPU") -> list.add("Suspend demanding 3D games or graphic software.")
                cause.contains("CPU") -> list.add("Kill frozen background applications in Settings.")
                cause.contains("Camera") -> list.add("Close active viewfinder or video recording sessions.")
                cause.contains("GPS") -> list.add("Toggle system location services to Off temporarily.")
                cause.contains("Baseband") -> list.add("Enable Airplane mode briefly to reset antenna search.")
                cause.contains("Wake-Locks") -> list.add("Activate Netra Smart Battery Saver mode.")
            }
        }
        list.add("Remove phone cover case to allow chassis heat sinking.")
        list.add("Place the host on a cool, well-ventilated flat surface.")
        list.distinct()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("smart_heat_analyzer_container")
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header Card
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
                        imageVector = Icons.Filled.Thermostat,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Smart Heat Source Analyzer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Monitors dynamic thermal loads and identifies probable causes.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.isExternalHeatInferred) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("external_heat_warning_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, Color(0xFFF44336))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "🔴 External Heat Inferred",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFFF44336)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Confidence Score: ${state.externalHeatConfidence}%",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFFF44336)
                    )
                    Text(
                        text = "Temperature Rise Rate: ${String.format(java.util.Locale.US, "%.3f", state.externalHeatRiseRate)} °C/min",
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Inferred Reasons:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    val inferenceReasons = remember(state.ambientLightLux, state.isCharging, state.isPocketModeActive, state.outdoorTemp) {
                        val reasons = mutableListOf<String>()
                        if (state.externalHeatRiseRate > 0.05f) {
                            reasons.add("Abnormal continuous temperature rise detected.")
                        }
                        if (state.isCharging) {
                            reasons.add("Rise rate exceeds normal charging dissipation models.")
                        } else {
                            reasons.add("Device is not charging, yet temperature continues to rise.")
                        }
                        if (state.ambientLightLux > 5000f) {
                            reasons.add("High ambient light level (${state.ambientLightLux.toInt()} Lux) indicates direct sunlight exposure.")
                        }
                        if (state.outdoorTemp > 0f && state.temperature > state.outdoorTemp + 5f) {
                            reasons.add("Device temperature significantly exceeds local weather data (+${String.format(java.util.Locale.US, "%.1f", state.temperature - state.outdoorTemp)}°C).")
                        }
                        reasons
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    ) {
                        inferenceReasons.forEach { reason ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(4.dp).background(Color(0xFFF44336), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = reason,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFFF44336).copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Cooling Guidance (User Action Required):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFF44336)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Info, null, tint = Color(0xFFF44336), modifier = Modifier.size(14.dp).padding(top = 1.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Move the phone away from external heat sources (stove, heater, vehicle dashboard).",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Info, null, tint = Color(0xFFF44336), modifier = Modifier.size(14.dp).padding(top = 1.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Place the phone in a cooler, shaded location out of direct sunlight.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Info, null, tint = Color(0xFFF44336), modifier = Modifier.size(14.dp).padding(top = 1.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Remove thick protective cases to allow natural chassis heat dissipation.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (state.isCharging) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Filled.Info, null, tint = Color(0xFFF44336), modifier = Modifier.size(14.dp).padding(top = 1.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Stop charging immediately to reduce secondary battery-dissipated heat.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Thermal Gauge
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "LIVE BATTERY CORE TEMPERATURE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                Text(
                    text = "${temp}°C",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    color = color
                )

                Text(
                    text = tempStatus,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Min Temp", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (state.lowestTemp > -900f) "${state.lowestTemp}°C" else "${temp}°C",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Current Temp", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "${temp}°C",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = color
                        )
                    }
                    Box(modifier = Modifier.width(1.dp).height(32.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Max Temp", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = if (state.highestTemp > -900f) "${state.highestTemp}°C" else "${temp}°C",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (temp >= 45f) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Red.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CRITICAL THERMAL LEVEL: Disconnect charger and cease heavy app use to protect internal lithium structures.",
                                color = Color.Red,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Probable Causes Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Likely Heating Sources / Causes:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    possibleCauses.forEach { cause ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(color, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cause,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Recommended Actions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Recommended Cooling Actions:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recommendations.forEach { recommendation ->
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = recommendation,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Footer Branding
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
