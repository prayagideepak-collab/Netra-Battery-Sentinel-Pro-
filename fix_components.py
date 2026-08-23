import re

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    content = f.read()

# I will replace the Charge Rate TelemetryCard with a new Extracted Component

charge_rate_card = """TelemetryCard(
                        modifier = Modifier.weight(1f),
                        title = "Charge Rate",
                        value = if (state.isCharging) {
                            if (state.speed > 0) "+${String.format(java.util.Locale.US, "%.1f", state.speed)}%/h" else "${state.currentNow}mA"
                        } else {
                            if (state.speed > 0) "-${String.format(java.util.Locale.US, "%.1f", state.speed)}%/h" else "${state.currentNow}mA"
                        },
                        subtitle = if (state.speed > 20) "Fast" else "Normal",
                        valueColor = if (state.isCharging) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
                    )"""

new_charge_rate_card = """ChargeRateCard(batteryStateState, Modifier.weight(1f))"""

content = content.replace(charge_rate_card, new_charge_rate_card)

# And Discharge Rate Card

discharge_rate_card = """Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (state.isCharging) "Charge Rate" else "Discharge Rate",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${state.currentNow} mA",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${String.format(java.util.Locale.US, "%.1f", state.powerWatt)} Watts",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                        InteractiveRealtimeGraph(
                            points = listOf(0.8f, 0.7f, 0.7f, 0.6f, 0.5f),
                            labelY = "",
                            lineColor = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }"""
new_discharge_rate_card = """DischargeRateAnalyticsCard(batteryStateState, Modifier.weight(1f))"""

content = content.replace(discharge_rate_card, new_discharge_rate_card)

# And Time Until Full Charge

time_until_card = """Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (state.isCharging) "Time Until Full Charge" else "Time Until Empty",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = com.example.util.TimeManager.formatDurationMs(state.remainingTimeMs),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                        InteractiveRealtimeGraph(
                            points = listOf(0.2f, 0.5f, 0.8f, 0.9f, 1.0f),
                            labelY = "",
                            lineColor = if (state.isCharging) Color(0xFF00E676) else Color(0xFFFF1744)
                        )
                    }
                }
            }"""
new_time_until_card = """TimeUntilAnalyticsCard(batteryStateState, Modifier.weight(1f))"""
content = content.replace(time_until_card, new_time_until_card)

components = """
@Composable
fun ChargeRateCard(batteryStateState: androidx.compose.runtime.State<com.example.service.BatteryState>, modifier: Modifier) {
    val speed by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { batteryStateState.value.speed } }
    val currentNow by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { batteryStateState.value.currentNow } }
    val isCharging by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { batteryStateState.value.isCharging } }
    
    TelemetryCard(
        modifier = modifier,
        title = "Charge Rate",
        value = if (isCharging) {
            if (speed > 0) "+${String.format(java.util.Locale.US, "%.1f", speed)}%/h" else "${currentNow}mA"
        } else {
            if (speed > 0) "-${String.format(java.util.Locale.US, "%.1f", speed)}%/h" else "${currentNow}mA"
        },
        subtitle = if (speed > 20) "Fast" else "Normal",
        valueColor = if (isCharging) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun DischargeRateAnalyticsCard(batteryStateState: androidx.compose.runtime.State<com.example.service.BatteryState>, modifier: Modifier) {
    val currentNow by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { batteryStateState.value.currentNow } }
    val powerWatt by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { batteryStateState.value.powerWatt } }
    val isCharging by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { batteryStateState.value.isCharging } }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isCharging) "Charge Rate" else "Discharge Rate",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${currentNow} mA",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "${String.format(java.util.Locale.US, "%.1f", powerWatt)} Watts",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                InteractiveRealtimeGraph(
                    points = listOf(0.8f, 0.7f, 0.7f, 0.6f, 0.5f),
                    labelY = "",
                    lineColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TimeUntilAnalyticsCard(batteryStateState: androidx.compose.runtime.State<com.example.service.BatteryState>, modifier: Modifier) {
    val remainingTimeMs by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { batteryStateState.value.remainingTimeMs } }
    val isCharging by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { batteryStateState.value.isCharging } }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isCharging) "Time Until Full Charge" else "Time Until Empty",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = com.example.util.TimeManager.formatDurationMs(remainingTimeMs),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                InteractiveRealtimeGraph(
                    points = listOf(0.2f, 0.5f, 0.8f, 0.9f, 1.0f),
                    labelY = "",
                    lineColor = if (isCharging) Color(0xFF00E676) else Color(0xFFFF1744)
                )
            }
        }
    }
}
"""

content = content.replace("@OptIn(ExperimentalMaterial3Api::class)", components + "\n@OptIn(ExperimentalMaterial3Api::class)")

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.write(content)
