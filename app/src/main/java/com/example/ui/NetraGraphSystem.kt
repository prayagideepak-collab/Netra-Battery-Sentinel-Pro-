package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BatteryHistoryEntity
import com.example.data.BatteryTrendLog
import com.example.data.ChargingSession
import com.example.service.BatteryState
import com.example.util.TimeManager
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Netra Battery Sentinel Pro — Unified Graph System
 * High-precision, ultra-low-power, zero-fabrication visualization architecture.
 * Implements the unified Netra UX pattern:
 * Header -> Primary Live Value -> Secondary Value -> Condition -> Graph -> Time Range -> Date Selector -> Statistics -> Explanation
 */

enum class NetraTimeRange(val label: String, val durationMs: Long) {
    ONE_MINUTE("1m", 60_000L),
    TEN_MINUTES("10m", 600_000L),
    ONE_HOUR("1h", 3600_000L),
    SIX_HOURS("6h", 21600_000L),
    TWENTY_FOUR_HOURS("24h", 86400_000L)
}

enum class NetraMetricType(
    val title: String,
    val unit: String,
    val icon: ImageVector,
    val isSigned: Boolean
) {
    BATTERY_LEVEL("Battery Level", "%", Icons.Outlined.BatteryStd, false),
    VOLTAGE("Voltage", "mV", Icons.Outlined.Speed, false),
    CURRENT("Electric Current", "mA", Icons.Outlined.ElectricMeter, true),
    POWER("Wattage & Power", "W", Icons.Outlined.Bolt, true),
    TEMPERATURE("Temperature", "°C", Icons.Outlined.Thermostat, false)
}

data class NetraUnifiedPoint(
    val timestamp: Long,
    val value: Float,
    val secondaryText: String? = null,
    val isCharging: Boolean = false,
    val isAbnormalDrop: Boolean = false,
    val rawTemperature: Float = -1f,
    val rawVoltageMv: Int = 0,
    val rawCurrentMa: Int = 0,
    val rawLevel: Int = -1
)

/**
 * Shared Header for Graph Detail Views
 */
@Composable
fun NetraGraphHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .testTag("btn_graph_back")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Primary Live Metric Display with supporting secondary details and status pill
 */
