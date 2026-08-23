import re

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    content = f.read()

# Replace all fully qualified androidx names in the new TimeSelectDropdown
time_select_part = content[content.rfind('@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun TimeSelectDropdown'):]

new_time_select_part = time_select_part.replace('androidx.compose.material3.', '')
new_time_select_part = new_time_select_part.replace('androidx.compose.runtime.', '')

content = content[:content.rfind('@OptIn(ExperimentalMaterial3Api::class)\n@Composable\nfun TimeSelectDropdown')] + new_time_select_part

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.write(content)
