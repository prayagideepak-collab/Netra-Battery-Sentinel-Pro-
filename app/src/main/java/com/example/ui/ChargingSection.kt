package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.BatteryViewModel
import com.example.data.ChargingSession
import com.example.service.BatteryState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChargingSection(viewModel: BatteryViewModel) {
    val state by viewModel.sanitizedBatteryState.collectAsStateWithLifecycle()
    val sessionList by viewModel.sessions.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 1. Live Telemetry State derived from authoritative BatteryState
    val isChargingState = state.isCharging
    val livePercentage = state.percentage
    val liveCurrentNow = kotlin.math.abs(state.currentNow.toFloat())
    val liveVoltage = if (state.voltage > 0) state.voltage / 1000f else 0f
    val liveTemp = if (state.temperature > -900f) state.temperature else 0f
    val livePower = (liveCurrentNow * liveVoltage) / 1000f
    val liveSource = state.chargingType.takeIf { it.isNotBlank() } ?: "None"
    val liveHealth = state.health
    val liveClassification = if (state.chargingSpeed.isNotBlank() && state.chargingSpeed != "None") {
        state.chargingSpeed
    } else if (isChargingState) {
        "STANDARD CHARGING"
    } else {
        "DISCONNECTED"
    }

    // Rolling graph buffer for live Current (mA) from authoritative stream
    val liveGraphBuffer = remember { mutableStateListOf<Float>() }

    LaunchedEffect(state.currentNow, state.isCharging) {
        if (state.isCharging && liveCurrentNow > 0) {
            if (liveGraphBuffer.size >= 30) {
                liveGraphBuffer.removeAt(0)
            }
            liveGraphBuffer.add(liveCurrentNow)
        } else if (!state.isCharging) {
            liveGraphBuffer.clear()
        }
    }

    // Infinite animation transition for the ring pulsation & sweep
    val infiniteTransition = rememberInfiniteTransition(label = "charging_animation")
    val pulsingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsing_glow"
    )
    val rotatingSweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotating_sweep"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14)) // Immersive high-contrast dark space
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // HEADER BLOCK
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔋 NETRA LIVE CHARGING SENTINEL",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF00FFCC),
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = if (isChargingState) "Real-time Telemetry Stream active" else "Connect charger to activate telemetry",
                    fontSize = 11.sp,
                    color = Color.LightGray.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // 2. LIVE / DISCONNECTED BANNER PANEL
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isChargingState) {
                            Brush.horizontalGradient(listOf(Color(0xFF004D40), Color(0xFF00796B)))
                        } else {
                            Brush.horizontalGradient(listOf(Color(0xFF263238), Color(0xFF37474F)))
                        }
                    )
                    .border(
                        1.dp,
                        if (isChargingState) Color(0xFF00FFCC) else Color.Gray.copy(alpha = 0.4f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isChargingState) "⚡ CHARGER DETECTED" else "🔌 DISCONNECTED (Static Mode)",
                        color = if (isChargingState) Color(0xFF00FFCC) else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. THE CHARGING RING / PULSING COMPOSABLE
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isChargingState) {
                    // Pulsing animated aura for Active Charging
                    Canvas(modifier = Modifier.size(200.dp)) {
                        // Outer rotating glow ring
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color(0xFF00FFCC).copy(alpha = 0.1f),
                                    Color(0xFF00FFCC),
                                    Color(0xFF00FFCC).copy(alpha = 0.1f)
                                )
                            ),
                            startAngle = rotatingSweepAngle,
                            sweepAngle = 320f,
                            useCenter = false,
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )

                        // Base track circle
                        drawCircle(
                            color = Color(0xFF1E293B),
                            radius = 80.dp.toPx(),
                            style = Stroke(width = 12.dp.toPx())
                        )

                        // Actual battery percentage ring
                        drawArc(
                            color = Color(0xFF00FFCC),
                            startAngle = -90f,
                            sweepAngle = (livePercentage * 360f / 100f),
                            useCenter = false,
                            style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Inside texts
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$livePercentage%",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00FFCC)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = String.format(Locale.US, "%.1f W", livePower),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = liveClassification,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (liveClassification.contains("WARNING")) Color(0xFFFF5252) else Color(0xFF00FFCC),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    // Static mode circle when discharging
                    Canvas(modifier = Modifier.size(180.dp)) {
                        drawCircle(
                            color = Color(0xFF1E293B),
                            radius = 70.dp.toPx(),
                            style = Stroke(width = 8.dp.toPx())
                        )
                        drawArc(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            startAngle = -90f,
                            sweepAngle = (state.percentage * 360f / 100f),
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${state.percentage}%",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.LightGray
                        )
                        Text(
                            text = "Static Monitor",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // 4. TELEMETRY CARDS (Grid of 6 metrics)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isChargingState) {
                    // --- LIVE TELEMETRY ROW 1 ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiveMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "CHARGING CURRENT",
                            value = if (liveCurrentNow > 0) String.format(Locale.US, "%.0f mA", liveCurrentNow) else "Unavailable",
                            subtext = "Active current draw",
                            color = Color(0xFF00FFCC)
                        )
                        LiveMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "VOLTAGE LEVEL",
                            value = if (liveVoltage > 0) String.format(Locale.US, "%.2f V", liveVoltage) else "Unavailable",
                            subtext = "Dynamic cell tension",
                            color = Color(0xFF00E5FF)
                        )
                    }

                    // --- LIVE TELEMETRY ROW 2 ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tempColor = when {
                            liveTemp >= 40f -> Color(0xFFFF3366)
                            liveTemp >= 38f -> Color(0xFFFF9100)
                            else -> Color(0xFF00FFCC)
                        }
                        LiveMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "THERMAL INTAKE",
                            value = if (liveTemp > 0) String.format(Locale.US, "%.1f °C", liveTemp) else "Unavailable",
                            subtext = if (liveTemp >= 38f) "WARNING: Thermal stress" else "Optimal operating heat",
                            color = tempColor
                        )
                        LiveMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "BATTERY HEALTH",
                            value = liveHealth,
                            subtext = "Integrity check",
                            color = if (liveHealth == "Good") Color(0xFF00FFCC) else Color(0xFFFF3366)
                        )
                    }

                    // --- LIVE TELEMETRY ROW 3 ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiveMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "POWER SOURCE",
                            value = liveSource,
                            subtext = "Adapter connection type",
                            color = Color.White
                        )
                        LiveMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "TOTAL POWER WATT",
                            value = if (livePower > 0) String.format(Locale.US, "%.2f W", livePower) else "Unavailable",
                            subtext = "Effective power intake",
                            color = Color(0xFFE040FB)
                        )
                    }
                } else {
                    // --- HISTORICAL STATISTICS GRID ---
                    Text(
                        text = "📊 Authoritative Today's Statistics",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LiveMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "TODAY'S BATTERY RANGE",
                            value = "${if (state.lowestBattery24h != -1) state.lowestBattery24h else 0}% - ${if (state.highestBattery24h != -1) state.highestBattery24h else 0}%",
                            subtext = "Lowest / Highest state today",
                            color = Color(0xFF90A4AE)
                        )
                        LiveMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "TODAY'S TEMPERATURE RANGE",
                            value = "${String.format(Locale.US, "%.1f", state.lowestTemp)}°C - ${String.format(Locale.US, "%.1f", state.highestTemp)}°C",
                            subtext = "Minimum / Maximum bounds today",
                            color = Color(0xFFFF9100)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val minVol = if (state.lowestVoltage24h != -1) String.format(Locale.US, "%.2fV", state.lowestVoltage24h / 1000f) else "N/A"
                        val maxVol = if (state.highestVoltage24h != -1) String.format(Locale.US, "%.2fV", state.highestVoltage24h / 1000f) else "N/A"
                        LiveMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "TODAY'S VOLTAGE RANGE",
                            value = "$minVol - $maxVol",
                            subtext = "Tension bounds today",
                            color = Color(0xFF00E5FF)
                        )
                        LiveMetricCard(
                            modifier = Modifier.weight(1f),
                            title = "TODAY'S PEAK SPEED",
                            value = String.format(Locale.US, "%.1f W", state.peakWatt),
                            subtext = "Peak documented session today",
                            color = Color(0xFFE040FB)
                        )
                    }
                }
            }
        }

        // 5. ANIMATED ROLLING LIVE GRAPH
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141822)),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📈 Rolling Live Telemetry Current Graph",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray
                        )
                        if (isChargingState) {
                            Text(
                                text = "STREAMING (300ms)",
                                fontSize = 9.sp,
                                color = Color(0xFF00FFCC),
                                fontWeight = FontWeight.ExtraBold
                            )
                        } else {
                            Text(
                                text = "FROZEN (DISCONNECTED)",
                                fontSize = 9.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (liveGraphBuffer.isNotEmpty()) {
                            val maxBufferValue = (liveGraphBuffer.maxOrNull() ?: 1000f).coerceAtLeast(100f)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height
                                val pointsCount = liveGraphBuffer.size
                                val stepX = width / 29f

                                val path = Path()
                                liveGraphBuffer.forEachIndexed { idx, value ->
                                    val x = idx * stepX
                                    val y = height - (value / maxBufferValue * height)
                                    if (idx == 0) {
                                        path.moveTo(x, y)
                                    } else {
                                        path.lineTo(x, y)
                                    }
                                }

                                // Fill path below curve with nice soft glowing color
                                val fillPath = Path().apply {
                                    addPath(path)
                                    lineTo((pointsCount - 1) * stepX, height)
                                    lineTo(0f, height)
                                    close()
                                }

                                drawPath(
                                    path = fillPath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF00FFCC).copy(alpha = 0.2f),
                                            Color(0xFF00FFCC).copy(alpha = 0.0f)
                                        )
                                    )
                                )

                                drawPath(
                                    path = path,
                                    color = Color(0xFF00FFCC),
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        } else {
                            // Dotted offline graph placeholder
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawLine(
                                    color = Color.Gray.copy(alpha = 0.3f),
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(size.width, size.height / 2),
                                    strokeWidth = 2f,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(10f, 10f), 0f
                                    )
                                )
                            }
                            Text(
                                text = "Offline (Connect power to stream current plot)",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // 6. HISTORICAL LOG OF DISCHARGING / CHARGING SESSIONS
        item {
            Text(
                text = "🧾 Finished Charging Session Log (Database)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (sessionList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141822)),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Text(
                        text = "No recorded sessions in database. Plug in and out to save history.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    )
                }
            }
        } else {
            items(sessionList.take(8)) { session ->
                SessionHistoryCard(session)
            }
        }
    }
}

