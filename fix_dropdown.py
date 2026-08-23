import re

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    content = f.read()

# Replace the TimeSelectDropdown at the end of the file with the correct one

old_dropdown = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSelectDropdown(
    label: String,
    options: List<String>,
    selectedTime: String,
    onTimeSelected: (String) -> Unit
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = selectedTime,
            onValueChange = {},
            readOnly = true,
            label = { androidx.compose.material3.Text(label, fontSize = 10.sp) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
        )
        androidx.compose.material3.ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { androidx.compose.material3.Text(option, fontSize = 12.sp) },
                    onClick = {
                        onTimeSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}"""

new_dropdown = """@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSelectDropdown(
    label: String,
    selectedTime: String,
    onTimeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val options = androidx.compose.runtime.remember {
        val list = mutableListOf<String>()
        val cal = java.util.Calendar.getInstance()
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
        for (h in 0..23) {
            for (m in listOf(0, 30)) {
                cal.set(java.util.Calendar.HOUR_OF_DAY, h)
                cal.set(java.util.Calendar.MINUTE, m)
                list.add(sdf.format(cal.time))
            }
        }
        list
    }

    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        androidx.compose.material3.OutlinedTextField(
            value = selectedTime,
            onValueChange = {},
            readOnly = true,
            label = { androidx.compose.material3.Text(label) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        androidx.compose.material3.ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { androidx.compose.material3.Text(option) },
                    onClick = {
                        onTimeSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}"""

if old_dropdown in content:
    content = content.replace(old_dropdown, new_dropdown)
else:
    # Just remove any TimeSelectDropdown and append the new one
    content = re.sub(r'@OptIn\(ExperimentalMaterial3Api::class\)\n@Composable\nfun TimeSelectDropdown.*?\}\n\}\n\}', '', content, flags=re.DOTALL)
    content += "\n" + new_dropdown

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.write(content)

