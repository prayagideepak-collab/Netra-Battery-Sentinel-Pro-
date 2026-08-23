import re

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    content = f.read()

# I messed up the arguments in MainDashboard. Let's fix them.
# The replacement `state = batteryState` -> `batteryStateState = batteryStateState`
# happened on these lines:
# 343: batteryStateState = batteryStateState,
# 374: batteryStateState = batteryStateState,
# 378: batteryStateState = batteryStateState,
# 424: batteryStateState = batteryStateState)
# 425: batteryStateState = batteryStateState, viewModel = viewModel)
# 426: batteryStateState = batteryStateState)

content = content.replace("batteryStateState = batteryStateState", "state = batteryStateState.value")
# Now fix MonitorScreen back to passing batteryStateState
content = content.replace("MonitorScreen(\n                        state = batteryStateState.value,", "MonitorScreen(\n                        batteryStateState = batteryStateState,")

# Also the settings screen in MainDashboard uses batteryState.percentage
content = content.replace("batteryState.percentage", "batteryStateState.value.percentage")


with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.write(content)
