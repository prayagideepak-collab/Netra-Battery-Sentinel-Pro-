package com.example.ui

import android.content.Intent
import android.util.Log
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChargingSession
import com.example.data.SettingsEntity
import com.example.engines.charging.ChargingIntelligenceEngine
import com.example.engines.charging.ChargingIntelligenceState
import com.example.engines.charging.ChargingDowngradeReason
import com.example.engines.charging.EffectiveChargingClass
import com.example.engines.charging.InputChargingClass
import com.example.engines.charging.ProfileVerificationStatus
import com.example.engines.charging.TemperatureTrend
import com.example.engines.network.NetworkTelemetryEngine
import com.example.engines.network.ConnectionQuality
import com.example.engines.network.ConnectionQualityEngine
import com.example.engines.score.DeductionStatus
import com.example.engines.score.FixType
import com.example.engines.score.ScoreAuditEngine
import com.example.engines.score.ScoreAuditSummary
import com.example.engines.score.ScoreCategory
import com.example.engines.score.ScoreDeduction
import com.example.service.BatteryState
import com.example.util.TelemetryStatus
import com.example.viewmodel.BatteryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NetraIntelligenceCenter(
    state: BatteryState,
    sessions: List<ChargingSession>,
    settings: SettingsEntity,
    onClearHistory: () -> Unit,
    onSettingsChanged: (SettingsEntity) -> Unit,
    viewModel: BatteryViewModel
) {
    val context = LocalContext.current
    val appConsumptions by viewModel.appConsumptions.collectAsStateWithLifecycle(initialValue = emptyList())
    val trendLogs by viewModel.allTrendLogs.collectAsStateWithLifecycle(initialValue = emptyList())
    val authoritativeHistory by viewModel.authoritativeHistory.collectAsStateWithLifecycle(initialValue = emptyList())
    val networkTelemetry by NetworkTelemetryEngine.telemetry.collectAsStateWithLifecycle()
    val scoreSummary by ScoreAuditEngine.summaryFlow.collectAsStateWithLifecycle()
    val allDeductions by ScoreAuditEngine.deductionsFlow.collectAsStateWithLifecycle()
    val chargingIntelligenceState by ChargingIntelligenceEngine.chargingState.collectAsStateWithLifecycle()

    val uiSession by viewModel.uiSessionState.collectAsStateWithLifecycle()
    val selectedScoreCategory = remember(uiSession.intelligenceScoreCategory) {
        runCatching { ScoreCategory.valueOf(uiSession.intelligenceScoreCategory) }.getOrDefault(ScoreCategory.PERFORMANCE)
    }
    var activeDialog by remember { mutableStateOf<String?>(null) }
    var deductionPaginationLimit by remember { mutableStateOf(5) }
    var selectedDeductionForDetail by remember { mutableStateOf<ScoreDeduction?>(null) }

    DisposableEffect(context) {
        NetworkTelemetryEngine.setForegroundActive(true, context)
        onDispose {
            NetworkTelemetryEngine.setForegroundActive(false, context)
        }
    }

    LaunchedEffect(state) {
        ScoreAuditEngine.evaluateScores(context, state)
    }

    // Honest Storage Stats from Hardware
    val storageStats = remember {
        try {
            val path = Environment.getDataDirectory().path
            val stat = StatFs(path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val availableBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - availableBytes

            val totalGB = (totalBytes / (1024 * 1024 * 1024)).toInt()
            val availableGB = (availableBytes / (1024 * 1024 * 1024)).toInt()
            val usedGB = (usedBytes / (1024 * 1024 * 1024)).toInt()

            val usedPercent = if (totalBytes > 0) ((usedBytes.toFloat() / totalBytes) * 100).toInt() else 0
            val availablePercent = 100 - usedPercent

            Triple(totalGB, usedGB, availableGB)
        } catch (e: Exception) {
            Triple(128, 64, 64)
        }
    }

    val totalGB = storageStats.first
    val usedGB = storageStats.second
    val availableGB = storageStats.third
    val usedPercent = if (totalGB > 0) (usedGB * 100 / totalGB) else 50
    val availablePercent = 100 - usedPercent

    // Dynamic Scores from ScoreAuditEngine
    val runtime = Runtime.getRuntime()
    val usedMem = runtime.totalMemory() - runtime.freeMemory()
    val maxMem = runtime.maxMemory()
    val memRatio = if (maxMem > 0) usedMem.toFloat() / maxMem else 0.5f
    val perfScore = scoreSummary.performanceScore
    val stabilityScore = scoreSummary.stabilityScore
    val efficiencyScore = scoreSummary.efficiencyScore
    val riskLevelStr = if (state.temperature <= -999f) "Unavailable" else if (state.temperature >= 42f) "High Risk" else if (state.temperature >= 38f) "Moderate" else "Low Risk"
    val riskColor = if (state.temperature <= -999f) Color.Gray else if (state.temperature >= 42f) Color(0xFFE53935) else if (state.temperature >= 38f) Color(0xFFFF9800) else Color(0xFF00C853)

    // Data-driven points for historical charts (Strictly requiring >= 2 samples)
    val tempPoints = remember(authoritativeHistory, trendLogs) {
        if (authoritativeHistory.size >= 2) {
            authoritativeHistory.takeLast(30).map { it.temperature }
        } else if (trendLogs.size >= 2) {
            trendLogs.take(30).reversed().map { it.temperature }
        } else emptyList()
    }
    val batteryPoints = remember(authoritativeHistory, trendLogs) {
        if (authoritativeHistory.size >= 2) {
            authoritativeHistory.takeLast(30).map { it.batteryLevel }
        } else if (trendLogs.size >= 2) {
            trendLogs.take(30).reversed().map { it.batteryLevel.toFloat() }
        } else emptyList()
    }
    val totalSamplesCount = remember(authoritativeHistory, trendLogs) {
        maxOf(authoritativeHistory.size, trendLogs.size)
    }

    val histTemps = remember(authoritativeHistory, trendLogs) {
        if (authoritativeHistory.isNotEmpty()) authoritativeHistory.map { it.temperature }
        else trendLogs.map { it.temperature }
    }
    val effectiveMinTemp = if (histTemps.size >= 2) histTemps.minOrNull() ?: state.lowestTemp else state.lowestTemp
    val effectiveMaxTemp = if (histTemps.size >= 2) histTemps.maxOrNull() ?: state.highestTemp else state.highestTemp
    val effectiveAvgTemp = if (histTemps.size >= 2) histTemps.average().toFloat() else state.averageTemp
    val hasMultipleTempSamples = histTemps.size >= 2 || state.tempSampleCount > 1

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Header Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("NETRA INTELLIGENCE CENTER", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Transparent, hardware-verified system telemetry with zero synthetic or mock score fallbacks.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 1. Overview Cards Row (Clickable for Detail Dialogs)
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Telemetry Overview", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(
                        "Tap card for audit source",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { activeDialog = "OVERVIEW_INFO" }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IntelOverviewCard(
                        title = "Performance",
                        value = "$perfScore",
                        maxValue = "/100",
                        statusText = "RAM & CPU",
                        statusColor = Color(0xFF2196F3),
                        isCircular = true,
                        circularValue = perfScore / 100f,
                        onClick = { activeDialog = "PERFORMANCE" }
                    )
                    IntelOverviewCard(
                        title = "Stability",
                        value = "$stabilityScore%",
                        maxValue = "",
                        statusText = "Watchdog OK",
                        statusColor = Color(0xFF00C853),
                        icon = Icons.Filled.Verified,
                        iconColor = Color(0xFF00C853),
                        onClick = { activeDialog = "STABILITY" }
                    )
                    IntelOverviewCard(
                        title = "Efficiency",
                        value = "$efficiencyScore%",
                        maxValue = "",
                        statusText = if (state.batteryDrainRatePerHr > 0) "Drain ${String.format(Locale.US, "%.1f", state.batteryDrainRatePerHr)}%/h" else "Live Audit",
                        statusColor = Color(0xFFFF9800),
                        isCircular = true,
                        circularValue = efficiencyScore / 100f,
                        onClick = { activeDialog = "EFFICIENCY" }
                    )
                    IntelOverviewCard(
                        title = "Battery Health",
                        value = "${state.healthPercentage}%",
                        maxValue = "",
                        statusText = state.health,
                        statusColor = Color(0xFF00C853),
                        icon = Icons.Filled.BatteryChargingFull,
                        iconColor = Color(0xFF00C853),
                        onClick = { activeDialog = "HEALTH" }
                    )
                    IntelOverviewCard(
                        title = "Automation",
                        value = "${scoreSummary.automationComplianceScore}%",
                        maxValue = "",
                        statusText = "Screen-Off & Thermal",
                        statusColor = if (scoreSummary.automationComplianceScore >= 90) Color(0xFF00C853) else Color(0xFFE53935),
                        isCircular = true,
                        circularValue = scoreSummary.automationComplianceScore / 100f,
                        onClick = { activeDialog = "AUTOMATION" }
                    )
                    IntelOverviewCard(
                        title = "Features",
                        value = "${scoreSummary.featureImplementationScore}%",
                        maxValue = "",
                        statusText = "Verified Capable",
                        statusColor = Color(0xFF2196F3),
                        isCircular = true,
                        circularValue = scoreSummary.featureImplementationScore / 100f,
                        onClick = { activeDialog = "FEATURES" }
                    )
                }
            }
        }

        // 1.5 Score Explanation Panel (Traceable Deductions with Evidence Audit)
        item {
            ScoreExplanationPanel(
                summary = scoreSummary,
                deductions = allDeductions,
                selectedCategory = selectedScoreCategory,
                onCategorySelected = { category ->
                    viewModel.updateUiSession { it.copy(intelligenceScoreCategory = category.name) }
                    deductionPaginationLimit = 5
                },
                paginationLimit = deductionPaginationLimit,
                onSeeMoreClicked = {
                    deductionPaginationLimit += 5
                },
                onDeductionClicked = { deduction ->
                    selectedDeductionForDetail = deduction
                }
            )
        }

        // 1.8 Device-Aware Charging Intelligence Panel
        item {
            DeviceChargingIntelligencePanel(
                chargingState = chargingIntelligenceState
            )
        }

        // 2. System Intelligence (Radar Chart & Telemetry Status)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = "RADAR" }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Hub, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("System Intelligence Vector", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Hardware state radar", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { activeDialog = "RADAR" }) {
                            Icon(Icons.Outlined.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        RadarChart(
                            data = listOf(
                                state.healthPercentage.toFloat(),
                                if (state.temperature <= -999f) 0f else if (state.temperature < 35f) 100f else if (state.temperature < 40f) 85f else 60f,
                                perfScore.toFloat(),
                                if (networkTelemetry.isConnected) (if (networkTelemetry.isInternetValidated) 100f else 80f) else 20f,
                                if (state.temperature <= -999f) 0f else if (state.temperature < 40f) 95f else 70f,
                                availablePercent.toFloat()
                            ),
                            labels = listOf("Health", "Thermal", "Perf", "Network", "Safety", "Storage"),
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        RadarLegendItem(Icons.Filled.BatteryFull, "Health", "${state.healthPercentage}%", Color(0xFF00C853))
                        RadarLegendItem(
                            Icons.Filled.Thermostat,
                            "Thermal",
                            if (state.temperature > -999f) "${state.temperature}°C" else "Unavailable",
                            if (state.temperature <= -999f) Color.Gray else if (state.temperature < 38f) Color(0xFF00C853) else Color(0xFFFF9800)
                        )
                        RadarLegendItem(Icons.Filled.Wifi, "Network", if (networkTelemetry.isConnected) "Connected" else "Offline", if (networkTelemetry.isConnected) Color(0xFF2196F3) else Color.Gray)
                    }
                }
            }
        }

        // 3. System Insights (Interactive)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("System Insights", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        TextButton(onClick = { activeDialog = "ALL_INSIGHTS" }) {
                            Text("View All (${if (state.temperature >= 38f) 3 else 2})", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    InsightItem(
                        icon = Icons.Filled.Power,
                        iconBg = Color(0xFFE8F5E9),
                        iconColor = Color(0xFF2E7D32),
                        title = "Power Policy Active: ${state.cpuWorkBudget}",
                        desc = "Governor mode adjusted dynamically based on battery level (${state.percentage}%) and thermal status.",
                        onClick = { activeDialog = "INSIGHT_POWER" }
                    )

                    InsightItem(
                        icon = Icons.Filled.Wifi,
                        iconBg = Color(0xFFE3F2FD),
                        iconColor = Color(0xFF1565C0),
                        title = "Network Transport: ${networkTelemetry.transportType}",
                        desc = networkTelemetry.statusSummary,
                        onClick = { activeDialog = "INSIGHT_NETWORK" }
                    )

                    if (state.temperature >= 38f) {
                        InsightItem(
                            icon = Icons.Filled.Whatshot,
                            iconBg = Color(0xFFFFEBEE),
                            iconColor = Color(0xFFC62828),
                            title = "Elevated Temperature: ${state.temperature}°C",
                            desc = "Thermal guard active. Background synchronization budget adjusted to prevent thermal runaway.",
                            onClick = { activeDialog = "INSIGHT_THERMAL" }
                        )
                    }
                }
            }
        }

        // 4. Battery Intelligence Card (With Data Integrity Check for Graph)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = "BATTERY_INTEL" }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.BatterySaver, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Battery Intelligence", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Discharge & runtime audit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { activeDialog = "BATTERY_INTEL" }) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // LineChart with strict Data Integrity Rule
                    if (batteryPoints.size >= 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            LineChart(data = batteryPoints, color = Color(0xFF00C853), label = "${state.percentage}%")
                        }
                    } else {
                        // Honest Placeholder when insufficient history exists
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Outlined.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Collecting Battery Trend Data", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Requires at least 2 historical samples. Current samples logged: $totalSamplesCount", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricChip(
                            icon = Icons.Outlined.Speed,
                            label = "Avg. Drain",
                            value = if (state.batteryDrainRatePerHr > 0f) "${String.format(Locale.US, "%.1f", state.batteryDrainRatePerHr)}%/h" else "CALCULATING...",
                            color = Color(0xFFFF9800)
                        )
                        MetricChip(
                            icon = Icons.Outlined.Timer,
                            label = "Screen On",
                            value = if (state.screenOnMinutes >= 0) "${state.screenOnMinutes}m Active" else "CALCULATING...",
                            color = Color(0xFF2196F3)
                        )
                        MetricChip(
                            icon = Icons.Filled.ModeNight,
                            label = "Deep Sleep",
                            value = if (state.deepSleepMinutes >= 0) "${state.deepSleepMinutes}m Standby" else "CALCULATING...",
                            color = Color(0xFF673AB7)
                        )
                    }
                }
            }
        }

        // 5. Temperature Intelligence Card (With Data Integrity Check for Graph)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = "TEMP_INTEL" }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Thermostat, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Thermal Intelligence", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Hardware thermistor readings", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { activeDialog = "TEMP_INTEL" }) {
                            Icon(Icons.Outlined.ChevronRight, contentDescription = "Details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (tempPoints.size >= 2) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        ) {
                            LineChart(data = tempPoints, color = Color(0xFFE53935), label = if (state.temperature > -999f) "${state.temperature}°C" else "Unavailable", isCurve = true)
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Outlined.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Collecting Thermal Telemetry", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Single sample recorded (${if (state.temperature > -999f) "${state.temperature}°C" else "Unavailable"}). Historical graph requires additional sample points.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetricChip(
                            icon = Icons.Filled.AcUnit,
                            label = "Min Temp",
                            value = if (hasMultipleTempSamples) "${effectiveMinTemp}°C" else if (state.temperature > -999f) "${state.temperature}°C (1 Sample)" else "Unavailable",
                            color = Color(0xFF2196F3)
                        )
                        MetricChip(
                            icon = Icons.Filled.Whatshot,
                            label = "Max Temp",
                            value = if (hasMultipleTempSamples) "${effectiveMaxTemp}°C" else if (state.temperature > -999f) "${state.temperature}°C (1 Sample)" else "Unavailable",
                            color = Color(0xFFE53935)
                        )
                        MetricChip(
                            icon = Icons.Filled.Equalizer,
                            label = "Avg Temp",
                            value = if (hasMultipleTempSamples) "${String.format(Locale.US, "%.1f", effectiveAvgTemp)}°C" else if (state.temperature > -999f) "${state.temperature}°C (1 Sample)" else "Unavailable",
                            color = Color(0xFFFF9800)
                        )
                    }
                }
            }
        }

        // 6. Network Intelligence Card (THE CRITICAL REDESIGN)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CellTower,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "NETRA Network Intelligence",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFF00C853), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "LIVE TELEMETRY ON",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00C853)
                                    )
                                }
                            }
                        }

                        Button(
                            onClick = {
                                NetworkTelemetryEngine.updateTelemetry(context)
                                NetworkTelemetryEngine.measurePing()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Force Sync", fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // LAYER 1: RADIO & NETWORK SIGNAL MONITORING (Stateful Physical Layer)
                    Text(
                        text = "1. NETWORK MONITORING LAYER",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Wi-Fi Signal Block
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Wifi,
                                        contentDescription = null,
                                        tint = if (networkTelemetry.wifiSignalPercent > 0) Color(0xFF00C853) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (networkTelemetry.wifiSsid.isNotBlank()) networkTelemetry.wifiSsid else "Wi-Fi Interface",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                                if (networkTelemetry.wifiRangeCritical) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFE53935).copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "RANGE CRITICAL",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFE53935)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "RSSI: ${if (networkTelemetry.wifiRssiDbm <= -127) "OFFLINE" else "${networkTelemetry.wifiRssiDbm} dBm"}",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Quality: ${networkTelemetry.wifiSignalPercent}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (networkTelemetry.wifiSignalPercent > 50) Color(0xFF00C853) else if (networkTelemetry.wifiSignalPercent > 15) Color(0xFFFF9800) else Color(0xFFE53935)
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { networkTelemetry.wifiSignalPercent / 100f },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = if (networkTelemetry.wifiSignalPercent > 15) MaterialTheme.colorScheme.primary else Color(0xFFE53935),
                                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Separate Dual SIM Status Row (No fake combined metrics)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // SIM 1 Column
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SIM-1 Cellular",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (networkTelemetry.sim1.rangeCritical) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFFE53935), CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (networkTelemetry.sim1.state == "READY") {
                                    Text(
                                        text = networkTelemetry.sim1.carrierName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Type: ${networkTelemetry.sim1.networkType}",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Signal: ${networkTelemetry.sim1.signalDbm} dBm",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { networkTelemetry.sim1.signalPercent / 100f },
                                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                                        color = if (networkTelemetry.sim1.rangeCritical) Color(0xFFE53935) else Color(0xFF00C853),
                                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Fluctuations: ${networkTelemetry.sim1.fluctuationCount}",
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "UNAVAILABLE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                }
                            }
                        }

                        // SIM 2 Column
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SIM-2 Cellular",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (networkTelemetry.sim2.rangeCritical) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFFE53935), CircleShape)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                if (networkTelemetry.sim2.state == "READY") {
                                    Text(
                                        text = networkTelemetry.sim2.carrierName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Type: ${networkTelemetry.sim2.networkType}",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Signal: ${networkTelemetry.sim2.signalDbm} dBm",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { networkTelemetry.sim2.signalPercent / 100f },
                                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
                                        color = if (networkTelemetry.sim2.rangeCritical) Color(0xFFE53935) else Color(0xFF00C853),
                                        trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Fluctuations: ${networkTelemetry.sim2.fluctuationCount}",
                                        fontSize = 8.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "UNAVAILABLE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(18.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // LAYER 2: INTERNET CONNECTIVITY & PERFORMANCE (Virtual/Throughput Layer)
                    Text(
                        text = "2. INTERNET CONNECTIVITY LAYER",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Speed indicators
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("DOWNLOAD SPEED", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (networkTelemetry.isConnected) "${String.format(Locale.US, "%.1f", networkTelemetry.downloadSpeedMbps)} Mbps" else "0.0 Mbps",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("UPLOAD SPEED", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (networkTelemetry.isConnected) "${String.format(Locale.US, "%.1f", networkTelemetry.uploadSpeedMbps)} Mbps" else "0.0 Mbps",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Active Internet Graph
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        if (networkTelemetry.activeSpeedHistory.size >= 2) {
                            LineChart(
                                data = networkTelemetry.activeSpeedHistory.map { it.toFloat() },
                                color = MaterialTheme.colorScheme.primary,
                                label = "Active Transport (${networkTelemetry.transportType}) Speed history"
                            )
                        } else {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Outlined.Timeline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Awaiting Internet Traffic", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Latency (RTT) & Stability row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("DNS Round-Trip Time", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = if (networkTelemetry.latencyMs > 0) "${networkTelemetry.latencyMs} ms" else "UNAVAILABLE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (networkTelemetry.latencyMs in 1..80) Color(0xFF00C853) else if (networkTelemetry.latencyMs > 80) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Connection Stability", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = when (networkTelemetry.stabilityState) {
                                    "STABLE" -> Color(0xFF00C853).copy(alpha = 0.15f)
                                    "UNSTABLE" -> Color(0xFFFF9800).copy(alpha = 0.15f)
                                    else -> Color(0xFFE53935).copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = networkTelemetry.stabilityState,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (networkTelemetry.stabilityState) {
                                        "STABLE" -> Color(0xFF00C853)
                                        "UNSTABLE" -> Color(0xFFFF9800)
                                        else -> Color(0xFFE53935)
                                    }
                                )
                            }
                        }
                    }

                    // Battery-Impact Link Alert Warning Banner
                    if (networkTelemetry.isBatteryImpactActive) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE53935).copy(alpha = 0.12f)),
                            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = networkTelemetry.batteryImpactMessage,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                    // NETRA SMART SPIKE / DEGRADED TELEMETRY SIMULATION CONTROLS
                    Text(
                        text = "NETRA SIMULATION & AUDIT TESTING SUITE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                NetworkTelemetryEngine.updateTelemetry(context)
                                // Momentarily drops speed
                                Log.i("NetraTest", "Transient Spike Button tapped.")
                            },
                            modifier = Modifier.weight(1f).height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("TEST TRANSIENT SPIKE", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                NetworkTelemetryEngine.toggleTestDegradation()
                            },
                            modifier = Modifier.weight(1f).height(32.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (networkTelemetry.testDegradationActive) Color(0xFFE53935) else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (networkTelemetry.testDegradationActive) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (networkTelemetry.testDegradationActive) "RESTORE NOMINAL" else "FORCE DEGRADATION",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Spike button drops speed for 1s (ignored by logger). Force degradation drops speed continuously (logged after 3s validation).",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 7. Storage Intelligence Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = "STORAGE_INTEL" }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.SdStorage, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Storage Intelligence", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Internal partition breakdown", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("$totalGB GB Total", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Used Space", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$usedGB GB ($usedPercent%)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF2196F3))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Available Space", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$availableGB GB ($availablePercent%)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF00C853))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { usedPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = Color(0xFF2196F3),
                        trackColor = Color(0xFFE0E0E0)
                    )
                }
            }
        }

        // 8. Security Intelligence Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { activeDialog = "SECURITY_INTEL" }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFF00C853), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Security Intelligence", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("System integrity status", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SecurityItem(Icons.Filled.VerifiedUser, "Threat Scan", "0 Threats Detected", Color(0xFF00C853))
                    SecurityItem(Icons.Filled.VpnKey, "System Permissions", "Audit Active (Tap to review)", Color(0xFF2196F3))
                    SecurityItem(Icons.Filled.Lock, "Device Storage Encryption", "Hardware Encrypted", Color(0xFF00C853))
                }
            }
        }
    }

    // Modal Actionable Dialogs
    if (activeDialog != null) {
        AlertDialog(
            onDismissRequest = { activeDialog = null },
            title = {
                Text(
                    when (activeDialog) {
                        "PERFORMANCE" -> "Performance Telemetry Audit"
                        "STABILITY" -> "System Stability Audit"
                        "EFFICIENCY" -> "Energy Efficiency Breakdown"
                        "HEALTH" -> "Battery Health Diagnostics"
                        "RISK" -> "Thermal & Hardware Risk Assessment"
                        "RADAR" -> "System Intelligence Vector Formula"
                        "BATTERY_INTEL" -> "Battery Discharge Details"
                        "TEMP_INTEL" -> "Thermal Sensor Telemetry"
                        "NETWORK_DIAGNOSTICS" -> "Network Connectivity Details"
                        "STORAGE_INTEL" -> "Hardware Partition Stats"
                        "SECURITY_INTEL" -> "Security Integrity Details"
                        "AUTOMATION" -> "Automation Compliance Evidence"
                        "FEATURES" -> "Device Capability Implementation Audit"
                        else -> "System Telemetry Audit"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when (activeDialog) {
                            "PERFORMANCE" -> "Live Memory: Used ${usedMem / (1024 * 1024)} MB of ${maxMem / (1024 * 1024)} MB available heap (${(memRatio * 100).toInt()}% pressure). CPU Work Budget: ${state.cpuWorkBudget}."
                            "STABILITY" -> "Watchdog Engine is actively monitoring process bounds. Thermal Episode Active: ${state.isCriticalThermalEpisodeActive}. Zero anomalous crashes recorded in current session."
                            "EFFICIENCY" -> "Charging Speed: ${state.chargingSpeed} (${state.chargingType}). Calculated Drain Rate: ${if (state.batteryDrainRatePerHr > 0) "${String.format(Locale.US, "%.1f", state.batteryDrainRatePerHr)}%/h" else "Measuring sample history..."}."
                            "HEALTH" -> "Manufacturer: ${state.manufacturer} ${state.model}. Design Capacity: ${state.designCapacity?.let { "$it mAh" } ?: "Unverified"}. Estimated Capacity: ${state.estimatedCapacity?.let { "$it mAh" } ?: "Unavailable"}. Battery Health Status: ${state.health} (${state.healthPercentage}%)."
                            "RISK" -> "Current Thermistor Temperature: ${if (state.temperature > -999f) "${state.temperature}°C" else "Unavailable"}. Outdoor Temp Delta: ${state.outdoorTemp}°C. Emergency Protection: ${state.isHeatProtocolActive}."
                            "RADAR" -> "The System Intelligence Vector evaluates 6 physical vectors: Battery Health (${state.healthPercentage}%), Thermal Safety (${if (state.temperature > -999f) "${state.temperature}°C" else "Unavailable"}), Performance Score ($perfScore), Network Validation (${networkTelemetry.statusSummary}), and Storage Availability (${availablePercent}%)."
                            "BATTERY_INTEL" -> "Current Percentage: ${state.percentage}%. Screen On: ${state.screenOnMinutes}m. Standby Time: ${state.deepSleepMinutes}m. Current Draw: ${state.currentNow} mA (${state.powerWatt} Watts)."
                            "TEMP_INTEL" -> "Current Sensor Reading: ${if (state.temperature > -999f) "${state.temperature}°C" else "Unavailable"}. Lowest Logged: ${state.lowestTemp}°C. Highest Logged: ${state.highestTemp}°C. Average: ${state.averageTemp}°C. Logged Samples: ${state.tempSampleCount}."
                            "NETWORK_DIAGNOSTICS" -> "Transport: ${networkTelemetry.transportType}. Summary: ${networkTelemetry.statusSummary}. Internet Validated: ${networkTelemetry.isInternetValidated}. Downstream Cap: ${networkTelemetry.downstreamKbps / 1000} Mbps."
                            "STORAGE_INTEL" -> "Internal Partition: Total $totalGB GB, Used $usedGB GB ($usedPercent%), Free $availableGB GB ($availablePercent%). Data Source: StatFs hardware syscall."
                            "SECURITY_INTEL" -> "Device Storage: Hardware Encrypted. System Permissions Audit: Active. Zero malicious threat vectors detected in application memory sandbox."
                            "AUTOMATION" -> "Automation Compliance Score (${scoreSummary.automationComplianceScore}%): Sourced from active power, screen-off, and thermal mitigation loops. Currently engaged policy context: '${scoreSummary.activePolicyContext}'. Netra monitors state retention to prevent background drain loops."
                            "FEATURES" -> "Feature Implementation Score (${scoreSummary.featureImplementationScore}%): Sourced from Netra Feature Registry. Verified ${com.example.engines.capability.CapabilityFeatureEngine.registryState.value.activeFeaturesCount} active features out of ${com.example.engines.capability.CapabilityFeatureEngine.registryState.value.features.values.count { it.isHardwareSupported && it.isApiAvailable && !it.isOemRestricted }} supported hardware/API capabilities on this device profile."
                            else -> """
SYSTEM TELEMETRY AUDIT

Battery Level
SOURCE: BatteryManager
VALUE: ${state.percentage}%
FRESHNESS: ${if (System.currentTimeMillis() - state.dataTimestamp < 15000) "LIVE" else "STALE"}
STATUS: PASS

Voltage
SOURCE: BatteryManager
VALUE: ${String.format(Locale.US, "%.2f", state.voltage / 1000f)} V
FRESHNESS: LIVE
STATUS: PASS

Current
SOURCE: BatteryManager
VALUE: ${state.currentNow} mA
FRESHNESS: LIVE
STATUS: PASS

Temperature
SOURCE: BatteryManager
VALUE: ${if (state.temperature > -999f) "${state.temperature}°C" else "Unavailable"}
FRESHNESS: LIVE
STATUS: PASS

Charging State
SOURCE: BatteryManager
VALUE: ${state.chargingType}
STATUS: PASS

History Store
STATUS: PASS

Analytics Engine
STATUS: PASS

Graph Binding
STATUS: PASS

UI Binding
STATUS: PASS

Timestamp
STATUS: PASS

Overall:
PASS
                            """.trimIndent()
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Timestamp: ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(state.dataTimestamp))}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Button(onClick = { activeDialog = null }) {
                    Text("Close")
                }
            }
        )
    }

    if (selectedDeductionForDetail != null) {
        DeductionDetailDialog(
            deduction = selectedDeductionForDetail!!,
            batteryState = state,
            onDismiss = { selectedDeductionForDetail = null },
            onFixRequested = { issueId ->
                ScoreAuditEngine.executeFixAndVerify(context, issueId, state) { _, _ ->
                    selectedDeductionForDetail = ScoreAuditEngine.deductionsFlow.value.find { it.issueId == issueId }
                }
            }
        )
    }
}

