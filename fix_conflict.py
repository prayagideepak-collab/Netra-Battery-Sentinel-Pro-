import re

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    content = f.read()

# completely remove all occurrences of StateReader
pattern = r'@Composable\nfun <T> StateReader\(\n    state: androidx\.compose\.runtime\.State<com\.example\.service\.BatteryState>,\n    selector: \(com\.example\.service\.BatteryState\) -> T,\n    content: @Composable \(T\) -> Unit\n\) \{\n    val value by androidx\.compose\.runtime\.remember \{ androidx\.compose\.runtime\.derivedStateOf \{ selector\(state\.value\) \} \}\n    content\(value\)\n\}'

content = re.sub(pattern, '', content)
# Check if there are other forms
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


with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.write(content)

