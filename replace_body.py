import sys

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if i >= 683 and i <= 1593:
        # Skip these lines
        pass
    else:
        new_lines.append(line)
        if i == 682: # Just before the deleted section
            new_lines.append("        NormalModularDashboard(\n")
            new_lines.append("            state = state,\n")
            new_lines.append("            viewModel = viewModel,\n")
            new_lines.append("            hasBluetoothPermission = hasBluetoothPermission,\n")
            new_lines.append("            bluetoothLauncher = bluetoothLauncher,\n")
            new_lines.append("            context = context,\n")
            new_lines.append("            onShowHealthDialog = { showHealthDialog = true },\n")
            new_lines.append("            onShowTempDialog = { showTempDialog = true },\n")
            new_lines.append("            onShowPowerDialog = { showPowerDialog = true }\n")
            new_lines.append("        )\n")

with open("app/src/main/java/com/example/ui/MainDashboard.kt", "w") as f:
    f.writelines(new_lines)

