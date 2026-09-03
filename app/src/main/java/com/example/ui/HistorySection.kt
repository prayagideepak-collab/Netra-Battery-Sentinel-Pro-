package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChargingSession
import com.example.util.TimeManager
import com.example.viewmodel.BatteryViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Netra Battery Sentinel Pro — Full History & Analytics Section
 * Production-ready historical graphing and session intelligence.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistorySection(viewModel: BatteryViewModel) {
    var selectedMetricType by remember { mutableStateOf(NetraMetricType.BATTERY_LEVEL) }
    var selectedRange by remember { mutableStateOf(NetraTimeRange.TWENTY_FOUR_HOURS) }

    val batteryState by viewModel.batteryState.collectAsStateWithLifecycle()
    val history24h by viewModel.batteryHistory24h.collectAsStateWithLifecycle()
    val trendLogs by viewModel.allTrendLogs.collectAsStateWithLifecycle(emptyList())
    val selectedCalendarDate by viewModel.selectedCalendarDate.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle(emptyList())

    // Convert to unified graph points
    val points = remember(selectedMetricType, selectedRange, selectedCalendarDate, history24h, trendLogs, batteryState) {
        val list = mutableListOf<NetraUnifiedPoint>()
        val is24h = selectedRange == NetraTimeRange.TWENTY_FOUR_HOURS

        if (is24h) {
            val startMs = TimeManager.getStartOfLocalDay(selectedCalendarDate)
            val endMs = TimeManager.getEndOfLocalDay(selectedCalendarDate)
            val dateHistory = history24h.filter { it.timestamp in startMs..endMs }.sortedBy { it.timestamp }

            var prevLevel: Int? = null
            var prevTimestamp: Long? = null

            dateHistory.forEach { h ->
                val v = when (selectedMetricType) {
                    NetraMetricType.BATTERY_LEVEL -> h.batteryLevel.toFloat()
                    NetraMetricType.VOLTAGE -> h.voltageMv.toFloat()
                    NetraMetricType.CURRENT -> h.currentNowMa.toFloat()
                    NetraMetricType.POWER -> (h.voltageMv / 1000f) * (abs(h.currentNowMa) / 1000f) * (if (h.isCharging) 1f else -1f)
                    NetraMetricType.TEMPERATURE -> h.temperature
                }

                var isAbnormal = false
                if (selectedMetricType == NetraMetricType.BATTERY_LEVEL && !h.isCharging && prevLevel != null && prevTimestamp != null) {
                    val levelDelta = prevLevel!! - h.batteryLevel
                    val timeDeltaMin = (h.timestamp - prevTimestamp!!) / 60000f
                    if (levelDelta >= 2 && timeDeltaMin < 2f) {
                        isAbnormal = true
                    }
                }
                prevLevel = h.batteryLevel
                prevTimestamp = h.timestamp

                val secText = when (selectedMetricType) {
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
                val v = when (selectedMetricType) {
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

    val isToday = TimeManager.isToday(selectedCalendarDate)
    val latestHistoryOnDate = if (selectedRange == NetraTimeRange.TWENTY_FOUR_HOURS) points.lastOrNull() else null

    val primaryValStr = when (selectedMetricType) {
        NetraMetricType.BATTERY_LEVEL -> if (isToday || selectedRange != NetraTimeRange.TWENTY_FOUR_HOURS) "${batteryState.percentage}%" else latestHistoryOnDate?.let { "${it.rawLevel}%" } ?: "Unavailable"
        NetraMetricType.VOLTAGE -> if (isToday || selectedRange != NetraTimeRange.TWENTY_FOUR_HOURS) (if (batteryState.voltage > 0) "${batteryState.voltage} mV" else "Unavailable") else latestHistoryOnDate?.let { "${it.rawVoltageMv} mV" } ?: "Unavailable"
        NetraMetricType.CURRENT -> if (isToday || selectedRange != NetraTimeRange.TWENTY_FOUR_HOURS) "${batteryState.currentNow} mA" else latestHistoryOnDate?.let { "${it.rawCurrentMa} mA" } ?: "Unavailable"
        NetraMetricType.POWER -> if (isToday || selectedRange != NetraTimeRange.TWENTY_FOUR_HOURS) String.format(Locale.US, "%.2f W", batteryState.powerWatt) else latestHistoryOnDate?.let { String.format(Locale.US, "%.2f W", (it.rawVoltageMv / 1000f) * (abs(it.rawCurrentMa) / 1000f)) } ?: "Unavailable"
        NetraMetricType.TEMPERATURE -> if (isToday || selectedRange != NetraTimeRange.TWENTY_FOUR_HOURS) (if (batteryState.temperature > -999f) String.format(Locale.US, "%.1f °C", batteryState.temperature) else "Unavailable") else latestHistoryOnDate?.let { String.format(Locale.US, "%.1f °C", it.rawTemperature) } ?: "Unavailable"
    }

    val secondaryValStr = when (selectedMetricType) {
        NetraMetricType.BATTERY_LEVEL -> if (batteryState.isCharging) "Charging (${batteryState.chargingType})" else "Discharging"
        NetraMetricType.VOLTAGE -> if (batteryState.voltage > 0) String.format(Locale.US, "%.3f V • %s", batteryState.voltage / 1000f, if (batteryState.isCharging) "Charging" else "Discharging") else null
        NetraMetricType.CURRENT -> if (batteryState.voltage > 0) "Voltage: ${batteryState.voltage} mV • Power: ${String.format(Locale.US, "%.2f W", batteryState.powerWatt)}" else null
        NetraMetricType.POWER -> "Current: ${batteryState.currentNow} mA • Voltage: ${batteryState.voltage} mV"
        NetraMetricType.TEMPERATURE -> if (batteryState.isHeatProtocolActive) "+${batteryState.solarHeatDeltaTemp}° Solar Heat Offset" else "Cell Sensor Normal"
    }

    val (conditionText, conditionColor) = when (selectedMetricType) {
        NetraMetricType.BATTERY_LEVEL -> Pair(if (batteryState.isCharging) "Charging" else "Discharging", if (batteryState.isCharging) Color(0xFF00E676) else MaterialTheme.colorScheme.primary)
        NetraMetricType.VOLTAGE -> when {
            batteryState.voltage > 4350 -> Pair("High Saturation", Color(0xFFFF9100))
            batteryState.voltage in 3600..4350 -> Pair("Nominal Range", Color(0xFF00E676))
            batteryState.voltage > 0 -> Pair("Low Terminal", Color(0xFFFF5252))
            else -> Pair("Unavailable", Color.Gray)
        }
        NetraMetricType.CURRENT -> when {
            batteryState.isCharging -> Pair("Charge Inflow", Color(0xFF00E676))
            abs(batteryState.currentNow) > 1000 -> Pair("Heavy Load Drain", Color(0xFFFF5252))
            else -> Pair("Nominal Drain", MaterialTheme.colorScheme.primary)
        }
        NetraMetricType.POWER -> when {
            batteryState.isCharging -> Pair("Power Inflow", Color(0xFF00E676))
            else -> Pair("Active Power Load", Color(0xFFFFAB00))
        }
        NetraMetricType.TEMPERATURE -> when {
            batteryState.temperature >= 45f -> Pair("Critical Overheat", Color(0xFFFF1744))
            batteryState.temperature >= 40f -> Pair("Warm / Heavy Load", Color(0xFFFF9100))
            batteryState.temperature >= 35f -> Pair("Moderate Operating", MaterialTheme.colorScheme.primary)
            batteryState.temperature > 0f -> Pair("Cool & Optimal", Color(0xFF00E676))
            else -> Pair("Unavailable", Color.Gray)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_section_container")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "HISTORICAL TELEMETRY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Hardware Telemetry Intelligence",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "24/7 SQL Archive",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Metric Selector Scrollable Row / Tabs
        item {
            ScrollableTabRow(
                selectedTabIndex = selectedMetricType.ordinal,
                edgePadding = 0.dp,
                containerColor = Color.Transparent,
                divider = {},
                indicator = {}
            ) {
                NetraMetricType.values().forEach { metric ->
                    val isSelected = metric == selectedMetricType
                    Surface(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedMetricType = metric }
                            .testTag("tab_metric_${metric.name}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = metric.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = metric.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Primary Live Value Card
        item {
            NetraPrimaryLiveValueCard(
                metricType = selectedMetricType,
                primaryValueText = primaryValStr,
                secondaryText = secondaryValStr,
                conditionText = conditionText,
                conditionColor = conditionColor
            )
        }

        // Time Range Controls
        item {
            NetraTimeRangeSegmentedControl(
                selectedRange = selectedRange,
                onRangeSelected = { selectedRange = it }
            )
        }

        // Calendar Date Selector (When 24h is active)
        if (selectedRange == NetraTimeRange.TWENTY_FOUR_HOURS) {
            item {
                NetraCalendarDateSelector(
                    selectedDateMs = selectedCalendarDate,
                    onPreviousDayClick = { viewModel.selectPreviousDay() },
                    onNextDayClick = { viewModel.selectNextDay() },
                    onTodayClick = { viewModel.selectToday() }
                )
            }
        }

        // Unified High-Precision Interactive Graph
        item {
            NetraUnifiedGraphCanvas(
                points = points,
                metricType = selectedMetricType,
                timeRange = selectedRange,
                selectedDateMs = selectedCalendarDate
            )
        }

        // Window / Day Telemetry Statistics
        item {
            NetraGraphStatisticsCard(
                metricType = selectedMetricType,
                points = points,
                selectedDateMs = selectedCalendarDate,
                is24h = selectedRange == NetraTimeRange.TWENTY_FOUR_HOURS
            )
        }

        // Technical Factual Explanation
        item {
            NetraMetricFactualExplanationCard(metricType = selectedMetricType)
        }

        // Charging Power by Battery Level (Bucketed analysis)
        item {
            NetraChargingPowerByLevelCard(
                sessions = sessions,
                trendLogs = trendLogs
            )
        }

        // Historical Sessions List
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recorded Power Sessions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${sessions.size} sessions",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (sessions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No charging or discharge sessions recorded yet.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(sessions.take(20)) { session ->
                SessionHistoryItem(session = session)
            }
        }
    }
}

@Composable
fun SessionHistoryItem(session: ChargingSession) {
    val isDischarge = session.isDischarge
    val primaryColor = if (isDischarge) Color(0xFFFF9100) else Color(0xFF00E676)
    val timeFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = primaryColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isDischarge) Icons.Outlined.BatteryAlert else Icons.Outlined.Bolt,
                                contentDescription = null,
                                tint = primaryColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isDischarge) "Discharge Session" else "Charging (${session.chargingType})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = timeFmt.format(Date(session.startTime)),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    val endPct = session.endPercentage ?: session.startPercentage
                    val delta = endPct - session.startPercentage
                    val deltaSign = if (delta > 0) "+$delta%" else "$delta%"
                    Text(
                        text = "${session.startPercentage}% → $endPct% ($deltaSign)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    Text(
                        text = if (session.endTime != null) TimeManager.formatDurationMs(session.endTime - session.startTime) else "In Progress",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (session.avgPower > 0f || session.maxTemperature > 0f) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (session.avgPower > 0f) {
                        Text(
                            text = "Avg Power: ${String.format(Locale.US, "%.1f W", session.avgPower)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (session.maxTemperature > 0f) {
                        Text(
                            text = "Max Temp: ${String.format(Locale.US, "%.1f °C", session.maxTemperature)}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (session.screenOnTimeMinutes > 0) {
                        Text(
                            text = "Screen: ${session.screenOnTimeMinutes}m",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
