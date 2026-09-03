package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ChargingSession
import com.example.data.BatteryTrendLog
import java.text.SimpleDateFormat
import java.util.*

data class RechartsDataPoint(
    val timestamp: Long,
    val level: Float,               // 0..100%
    val temperature: Float,         // Temperature in °C
    val isCharging: Boolean,
    val dischargeRate: Float,       // %/hr
    val voltage: Int,               // mV
    val isSpike: Boolean,           // temperature >= 38.0°C
    val spikeSeverity: String,      // "NORMAL", "WARM", "CRITICAL", "OVERHEAT"
    val info: String
)

enum class RechartsTimeFrame(val label: String, val durationMs: Long) {
    SIX_HOURS("6 Hours", 6 * 3600 * 1000L),
    TWELVE_HOURS("12 Hours", 12 * 3600 * 1000L),
    TWENTY_FOUR_HOURS("24 Hours", 24 * 3600 * 1000L),
    SEVEN_DAYS("7 Days", 7 * 24 * 3600 * 1000L)
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun RechartsHistoricalDashboard(
    sessions: List<ChargingSession>,
    trendLogs: List<BatteryTrendLog>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val surfaceColor = MaterialTheme.colorScheme.surface

    var selectedTimeFrame by remember { mutableStateOf(RechartsTimeFrame.TWENTY_FOUR_HOURS) }
    var activeChartTab by remember { mutableIntStateOf(0) } // 0: Dual Overlay, 1: Temp Spikes, 2: Usage Rates

    // Filter or generate data points for the selected timeframe
    val points = remember(sessions, trendLogs, selectedTimeFrame) {
        val list = mutableListOf<RechartsDataPoint>()
        val now = System.currentTimeMillis()

        // 1. Convert Room trend logs
        val filteredLogs = if (selectedTimeFrame == RechartsTimeFrame.TWENTY_FOUR_HOURS) {
            val startTime = com.example.util.TimeManager.getStartOfLocalDay(now)
            val endTime = com.example.util.TimeManager.getEndOfLocalDay(now)
            trendLogs.filter { it.timestamp in startTime..endTime }.sortedBy { it.timestamp }
        } else {
            val startTime = now - selectedTimeFrame.durationMs
            trendLogs.filter { it.timestamp >= startTime }.sortedBy { it.timestamp }
        }

        filteredLogs.forEach { log ->
            val isCharging = log.dischargeRate <= 0f
            val temp = log.temperature
            val isSpike = temp >= 38.0f
            val severity = when {
                temp >= 45.0f -> "OVERHEAT"
                temp >= 42.0f -> "CRITICAL"
                temp >= 38.0f -> "WARM"
                else -> "NORMAL"
            }
            list.add(
                RechartsDataPoint(
                    timestamp = log.timestamp,
                    level = log.batteryLevel.toFloat(),
                    temperature = temp,
                    isCharging = isCharging,
                    dischargeRate = log.dischargeRate,
                    voltage = log.voltage,
                    isSpike = isSpike,
                    spikeSeverity = severity,
                    info = if (isSpike) "⚠️ Thermal Spike: ${temp}°C" else if (isCharging) "Charging" else "Discharging"
                )
            )
        }

        list.sortBy { it.timestamp }
        list
    }

    var selectedPointIndex by remember { mutableIntStateOf(-1) }

    // Analytics summary - strictly computed without synthetic fallbacks
    val peakTemp = remember(points) { points.maxOfOrNull { it.temperature } }
    val spikeCount = remember(points) { points.count { it.isSpike } }
    val maxDrainRate = remember(points) { points.filter { !it.isCharging }.maxOfOrNull { it.dischargeRate } }
    val avgTemp = remember(points) { if (points.isNotEmpty()) points.map { it.temperature }.average().toFloat() else null }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 1. Header with Recharts & Safety Sensor Branding
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ShowChart,
                            contentDescription = "Recharts Visualization",
                            tint = primaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Recharts™ Safety & Usage Plotter",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Dual-axis battery usage & safety thermal spike sensor",
                            fontSize = 11.sp,
                            color = onSurfaceVariant
                        )
                    }
                }

                if (spikeCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(100.dp),
                        color = Color(0xFFD32F2F).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.4f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFFD32F2F),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$spikeCount Thermal ${if (spikeCount == 1) "Spike" else "Spikes"}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Time Window Chips Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RechartsTimeFrame.entries.forEach { tf ->
                    val isSelected = selectedTimeFrame == tf
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedTimeFrame = tf
                            selectedPointIndex = -1
                        },
                        label = {
                            Text(
                                text = tf.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor.copy(alpha = 0.18f),
                            selectedLabelColor = primaryColor
                        ),
                        modifier = Modifier.height(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. GLOBAL GRAPH SEPARATION RULE: Independent Metric Cards
            Text(
                text = "INDEPENDENT TELEMETRY METRIC GRAPHS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Card A: Battery Level (%) Independent Graph
            HistoricalMetricCard(
                title = "Battery Level (%)",
                currentValue = points.lastOrNull()?.let { "${it.level.toInt()}%" } ?: "Unavailable",
                unit = "0% - 100%",
                lineColor = primaryColor,
                points = points,
                valueSelector = { it.level },
                maxValue = 100f,
                minValue = 0f,
                textMeasurer = textMeasurer,
                density = density,
                gridColor = gridColor,
                onSurfaceVariant = onSurfaceVariant,
                selectedTimeFrame = selectedTimeFrame
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Card B: Safety Sensor Temperature (°C) Independent Graph
            HistoricalMetricCard(
                title = "Safety Sensor Temperature (°C)",
                currentValue = points.lastOrNull()?.let { String.format(Locale.US, "%.1f°C", it.temperature) } ?: "Unavailable",
                unit = "20°C - 50°C",
                lineColor = Color(0xFFFF5722),
                points = points,
                valueSelector = { it.temperature },
                maxValue = 50f,
                minValue = 20f,
                showThresholds = true,
                textMeasurer = textMeasurer,
                density = density,
                gridColor = gridColor,
                onSurfaceVariant = onSurfaceVariant,
                selectedTimeFrame = selectedTimeFrame
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 6. SAFETY SENSOR THERMAL SPIKE ANALYTICS CARDS
            Text(
                text = "SAFETY SENSOR SPIKE SUMMARY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Peak Temperature
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("Peak Sensor Temp", fontSize = 10.sp, color = onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = peakTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Unavailable",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = if ((peakTemp ?: 0f) >= 42f) Color(0xFFD32F2F) else if ((peakTemp ?: 0f) >= 38f) Color(0xFFFF9800) else Color(0xFF4CAF50)
                    )
                }

                // Total Spikes
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("Thermal Spikes", fontSize = 10.sp, color = onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$spikeCount Detected",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = if (spikeCount > 0) Color(0xFFD32F2F) else Color(0xFF4CAF50)
                    )
                }

                // Average Temperature
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                        .padding(10.dp)
                ) {
                    Text("Avg Sensor Temp", fontSize = 10.sp, color = onSurfaceVariant)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = avgTemp?.let { String.format(Locale.US, "%.1f°C", it) } ?: "Unavailable",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7. HISTORICAL SAFETY SENSOR EVENT LOG
            val spikeEvents = remember(points) { points.filter { it.isSpike }.sortedByDescending { it.timestamp } }
            if (spikeEvents.isNotEmpty()) {
                Text(
                    text = "SAFETY SENSOR SPIKE LOGS (${spikeEvents.size})",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = onSurfaceVariant,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    spikeEvents.take(4).forEach { event ->
                        val dateStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(event.timestamp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFD32F2F).copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .border(0.5.dp, Color(0xFFD32F2F).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Thermal Spike: ${String.format(Locale.US, "%.1f", event.temperature)}°C",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$dateStr • Level ${event.level.toInt()}% • Voltage ${event.voltage}mV",
                                        fontSize = 10.sp,
                                        color = onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = event.spikeSeverity,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun HistoricalMetricCard(
    title: String,
    currentValue: String,
    unit: String,
    lineColor: Color,
    points: List<RechartsDataPoint>,
    valueSelector: (RechartsDataPoint) -> Float,
    maxValue: Float,
    minValue: Float,
    showThresholds: Boolean = false,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    gridColor: Color,
    onSurfaceVariant: Color,
    selectedTimeFrame: RechartsTimeFrame
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0C14)),
        border = BorderStroke(0.5.dp, lineColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(lineColor, CircleShape))
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                Text(
                    text = currentValue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = lineColor
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF121624))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (points.isEmpty()) {
                    Text(
                        text = "No historical telemetry points for this timeframe",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val paddingLeft = 28.dp.toPx()
                    val paddingRight = 24.dp.toPx()
                    val paddingTop = 12.dp.toPx()
                    val paddingBottom = 20.dp.toPx()

                    val chartWidth = width - paddingLeft - paddingRight
                    val chartHeight = height - paddingTop - paddingBottom

                    val range = (maxValue - minValue).coerceAtLeast(1f)

                    // Grid lines
                    val gridSteps = 3
                    for (i in 0..gridSteps) {
                        val fraction = i.toFloat() / gridSteps
                        val y = paddingTop + chartHeight * (1f - fraction)
                        drawLine(
                            color = gridColor,
                            start = Offset(paddingLeft, y),
                            end = Offset(width - paddingRight, y),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        val valLabel = minValue + fraction * range
                        val labelText = if (maxValue > 50f) "${valLabel.toInt()}%" else "${valLabel.toInt()}°C"
                        drawText(
                            textMeasurer = textMeasurer,
                            text = labelText,
                            topLeft = Offset(2.dp.toPx(), y - 6.dp.toPx()),
                            style = TextStyle(color = onSurfaceVariant, fontSize = 8.sp, fontWeight = FontWeight.Medium)
                        )
                    }

                    // Threshold lines if requested (for temp)
                    if (showThresholds) {
                        val y38 = paddingTop + chartHeight * (1f - ((38f - minValue) / range).coerceIn(0f, 1f))
                        drawLine(
                            color = Color(0xFFFF9800).copy(alpha = 0.5f),
                            start = Offset(paddingLeft, y38),
                            end = Offset(width - paddingRight, y38),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f), 0f)
                        )
                        val y42 = paddingTop + chartHeight * (1f - ((42f - minValue) / range).coerceIn(0f, 1f))
                        drawLine(
                            color = Color(0xFFD32F2F).copy(alpha = 0.7f),
                            start = Offset(paddingLeft, y42),
                            end = Offset(width - paddingRight, y42),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 3f), 0f)
                        )
                    }

                    if (points.size > 1) {
                        val path = Path()
                        val areaPath = Path()

                        val firstVal = valueSelector(points.first())
                        val firstNorm = ((firstVal - minValue) / range).coerceIn(0f, 1f)
                        val firstY = paddingTop + chartHeight * (1f - firstNorm)
                        val firstX = paddingLeft

                        path.moveTo(firstX, firstY)
                        areaPath.moveTo(firstX, height - paddingBottom)
                        areaPath.lineTo(firstX, firstY)

                        for (i in 1 until points.size) {
                            val pt = points[i]
                            val xFraction = i.toFloat() / (points.size - 1)
                            val x = paddingLeft + chartWidth * xFraction
                            val v = valueSelector(pt)
                            val norm = ((v - minValue) / range).coerceIn(0f, 1f)
                            val y = paddingTop + chartHeight * (1f - norm)

                            val prevPt = points[i - 1]
                            val prevXFraction = (i - 1).toFloat() / (points.size - 1)
                            val prevX = paddingLeft + chartWidth * prevXFraction
                            val prevV = valueSelector(prevPt)
                            val prevNorm = ((prevV - minValue) / range).coerceIn(0f, 1f)
                            val prevY = paddingTop + chartHeight * (1f - prevNorm)

                            val cx = prevX + (x - prevX) / 2f
                            path.cubicTo(cx, prevY, cx, y, x, y)
                            areaPath.cubicTo(cx, prevY, cx, y, x, y)
                        }

                        areaPath.lineTo(paddingLeft + chartWidth, height - paddingBottom)
                        areaPath.close()

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(lineColor.copy(alpha = 0.25f), lineColor.copy(alpha = 0.01f)),
                                startY = paddingTop,
                                endY = height - paddingBottom
                            )
                        )

                        drawPath(
                            path = path,
                            color = lineColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        if (showThresholds) {
                            points.forEachIndexed { idx, pt ->
                                if (pt.temperature >= 38.0f) {
                                    val xFraction = idx.toFloat() / (points.size - 1)
                                    val x = paddingLeft + chartWidth * xFraction
                                    val norm = ((pt.temperature - minValue) / range).coerceIn(0f, 1f)
                                    val y = paddingTop + chartHeight * (1f - norm)
                                    val sColor = if (pt.temperature >= 42f) Color(0xFFD32F2F) else Color(0xFFFF9800)
                                    drawCircle(color = sColor, radius = 4.dp.toPx(), center = Offset(x, y))
                                    drawCircle(color = Color.White, radius = 1.5.dp.toPx(), center = Offset(x, y))
                                }
                            }
                        }
                    }
                }
            }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Unit: $unit", fontSize = 9.sp, color = onSurfaceVariant)
                Text(text = "Live Stream", fontSize = 9.sp, color = lineColor.copy(alpha = 0.8f))
            }
        }
    }
}
