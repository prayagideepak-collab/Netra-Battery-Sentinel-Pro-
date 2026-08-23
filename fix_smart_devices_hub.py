with open("app/src/main/java/com/example/ui/SmartDevicesHub.kt", "r") as f:
    content = f.read()

content = content.replace("import androidx.compose.ui.text.style.TextAlign\n", "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextOverflow\n")

with open("app/src/main/java/com/example/ui/SmartDevicesHub.kt", "w") as f:
    f.write(content)
