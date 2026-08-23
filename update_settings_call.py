import re

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    content = f.read()

old_call = """                    4 -> SettingsScreen(
                        settings = settings,
                        onSettingsChanged = { viewModel.updateSettings(it) },
                        viewModel = viewModel
                    )"""

new_call = """                    4 -> NetraSettingsCenterScreen(
                        settings = settings,
                        onSettingsChanged = { viewModel.updateSettings(it) },
                        viewModel = viewModel
                    )"""

content = content.replace(old_call, new_call)

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.write(content)
