        Text(
            text = "Netra Battery Sentinel Pro",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(10.dp))

        // 3. Absolute Truth Engine & System Resume Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = when (systemStatus) {
                                    com.example.viewmodel.BatteryViewModel.SystemOperationalStatus.ACTIVE_VERIFIED -> Color(0xFF00E676)
                                    com.example.viewmodel.BatteryViewModel.SystemOperationalStatus.RECOVERING_REVALIDATING -> Color(0xFFFFAB00)
                                    com.example.viewmodel.BatteryViewModel.SystemOperationalStatus.SUSPENDED -> Color(0xFFFF1744)
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "System Status: RESUMED & ACTIVE [VERIFIED]",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = systemStatusMessage,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
                OutlinedButton(
                    onClick = { viewModel.resumeSystem(context) },
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .testTag("resume_system_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Resume", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }

        // 4. System Telemetry Grid
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Battery Health
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).padding(end = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Battery Health", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.healthPercentage}%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    Text(state.health, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Temperature
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Temperature", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${state.temperature}°C", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    val tempStatus = when {
                        state.temperature < 35f -> "Cool"
                        state.temperature < 40f -> "Normal"
                        else -> "Hot"
                    }
                    Text(tempStatus, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Voltage
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Voltage", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${String.format(java.util.Locale.US, "%.3f", state.voltage / 1000f)} V", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    val voltStatus = if (state.voltage in 3000..4500) "Good" else "Warning"
                    Text(voltStatus, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Charge Rate
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Charge Rate", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val rateText = if (state.isCharging) {
                        if (state.speed > 0) "+${String.format(java.util.Locale.US, "%.1f", state.speed)}%/h" else "${state.currentNow}mA"
                    } else {
                        if (state.speed > 0) "-${String.format(java.util.Locale.US, "%.1f", state.speed)}%/h" else "${state.currentNow}mA"
                    }
                    Text(rateText, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (state.isCharging) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface)
                    val speedStatus = if (state.speed > 20) "Fast" else "Normal"
                    Text(speedStatus, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        // Developer Mode
        if (isAdvancedMode) {
            // Keep this for developer mode content if any
        }

        var isIgnoringBatteryOptimizations by remember {
            mutableStateOf(viewModel.isIgnoringBatteryOptimizations(context))
        }
        var hasBluetoothPermission by remember {
            mutableStateOf(com.example.service.BluetoothDeviceMonitor.hasBluetoothPermission(context))
        }

        val bluetoothLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { isGranted ->
                hasBluetoothPermission = isGranted
                viewModel.refreshBluetoothDevices(context)
            }
        )

        // Launch a periodic check for ignoring battery optimization status
        LaunchedEffect(Unit) {
            while(true) {
                isIgnoringBatteryOptimizations = viewModel.isIgnoringBatteryOptimizations(context)
                kotlinx.coroutines.delay(5000)
            }
        }

        // ----------------- TRINETRA EYES & ADAPTIVE MODE TOGGLE -----------------

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Trinetra Eyes (symbolic 3 eyes representing Observation, Analysis, Prediction)
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape)) // Observation
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFFFFBC00), CircleShape)) // Analysis
                    Box(modifier = Modifier.size(8.dp).background(Color(0xFF2196F3), CircleShape)) // Prediction
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "TRINETRA ACTIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }
                Text(
                    text = "Observe • Analyze • Predict",
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Simple vs Advanced Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(100.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(100.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                listOf(false to "Normal Mode", true to "Developer Mode").forEach { (isAdv, label) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(
                                if (isAdvancedMode == isAdv) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .clickable { isAdvancedMode = isAdv }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAdvancedMode == isAdv) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        NetraWatchdogMonitorCard(viewModel = viewModel, modifier = Modifier.padding(bottom = 16.dp))

        // ----------------- PERMISSION REPAIR CENTER (ALERT BANNER) -----------------
        val hasUsageStats = remember(isIgnoringBatteryOptimizations) {
            hasUsageStatsPermission(context)
        }

        // ----------------- SMART FEATURE ACCESS ENGINE STATE -----------------
        val totalFeaturesCount = 32
        val disabledFeatures = remember(hasNotificationPermission, hasUsageStats, isIgnoringBatteryOptimizations, hasBluetoothPermission) {
            val list = mutableListOf<Pair<String, String>>()
            if (!hasUsageStats) {
                list.add("Background Battery Analyzer" to "Usage Access permission was skipped.")
                list.add("Foreground/Background App Analytics" to "Usage Access permission was skipped.")
                list.add("Top Power Consuming Apps" to "Usage Access permission was skipped.")
                list.add("Screen Time Analytics" to "Usage Access permission was skipped.")
            }
            if (!hasBluetoothPermission) {
                list.add("Bluetooth Battery %" to "Nearby Devices permission was skipped.")
                list.add("Smart Watch Battery Tracker" to "Nearby Devices permission was skipped.")
                list.add("Earbuds Battery Tracker" to "Nearby Devices permission was skipped.")
                list.add("Speaker Battery Tracker" to "Nearby Devices permission was skipped.")
            }
            if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                list.add("Charger Connected Alert" to "Notification permission was skipped.")
                list.add("Full Battery Alert" to "Notification permission was skipped.")
                list.add("Low Battery Alert" to "Notification permission was skipped.")
                list.add("Temperature Alert" to "Notification permission was skipped.")
                list.add("Temperature Warning Notification" to "Notification permission was skipped.")
            }
            if (!isIgnoringBatteryOptimizations) {
                list.add("Continuous Background Monitoring" to "Battery Optimization was not disabled.")
            }
            list
        }
        val enabledFeaturesCount = totalFeaturesCount - disabledFeatures.size
        
        // Netra v1.5 - Smart Permission Policy: No big red warning banners or pop-up spam.
        // Show only a friendly, non-intrusive notification if Battery Optimization is enabled.
        if (!isIgnoringBatteryOptimizations) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Background monitoring may be limited due to system battery optimization.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        if (isAdvancedMode) {
            // ----------------- PERMISSION HEALTH DASHBOARD CARD -----------------
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.HealthAndSafety,
                            contentDescription = "Health Panel",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Netra Permission Health Dashboard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Grid of 6 permissions with their specific state and action handler
                    // State: 0 = Enabled, 1 = Skipped, 2 = Not Required / Not Supported
                    val permissionsData = listOf(
                        Quad(
                            "Notifications",
                            if (Build.VERSION.SDK_INT < 33) 0 else if (hasNotificationPermission) 0 else 1,
                            "Voice alerts & thermal warnings",
                            { onRequestNotificationPermission() }
                        ),
                        Quad(
                            "Usage Access",
                            if (hasUsageStats) 0 else 1,
                            "Top apps usage tracking",
                            {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {}
                            }
                        ),
                        Quad(
                            "Battery Optimization",
                            if (isIgnoringBatteryOptimizations) 0 else 1,
                            "Unrestricted background logs",
                            { viewModel.requestIgnoreBatteryOptimizations(context) }
                        ),
                        Quad(
                            "Nearby Devices",
                            if (Build.VERSION.SDK_INT < 31) 2 else if (hasBluetoothPermission) 0 else 1,
                            "Peripheral battery tracking",
                            {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    bluetoothLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                } else {
                                    android.widget.Toast.makeText(context, "Nearby permission not required on this Android version.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ),
                        Quad(
                            "Auto Start Status",
                            if (settings.autoStartConfigured) 0 else 1,
                            "Survivor of phone reboots",
                            {
                                openAutoStartSettings(context)
                                viewModel.updateSettings(settings.copy(autoStartConfigured = true))
                            }
                        ),
                        Quad(
                            "Boot Auto Start",
                            0, // Auto-granted by system on install
                            "Automatic background launch",
                            {
                                android.widget.Toast.makeText(context, "Boot auto start is standard and automatically enabled by system.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        permissionsData.chunked(2).forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { item ->
                                    val (statusText, statusColor, statusIcon) = when (item.second) {
                                        0 -> Triple("🟢 Enabled", Color(0xFF43A047), Icons.Filled.CheckCircle)
                                        1 -> Triple("🟡 Skipped by User", Color(0xFFFB8C00), Icons.Filled.Warning)
                                        else -> Triple("⚪ Not Required", Color(0xFF9E9E9E), Icons.Filled.Info)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                            .border(BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)), RoundedCornerShape(8.dp))
                                            .clickable { item.fourth() }
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.first,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = statusText,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = statusColor
                                                )
                                            }