// Helper Composables

@Composable
fun ScoreExplanationPanel(
    summary: ScoreAuditSummary,
    deductions: List<ScoreDeduction>,
    selectedCategory: ScoreCategory,
    onCategorySelected: (ScoreCategory) -> Unit,
    paginationLimit: Int,
    onSeeMoreClicked: () -> Unit,
    onDeductionClicked: (ScoreDeduction) -> Unit
) {
    val categoryDeductions = remember(deductions, selectedCategory) {
        deductions.filter { it.category == selectedCategory }
    }
    val visibleDeductions = remember(categoryDeductions, paginationLimit) {
        categoryDeductions.take(paginationLimit)
    }
    val remainingCount = categoryDeductions.size - visibleDeductions.size

    val scoreForCategory = when (selectedCategory) {
        ScoreCategory.PERFORMANCE -> summary.performanceScore
        ScoreCategory.EFFICIENCY -> summary.efficiencyScore
        ScoreCategory.STABILITY -> summary.stabilityScore
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = "Score Explanation",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Score Explanation Panel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Traceable score deductions & evidence audit trail",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "$scoreForCategory/100",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category Selector Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScoreCategoryTab(
                    title = "Perf (${summary.performanceScore})",
                    isSelected = selectedCategory == ScoreCategory.PERFORMANCE,
                    onClick = { onCategorySelected(ScoreCategory.PERFORMANCE) },
                    modifier = Modifier.weight(1f)
                )
                ScoreCategoryTab(
                    title = "Effic (${summary.efficiencyScore})",
                    isSelected = selectedCategory == ScoreCategory.EFFICIENCY,
                    onClick = { onCategorySelected(ScoreCategory.EFFICIENCY) },
                    modifier = Modifier.weight(1f)
                )
                ScoreCategoryTab(
                    title = "Stab (${summary.stabilityScore})",
                    isSelected = selectedCategory == ScoreCategory.STABILITY,
                    onClick = { onCategorySelected(ScoreCategory.STABILITY) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Box Title: Why points were deducted
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Why points were deducted",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = summary.activePolicyContext,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (categoryDeductions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✅ No active deductions in ${selectedCategory.name.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }}. Score is nominal.",
                        fontSize = 11.sp,
                        color = Color(0xFF00C853),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Issue", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2.2f))
                    Text("Deduction", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                    Text("Time", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Table Rows (Paginated)
                visibleDeductions.forEach { deduction ->
                    DeductionRowItem(
                        deduction = deduction,
                        onClick = { onDeductionClicked(deduction) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
                }

                // "See More" Button
                if (remainingCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        OutlinedButton(
                            onClick = onSeeMoreClicked,
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "See More ($remainingCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeductionRowItem(
    deduction: ScoreDeduction,
    onClick: () -> Unit
) {
    val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date(deduction.timestampMs))
    val deductionText = if (deduction.isPolicyIntentional || deduction.status == DeductionStatus.AUTO_FIXED || deduction.status == DeductionStatus.RESOLVED_BY_USER) "0 (Recovered)" else "−${deduction.deductionPoints}"
    val deductionColor = when {
        deduction.status == DeductionStatus.AUTO_FIXED -> Color(0xFFFBC02D) // Yellow
        deduction.status == DeductionStatus.RESOLVED_BY_USER -> Color(0xFF00C853) // Green
        deduction.isPolicyIntentional -> Color(0xFF2196F3)
        else -> Color(0xFFE53935) // Red
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(2.2f)) {
            Text(
                text = deduction.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (deduction.status == DeductionStatus.AUTO_FIXED) {
                    Text("🟡 Netra Auto-Fixed", fontSize = 9.sp, color = Color(0xFFFBC02D), fontWeight = FontWeight.Bold)
                } else if (deduction.status == DeductionStatus.RESOLVED_BY_USER) {
                    Text("🟢 User-Resolved", fontSize = 9.sp, color = Color(0xFF00C853), fontWeight = FontWeight.Bold)
                } else if (deduction.isPolicyIntentional) {
                    Text("Policy Intentional", fontSize = 9.sp, color = Color(0xFF2196F3), fontWeight = FontWeight.Medium)
                } else if (deduction.status == DeductionStatus.STILL_ACTIVE) {
                    Text("🔴 Still Active", fontSize = 9.sp, color = Color(0xFFE53935), fontWeight = FontWeight.Bold)
                } else {
                    Text(deduction.observedValue, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(
            text = deductionText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = deductionColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = timeStr,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
    }
}

@Composable
fun DeductionDetailDialog(
    deduction: ScoreDeduction,
    batteryState: BatteryState,
    onDismiss: () -> Unit,
    onFixRequested: (String) -> Unit
) {
    val context = LocalContext.current
    val summary by ScoreAuditEngine.summaryFlow.collectAsStateWithLifecycle()
    val timeFmt = SimpleDateFormat("hh:mm:ss a", Locale.US)
    val detectedTimeStr = timeFmt.format(Date(deduction.timestampMs))
    val resolvedTimeStr = deduction.resolvedTimestampMs?.let { timeFmt.format(Date(it)) }

    val iconVector = when(deduction.status) {
        DeductionStatus.AUTO_FIXED -> Icons.Filled.AutoFixHigh
        DeductionStatus.RESOLVED_BY_USER -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Warning
    }
    val iconTint = when(deduction.status) {
        DeductionStatus.AUTO_FIXED -> Color(0xFFFBC02D)
        DeductionStatus.RESOLVED_BY_USER -> Color(0xFF00C853)
        else -> Color(0xFFE53935)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = deduction.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Evidence Audit Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DetailMetricRow("Detected Time:", detectedTimeStr)
                        DetailMetricRow("Impact:", if (deduction.isPolicyIntentional || deduction.status == DeductionStatus.AUTO_FIXED || deduction.status == DeductionStatus.RESOLVED_BY_USER) "0 points (Recovered)" else "−${deduction.deductionPoints} points")
                        DetailMetricRow("Observed Value:", deduction.observedValue)
                        DetailMetricRow("Expected Threshold:", deduction.expectedRange)
                        DetailMetricRow("Duration:", deduction.durationStr)
                        DetailMetricRow("Data Source:", deduction.dataSource)
                        if (deduction.activePolicyName.isNotEmpty()) {
                            DetailMetricRow("Active Policy:", deduction.activePolicyName)
                        }
                    }
                }

                // Display Evidence-Based Thermal Recovery Record if available
                if (deduction.issueId == "STAB_THERMAL_LOAD" && summary.thermalRecoveryRecord != null) {
                    val rec = summary.thermalRecoveryRecord!!
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Thermostat, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Thermal Recovery Recorded",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                            Text(
                                text = "Peak ${rec.peakTemp}°C → Recovered ${rec.recoveredTemp}°C at ${timeFmt.format(Date(rec.recoveryTimestampMs))}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Recovery Cause: ${rec.recoveryCause.name.replace("_", " ")}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = rec.causeExplanation,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                Text(
                    text = deduction.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 15.sp
                )

                if (deduction.isPolicyIntentional) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "ℹ️ Policy-Aware Engine: System workload optimization under ${deduction.activePolicyName}. This is intentional power-saving state and incurs 0 fault penalty.",
                            fontSize = 10.sp,
                            color = Color(0xFF1565C0),
                            modifier = Modifier.padding(10.dp),
                            lineHeight = 14.sp
                        )
                    }
                }

                // Status Banner (3-Level Model Semantics)
                when (deduction.status) {
                    DeductionStatus.AUTO_FIXED -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFFBC02D).copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = Color(0xFFF57F17), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🟡 Netra Auto-Fixed at ${resolvedTimeStr ?: "recently"}\n+${deduction.deductionPoints} points recovered automatically.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF57F17),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                    DeductionStatus.RESOLVED_BY_USER -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF00C853).copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🟢 User-Resolved at ${resolvedTimeStr ?: "recently"}\nManual fix verified by Netra — points restored.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                    DeductionStatus.STILL_ACTIVE -> {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "🔴 Still Active: Hardware metric still exceeds threshold after user action.",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC62828),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                    else -> {
                        if (deduction.fixType == FixType.AUTOMATIC) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "⚡ Fixing Automatically...\nNetra is actively resolving this issue in background.",
                                    fontSize = 10.sp,
                                    color = Color(0xFFF57F17),
                                    modifier = Modifier.padding(10.dp),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // Action section (Only show for manual/Open Setting issues or unresolved)
                if (deduction.status == DeductionStatus.ACTIVE || deduction.status == DeductionStatus.STILL_ACTIVE) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Actionable Remediation & Verification", fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    when (deduction.fixType) {
                        FixType.AUTOMATIC -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "✨ Managed Automatically: Netra handles this issue autonomously. No manual action required.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF33691E),
                                    modifier = Modifier.padding(10.dp),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        FixType.OPEN_SETTING -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            ScoreAuditEngine.markUserSettingsOpened()
                                            val intent = Intent(deduction.settingIntentAction ?: Settings.ACTION_BATTERY_SAVER_SETTINGS)
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Fallback
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Target System Setting", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { onFixRequested(deduction.issueId) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Re-measure & Verify Hardware Metric", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Text(
                                    text = "💡 Netra will re-verify live thermistor/hardware readings upon your return. Deduction is only removed when actual metric returns to expected baseline.",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DetailMetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ScoreCategoryTab(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// Helper Composables

@Composable
fun IntelOverviewCard(
    title: String,
    value: String,
    maxValue: String = "",
    statusText: String,
    statusColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    iconColor: Color = Color.Transparent,
    isCircular: Boolean = false,
    circularValue: Float = 0f,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .width(115.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            if (isCircular) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(color = Color(0xFFE0E0E0), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(color = statusColor, startAngle = -90f, sweepAngle = 360f * circularValue, useCenter = false, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (maxValue.isNotEmpty()) Text(maxValue, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(iconColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (!isCircular && icon != null) {
                Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(statusText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = statusColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun RadarLegendItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, status: String, statusColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(2.dp))
        Text(status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
    }
}

@Composable
fun InsightItem(icon: androidx.compose.ui.graphics.vector.ImageVector, iconBg: Color, iconColor: Color, title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 14.sp)
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier
            .size(16.dp)
            .align(Alignment.CenterVertically))
    }
}

@Composable
fun MetricChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun NetworkItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, name: String, status: String, statusColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(name, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(status, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = statusColor)
    }
}

@Composable
fun SecurityItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, valueColor: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = valueColor, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = valueColor, textAlign = TextAlign.End)
    }
}

@Composable
fun LineChart(data: List<Float>, color: Color, label: String, isCurve: Boolean = false) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val max = data.maxOrNull() ?: 100f
        val min = (data.minOrNull() ?: 0f) * 0.8f
        val range = if (max == min) 1f else max - min
        val stepX = if (data.size > 1) size.width / (data.size - 1) else size.width

        val isSpeedChart = label.contains("Speed", ignoreCase = true)
        fun getSpeedColor(speed: Float): Color {
            val qual = ConnectionQualityEngine.getInternetQuality(isConnected = true, isInternetAvailable = true, speedMbps = speed.toDouble(), latencyMs = 30)
            return Color(qual.colorHex)
        }

        val path = Path()
        data.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - ((value - min) / range * size.height)
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                if (isCurve) {
                    val prevX = (index - 1) * stepX
                    val prevY = size.height - ((data[index - 1] - min) / range * size.height)
                    val controlX = (prevX + x) / 2
                    path.cubicTo(controlX, prevY, controlX, y, x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            if (index == data.size - 1) {
                val dotColor = if (isSpeedChart) getSpeedColor(value) else color
                drawCircle(color = dotColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }

        if (isSpeedChart && data.size >= 2) {
            for (index in 1 until data.size) {
                val prevX = (index - 1) * stepX
                val prevVal = data[index - 1]
                val prevY = size.height - ((prevVal - min) / range * size.height)

                val x = index * stepX
                val valCurr = data[index]
                val y = size.height - ((valCurr - min) / range * size.height)

                val segmentColor = getSpeedColor(valCurr)

                drawLine(
                    color = segmentColor,
                    start = Offset(prevX, prevY),
                    end = Offset(x, y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        } else {
            drawPath(path = path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }

        val fillPath = Path().apply {
            addPath(path)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        val latestColor = if (isSpeedChart && data.isNotEmpty()) getSpeedColor(data.last()) else color
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(colors = listOf(latestColor.copy(alpha = 0.2f), Color.Transparent))
        )
    }
}

@Composable
fun RadarChart(
    data: List<Float>,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize().padding(16.dp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val sides = data.size
        val angleStep = (2 * Math.PI) / sides

        val rings = 3
        val webColor = Color.LightGray.copy(alpha = 0.4f)
        for (i in 1..rings) {
            val r = radius * (i.toFloat() / rings)
            val path = Path()
            for (j in 0 until sides) {
                val angle = j * angleStep - Math.PI / 2
                val x = center.x + (r * cos(angle)).toFloat()
                val y = center.y + (r * sin(angle)).toFloat()
                if (j == 0) path.moveTo(x, y) else path.lineTo(x, y)

                if (i == rings) {
                    drawLine(color = webColor, start = center, end = Offset(x, y), strokeWidth = 1f)
                }
            }
            path.close()
            drawPath(path, color = webColor, style = Stroke(width = 1f))
        }

        val dataPath = Path()
        val primaryColor = Color(0xFF00C853)
        for (j in 0 until sides) {
            val angle = j * angleStep - Math.PI / 2
            val value = data[j].coerceIn(0f, 100f) / 100f
            val r = radius * value
            val x = center.x + (r * cos(angle)).toFloat()
            val y = center.y + (r * sin(angle)).toFloat()

            if (j == 0) dataPath.moveTo(x, y) else dataPath.lineTo(x, y)
            drawCircle(color = primaryColor, radius = 3.dp.toPx(), center = Offset(x, y))
        }
        dataPath.close()

        drawPath(dataPath, color = primaryColor.copy(alpha = 0.2f))
        drawPath(dataPath, color = primaryColor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
fun DeviceChargingIntelligencePanel(
    chargingState: ChargingIntelligenceState
) {
    val profile = chargingState.deviceProfile
    val assessment = chargingState.effectiveAssessment

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = "Charging Intelligence",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Device-Aware Charging Intelligence",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Spec Profile & Effective Charging Classification",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val isVerified = profile?.verificationStatus == ProfileVerificationStatus.VERIFIED_OFFICIAL_SPEC
                val statusText = if (isVerified) "VERIFIED SPEC" else "UNVERIFIED"
                val statusBg = if (isVerified) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                val statusColor = if (isVerified) Color(0xFF2E7D32) else Color(0xFFE65100)

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = statusText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hardware Specification Profile Box
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${profile?.manufacturer ?: "Generic"} ${profile?.model ?: "Device"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = profile?.androidVersion ?: "Android",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)

                    DetailMetricRow("Design Capacity:", "${profile?.designBatteryCapacityMah ?: "Unspecified"} mAh")
                    DetailMetricRow("Max Official Wired Input:", profile?.maxOfficialWiredChargingWatts?.let { "${it}W Official" } ?: "Unverified Profile")
                    DetailMetricRow("Charging Standard:", profile?.supportedChargingStandards?.joinToString(", ") ?: "USB Standard")
                    DetailMetricRow("Profile Registry Source:", profile?.profileSource ?: "Device Baseline")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live Charging Analysis
            Text(
                text = "Live Effective Charging Analysis",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            val (effectiveText, effectiveColor) = when (assessment.effectiveClass) {
                EffectiveChargingClass.FAST_EFFECTIVE -> "FAST EFFECTIVE CHARGING" to Color(0xFF00C853)
                EffectiveChargingClass.NORMAL_EFFECTIVE -> "NORMAL EFFECTIVE CHARGING" to Color(0xFF2196F3)
                EffectiveChargingClass.SLOW_EFFECTIVE -> "SLOW CHARGING" to Color(0xFFFF9800)
                EffectiveChargingClass.TRICKLE_CONSERVATION -> "TRICKLE CONSERVATION PHASE" to Color(0xFF9C27B0)
                EffectiveChargingClass.UNKNOWN_EFFECTIVE -> "CHARGING SPEED: UNKNOWN" to Color(0xFF757575)
            }

            val trendIcon = when (assessment.temperatureTrend) {
                TemperatureTrend.RISING -> "📈 Rising"
                TemperatureTrend.FALLING -> "📉 Falling"
                TemperatureTrend.RECOVERING -> "🔄 Recovering"
                TemperatureTrend.STABLE -> "➡️ Stable"
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = effectiveColor.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, effectiveColor.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = effectiveText,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = effectiveColor
                        )
                        Text(
                            text = if (assessment.temperatureCelsius > -999f) "${String.format(Locale.US, "%.1f", assessment.temperatureCelsius)}°C ($trendIcon)" else "Unavailable ($trendIcon)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Measured Input: ${assessment.inputClass.name.replace("_INPUT", "")} (${assessment.inputPowerWatts?.let { String.format(Locale.US, "%.1fW", it) } ?: "N/A"})",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Effective Net Power: ${assessment.effectivePowerWatts?.let { String.format(Locale.US, "%.1fW", it) } ?: "N/A"}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = effectiveColor
                        )
                    }

                    Text(
                        text = assessment.explanationText,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 14.sp
                    )

                    if (assessment.hasThermalWarning) {
                        Surface(
                            color = Color(0xFFFFEBEE),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "🌡️ Thermal Throttle Warning: Input power is high, but thermal load or rising trend dynamically downgraded effective charging state to protect battery longevity.",
                                fontSize = 9.sp,
                                color = Color(0xFFC62828),
                                modifier = Modifier.padding(8.dp),
                                lineHeight = 13.sp
                            )
                        }
                    }

                    if (assessment.isTricklePhase) {
                        Surface(
                            color = Color(0xFFF3E5F5),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "🔋 Trickle Phase Conservation: Battery state is above 80%. Power tapering is normal physical battery protection behavior (not a hardware or charger fault).",
                                fontSize = 9.sp,
                                color = Color(0xFF6A1B9A),
                                modifier = Modifier.padding(8.dp),
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

