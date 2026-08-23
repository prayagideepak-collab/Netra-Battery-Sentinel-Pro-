import re

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    content = f.read()

# Remove the duplicate StateReader at line 4496 if it exists
match = re.search(r'@Composable\nfun <T> StateReader', content[1000:])
if match:
    # Actually just remove all StateReader declarations and add one cleanly
    pass

content = re.sub(r'@Composable\nfun <T> StateReader.*?\n}', '', content, flags=re.DOTALL)

state_reader = """
@Composable
fun <T> StateReader(
    state: androidx.compose.runtime.State<com.example.service.BatteryState>,
    selector: (com.example.service.BatteryState) -> T,
    content: @Composable (T) -> Unit
) {
    val value by androidx.compose.runtime.remember { androidx.compose.runtime.derivedStateOf { selector(state.value) } }
    content(value)
}
"""

content = content.replace("@OptIn(ExperimentalMaterial3Api::class)", state_reader + "\n@OptIn(ExperimentalMaterial3Api::class)")

# Also, there's a problem: MonitorScreen doesn't compile because `state` is unresolved.
# I will define `val state = batteryStateState.value` at the top of MonitorScreen,
# but I'll use StateReader for the frequently changing ones: temperature, voltage, powerWatt, currentNow, speed.

content = content.replace(
    "fun MonitorScreen(\n    batteryStateState: State<BatteryState>,",
    "fun MonitorScreen(\n    batteryStateState: State<BatteryState>,"
)

monitor_top = """
    var isAdvancedMode by remember { mutableStateOf(false) }
"""
new_monitor_top = """
    var isAdvancedMode by remember { mutableStateOf(false) }
    val state = batteryStateState.value
"""
content = content.replace(monitor_top, new_monitor_top)

# Wrap Temperature
temp_card = """TelemetryCard(
                        modifier = Modifier.weight(1f),
                        title = "Temperature",
                        value = "${state.temperature}°C",
                        subtitle = when {
                            state.temperature < 35f -> "Cool"
                            state.temperature < 40f -> "Normal"
                            else -> "Hot"
                        },
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )"""
new_temp_card = """StateReader(batteryStateState, { it.temperature }) { temp ->
                        TelemetryCard(
                            modifier = Modifier.weight(1f),
                            title = "Temperature",
                            value = "${temp}°C",
                            subtitle = when {
                                temp < 35f -> "Cool"
                                temp < 40f -> "Normal"
                                else -> "Hot"
                            },
                            valueColor = MaterialTheme.colorScheme.onSurface
                        )
                    }"""
content = content.replace(temp_card, new_temp_card)

# Wrap Voltage
volt_card = """TelemetryCard(
                        modifier = Modifier.weight(1f),
                        title = "Voltage",
                        value = "${String.format(java.util.Locale.US, "%.3f", state.voltage / 1000f)} V",
                        subtitle = if (state.voltage in 3000..4500) "Good" else "Warning",
                        valueColor = MaterialTheme.colorScheme.onSurface
                    )"""
new_volt_card = """StateReader(batteryStateState, { it.voltage }) { volt ->
                        TelemetryCard(
                            modifier = Modifier.weight(1f),
                            title = "Voltage",
                            value = "${String.format(java.util.Locale.US, "%.3f", volt / 1000f)} V",
                            subtitle = if (volt in 3000..4500) "Good" else "Warning",
                            valueColor = MaterialTheme.colorScheme.onSurface
                        )
                    }"""
content = content.replace(volt_card, new_volt_card)

# Fix batteryState.percentage in SettingsScreen
content = content.replace("batteryStateState.value.percentage", "batteryState.percentage")

# Fix line 475 Unresolved reference 'batteryState' in SettingsScreen
# Oh, in MainDashboard, settings screen uses `batteryState.percentage`. 
# MainDashboard's `batteryStateState` should be used instead!
content = content.replace("batteryState.percentage == -1", "batteryStateState.value.percentage == -1")
content = content.replace("batteryState.percentage !=", "batteryStateState.value.percentage !=")

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.write(content)
