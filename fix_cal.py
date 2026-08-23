import re

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    content = f.read()

content = content.replace("batteryState = batteryState,", "batteryState = batteryStateState.value,")

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.write(content)
