import re

with open("app/src/main/java/com/example/ui/SmartDevicesHub.kt", "r") as f:
    content = f.read()

# Replace fake Nearby Devices with real bonded but disconnected devices or nothing
nearby_replacement = """
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val disconnectedBt = netraDevices.filter { !it.isWifi && (it.connectionStatus == "Disconnected" || it.connectionStatus == "Offline" || it.connectionStatus == "Saved") }
                
                if (isScanning) {
                    item {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                } else if (disconnectedBt.isEmpty()) {
                    item {
                        Text("No nearby devices found", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    items(disconnectedBt) { dev ->
                        NearbyDeviceCard(
                            name = dev.name,
                            type = dev.deviceType,
                            icon = if (dev.deviceType.contains("Audio") || dev.deviceType.contains("Speaker")) Icons.Filled.Speaker else Icons.Filled.Bluetooth,
                            signal = "Saved",
                            battery = dev.batteryLevel,
                            color = Color(0xFF9E9E9E)
                        )
                    }
                }
            }
"""
content = re.sub(
    r"LazyRow\([\s\S]*?item \{\n\s*NearbyDeviceCard\(\"Smart TV\"[\s\S]*?item \{\n\s*NearbyDeviceCard\(\"Security Cam\"[\s\S]*?\}\n\s*\}\n\s*\}",
    nearby_replacement.strip(),
    content
)


# Replace fake Activity log with dynamic or empty state
log_replacement = """
            val recentLogs = viewModel.netraConnectedDevices.value.map { "${it.name} ${it.connectionStatus}" }.take(3)
            if (recentLogs.isEmpty()) {
                Text("No recent activity.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                recentLogs.forEach { logText ->
                    ActivityLogItem(
                        title = logText,
                        subtitle = "Device status updated automatically.",
                        time = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                        icon = Icons.Filled.Info,
                        color = Color(0xFF2196F3)
                    )
                }
            }
"""
content = re.sub(
    r"ActivityLogItem\([\s\S]*?color = Color\(0xFF4CAF50\)\n\s*\)",
    log_replacement.strip(),
    content
)

# Add Live Graphs Section
graphs_section = """
        // Live Graphs
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Live Graphs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Icon(Icons.Filled.ShowChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(120.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Collecting Live Data", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        
        // Device Activity Log
"""
content = content.replace("// Device Activity Log", graphs_section.strip())

with open("app/src/main/java/com/example/ui/SmartDevicesHub.kt", "w") as f:
    f.write(content)