@Composable
fun NetraPrimaryLiveValueCard(
    metricType: NetraMetricType,
    primaryValueText: String,
    secondaryText: String?,
    conditionText: String,
    conditionColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = metricType.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = metricType.title.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = conditionColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, conditionColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = conditionText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = conditionColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = primaryValueText,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-1).sp
            )

            if (secondaryText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Segmented Time Range Selector: 1m, 10m, 1h, 6h, 24h
 */
@Composable
fun NetraTimeRangeSegmentedControl(
    selectedRange: NetraTimeRange,
    onRangeSelected: (NetraTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            NetraTimeRange.values().forEach { range ->
                val isSelected = range == selectedRange
                val bgColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    label = "timerange_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "timerange_text"
                )

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onRangeSelected(range) }
                        .testTag("time_range_${range.label}"),
                    shape = RoundedCornerShape(10.dp),
                    color = bgColor
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = range.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

/**
 * Calendar-Day Navigation Bar (Active in 24h mode)
 */
@Composable
fun NetraCalendarDateSelector(
    selectedDateMs: Long,
    onPreviousDayClick: () -> Unit,
    onNextDayClick: () -> Unit,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isToday = TimeManager.isToday(selectedDateMs)
    val dateDisplayStr = if (isToday) {
        "Today, ${TimeManager.formatCalendarDate(selectedDateMs)}"
    } else {
        TimeManager.formatCalendarDate(selectedDateMs)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPreviousDayClick,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("btn_prev_calendar_day")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Previous Day",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = dateDisplayStr,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "00:00 → 23:59 (Local Calendar Day)",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isToday) {
                    TextButton(
                        onClick = onTodayClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("btn_today_jump")
                    ) {
                        Text(
                            text = "Today",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onNextDayClick,
                    enabled = !isToday,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("btn_next_calendar_day")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Day",
                        tint = if (!isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Universal High-Precision Interactive Graph Canvas
 */
@Composable
fun NetraUnifiedGraphCanvas(
    points: List<NetraUnifiedPoint>,
    metricType: NetraMetricType,
    timeRange: NetraTimeRange,
    selectedDateMs: Long,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    positiveColor: Color = Color(0xFF00E676),
    negativeColor: Color = Color(0xFFFF5252),
    abnormalDropColor: Color = Color(0xFFFF1744)
) {
    val is24h = timeRange == NetraTimeRange.TWENTY_FOUR_HOURS
    val startWindowMs = if (is24h) {
        TimeManager.getStartOfLocalDay(selectedDateMs)
    } else {
        val now = System.currentTimeMillis()
        now - timeRange.durationMs
    }
    val endWindowMs = if (is24h) {
        TimeManager.getEndOfLocalDay(selectedDateMs)
    } else {
        System.currentTimeMillis()
    }

    var touchedIndex by remember(points, timeRange, selectedDateMs) { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        if (points.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (is24h) "No telemetry recorded for this date." else "Awaiting telemetry stream...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points, startWindowMs, endWindowMs) {
                        detectTapGestures(
                            onTap = { offset ->
                                val leftPad = 38.dp.toPx()
                                val rightPad = 14.dp.toPx()
                                val drawWidth = size.width - leftPad - rightPad
                                if (drawWidth > 0 && points.isNotEmpty()) {
                                    val relX = (offset.x - leftPad).coerceIn(0f, drawWidth)
                                    val pct = relX / drawWidth
                                    val totalDuration = (endWindowMs - startWindowMs).coerceAtLeast(1000L)
                                    val targetTimestamp = startWindowMs + (pct * totalDuration).toLong()
                                    val closest = points.minByOrNull { abs(it.timestamp - targetTimestamp) }
                                    if (closest != null) {
                                        touchedIndex = points.indexOf(closest)
                                    }
                                }
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    if (width <= 0 || height <= 0) return@Canvas

                    val leftPad = 38.dp.toPx()
                    val rightPad = 14.dp.toPx()
                    val topPad = 18.dp.toPx()
                    val bottomPad = 26.dp.toPx()

                    val drawWidth = width - leftPad - rightPad
                    val drawHeight = height - topPad - bottomPad

                    // Calculate Y-bounds from data or fixed physical range
                    val (minBound, maxBound) = when (metricType) {
                        NetraMetricType.BATTERY_LEVEL -> Pair(0f, 100f)
                        NetraMetricType.CURRENT, NetraMetricType.POWER -> {
                            val maxVal = points.maxOfOrNull { it.value } ?: 0f
                            val minVal = points.minOfOrNull { it.value } ?: 0f
                            val absMax = maxOf(abs(maxVal), abs(minVal), 0.1f) * 1.15f
                            Pair(-absMax, absMax)
                        }
                        NetraMetricType.VOLTAGE -> {
                            val maxVal = points.maxOfOrNull { it.value } ?: 4200f
                            val minVal = points.minOfOrNull { it.value } ?: 3700f
                            val pad = ((maxVal - minVal) * 0.15f).coerceAtLeast(50f)
                            Pair((minVal - pad).coerceAtLeast(0f), maxVal + pad)
                        }
                        NetraMetricType.TEMPERATURE -> {
                            val maxVal = points.maxOfOrNull { it.value } ?: 35f
                            val minVal = points.minOfOrNull { it.value } ?: 25f
                            val pad = ((maxVal - minVal) * 0.2f).coerceAtLeast(2f)
                            Pair((minVal - pad).coerceAtLeast(0f), maxVal + pad)
                        }
                    }

                    val rangeY = (maxBound - minBound).coerceAtLeast(0.01f)
                    val totalDurationMs = (endWindowMs - startWindowMs).coerceAtLeast(1000L)

                    // Draw Horizontal Grid Lines & Y-Labels
                    val gridFractions = listOf(0.0f, 0.5f, 1.0f)
                    gridFractions.forEach { fraction ->
                        val y = topPad + (1f - fraction) * drawHeight
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.2f),
                            start = Offset(leftPad, y),
                            end = Offset(width - rightPad, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )

                        val yVal = minBound + fraction * rangeY
                        val labelStr = when (metricType) {
                            NetraMetricType.BATTERY_LEVEL -> "${yVal.roundToInt()}%"
                            NetraMetricType.VOLTAGE -> if (yVal >= 1000f) "${(yVal / 1000f).roundToInt()}V" else "${yVal.roundToInt()}"
                            NetraMetricType.CURRENT -> "${yVal.roundToInt()}mA"
                            NetraMetricType.POWER -> String.format(Locale.US, "%.1fW", yVal)
                            NetraMetricType.TEMPERATURE -> "${yVal.roundToInt()}°"
                        }

                        drawContext.canvas.nativeCanvas.drawText(
                            labelStr,
                            leftPad - 4.dp.toPx(),
                            y + 3.dp.toPx(),
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 9.sp.toPx()
                                textAlign = android.graphics.Paint.Align.RIGHT
                                isAntiAlias = true
                            }
                        )
                    }

                    // Draw Zero Reference Line for Signed Parameters (Current, Power)
                    if (metricType.isSigned && minBound < 0f && maxBound > 0f) {
                        val zeroY = topPad + (1f - ((0f - minBound) / rangeY)) * drawHeight
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.45f),
                            start = Offset(leftPad, zeroY),
                            end = Offset(width - rightPad, zeroY),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    val effectiveLineColor = when {
                        metricType == NetraMetricType.TEMPERATURE -> Color(0xFFFF9100)
                        metricType == NetraMetricType.CURRENT || metricType == NetraMetricType.POWER -> positiveColor
                        else -> lineColor
                    }

                    // Map Points to Canvas Coordinates
                    val pointOffsets = mutableListOf<Pair<Offset, NetraUnifiedPoint>>()
                    points.forEach { pt ->
                        val timeFraction = ((pt.timestamp - startWindowMs).toFloat() / totalDurationMs).coerceIn(0f, 1f)
                        val x = leftPad + timeFraction * drawWidth
                        val yFraction = ((pt.value - minBound) / rangeY).coerceIn(0f, 1f)
                        val y = topPad + (1f - yFraction) * drawHeight
                        pointOffsets.add(Pair(Offset(x, y), pt))
                    }

                    // Draw Graph Line & Shading
                    if (pointOffsets.isNotEmpty()) {
                        val path = Path()
                        val abnormalPaths = mutableListOf<Path>()

                        var activeAbnormalPath: Path? = null

                        pointOffsets.forEachIndexed { idx, (offset, pt) ->
                            if (idx == 0) {
                                path.moveTo(offset.x, offset.y)
                            } else {
                                path.lineTo(offset.x, offset.y)
                            }

                            // Abnormal Rapid Drop segment tracking
                            if (pt.isAbnormalDrop) {
                                if (activeAbnormalPath == null) {
                                    activeAbnormalPath = Path().apply { moveTo(offset.x, offset.y) }
                                } else {
                                    activeAbnormalPath?.lineTo(offset.x, offset.y)
                                }
                            } else {
                                activeAbnormalPath?.let { abnormalPaths.add(it) }
                                activeAbnormalPath = null
                            }
                        }
                        activeAbnormalPath?.let { abnormalPaths.add(it) }

                        // Gradient Fill Under Standard Path
                        val fillPath = Path().apply {
                            addPath(path)
                            val lastX = pointOffsets.last().first.x
                            val firstX = pointOffsets.first().first.x
                            val zeroBaselineY = if (metricType.isSigned && minBound < 0f) {
                                topPad + (1f - ((0f - minBound) / rangeY)) * drawHeight
                            } else {
                                height - bottomPad
                            }
                            lineTo(lastX, zeroBaselineY)
                            lineTo(firstX, zeroBaselineY)
                            close()
                        }

                        val effectiveLineColor = when {
                            metricType == NetraMetricType.TEMPERATURE -> Color(0xFFFF9100)
                            metricType == NetraMetricType.CURRENT || metricType == NetraMetricType.POWER -> positiveColor
                            else -> lineColor
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(effectiveLineColor.copy(alpha = 0.20f), Color.Transparent)
                            )
                        )

                        // Main Curve Line
                        drawPath(
                            path = path,
                            color = effectiveLineColor,
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )

                        // Highlight Abnormal Drop segments in RED
                        abnormalPaths.forEach { abPath ->
                            drawPath(
                                path = abPath,
                                color = abnormalDropColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }

                        // Latest Point Indicator
                        val latestPt = pointOffsets.last().first
                        drawCircle(color = effectiveLineColor, radius = 4.dp.toPx(), center = latestPt)
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = latestPt)
                    }

                    // Draw X-Axis Time Labels
                    val xTimeLabels = if (is24h) {
                        listOf(
                            Pair(0.0f, "12 AM"),
                            Pair(0.25f, "6 AM"),
                            Pair(0.5f, "12 PM"),
                            Pair(0.75f, "6 PM"),
                            Pair(1.0f, "11:59 PM")
                        )
                    } else {
                        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)
                        listOf(
                            Pair(0.0f, timeFmt.format(Date(startWindowMs))),
                            Pair(0.5f, timeFmt.format(Date(startWindowMs + totalDurationMs / 2))),
                            Pair(1.0f, timeFmt.format(Date(endWindowMs)))
                        )
                    }

                    xTimeLabels.forEach { (fraction, label) ->
                        val x = leftPad + fraction * drawWidth
                        val y = height - 8.dp.toPx()
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            x,
                            y,
                            android.graphics.Paint().apply {
                                color = android.graphics.Color.GRAY
                                textSize = 9.sp.toPx()
                                textAlign = when (fraction) {
                                    0.0f -> android.graphics.Paint.Align.LEFT
                                    1.0f -> android.graphics.Paint.Align.RIGHT
                                    else -> android.graphics.Paint.Align.CENTER
                                }
                                isAntiAlias = true
                            }
                        )
                    }

                    // Touched / Probed Point Highlight
                    touchedIndex?.let { idx ->
                        if (idx in pointOffsets.indices) {
                            val (ptOffset, pt) = pointOffsets[idx]
                            // Vertical guide line
                            drawLine(
                                color = Color.White.copy(alpha = 0.6f),
                                start = Offset(ptOffset.x, topPad),
                                end = Offset(ptOffset.x, height - bottomPad),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                            )
                            drawCircle(color = Color.White, radius = 5.5.dp.toPx(), center = ptOffset)
                            drawCircle(color = if (pt.isAbnormalDrop) abnormalDropColor else effectiveLineColor, radius = 3.5.dp.toPx(), center = ptOffset)
                        }
                    }
                }

                // Tooltip Overlay
                touchedIndex?.let { idx ->
                    if (idx in points.indices) {
                        val point = points[idx]
                        val timeFmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
                        val dateFmt = remember { SimpleDateFormat("dd MMM", Locale.US) }
                        val timeStr = timeFmt.format(Date(point.timestamp))
                        val dateStr = dateFmt.format(Date(point.timestamp))

                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                Text(
                                    text = "$dateStr • $timeStr",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val valDisplay = when (metricType) {
                                    NetraMetricType.BATTERY_LEVEL -> "${point.value.roundToInt()}%"
                                    NetraMetricType.VOLTAGE -> "${point.value.roundToInt()} mV"
                                    NetraMetricType.CURRENT -> "${point.value.roundToInt()} mA"
                                    NetraMetricType.POWER -> String.format(Locale.US, "%.2f W", point.value)
                                    NetraMetricType.TEMPERATURE -> String.format(Locale.US, "%.1f °C", point.value)
                                }
                                Text(
                                    text = "${metricType.title}: $valDisplay",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (point.isAbnormalDrop) abnormalDropColor else MaterialTheme.colorScheme.primary
                                )
                                if (point.secondaryText != null) {
                                    Text(
                                        text = point.secondaryText,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

/**
 * Statistics Grid for the Selected Time Window / Calendar Date
 */
@Composable
fun NetraGraphStatisticsCard(
    metricType: NetraMetricType,
    points: List<NetraUnifiedPoint>,
    selectedDateMs: Long,
    is24h: Boolean,
    modifier: Modifier = Modifier
) {
    val validPoints = points.filter { it.value.isFinite() }

    val avgVal = if (validPoints.isNotEmpty()) validPoints.map { it.value }.average().toFloat() else null
    val minVal = validPoints.minOfOrNull { it.value }
    val maxVal = validPoints.maxOfOrNull { it.value }
    val latestVal = validPoints.lastOrNull()?.value

    fun formatVal(v: Float?): String {
        if (v == null) return "Unavailable"
        return when (metricType) {
            NetraMetricType.BATTERY_LEVEL -> "${v.roundToInt()}%"
            NetraMetricType.VOLTAGE -> "${v.roundToInt()} mV"
            NetraMetricType.CURRENT -> "${v.roundToInt()} mA"
            NetraMetricType.POWER -> String.format(Locale.US, "%.2f W", v)
            NetraMetricType.TEMPERATURE -> String.format(Locale.US, "%.1f °C", v)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "WINDOW TELEMETRY STATISTICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatItem(label = "AVERAGE", value = formatVal(avgVal), modifier = Modifier.weight(1f))
                StatItem(label = "MINIMUM", value = formatVal(minVal), modifier = Modifier.weight(1f))
                StatItem(label = "MAXIMUM", value = formatVal(maxVal), modifier = Modifier.weight(1f))
                StatItem(label = "LATEST", value = formatVal(latestVal), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Factual Technical Explanation Card (Metric specific, zero fake claims)
 */
@Composable
fun NetraMetricFactualExplanationCard(
    metricType: NetraMetricType,
    modifier: Modifier = Modifier
) {
    val explanation = when (metricType) {
        NetraMetricType.VOLTAGE ->
            "Nominal Lithium-Ion terminal voltage typically varies between 3.6V (discharged) and 4.2V–4.45V (full saturation). Direct ADC voltage telemetry is sampled from the device Fuel Gauge IC."
        NetraMetricType.CURRENT ->
            "Electrical current reflects instantaneous charge inflow (positive) or system load outflow (negative) measured across internal sense resistors in milliamps (mA)."
        NetraMetricType.POWER ->
            "Instantaneous electrical wattage calculated authoritatively via Joule's law (P = V × I). Reflects real charging power delivery or active hardware power dissipation."
        NetraMetricType.TEMPERATURE ->
            "Internal battery thermistor readings sampled in real time. Standard operating bounds are 15°C to 40°C. Autonomous thermal throttling activates if cell temperature exceeds 45°C."
        NetraMetricType.BATTERY_LEVEL ->
            "State of Charge (SoC) estimated by the device Fuel Gauge hardware through Coulomb counting and open-circuit voltage modeling. Abnormal rapid drop detection flags unexpected discharge spikes."
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = explanation,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }
    }
}

/**
 * Complete Netra Metric Detail Full-Screen / Modal Component
 */
@Composable
fun NetraUnifiedMetricScreen(
    metricType: NetraMetricType,
    state: BatteryState,
    history24h: List<BatteryHistoryEntity>,
    trendLogs: List<BatteryTrendLog>,
    selectedCalendarDate: Long,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onClose: () -> Unit
) {
    var selectedRange by remember { mutableStateOf(NetraTimeRange.TWENTY_FOUR_HOURS) }

    // Assemble unified graph points strictly from authoritative data
    val points = remember(selectedRange, selectedCalendarDate, history24h, trendLogs, state) {
        val list = mutableListOf<NetraUnifiedPoint>()
        val is24h = selectedRange == NetraTimeRange.TWENTY_FOUR_HOURS

        if (is24h) {
            val startMs = TimeManager.getStartOfLocalDay(selectedCalendarDate)
            val endMs = TimeManager.getEndOfLocalDay(selectedCalendarDate)
            val dateHistory = history24h.filter { it.timestamp in startMs..endMs }.sortedBy { it.timestamp }

            var prevLevel: Int? = null
            var prevTimestamp: Long? = null

            dateHistory.forEach { h ->
                val v = when (metricType) {
                    NetraMetricType.BATTERY_LEVEL -> h.batteryLevel.toFloat()
                    NetraMetricType.VOLTAGE -> h.voltageMv.toFloat()
                    NetraMetricType.CURRENT -> h.currentNowMa.toFloat()
                    NetraMetricType.POWER -> (h.voltageMv / 1000f) * (abs(h.currentNowMa) / 1000f) * (if (h.isCharging) 1f else -1f)
                    NetraMetricType.TEMPERATURE -> h.temperature
                }

                // Detect abnormal rapid discharge drop (> 1% drop in < 2 minutes when discharging)
                var isAbnormal = false
                if (metricType == NetraMetricType.BATTERY_LEVEL && !h.isCharging && prevLevel != null && prevTimestamp != null) {
                    val levelDelta = prevLevel!! - h.batteryLevel
                    val timeDeltaMin = (h.timestamp - prevTimestamp!!) / 60000f
                    if (levelDelta >= 2 && timeDeltaMin < 2f) {
                        isAbnormal = true
                    }
                }
                prevLevel = h.batteryLevel
                prevTimestamp = h.timestamp

                val secText = when (metricType) {
                    NetraMetricType.BATTERY_LEVEL -> if (h.isCharging) "Charging (${h.chargingType})" else "Discharging"
                    NetraMetricType.VOLTAGE -> "${h.batteryLevel}% • ${if (h.isCharging) "Charging" else "Discharging"}"
                    NetraMetricType.CURRENT -> "Voltage: ${h.voltageMv} mV • Temp: ${h.temperature}°C"
                    NetraMetricType.POWER -> "Current: ${h.currentNowMa} mA • Voltage: ${h.voltageMv} mV"
                    NetraMetricType.TEMPERATURE -> "Level: ${h.batteryLevel}% • Status: ${h.batteryStatus}"
                }

                list.add(
                    NetraUnifiedPoint(
                        timestamp = h.timestamp,
                        value = v,
                        secondaryText = secText,
                        isCharging = h.isCharging,
                        isAbnormalDrop = isAbnormal,
                        rawTemperature = h.temperature,
                        rawVoltageMv = h.voltageMv,
                        rawCurrentMa = h.currentNowMa,
                        rawLevel = h.batteryLevel
                    )
                )
            }
        } else {
            val now = System.currentTimeMillis()
            val startMs = now - selectedRange.durationMs
            val recentLogs = trendLogs.filter { it.timestamp >= startMs }.sortedBy { it.timestamp }

            recentLogs.forEach { log ->
                val isChg = log.dischargeRate <= 0f || log.currentNow > 0
                val v = when (metricType) {
                    NetraMetricType.BATTERY_LEVEL -> log.batteryLevel.toFloat()
                    NetraMetricType.VOLTAGE -> log.voltage.toFloat()
                    NetraMetricType.CURRENT -> log.currentNow.toFloat()
                    NetraMetricType.POWER -> (log.voltage / 1000f) * (abs(log.currentNow) / 1000f) * (if (isChg) 1f else -1f)
                    NetraMetricType.TEMPERATURE -> log.temperature
                }

                list.add(
                    NetraUnifiedPoint(
                        timestamp = log.timestamp,
                        value = v,
                        secondaryText = "${log.batteryLevel}% • Temp ${log.temperature}°C",
                        isCharging = isChg,
                        isAbnormalDrop = false,
                        rawTemperature = log.temperature,
                        rawVoltageMv = log.voltage,
                        rawCurrentMa = log.currentNow,
                        rawLevel = log.batteryLevel
                    )
                )
            }
        }
        list
    }

    // Determine current / primary live value
    val isToday = TimeManager.isToday(selectedCalendarDate)
    val latestHistoryOnDate = if (selectedRange == NetraTimeRange.TWENTY_FOUR_HOURS) points.lastOrNull() else null

    val primaryValStr = when (metricType) {
        NetraMetricType.BATTERY_LEVEL -> {
            if (isToday || selectedRange != NetraTimeRange.TWENTY_FOUR_HOURS) "${state.percentage}%"
            else latestHistoryOnDate?.let { "${it.rawLevel}%" } ?: "Unavailable"
        }
        NetraMetricType.VOLTAGE -> {
            if (isToday || selectedRange != NetraTimeRange.TWENTY_FOUR_HOURS) {
                if (state.voltage > 0) "${state.voltage} mV" else "Unavailable"
            } else latestHistoryOnDate?.let { "${it.rawVoltageMv} mV" } ?: "Unavailable"
        }
        NetraMetricType.CURRENT -> {
            if (isToday || selectedRange != NetraTimeRange.TWENTY_FOUR_HOURS) {
                "${state.currentNow} mA"
            } else latestHistoryOnDate?.let { "${it.rawCurrentMa} mA" } ?: "Unavailable"
        }
        NetraMetricType.POWER -> {
            if (isToday || selectedRange != NetraTimeRange.TWENTY_FOUR_HOURS) {
                String.format(Locale.US, "%.2f W", state.powerWatt)
            } else latestHistoryOnDate?.let {
                val p = (it.rawVoltageMv / 1000f) * (abs(it.rawCurrentMa) / 1000f)
                String.format(Locale.US, "%.2f W", p)
            } ?: "Unavailable"
        }
        NetraMetricType.TEMPERATURE -> {
            if (isToday || selectedRange != NetraTimeRange.TWENTY_FOUR_HOURS) {
                if (state.temperature > -999f) String.format(Locale.US, "%.1f °C", state.temperature) else "Unavailable"
            } else latestHistoryOnDate?.let { String.format(Locale.US, "%.1f °C", it.rawTemperature) } ?: "Unavailable"
        }
    }

    val secondaryValStr = when (metricType) {
        NetraMetricType.BATTERY_LEVEL -> if (state.isCharging) "Charging (${state.chargingType})" else "Discharging"
        NetraMetricType.VOLTAGE -> if (state.voltage > 0) String.format(Locale.US, "%.3f V • %s", state.voltage / 1000f, if (state.isCharging) "Charging" else "Discharging") else null
        NetraMetricType.CURRENT -> if (state.voltage > 0) "Voltage: ${state.voltage} mV • Power: ${String.format(Locale.US, "%.2f W", state.powerWatt)}" else null
        NetraMetricType.POWER -> "Current: ${state.currentNow} mA • Voltage: ${state.voltage} mV"
        NetraMetricType.TEMPERATURE -> if (state.isHeatProtocolActive) "+${state.solarHeatDeltaTemp}° Solar Heat Offset" else "Cell Sensor Normal"
    }

    val (conditionText, conditionColor) = when (metricType) {
        NetraMetricType.BATTERY_LEVEL -> Pair(if (state.isCharging) "Charging" else "Discharging", if (state.isCharging) Color(0xFF00E676) else MaterialTheme.colorScheme.primary)
        NetraMetricType.VOLTAGE -> when {
            state.voltage > 4350 -> Pair("High Saturation", Color(0xFFFF9100))
            state.voltage in 3600..4350 -> Pair("Nominal Range", Color(0xFF00E676))
            state.voltage > 0 -> Pair("Low Terminal", Color(0xFFFF5252))
            else -> Pair("Unavailable", Color.Gray)
        }
        NetraMetricType.CURRENT -> when {
            state.isCharging -> Pair("Charge Inflow", Color(0xFF00E676))
            abs(state.currentNow) > 1000 -> Pair("Heavy Load Drain", Color(0xFFFF5252))
            else -> Pair("Nominal Drain", MaterialTheme.colorScheme.primary)
        }
        NetraMetricType.POWER -> when {
            state.isCharging -> Pair("Power Inflow", Color(0xFF00E676))
            else -> Pair("Active Power Load", Color(0xFFFFAB00))
        }
        NetraMetricType.TEMPERATURE -> when {
            state.temperature >= 45f -> Pair("Critical Overheat", Color(0xFFFF1744))
            state.temperature >= 40f -> Pair("Warm / Heavy Load", Color(0xFFFF9100))
            state.temperature >= 35f -> Pair("Moderate Operating", MaterialTheme.colorScheme.primary)
            state.temperature > 0f -> Pair("Cool & Optimal", Color(0xFF00E676))
            else -> Pair("Unavailable", Color.Gray)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header
        NetraGraphHeader(
            title = metricType.title,
            subtitle = "Authoritative Live & Historical Hardware Telemetry",
            onBack = onClose
        )

        // 2. Primary Live Value Card
        NetraPrimaryLiveValueCard(
            metricType = metricType,
            primaryValueText = primaryValStr,
            secondaryText = secondaryValStr,
            conditionText = conditionText,
            conditionColor = conditionColor
        )

        // 3. Time Range Controls
        NetraTimeRangeSegmentedControl(
            selectedRange = selectedRange,
            onRangeSelected = { selectedRange = it }
        )

        // 4. Date Selector (When 24h mode is active)
        if (selectedRange == NetraTimeRange.TWENTY_FOUR_HOURS) {
            NetraCalendarDateSelector(
                selectedDateMs = selectedCalendarDate,
                onPreviousDayClick = onPreviousDay,
                onNextDayClick = onNextDay,
                onTodayClick = onToday
            )
        }

        // 5. Unified Graph Card
        NetraUnifiedGraphCanvas(
            points = points,
            metricType = metricType,
            timeRange = selectedRange,
            selectedDateMs = selectedCalendarDate
        )

        // 6. Statistics Card
        NetraGraphStatisticsCard(
            metricType = metricType,
            points = points,
            selectedDateMs = selectedCalendarDate,
            is24h = selectedRange == NetraTimeRange.TWENTY_FOUR_HOURS
        )

        // 7. Factual Explanation
        NetraMetricFactualExplanationCard(metricType = metricType)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Charging Power by Battery Level (Bucketed: 0-10%, 10-20%, ..., 90-100%)
 * Derived strictly from real charging telemetry.
 */
@Composable
fun NetraChargingPowerByLevelCard(
    sessions: List<ChargingSession>,
    trendLogs: List<BatteryTrendLog>,
    modifier: Modifier = Modifier
) {
    data class BucketData(
        val rangeLabel: String,
        val avgPowerW: Float?,
        val peakPowerW: Float?,
        val sampleCount: Int
    )

    val bucketResults = remember(sessions, trendLogs) {
        val buckets = Array(10) { mutableListOf<Float>() }

        // Derive from trend logs during charging
        trendLogs.filter { it.dischargeRate <= 0f && it.voltage > 0 && it.currentNow > 0 }.forEach { log ->
            val bucketIdx = (log.batteryLevel / 10).coerceIn(0, 9)
            val powerW = (log.voltage / 1000f) * (log.currentNow / 1000f)
            if (powerW > 0.1f && powerW < 150f) {
                buckets[bucketIdx].add(powerW)
            }
        }

        val ranges = listOf(
            "0-10%", "10-20%", "20-30%", "30-40%", "40-50%",
            "50-60%", "60-70%", "70-80%", "80-90%", "90-100%"
        )

        ranges.mapIndexed { idx, label ->
            val samples = buckets[idx]
            BucketData(
                rangeLabel = label,
                avgPowerW = if (samples.isNotEmpty()) samples.average().toFloat() else null,
                peakPowerW = samples.maxOrNull(),
                sampleCount = samples.size
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ElectricBolt,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CHARGING POWER BY BATTERY LEVEL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "Hardware Telemetry",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bucketed Bar / Row Visualizer
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bucketResults.forEach { b ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = b.rangeLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(55.dp)
                        )

                        if (b.avgPowerW != null) {
                            val maxChartW = 33f // Reference max scale
                            val fraction = (b.avgPowerW / maxChartW).coerceIn(0.05f, 1f)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFF00E676), Color(0xFF00B0FF))
                                            )
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = String.format(Locale.US, "%.1fW (Peak %.1fW)", b.avgPowerW, b.peakPowerW ?: b.avgPowerW),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(110.dp),
                                textAlign = TextAlign.End
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "Insufficient charging data",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "---",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.width(110.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}
