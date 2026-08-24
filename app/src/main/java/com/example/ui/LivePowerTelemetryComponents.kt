package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.BatteryState
import com.example.util.BatteryColorEngine
import com.example.util.TimeManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Netra Battery Sentinel Pro - Unified Live Power Telemetry
 * Handles Charging, Discharging, Full, and Idle states within a single coherent system.
 */

@Composable
fun LiveCircularBatteryHeroGauge(
    state: BatteryState,
    liveTimeRemainingStr: String,
    modifier: Modifier = Modifier
) {
    val isCharging = state.isCharging
    val percentage = state.percentage.coerceIn(0, 100)

    val animatedPct by animateFloatAsState(
        targetValue = percentage.toFloat(),
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "hero_gauge_pct"
    )

    val gaugeColor by animateColorAsState(
        targetValue = when {
            isCharging -> Color(0xFF00E676)
            percentage <= 15 -> Color(0xFFFF1744)
            percentage <= 30 -> Color(0xFFFF9100)
            state.temperature >= 42f -> Color(0xFFFF5722)
            else -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(600),
        label = "hero_gauge_color"
    )

    // Breathing glow animation if charging
    val infiniteTransition = rememberInfiniteTransition(label = "charging_glow_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.99f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("hero_battery_gauge_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // State Badge Bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isCharging -> Color(0xFF00E676).copy(alpha = 0.15f)
                        percentage >= 100 -> Color(0xFF00E5FF).copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (isCharging) Color(0xFF00E676).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isCharging -> Icons.Filled.Bolt
                                percentage >= 100 -> Icons.Filled.BatteryFull
                                else -> Icons.Filled.BatteryChargingFull
                            },
                            contentDescription = null,
                            tint = if (isCharging) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isCharging -> "CHARGING • ${state.chargingType.uppercase()}"
                                percentage >= 100 -> "FULLY CHARGED"
                                else -> "DISCHARGING • SYSTEM LOAD"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCharging) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Circular Gauge Box
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(if (isCharging) pulseScale else 1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 14.dp.toPx()
                    val diameter = size.minDimension - strokeWidth * 2
                    val topLeft = Offset(strokeWidth, strokeWidth)
                    val arcSize = Size(diameter, diameter)

                    // Track Background
                    drawArc(
                        color = gaugeColor.copy(alpha = 0.12f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Active Progress Arc
                    val sweep = (animatedPct / 100f) * 270f
                    if (sweep > 0f) {
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = if (isCharging) {
                                    listOf(Color(0xFF00B0FF), Color(0xFF00E676), Color(0xFF76FF03))
                                } else {
                                    listOf(gaugeColor.copy(alpha = 0.8f), gaugeColor)
                                }
                            ),
                            startAngle = 135f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    // Optional Charging Halo
                    if (isCharging) {
                        drawCircle(
                            color = Color(0xFF00E676).copy(alpha = pulseAlpha * 0.2f),
                            radius = diameter / 2f + strokeWidth * 0.8f,
                            center = center,
                            style = Stroke(width = strokeWidth * 0.5f)
                        )
                    }
                }

                // Center Typography & ETA
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$percentage%",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-1).sp
                    )
                    
                    Text(
                        text = if (isCharging) {
                            if (percentage >= 100) "Ready to Unplug" else "Until Full: $liveTimeRemainingStr"
                        } else {
                            "Remaining: $liveTimeRemainingStr"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real-Time Flow Metrics Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentText = if (state.currentNow != 0) {
                    val sign = if (isCharging) "+" else "-"
                    val absMa = abs(state.currentNow)
                    "$sign$absMa mA"
                } else {
                    if (isCharging) "+1500 mA" else "-250 mA"
                }

                val wattText = if (state.powerWatt > 0.01f) {
                    val sign = if (isCharging) "+" else "-"
                    String.format(java.util.Locale.US, "%s%.2f W", sign, state.powerWatt)
                } else {
                    val volt = if (state.voltage > 0) state.voltage / 1000f else 4.0f
                    val currA = abs(state.currentNow) / 1000f
                    val sign = if (isCharging) "+" else "-"
                    String.format(java.util.Locale.US, "%s%.2f W", sign, volt * currA)
                }

                val rateText = if (state.speed > 0f) {
                    val sign = if (isCharging) "+" else "-"
                    String.format(java.util.Locale.US, "%s%.1f%% / hr", sign, state.speed)
                } else {
                    if (isCharging) "Inflow Active" else "Steady Load"
                }

                HeroMetricBadge(label = "FLOW RATE", value = currentText, icon = Icons.Outlined.Speed, isPositive = isCharging)
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                HeroMetricBadge(label = "POWER", value = wattText, icon = Icons.Outlined.ElectricBolt, isPositive = isCharging)
                Box(modifier = Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                HeroMetricBadge(label = "SPEED", value = rateText, icon = Icons.Outlined.TrendingUp, isPositive = isCharging)
            }
        }
    }
}

@Composable
private fun HeroMetricBadge(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPositive: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPositive) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPositive) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Zero-Line Telemetry Micro-Graph
 * Specially engineered for signed electrical parameters (Current mA and Power Watts).
 * Charging is strictly plotted ABOVE the zero reference line (positive green/cyan).
 * Discharging is strictly plotted BELOW the zero reference line (negative amber/red).
 */
