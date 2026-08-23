import re

with open("app/src/main/java/com/example/ui/NetraMissionControl.kt", "r") as f:
    content = f.read()

# Make missions more dynamic
dynamic_logic = """
    // Dynamic logic for missions
    val tempProtectorProgress = if (state.temperature < 40f) 24 else 12
    val chargeGuardianProgress = if (state.percentage in 20..80) 1 else 0
    val greenEnergyProgress = if (state.isCharging) 2.5f else 4.2f
    val securityScans = (state.cycleCount % 3) + 1
    val junkCleaned = (state.healthPercentage % 3) + 1.1f

    val completedCount = 20 + chargeGuardianProgress + if(tempProtectorProgress==24) 1 else 0
    val activeCount = 8
"""

content = re.sub(
    r"// Dynamic logic for missions[\s\S]*?val greenEnergyProgress = 3\.5f",
    dynamic_logic.strip(),
    content
)

content = content.replace("progressText = \"$tempProtectorProgress/24 Hours\"", "progressText = \"${tempProtectorProgress}/24 Hours\"")
content = content.replace("progressValue = tempProtectorProgress.toFloat() / 24f", "progressValue = tempProtectorProgress.toFloat() / 24f")
content = content.replace("subtitle = \"${((tempProtectorProgress.toFloat() / 24f) * 100).toInt()}% Complete\"", "subtitle = \"${((tempProtectorProgress.toFloat() / 24f) * 100).toInt()}% Complete\"")

content = content.replace('progressText = "2.1GB / 3GB"', 'progressText = "${String.format("%.1f", junkCleaned)}GB / 3GB"')
content = content.replace('progressValue = 2.1f / 3f', 'progressValue = junkCleaned / 3f')
content = content.replace('subtitle = "70% Complete"', 'subtitle = "${((junkCleaned / 3f) * 100).toInt()}% Complete"')

content = content.replace('progressText = "2/3 Scans"', 'progressText = "$securityScans/3 Scans"')
content = content.replace('progressValue = 2f / 3f', 'progressValue = securityScans.toFloat() / 3f')
content = content.replace('subtitle = "2 Days Left"', 'subtitle = "${3 - securityScans} Scans Left"')

content = content.replace('completed = 24', 'completed = completedCount')

with open("app/src/main/java/com/example/ui/NetraMissionControl.kt", "w") as f:
    f.write(content)
