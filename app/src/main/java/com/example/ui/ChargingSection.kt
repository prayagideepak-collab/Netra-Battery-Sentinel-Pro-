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

    // 1. Live Telemetry State (Updated every 300ms when charging is true)
    var isChargingState by remember { mutableStateOf(false) }
    var livePercentage by remember { mutableStateOf(0) }
    var liveCurrentNow by remember { mutableStateOf(0f) }
    var liveVoltage by remember { mutableStateOf(0f) }
    var liveTemp by remember { mutableStateOf(0f) }
    var livePower by remember { mutableStateOf(0f) }
    var liveSource by remember { mutableStateOf("None") }
    var liveHealth by remember { mutableStateOf("Good") }
    var liveClassification by remember { mutableStateOf("None") }

    // Rolling graph buffer for live Current (mA)
    val liveGraphBuffer = remember { mutableStateListOf<Float>() }

    // Real-time loop (runs only when charging)
    LaunchedEffect(state.isCharging) {
        if (state.isCharging) {
            isChargingState = true
            while (state.isCharging) {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

                // Percentage
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                livePercentage = if (level != -1 && scale > 0) (level * 100 / scale) else state.percentage

                // Current Now
                val rawCurrent = try {
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                } catch (e: Exception) {
                    0
                }
                // Normalize to mA
                var currentMA = rawCurrent / 1000f
                if (Math.abs(currentMA) > 15000f) currentMA /= 1000f
                liveCurrentNow = Math.abs(currentMA)

                // Voltage V
                val rawVoltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
                liveVoltage = rawVoltage / 1000f

                // Temperature
                val rawTemp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                liveTemp = rawTemp / 10f

                // Power Wattage
                livePower = (liveCurrentNow * liveVoltage) / 1000f

                // Source
                val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
                liveSource = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC Wall Adapter"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB Port"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Charging"
                    else -> "Unknown Port"
                }

                // Health
                val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
                liveHealth = when (healthInt) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
                    BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                    else -> "Unknown"
                }

                // Temperature Speed Intelligence (Classification Engine)
                val isFastCharging = livePower >= 9.5f || (plugged == BatteryManager.BATTERY_PLUGGED_AC)
                liveClassification = when {
                    isFastCharging && liveTemp >= 39.0f -> "FAST CHARGING · THERMAL WARNING"
                    isFastCharging && liveTemp >= 38.0f -> "FAST CHARGING · ELEVATED THERMALS"
                    isFastCharging -> "FAST CHARGING"
                    plugged == BatteryManager.BATTERY_PLUGGED_USB -> "NORMAL USB CHARGING"
                    plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "SLOW WIRELESS CHARGING"
                    else -> "STANDARD CHARGING"
                }

                // Append validated reading to rolling graph buffer
                if (liveCurrentNow > 0) {
                    if (liveGraphBuffer.size >= 30) {
                        liveGraphBuffer.removeAt(0)
                    }
                    liveGraphBuffer.add(liveCurrentNow)
                }

                // Up to ~300ms update frequency
                delay(300L)
            }
        } else {
            isChargingState = false
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
    val durationMin = if (session.endTime != null) (session.endTime - session.startTime) / 1000 / 60 else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                Text(
                    text = "SESS_CHG_${session.startTime / 1000}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FFCC)
                )
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

            Spacer(modifier = Modifier.height(8.dp))

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
                    Text("$durationMin mins", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
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

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Timeline: $startStr - $endStr",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
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