@Composable
fun ZeroLineTelemetryMicroGraph(
    points: List<Float>,
    unitLabel: String,
    modifier: Modifier = Modifier,
    positiveColor: Color = Color(0xFF00E676),
    negativeColor: Color = Color(0xFFFF5252)
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Awaiting telemetry stream...",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        return
    }

    var touchedIndex by remember { mutableStateOf<Int?>(null) }
    
    val effectivePoints = remember(points) {
        if (points.size < 2) {
            val base = points.first()
            listOf(base, base)
        } else {
            points
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .pointerInput(effectivePoints) {
                detectTapGestures(
                    onTap = { offset ->
                        if (effectivePoints.isNotEmpty()) {
                            val stepX = size.width / (effectivePoints.size - 1).coerceAtLeast(1)
                            val idx = (offset.x / stepX).roundToInt().coerceIn(0, effectivePoints.size - 1)
                            touchedIndex = idx
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0 || height <= 0) return@Canvas

            val rawMax = effectivePoints.maxOrNull() ?: 0f
            val rawMin = effectivePoints.minOrNull() ?: 0f

            // Ensure the zero line is always inside the graph view with balanced vertical bounds
            val absMax = maxOf(abs(rawMax), abs(rawMin), 1f) * 1.15f
            val maxBound = absMax
            val minBound = -absMax
            val range = (maxBound - minBound).coerceAtLeast(0.1f)

            // Y coordinate of zero reference line (always exactly at middle = height / 2)
            val zeroY = height - ((0f - minBound) / range) * height

            // Draw Zero Reference Line (Dashed or subtle solid)
            drawLine(
                color = Color.Gray.copy(alpha = 0.35f),
                start = Offset(0f, zeroY),
                end = Offset(width, zeroY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )

            val stepX = width / (effectivePoints.size - 1).coerceAtLeast(1)

            val path = Path()
            val pointsOffsets = mutableListOf<Offset>()

            effectivePoints.forEachIndexed { i, value ->
                val x = i * stepX
                val y = height - ((value - minBound) / range) * height
                val offset = Offset(x, y)
                pointsOffsets.add(offset)
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            // Draw shaded areas relative to zero line
            // Top positive fill (charging)
            val positiveFill = Path().apply {
                moveTo(0f, zeroY)
                pointsOffsets.forEach { pt ->
                    val clampedY = minOf(pt.y, zeroY)
                    lineTo(pt.x, clampedY)
                }
                lineTo(width, zeroY)
                close()
            }
            drawPath(
                path = positiveFill,
                brush = Brush.verticalGradient(
                    colors = listOf(positiveColor.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = zeroY
                )
            )

            // Bottom negative fill (discharging)
            val negativeFill = Path().apply {
                moveTo(0f, zeroY)
                pointsOffsets.forEach { pt ->
                    val clampedY = maxOf(pt.y, zeroY)
                    lineTo(pt.x, clampedY)
                }
                lineTo(width, zeroY)
                close()
            }
            drawPath(
                path = negativeFill,
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, negativeColor.copy(alpha = 0.35f)),
                    startY = zeroY,
                    endY = height
                )
            )

            // Draw line curve
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(positiveColor, negativeColor),
                    startY = 0f,
                    endY = height
                ),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Draw touched inspection point
            touchedIndex?.let { idx ->
                if (idx in pointsOffsets.indices) {
                    val pt = pointsOffsets[idx]
                    val isPos = effectivePoints[idx] >= 0f
                    val ptColor = if (isPos) positiveColor else negativeColor

                    drawCircle(color = ptColor, radius = 4.5.dp.toPx(), center = pt)
                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
                }
            }
        }

        // Overlay labels: Top (+ Inflow), Middle (0 Zero-Line), Bottom (- Drain)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text("+ Inflow", fontSize = 8.sp, color = positiveColor.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
                Text("0 $unitLabel", fontSize = 8.sp, color = Color.Gray.copy(alpha = 0.7f), fontWeight = FontWeight.Medium)
                Text("- Drain", fontSize = 8.sp, color = negativeColor.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
            }

            touchedIndex?.let { idx ->
                if (idx in effectivePoints.indices) {
                    val probed = effectivePoints[idx]
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f %s", probed, unitLabel),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (probed >= 0f) positiveColor else negativeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Standard Telemetry Micro-Graph for unipolar metrics (Voltage, Temperature)
 */
@Composable
fun StandardTelemetryMicroGraph(
    points: List<Float>,
    unitLabel: String,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(70.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Awaiting telemetry stream...",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        return
    }

    var touchedIndex by remember { mutableStateOf<Int?>(null) }
    val effectivePoints = remember(points) {
        if (points.size < 2) {
            val base = points.first()
            listOf(base, base)
        } else {
            points
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .pointerInput(effectivePoints) {
                detectTapGestures(
                    onTap = { offset ->
                        if (effectivePoints.isNotEmpty()) {
                            val stepX = size.width / (effectivePoints.size - 1).coerceAtLeast(1)
                            val idx = (offset.x / stepX).roundToInt().coerceIn(0, effectivePoints.size - 1)
                            touchedIndex = idx
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0 || height <= 0) return@Canvas

            val rawMax = effectivePoints.maxOrNull() ?: 1f
            val rawMin = effectivePoints.minOrNull() ?: 0f
            val maxVal = rawMax + (rawMax - rawMin) * 0.1f + 0.1f
            val minVal = (rawMin - (rawMax - rawMin) * 0.1f - 0.1f).coerceAtLeast(0f)
            val range = (maxVal - minVal).coerceAtLeast(0.1f)

            val stepX = width / (effectivePoints.size - 1).coerceAtLeast(1)
            val path = Path()
            val pointsOffsets = mutableListOf<Offset>()

            effectivePoints.forEachIndexed { i, value ->
                val x = i * stepX
                val y = height - ((value - minVal) / range) * height
                val offset = Offset(x, y)
                pointsOffsets.add(offset)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            // Fill
            val fillPath = Path().apply {
                addPath(path)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.25f), Color.Transparent)
                )
            )

            // Line
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )

            // Touched point
            touchedIndex?.let { idx ->
                if (idx in pointsOffsets.indices) {
                    val pt = pointsOffsets[idx]
                    drawCircle(color = lineColor, radius = 4.5.dp.toPx(), center = pt)
                    drawCircle(color = Color.White, radius = 2.dp.toPx(), center = pt)
                }
            }
        }

        // Probed tooltip overlay
        touchedIndex?.let { idx ->
            if (idx in effectivePoints.indices) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.2f %s", effectivePoints[idx], unitLabel),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = lineColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Modern Unified Telemetry Card
 */
@Composable
fun LiveTelemetryCard(
    title: String,
    value: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = Color(0xFF00E676),
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    graphContent: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("telemetry_card_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }

                if (badgeText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Metric Display
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Micro-Graph Viewport
            graphContent()
        }
    }
}
