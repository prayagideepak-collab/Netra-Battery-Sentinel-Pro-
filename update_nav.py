import re

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    content = f.read()

# Update NotificationSettingsScreen to NetraNotificationCenterScreen and add 71
old_7 = """                    7 -> NotificationSettingsScreen(
                        settings = settings,
                        onSettingsChanged = { viewModel.updateSettings(it) },
                        onOpenCalibrationAssistant = { activeTab = 8 }
                    )"""

new_7 = """                    7 -> NetraNotificationCenterScreen(
                        viewModel = viewModel,
                        settings = settings,
                        onOpenSettings = { activeTab = 71 }
                    )
                    71 -> NotificationSettingsScreen(
                        settings = settings,
                        onSettingsChanged = { viewModel.updateSettings(it) },
                        onOpenCalibrationAssistant = { activeTab = 8 }
                    )"""

content = content.replace(old_7, new_7)

# Update Bottom Nav highlighting
old_nav_sel = "selected = activeTab == 7,"
new_nav_sel = "selected = activeTab == 7 || activeTab == 71,"
content = content.replace(old_nav_sel, new_nav_sel)

old_nav_icon = "icon = { Icon(if (activeTab == 7) Icons.Filled.Notifications else Icons.Outlined.Notifications, \"Notifications\") },"
new_nav_icon = "icon = { Icon(if (activeTab == 7 || activeTab == 71) Icons.Filled.Notifications else Icons.Outlined.Notifications, \"Notifications\") },"
content = content.replace(old_nav_icon, new_nav_icon)

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.write(content)
