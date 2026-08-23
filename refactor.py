import re
import os

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    content = f.read()

# 1. Add StateReader
state_reader = """
@Composable
fun <T> StateReader(
    state: State<BatteryState>,
    selector: (BatteryState) -> T,
    content: @Composable (T) -> Unit
) {
    val value by remember { derivedStateOf { selector(state.value) } }
    content(value)
}
"""
if "fun <T> StateReader" not in content:
    content = content.replace("@OptIn(ExperimentalMaterial3Api::class)", state_reader + "\n@OptIn(ExperimentalMaterial3Api::class)")

# 2. Change MainDashboard
content = content.replace(
    "val batteryState by viewModel.sanitizedBatteryState.collectAsStateWithLifecycle()",
    "val batteryStateState = viewModel.sanitizedBatteryState.collectAsStateWithLifecycle()"
)
# Fix the launched effect
content = content.replace("batteryState.percentage == -1", "batteryStateState.value.percentage == -1")
content = content.replace("batteryState.percentage !=", "batteryStateState.value.percentage !=")

# Fix the arguments passed to screens
content = content.replace("state = batteryState", "batteryStateState = batteryStateState")

# 3. Modify MonitorScreen signature
content = content.replace(
    "fun MonitorScreen(\n    state: BatteryState,",
    "fun MonitorScreen(\n    batteryStateState: State<BatteryState>,"
)

# Replace all the state reads in MonitorScreen with StateReader blocks
# This is tricky using regex, I will write specific replacements for MonitorScreen.

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.write(content)