@Composable
fun LiveMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtext: String,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141822)),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = title,
                fontSize = 9.sp,
                color = Color.LightGray.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                fontSize = 9.sp,
                color = Color.Gray,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SessionHistoryCard(session: ChargingSession) {
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
    val startStr = sdf.format(Date(session.startTime))
    val endStr = if (session.endTime != null) sdf.format(Date(session.endTime)) else "Active"

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141822)),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "SESS_CHG_${session.startTime / 1000}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FFCC)
                    )
                    // Status Badge
                    val statusColor = when (session.sessionStatus) {
                        "COMPLETED" -> Color(0xFF4CAF50)
                        "INTERRUPTED" -> Color(0xFFFFC107)
                        else -> Color(0xFF2196F3)
                    }
                    Text(
                        text = session.sessionStatus,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (session.fullyCharged) {
                        Text(
                            text = "⚡ FULLY CHARGED",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E676),
                            modifier = Modifier
                                .background(Color(0xFF00E676).copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = if (session.isDischarge) "DISCHARGE" else "CHARGED ${session.chargingType}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (session.isDischarge) Color(0xFFFF5252) else Color(0xFF4CAF50),
                        modifier = Modifier
                            .background(
                                if (session.isDischarge) Color(0xFFFF5252).copy(alpha = 0.1f) else Color(0xFF4CAF50).copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("START LEVEL", fontSize = 8.sp, color = Color.Gray)
                    Text("${session.startPercentage}%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column {
                    Text("END LEVEL", fontSize = 8.sp, color = Color.Gray)
                    Text(
                        "${session.endPercentage ?: "Active"}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Column {
                    Text("DURATION", fontSize = 8.sp, color = Color.Gray)
                    Text("${session.totalDurationSeconds}s", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column {
                    Text("PEAK TEMP", fontSize = 8.sp, color = Color.Gray)
                    Text(
                        String.format(Locale.US, "%.1f°C", session.maxTemperature),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (session.maxTemperature >= 38f) Color(0xFFFF9100) else Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0xFF1E293B)))
            Spacer(modifier = Modifier.height(8.dp))

            // Detailed Telemetry
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("START TEMP", fontSize = 8.sp, color = Color.Gray)
                    Text("${session.startTemperature}°C", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.LightGray)
                }
                Column {
                    Text("END TEMP", fontSize = 8.sp, color = Color.Gray)
                    Text(
                        text = session.endTemperature?.let { "${it}°C" } ?: "Active",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                }
                Column {
                    Text("OVERCHARGE", fontSize = 8.sp, color = Color.Gray)
                    Text(
                        text = "${session.overchargingDurationSeconds}s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (session.overchargingDurationSeconds > 0) Color(0xFFFF9100) else Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Formatted Time Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Start: ${session.formattedStartTime.ifEmpty { startStr }}",
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                    session.formattedEndTime?.let {
                        Text(
                            text = "End: $it",
                            fontSize = 9.sp,
                            color = Color.Gray
                        )
                    }
                }
                if (session.formattedFullChargeTime != null) {
                    Text(
                        text = "Full at: ${session.formattedFullChargeTime}",
                        fontSize = 9.sp,
                        color = Color(0xFF00FFCC),
                        fontWeight = FontWeight.Bold
                    )
                }
                if (session.avgPower > 0) {
                    Text(
                        text = "Avg: ${String.format(Locale.US, "%.1f W", session.avgPower)}",
                        fontSize = 9.sp,
                        color = Color.LightGray,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
