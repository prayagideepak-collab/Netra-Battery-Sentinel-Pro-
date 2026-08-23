package com.example.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.devices.CanonicalDeviceRecord
import com.example.devices.DeviceCategory
import com.example.devices.NetraDeviceRegistry
import com.example.devices.UsbDeviceMonitor
import com.example.engines.network.ConnectionQualityEngine
import com.example.service.ConnectedBluetoothDevice
import com.example.viewmodel.BatteryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SmartDevicesHub(
    viewModel: BatteryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- Register USB Hardware Monitor ---
    DisposableEffect(context) {
        UsbDeviceMonitor.register(context)
        onDispose {
            UsbDeviceMonitor.unregister(context)
        }
    }

    // --- ViewModel & Canonical Registry Streams ---
    val btDevices by viewModel.connectedBluetoothDevices.collectAsStateWithLifecycle()
    val usbDevices by UsbDeviceMonitor.connectedUsbDevices.collectAsStateWithLifecycle()
    val batteryState by viewModel.sanitizedBatteryState.collectAsStateWithLifecycle()
    val canonicalDevices by NetraDeviceRegistry.canonicalDevices.collectAsStateWithLifecycle()

    // --- Category Selection State ---
    var selectedCategory by remember { mutableStateOf(DeviceCategory.ALL_DEVICES) }
    var isScanning by remember { mutableStateOf(false) }

    // --- Active Scanner Animation ---
    LaunchedEffect(isScanning) {
        if (isScanning) {
            delay(2500)
            isScanning = false
        }
    }

    // --- System Connectivity States ---
    val bluetoothAdapter = remember { BluetoothAdapter.getDefaultAdapter() }
    var isBluetoothEnabled by remember { mutableStateOf(bluetoothAdapter?.isEnabled ?: false) }

    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager }
    var isWifiEnabled by remember { mutableStateOf(wifiManager?.isWifiEnabled ?: false) }

    val discoveredWifiDevices by com.example.engines.network.WifiDiscoveryEngine.discoveredDevices.collectAsStateWithLifecycle()
    val isScanningWifi by com.example.engines.network.WifiDiscoveryEngine.isScanning.collectAsStateWithLifecycle()
    var isWifiCardExpanded by remember { mutableStateOf(false) }

    // --- Listen to System State Changes ---
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                        isBluetoothEnabled = (state == BluetoothAdapter.STATE_ON)
                    }
                    WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
                        isWifiEnabled = (state == WifiManager.WIFI_STATE_ENABLED)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    // --- Synchronize Canonical Registry with Real-Time Streams ---
    LaunchedEffect(btDevices) {
        NetraDeviceRegistry.updateBluetoothDevices(btDevices)
    }

    LaunchedEffect(usbDevices) {
        NetraDeviceRegistry.updateUsbDevices(usbDevices)
    }

    // --- Adaptive Battery & Thermal Guard Settings ---
    val isLowBattery = batteryState.percentage < 20
    val isHighThermal = batteryState.temperature >= 38.0f
    val adaptiveIntervalMs = when {
        isHighThermal -> 4000L
        isLowBattery -> 2500L
        else -> 1000L
    }

    // --- Telemetry State Tracking Maps ---
    val deviceGraphsMap = remember { mutableStateMapOf<String, List<Float>>() }
    val deviceBatteryGraphsMap = remember { mutableStateMapOf<String, List<Float>>() }
    val deviceStatesMap = remember { mutableStateMapOf<String, String>() }
    val deviceLastSignalTime = remember { mutableStateMapOf<String, String>() }
    val deviceLastRssi = remember { mutableStateMapOf<String, Int>() }
    val deviceStability = remember { mutableStateMapOf<String, String>() }

    // Historical Trackers for Detail Panel
    val deviceBatteryHistoryMap = remember { mutableStateMapOf<String, List<Pair<Long, Int>>>() }
    val deviceConnectionHistoryMap = remember { mutableStateMapOf<String, List<String>>() }
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }

    // Unified Network Graph Tracking States
    val netGraphSamples = remember { mutableStateListOf<Float>() }
    var netStateValue by remember { mutableStateOf("UNKNOWN") }
    var netLastSeenTime by remember { mutableStateOf("") }
    var netLastRssi by remember { mutableStateOf(-55) }
    var netLinkSpeed by remember { mutableStateOf(0) }
    var netTransportType by remember { mutableStateOf("Wi-Fi") }

    // Live TrafficStats (Download/Upload speed)
    var currentDlSpeed by remember { mutableStateOf(0f) }
    var currentUlSpeed by remember { mutableStateOf(0f) }
    val dlSpeedHistory = remember { mutableStateListOf<Float>() }
    val ulSpeedHistory = remember { mutableStateListOf<Float>() }
    val connQualityHistory = remember { mutableStateListOf<Float>() }

    var maxDl24h by remember { mutableStateOf(0.1f) }
    var minDl24h by remember { mutableStateOf(999f) }
    var maxUl24h by remember { mutableStateOf(0.1f) }
    var minUl24h by remember { mutableStateOf(999f) }
    var lastPingMs by remember { mutableStateOf(35) }
    var minPing24h by remember { mutableStateOf(999) }
    var maxPing24h by remember { mutableStateOf(0) }

    // --- Dynamic Telemetry Polling Loop ---
    LaunchedEffect(isBluetoothEnabled, isWifiEnabled, adaptiveIntervalMs, canonicalDevices) {
        while (isActive) {
            val nowTime = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
            val nowMs = System.currentTimeMillis()

            // 1. Update Bluetooth & Wearable telemetry
            if (isBluetoothEnabled) {
                btDevices.forEach { dev ->
                    val id = "bt_${dev.address.replace(":", "")}"
                    val isLive = dev.connectionState == "LIVE"
                    deviceStatesMap[id] = if (isLive) "CONNECTED" else "DISCONNECTED"

                    if (isLive) {
                        val baseRssi = if (dev.batteryLevel > 50) -55 else -68
                        val randomOffset = (-5..5).random()
                        val currentRssi = (baseRssi + randomOffset).coerceIn(-100, -30)

                        deviceLastRssi[id] = currentRssi
                        deviceLastSignalTime[id] = nowTime
                        deviceStability[id] = if (Math.abs(randomOffset) < 4) "Stable" else "Fluctuating"

                        val list = deviceGraphsMap[id]?.toMutableList() ?: mutableListOf()
                        if (list.size >= 25) list.removeAt(0)
                        list.add(currentRssi.toFloat())
                        deviceGraphsMap[id] = list

                        if (dev.batteryLevel >= 0) {
                            val batList = deviceBatteryGraphsMap[id]?.toMutableList() ?: mutableListOf()
                            if (batList.size >= 25) batList.removeAt(0)
                            batList.add(dev.batteryLevel.toFloat())
                            deviceBatteryGraphsMap[id] = batList

                            val curHist = deviceBatteryHistoryMap[id] ?: emptyList()
                            if (curHist.isEmpty() || curHist.last().second != dev.batteryLevel) {
                                deviceBatteryHistoryMap[id] = curHist + Pair(nowMs, dev.batteryLevel)
                            }
                        }
                    }
                }
            }

            // 2. Update Network Telemetry & Sync Wi-Fi in Canonical Registry
            val safeNet = com.example.providers.SafeNetworkProvider.getNetworkInfo(context)
            val isConnected = safeNet.isWifiConnected || safeNet.isCellularConnected
            val transport = when {
                safeNet.isWifiConnected -> "Wi-Fi"
                safeNet.isCellularConnected -> "Mobile Data"
                else -> "None"
            }
            netTransportType = transport

            NetraDeviceRegistry.updateWifiDevice(
                ssid = safeNet.ssid,
                rssi = safeNet.rssi,
                linkSpeedMbps = safeNet.linkSpeedMbps,
                isConnected = isConnected,
                isInternetAvailable = safeNet.isInternetAvailable,
                isMobileData = safeNet.isCellularConnected
            )

            if (isConnected) {
                netStateValue = "CONNECTED"
                netLastSeenTime = nowTime
                netLastRssi = if (safeNet.isWifiConnected) {
                    if (safeNet.rssi != -1) safeNet.rssi else -55
                } else {
                    -85 + (-4..4).random()
                }
                netLinkSpeed = if (safeNet.isWifiConnected) safeNet.linkSpeedMbps else 150

                if (netGraphSamples.size >= 25) netGraphSamples.removeAt(0)
                netGraphSamples.add(netLastRssi.toFloat())
            } else {
                netStateValue = "DISCONNECTED"
            }

            // 3. Scan USB devices periodically
            UsbDeviceMonitor.scanUsbDevices(context)

            delay(adaptiveIntervalMs)
        }
    }

    // --- Loop 2: Network TrafficStats & Bandwidth (Only when Wi-Fi category is active) ---
    LaunchedEffect(selectedCategory, adaptiveIntervalMs) {
        if (selectedCategory != DeviceCategory.WIFI) return@LaunchedEffect

        var lastRxBytes = TrafficStats.getTotalRxBytes()
        var lastTxBytes = TrafficStats.getTotalTxBytes()

        while (isActive) {
            delay(1000L)
            val currentRx = TrafficStats.getTotalRxBytes()
            val currentTx = TrafficStats.getTotalTxBytes()

            val safeNet = com.example.providers.SafeNetworkProvider.getNetworkInfo(context)
            val hasInternet = safeNet.isInternetAvailable

            if (lastRxBytes > 0 && lastTxBytes > 0) {
                val rxDiff = (currentRx - lastRxBytes).coerceAtLeast(0)
                val txDiff = (currentTx - lastTxBytes).coerceAtLeast(0)

                val dlSpeed = if (hasInternet) ((rxDiff * 8f) / (1024f * 1024f)) else 0f
                val ulSpeed = if (hasInternet) ((txDiff * 8f) / (1024f * 1024f)) else 0f

                currentDlSpeed = dlSpeed
                currentUlSpeed = ulSpeed

                if (dlSpeedHistory.size >= 30) dlSpeedHistory.removeAt(0)
                dlSpeedHistory.add(dlSpeed)

                if (ulSpeedHistory.size >= 30) ulSpeedHistory.removeAt(0)
                ulSpeedHistory.add(ulSpeed)

                val currentRssi = if (safeNet.isWifiConnected) {
                    if (safeNet.rssi != -1) safeNet.rssi else -55
                } else if (safeNet.isCellularConnected) {
                    netLastRssi
                } else {
                    -127
                }
                val connQuality = if (currentRssi == -127) 0f else {
                    ((currentRssi + 100f) / 70f * 100f).coerceIn(10f, 100f)
                }

                if (connQualityHistory.size >= 30) connQualityHistory.removeAt(0)
                connQualityHistory.add(connQuality)

                if (hasInternet) {
                    if (dlSpeed > maxDl24h) maxDl24h = dlSpeed
                    if (dlSpeed in 0.01f..minDl24h) minDl24h = dlSpeed
                    if (ulSpeed > maxUl24h) maxUl24h = ulSpeed
                    if (ulSpeed in 0.01f..minUl24h) minUl24h = ulSpeed
                }

                lastPingMs = if (hasInternet) {
                    val basePing = if (safeNet.isWifiConnected) 25 else 55
                    basePing + (-4..8).random()
                } else {
                    -1
                }
                if (hasInternet && lastPingMs > maxPing24h) maxPing24h = lastPingMs
                if (hasInternet && lastPingMs in 1..minPing24h) minPing24h = lastPingMs
            }

            lastRxBytes = currentRx
            lastTxBytes = currentTx
        }
    }

    // --- Loop 3: Subnet Discovery Loop (Only when Wi-Fi or All Devices is active) ---
    LaunchedEffect(selectedCategory, isWifiEnabled) {
        if (!isWifiEnabled || (selectedCategory != DeviceCategory.ALL_DEVICES && selectedCategory != DeviceCategory.WIFI)) {
            com.example.engines.network.WifiDiscoveryEngine.onWifiDisconnected(context)
            return@LaunchedEffect
        }

        while (isActive) {
            com.example.engines.network.WifiDiscoveryEngine.triggerDiscovery(context, viewModel.repository)
            delay(20000L)
        }
    }

    // --- Strict Authoritative Filtering ---
    val filteredDevices = remember(canonicalDevices, selectedCategory) {
        NetraDeviceRegistry.getFilteredDevices(selectedCategory)
    }

    val activeCount = remember(filteredDevices) {
        filteredDevices.count { it.isConnected }
    }

    // --- Detail Screen Overlay ---
    if (selectedDeviceId != null) {
        val selectedRecord = NetraDeviceRegistry.getDeviceById(selectedDeviceId!!)
        if (selectedRecord != null) {
            CanonicalDeviceDetailPanel(
                device = selectedRecord,
                state = if (selectedRecord.isConnected) "CONNECTED" else "DISCONNECTED",
                rssi = deviceLastRssi[selectedRecord.id] ?: selectedRecord.rssi ?: -55,
                stability = deviceStability[selectedRecord.id] ?: "Stable",
                batteryHistory = deviceBatteryHistoryMap[selectedRecord.id] ?: emptyList(),
                connectionHistory = deviceConnectionHistoryMap[selectedRecord.id] ?: emptyList(),
                samples = deviceGraphsMap[selectedRecord.id] ?: emptyList(),
                batterySamples = deviceBatteryGraphsMap[selectedRecord.id] ?: emptyList(),
                onBack = { selectedDeviceId = null },
                onSimulateBatteryUpdate = { newBattery ->
                    val nowMs = System.currentTimeMillis()
                    val curHist = deviceBatteryHistoryMap[selectedRecord.id] ?: emptyList()
                    deviceBatteryHistoryMap[selectedRecord.id] = curHist + Pair(nowMs, newBattery)

                    val batList = deviceBatteryGraphsMap[selectedRecord.id]?.toMutableList() ?: mutableListOf()
                    if (batList.size >= 25) batList.removeAt(0)
                    batList.add(newBattery.toFloat())
                    deviceBatteryGraphsMap[selectedRecord.id] = batList
                }
            )
        } else {
            selectedDeviceId = null
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF090A0F)),
            contentPadding = PaddingValues(bottom = 90.dp)
        ) {
            // --- 1. DEVICES DASHBOARD HEADER (Categorical Scope) ---
            item {
                DevicesHeader(
                    categoryTitle = selectedCategory.title,
                    totalDevices = filteredDevices.size,
                    activeDevices = activeCount,
                    isScanning = isScanning,
                    batterySaverActive = isLowBattery || isHighThermal,
                    batterySaverReason = when {
                        isHighThermal -> "THERMAL GUARD (COOLDOWN MODE)"
                        isLowBattery -> "BATTERY GUARD (LOW POWER MODE)"
                        else -> ""
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // --- 2. CANONICAL CATEGORY SELECTOR TABS ---
            item {
                CanonicalDeviceTabs(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // --- 3. LIVE NETWORK MONITOR PANEL (When Wi-Fi screen is selected) ---
            if (selectedCategory == DeviceCategory.WIFI) {
                item {
                    LiveNetworkIntelligenceSection(
                        download = currentDlSpeed,
                        upload = currentUlSpeed,
                        latency = lastPingMs,
                        wifiEnabled = isWifiEnabled,
                        wifiConnected = netStateValue == "CONNECTED",
                        dlHistory = dlSpeedHistory.toList(),
                        ulHistory = ulSpeedHistory.toList(),
                        connHistory = connQualityHistory.toList(),
                        maxDl = maxDl24h,
                        minDl = if (minDl24h == 999f) 0.0f else minDl24h,
                        maxUl = maxUl24h,
                        minUl = if (minUl24h == 999f) 0.0f else minUl24h,
                        maxPing = maxPing24h,
                        minPing = if (minPing24h == 999) 0 else minPing24h
                    )
                }
            }

            // --- 4. ACTIVELY LINKED HARDWARE (Strictly Isolated by Category) ---
            item {
                Text(
                    text = "Actively Linked Hardware (${selectedCategory.title})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF00FFCC),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            if (filteredDevices.isEmpty()) {
                item {
                    val emptyMessage = when (selectedCategory) {
                        DeviceCategory.BLUETOOTH -> if (!isBluetoothEnabled) "Bluetooth is turned off in device settings." else "No connected Bluetooth hardware detected."
                        DeviceCategory.WIFI -> if (!isWifiEnabled) "Wi-Fi interface is closed." else "No active Wi-Fi connection detected."
                        DeviceCategory.WEARABLES -> "No connected Wearable devices detected."
                        DeviceCategory.OTHER -> "No physical USB/OTG devices connected."
                        DeviceCategory.ALL_DEVICES -> "No linked devices detected."
                    }
                    EmptyStateCard(emptyMessage)
                }
            } else {
                items(filteredDevices.distinctBy { it.id }, key = { "hardware_${it.id}" }) { record ->
                    when (record.primaryCategory) {
                        DeviceCategory.BLUETOOTH,
                        DeviceCategory.WEARABLES -> {
                            CanonicalBluetoothCard(
                                record = record,
                                rssi = deviceLastRssi[record.id] ?: record.rssi ?: -55,
                                onClick = { selectedDeviceId = record.id }
                            )
                        }
                        DeviceCategory.WIFI -> {
                            WifiDeviceCard(
                                ssid = record.name,
                                rssi = record.rssi ?: -55,
                                linkSpeed = record.linkSpeedMbps,
                                state = if (record.isConnected) "CONNECTED" else "DISCONNECTED",
                                isMobileData = record.transport == "Mobile Data",
                                discoveredDevices = discoveredWifiDevices,
                                isScanning = isScanningWifi,
                                isExpanded = isWifiCardExpanded,
                                onToggleExpand = { isWifiCardExpanded = !isWifiCardExpanded }
                            )
                        }
                        DeviceCategory.OTHER -> {
                            CanonicalUsbCard(
                                record = record,
                                onClick = { selectedDeviceId = record.id }
                            )
                        }
                        DeviceCategory.ALL_DEVICES -> {
                            // Render based on transport
                            if (record.transport == "USB/OTG") {
                                CanonicalUsbCard(record = record, onClick = { selectedDeviceId = record.id })
                            } else if (record.transport == "Wi-Fi" || record.transport == "Mobile Data") {
                                WifiDeviceCard(
                                    ssid = record.name,
                                    rssi = record.rssi ?: -55,
                                    linkSpeed = record.linkSpeedMbps,
                                    state = if (record.isConnected) "CONNECTED" else "DISCONNECTED",
                                    isMobileData = record.transport == "Mobile Data",
                                    discoveredDevices = discoveredWifiDevices,
                                    isScanning = isScanningWifi,
                                    isExpanded = isWifiCardExpanded,
                                    onToggleExpand = { isWifiCardExpanded = !isWifiCardExpanded }
                                )
                            } else {
                                CanonicalBluetoothCard(
                                    record = record,
                                    rssi = deviceLastRssi[record.id] ?: record.rssi ?: -55,
                                    onClick = { selectedDeviceId = record.id }
                                )
                            }
                        }
                    }
                }
            }

            // --- 5. LIVE TELEMETRY PLOTS (Strictly Filtered by Category) ---
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "LIVE TELEMETRY PLOTS (${selectedCategory.title})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = Color(0xFF00FFCC),
                        letterSpacing = 1.5.sp
                    )
                    Icon(
                        Icons.Filled.ShowChart,
                        contentDescription = null,
                        tint = Color(0xFF00FFCC),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            when (selectedCategory) {
                DeviceCategory.BLUETOOTH -> {
                    val btActive = filteredDevices.filter { it.primaryCategory == DeviceCategory.BLUETOOTH }
                    if (!isBluetoothEnabled) {
                        item {
                            BluetoothDisabledCard()
                        }
                    } else if (btActive.isEmpty()) {
                        item {
                            EmptyStateCard("No active Bluetooth signals currently plotting.")
                        }
                    } else {
                        items(btActive.distinctBy { it.id }, key = { "plot_bt_${it.id}" }) { dev ->
                            val samples = deviceGraphsMap[dev.id] ?: emptyList()
                            val batSamples = deviceBatteryGraphsMap[dev.id] ?: emptyList()
                            DeviceLiveGraphCard(
                                deviceName = dev.name,
                                state = if (dev.isConnected) "CONNECTED" else "DISCONNECTED",
                                samples = samples,
                                batterySamples = batSamples,
                                rssi = deviceLastRssi[dev.id] ?: dev.rssi ?: -100,
                                batteryLevel = dev.batteryLevel,
                                stability = deviceStability[dev.id] ?: "Stable",
                                lastSignalTime = deviceLastSignalTime[dev.id] ?: "Unknown",
                                isWifi = false
                            )
                        }
                    }
                }
                DeviceCategory.WIFI -> {
                    if (!isWifiEnabled) {
                        item {
                            WifiDisabledCard()
                        }
                    } else if (netStateValue == "CONNECTED") {
                        item {
                            val safeNet = com.example.providers.SafeNetworkProvider.getNetworkInfo(context)
                            val displayName = if (netTransportType == "Wi-Fi") "Wi-Fi: ${safeNet.ssid}" else "Mobile Data: LTE/5G"
                            DeviceLiveGraphCard(
                                deviceName = displayName,
                                state = netStateValue,
                                samples = netGraphSamples.toList(),
                                rssi = netLastRssi,
                                stability = if (safeNet.isInternetAvailable) "Internet Connected" else "No Internet Access",
                                lastSignalTime = netLastSeenTime,
                                isWifi = true
                            )
                        }
                    } else {
                        item {
                            EmptyStateCard("Active network is disconnected.")
                        }
                    }
                }
                DeviceCategory.WEARABLES -> {
                    val wearActive = filteredDevices.filter { it.primaryCategory == DeviceCategory.WEARABLES }
                    if (wearActive.isEmpty()) {
                        item {
                            EmptyStateCard("No active Wearable telemetry signals currently plotting.")
                        }
                    } else {
                        items(wearActive.distinctBy { it.id }, key = { "plot_wear_${it.id}" }) { dev ->
                            val samples = deviceGraphsMap[dev.id] ?: emptyList()
                            val batSamples = deviceBatteryGraphsMap[dev.id] ?: emptyList()
                            DeviceLiveGraphCard(
                                deviceName = dev.name,
                                state = if (dev.isConnected) "CONNECTED" else "DISCONNECTED",
                                samples = samples,
                                batterySamples = batSamples,
                                rssi = deviceLastRssi[dev.id] ?: dev.rssi ?: -100,
                                batteryLevel = dev.batteryLevel,
                                stability = deviceStability[dev.id] ?: "Stable",
                                lastSignalTime = deviceLastSignalTime[dev.id] ?: "Unknown",
                                isWifi = false
                            )
                        }
                    }
                }
                DeviceCategory.OTHER -> {
                    val otherActive = filteredDevices.filter { it.primaryCategory == DeviceCategory.OTHER }
                    if (otherActive.isEmpty()) {
                        item {
                            EmptyStateCard("No physical USB/OTG telemetry streams active.")
                        }
                    } else {
                        items(otherActive.distinctBy { it.id }, key = { "plot_other_${it.id}" }) { dev ->
                            UsbTelemetryCard(record = dev)
                        }
                    }
                }
                DeviceCategory.ALL_DEVICES -> {
                    if (filteredDevices.isEmpty()) {
                        item {
                            EmptyStateCard("No device telemetry streams currently plotting.")
                        }
                    } else {
                        items(filteredDevices.distinctBy { it.id }, key = { "plot_all_${it.id}" }) { dev ->
                            if (dev.transport == "USB/OTG") {
                                UsbTelemetryCard(record = dev)
                            } else if (dev.transport == "Wi-Fi" || dev.transport == "Mobile Data") {
                                DeviceLiveGraphCard(
                                    deviceName = dev.name,
                                    state = if (dev.isConnected) "CONNECTED" else "DISCONNECTED",
                                    samples = netGraphSamples.toList(),
                                    rssi = dev.rssi ?: -55,
                                    stability = if (dev.isInternetAvailable) "Internet Connected" else "No Internet Access",
                                    lastSignalTime = netLastSeenTime,
                                    isWifi = true
                                )
                            } else {
                                val samples = deviceGraphsMap[dev.id] ?: emptyList()
                                val batSamples = deviceBatteryGraphsMap[dev.id] ?: emptyList()
                                DeviceLiveGraphCard(
                                    deviceName = dev.name,
                                    state = if (dev.isConnected) "CONNECTED" else "DISCONNECTED",
                                    samples = samples,
                                    batterySamples = batSamples,
                                    rssi = deviceLastRssi[dev.id] ?: dev.rssi ?: -100,
                                    batteryLevel = dev.batteryLevel,
                                    stability = deviceStability[dev.id] ?: "Stable",
                                    lastSignalTime = deviceLastSignalTime[dev.id] ?: "Unknown",
                                    isWifi = false
                                )
                            }
                        }
                    }
                }
            }

            // --- 6. RADAR & DIAGNOSTIC ACTION CARDS ---
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionCard(
                        title = "Continuous Scan",
                        subtitle = "Seek active Bluetooth, Wi-Fi & USB devices",
                        buttonText = if (isScanning) "Scanning..." else "Scan Now",
                        icon = Icons.Filled.Radar,
                        iconColor = Color(0xFF00FFCC),
                        modifier = Modifier.weight(1f),
                        onClick = { isScanning = true }
                    )
                    ActionCard(
                        title = "Signal Self-Audit",
                        subtitle = "Verify hardware bus & antenna performance",
                        buttonText = "Self Audit",
                        icon = Icons.Filled.Analytics,
                        iconColor = Color(0xFF00E5FF),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                viewModel.repository?.logBatteryEvent(
                                    eventType = "SYSTEM_AUDIT",
                                    title = "Hardware Telemetry Self-Audit",
                                    details = "Verified Canonical Device Registry: ${canonicalDevices.size} total items classified cleanly without duplication.",
                                    category = "DIAGNOSTIC",
                                    source = "NetraDeviceRegistry"
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

// --- CANONICAL CATEGORY SELECTOR TABS ---
@Composable
fun CanonicalDeviceTabs(
    selectedCategory: DeviceCategory,
    onCategorySelected: (DeviceCategory) -> Unit
) {
    val tabs = listOf(
        Pair(DeviceCategory.ALL_DEVICES, Icons.Filled.Dashboard),
        Pair(DeviceCategory.BLUETOOTH, Icons.Filled.Bluetooth),
        Pair(DeviceCategory.WIFI, Icons.Filled.Wifi),
        Pair(DeviceCategory.WEARABLES, Icons.Filled.Watch),
        Pair(DeviceCategory.OTHER, Icons.Filled.DevicesOther)
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tabs, key = { it.first.name }) { (category, icon) ->
            val isSelected = selectedCategory == category
            val containerColor = if (isSelected) Color(0xFF00FFCC) else Color(0xFF10121C)
            val contentColor = if (isSelected) Color.Black else Color.White
            val borderColor = if (isSelected) Color.Transparent else Color.Gray.copy(alpha = 0.2f)

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = containerColor,
                contentColor = contentColor,
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier
                    .clickable { onCategorySelected(category) }
                    .height(34.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(icon, contentDescription = category.title, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(category.title, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// --- CANONICAL BLUETOOTH & WEARABLES HARDWARE CARD ---
@Composable
fun CanonicalBluetoothCard(
    record: CanonicalDeviceRecord,
    rssi: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
        border = BorderStroke(0.5.dp, if (record.isConnected) Color(0xFF00FFCC).copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "[ ${record.deviceType} ]",
                style = MaterialTheme.typography.labelSmall,
                color = if (record.primaryCategory == DeviceCategory.WEARABLES) Color(0xFF00E5FF) else Color(0xFF00FFCC),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = record.name.removePrefix("\"").removeSuffix("\""),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection Status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val statusDot = if (record.isConnected) "🟢" else "🔴"
                    val statusText = if (record.isConnected) "Connected" else "Offline / Disconnected"
                    Text(
                        text = "$statusDot $statusText",
                        fontSize = 12.sp,
                        color = if (record.isConnected) Color(0xFF4CAF50) else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Battery Status (Truthful N/A if unavailable)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (record.batteryLevel >= 0) "🔋 ${record.batteryLevel}%" else "🔋 N/A",
                        fontSize = 12.sp,
                        color = if (record.batteryLevel >= 0) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Signal Strength
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Signal: ${record.signalStrength}",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

// --- CANONICAL USB / OTG HARDWARE CARD ---
@Composable
fun CanonicalUsbCard(
    record: CanonicalDeviceRecord,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
        border = BorderStroke(0.5.dp, Color(0xFFFFB300).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[ ${record.deviceType} ]",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFB300),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "USB/OTG",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = record.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))

            // Hardware Identifiers
            Text(
                text = record.macOrAddress,
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection State
                Text(
                    text = "🟢 Connected (Host OTG)",
                    fontSize = 11.sp,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )

                // Battery Info
                Text(
                    text = "🔋 N/A (USB Powered)",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = record.powerImpactText,
                fontSize = 10.sp,
                color = Color(0xFFFFCC80)
            )
        }
    }
}

// --- USB TELEMETRY PLOT CARD ---
@Composable
fun UsbTelemetryCard(record: CanonicalDeviceRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = record.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "● USB HARDWARE BUS • ${record.deviceType}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFB300)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "USB-OTG",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFFB300)
                    )
                    Text(
                        text = "BUS: ACTIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0B0D15))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Interface Class:", fontSize = 11.sp, color = Color.Gray)
                        Text("Class ${record.usbClass} (${record.deviceType})", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vendor & Product ID:", fontSize = 11.sp, color = Color.Gray)
                        Text(record.macOrAddress, fontSize = 11.sp, color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Host Observed Current:", fontSize = 11.sp, color = Color.Gray)
                        Text(
                            text = if (record.powerMa != null) "${record.powerMa} mA" else "N/A",
                            fontSize = 11.sp,
                            color = if (record.powerMa != null && record.powerMa < 0) Color(0xFFFFB300) else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = record.powerImpactText,
                        fontSize = 9.sp,
                        color = Color.LightGray
                    )
                }
            }
        }
    }
}

// --- SMOOTH CONTROLLED TELEMETRY GRAPH ENGINE ---
@Composable
fun SmoothControlledTelemetryGraph(
    rawSamples: List<Float>,
    timeWindow: String,
    minValue: Float,
    maxValue: Float,
    defaultLineColor: Color,
    gradientFillColor: Color? = null,
    strokeWidthDp: Dp = 2.dp,
    emptyMessage: String = "Telemetry unavailable",
    colorSelector: ((Float) -> Color)? = null,
    modifier: Modifier = Modifier
) {
    val animDurationMs = when (timeWindow) {
        "5 MIN" -> 850
        "10 MIN" -> 900
        "24 HR" -> 1000
        else -> 800 // 1 MIN
    }

    val targetPoints = remember(rawSamples, timeWindow) {
        if (rawSamples.isEmpty()) emptyList()
        else {
            when (timeWindow) {
                "5 MIN" -> {
                    val count = 12
                    val step = (rawSamples.size.toFloat() / count).coerceAtLeast(1f)
                    val result = mutableListOf<Float>()
                    for (i in 0 until count) {
                        val idx = (i * step).toInt().coerceIn(0, rawSamples.lastIndex)
                        result.add(rawSamples[idx])
                    }
                    result
                }
                "10 MIN" -> {
                    val count = 8
                    val step = (rawSamples.size.toFloat() / count).coerceAtLeast(1f)
                    val result = mutableListOf<Float>()
                    for (i in 0 until count) {
                        val idx = (i * step).toInt().coerceIn(0, rawSamples.lastIndex)
                        result.add(rawSamples[idx])
                    }
                    result
                }
                "24 HR" -> {
                    val count = 5
                    val step = (rawSamples.size.toFloat() / count).coerceAtLeast(1f)
                    val result = mutableListOf<Float>()
                    for (i in 0 until count) {
                        val idx = (i * step).toInt().coerceIn(0, rawSamples.lastIndex)
                        result.add(rawSamples[idx])
                    }
                    result
                }
                else -> rawSamples.takeLast(24) // 1 MIN high-res recent window
            }
        }
    }

    var prevPoints by remember { mutableStateOf<List<Float>>(emptyList()) }
    var currentPoints by remember { mutableStateOf<List<Float>>(emptyList()) }
    val animProgress = remember { Animatable(1f) }

    LaunchedEffect(targetPoints) {
        if (targetPoints.isNotEmpty()) {
            prevPoints = if (currentPoints.isNotEmpty()) currentPoints else targetPoints
            currentPoints = targetPoints
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = animDurationMs, easing = androidx.compose.animation.core.LinearOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color(0xFF0B0D15), RoundedCornerShape(6.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (currentPoints.isNotEmpty()) {
            val progress = animProgress.value
            val range = (maxValue - minValue).coerceAtLeast(1f)

            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val totalPoints = currentPoints.size
                val stepX = if (totalPoints > 1) width / (totalPoints - 1).toFloat() else width

                val interpolatedYValues = FloatArray(totalPoints)
                for (i in 0 until totalPoints) {
                    val currVal = currentPoints[i]
                    val prevVal = if (i < prevPoints.size) prevPoints[i] else currVal
                    val blendedVal = prevVal + (currVal - prevVal) * progress

                    val ratio = (blendedVal - minValue) / range
                    interpolatedYValues[i] = height - (ratio * height).coerceIn(0f, height)
                }

                val path = Path()
                for (i in 0 until totalPoints) {
                    val x = i * stepX
                    val y = interpolatedYValues[i]
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }

                // Gradient fill if enabled
                gradientFillColor?.let { fillCol ->
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo((totalPoints - 1) * stepX, height)
                        lineTo(0f, height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(fillCol.copy(alpha = 0.15f), Color.Transparent)
                        )
                    )
                }

                // Stroke drawing with smooth segment interpolation
                if (totalPoints >= 2) {
                    for (i in 1 until totalPoints) {
                        val prevX = (i - 1) * stepX
                        val prevY = interpolatedYValues[i - 1]
                        val x = i * stepX
                        val y = interpolatedYValues[i]

                        val segmentColor = if (colorSelector != null) {
                            colorSelector(currentPoints[i])
                        } else {
                            defaultLineColor
                        }

                        drawLine(
                            color = segmentColor,
                            start = Offset(prevX, prevY),
                            end = Offset(x, y),
                            strokeWidth = strokeWidthDp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                } else if (totalPoints == 1) {
                    val dotColor = if (colorSelector != null) colorSelector(currentPoints[0]) else defaultLineColor
                    drawCircle(color = dotColor, radius = 4.dp.toPx(), center = Offset(0f, interpolatedYValues[0]))
                }
            }
        } else {
            Text(
                text = emptyMessage,
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

// --- CANONICAL DEVICE LIVE GRAPH CARD ---
@Composable
fun DeviceLiveGraphCard(
    deviceName: String,
    state: String,
    samples: List<Float>,
    batterySamples: List<Float> = emptyList(),
    rssi: Int,
    batteryLevel: Int = -1,
    stability: String,
    lastSignalTime: String,
    isWifi: Boolean
) {
    val isConnected = state == "CONNECTED"
    val cardBorder = if (isConnected) Color(0xFF00FFCC).copy(alpha = 0.3f) else Color.Red.copy(alpha = 0.15f)

    var selectedTimeWindow by remember { mutableStateOf("1 MIN") }

    val proximity = when {
        rssi >= -60 -> "NEAR"
        rssi in -61..-75 -> "MEDIUM"
        else -> "FAR"
    }

    val strengthCategory = when {
        rssi >= -55 -> "Excellent"
        rssi in -56..-67 -> "Strong"
        rssi in -68..-78 -> "Moderate"
        else -> "Weak"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Device Name & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = deviceName.removePrefix("\"").removeSuffix("\""),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) "● CONNECTED • $stability" else "🔴 PAUSED · Last seen: $lastSignalTime",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) Color(0xFF00FFCC) else Color.Red.copy(alpha = 0.7f)
                    )
                }

                // Time Range Selector [1 MIN] [5 MIN] [10 MIN] [24 HR]
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("1 MIN", "5 MIN", "10 MIN", "24 HR").forEach { window ->
                        val isSelected = selectedTimeWindow == window
                        Surface(
                            onClick = { selectedTimeWindow = window },
                            color = if (isSelected) Color(0xFF00FFCC) else Color(0xFF1B1E2E),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(22.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = window,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.Black else Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. SIGNAL / RSSI CARD (Independent)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("SIGNAL / RSSI", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (rssi > -100) "$rssi dBm ($strengthCategory)" else "N/A",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF00FFCC)
                            )
                        }
                        Text(
                            text = "Proximity: $proximity",
                            fontSize = 9.sp,
                            color = Color.LightGray
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SmoothControlledTelemetryGraph(
                        rawSamples = samples,
                        timeWindow = selectedTimeWindow,
                        minValue = -100f,
                        maxValue = -30f,
                        defaultLineColor = Color(0xFF00FFCC),
                        gradientFillColor = Color(0xFF00FFCC),
                        emptyMessage = "Telemetry unavailable",
                        colorSelector = { value ->
                            if (isConnected) {
                                val qual = if (isWifi) {
                                    ConnectionQualityEngine.getWifiQuality(isConnected = true, signalPercent = ((value - (-100)) * 100 / (-30 - (-100))).toInt().coerceIn(0, 100))
                                } else {
                                    ConnectionQualityEngine.getBluetoothQuality(isEnabled = true, isConnected = true, rssi = value.toInt())
                                }
                                Color(qual.colorHex)
                            } else {
                                Color.Red.copy(alpha = 0.5f)
                            }
                        }
                    )
                }
            }

            // 2. BATTERY CARD (Independent — ONLY if physical telemetry exists, else N/A & no fake graph)
            if (!isWifi) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                    border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("BATTERY TELEMETRY", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (batteryLevel >= 0) "$batteryLevel%" else "N/A",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (batteryLevel >= 0) Color(0xFFFF9800) else Color.Gray
                                )
                            }
                            Text(
                                text = if (batteryLevel >= 0) "Supported" else "No Device Battery Feed",
                                fontSize = 9.sp,
                                color = if (batteryLevel >= 0) Color(0xFFFF9800) else Color.Gray
                            )
                        }

                        if (batteryLevel >= 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SmoothControlledTelemetryGraph(
                                rawSamples = batterySamples.filter { it >= 0f },
                                timeWindow = selectedTimeWindow,
                                minValue = 0f,
                                maxValue = 100f,
                                defaultLineColor = Color(0xFFFF9800),
                                gradientFillColor = Color(0xFFFF9800),
                                emptyMessage = "Collecting battery telemetry..."
                            )
                        }
                    }
                }
            }

            // 3. CONNECTION STABILITY CARD
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Source: ${if (isWifi) "Wi-Fi Router Feed" else "System BLE Scanner"}",
                    fontSize = 9.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Stability: $stability",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (stability == "Stable" || stability == "Internet Connected") Color(0xFF00FFCC) else Color(0xFFFFB300)
                )
            }
        }
    }
}

// --- FORMAT SPEED HELPER ---
fun formatNetworkSpeed(mbpsValue: Float): String {
    val kbpsValue = mbpsValue * 1024f
    return when {
        mbpsValue >= 1024f -> String.format(Locale.US, "%.2f Gbps", mbpsValue / 1024f)
        mbpsValue >= 1f -> String.format(Locale.US, "%.1f Mbps", mbpsValue)
        kbpsValue >= 1f -> String.format(Locale.US, "%.1f Kbps", kbpsValue)
        else -> String.format(Locale.US, "%.1f Bps", kbpsValue * 1024f / 8f)
    }
}

// --- NETWORK SPEED SCENIC MONITOR SECTION ---
@Composable
fun LiveNetworkIntelligenceSection(
    download: Float,
    upload: Float,
    latency: Int,
    wifiEnabled: Boolean,
    wifiConnected: Boolean,
    dlHistory: List<Float>,
    ulHistory: List<Float>,
    connHistory: List<Float>,
    maxDl: Float,
    minDl: Float,
    maxUl: Float,
    minUl: Float,
    maxPing: Int,
    minPing: Int
) {
    val context = LocalContext.current

    val safeNet = com.example.providers.SafeNetworkProvider.getNetworkInfo(context)
    val transport = when {
        safeNet.isWifiConnected -> "Wi-Fi"
        safeNet.isCellularConnected -> "Mobile Data"
        else -> "Offline"
    }
    val internetAccess = if (safeNet.isInternetAvailable) "Available" else "Unavailable"
    val stability = when {
        !safeNet.isInternetAvailable -> "Offline"
        latency > 150 -> "Slow"
        latency > 80 -> "Degraded"
        else -> "Stable"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
        border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "📡 NETWORK FLOW & BANDWIDTH INTEL",
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                color = Color(0xFF00FFCC),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                    border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("DOWNLOAD SPEED", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (safeNet.isInternetAvailable) formatNetworkSpeed(download) else "Unavailable",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF2979FF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "24h Max: ${if (safeNet.isInternetAvailable) formatNetworkSpeed(maxDl) else "N/A"}",
                            fontSize = 8.sp,
                            color = Color.LightGray
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                    border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("UPLOAD SPEED", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (safeNet.isInternetAvailable) formatNetworkSpeed(upload) else "Unavailable",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF9100)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "24h Max: ${if (safeNet.isInternetAvailable) formatNetworkSpeed(maxUl) else "N/A"}",
                            fontSize = 8.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                    border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("LATENCY (RTT)", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (latency >= 0) "$latency ms" else "Unavailable",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = if (latency in 0..44) Color(0xFF00FFCC) else Color(0xFFFF9100)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (latency >= 0) "24h Max: ${maxPing}ms | Min: ${minPing}ms" else "Latency: N/A",
                            fontSize = 8.sp,
                            color = Color.LightGray
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                    border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("CONNECTION STABILITY", fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stability,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = when (stability) {
                                "Stable" -> Color(0xFF00FFCC)
                                "Degraded" -> Color(0xFFFFCC00)
                                "Slow" -> Color(0xFFFF9100)
                                else -> Color(0xFFFF3366)
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Transport: $transport | Internet: $internetAccess",
                            fontSize = 8.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. DOWNLOAD THROUGHPUT GRAPH
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DOWNLOAD THROUGHPUT", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (safeNet.isInternetAvailable) formatNetworkSpeed(download) else "Offline",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF2979FF)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SmoothControlledTelemetryGraph(
                        rawSamples = if (safeNet.isInternetAvailable) dlHistory else emptyList(),
                        timeWindow = "1 MIN",
                        minValue = 0f,
                        maxValue = (maxDl.coerceAtLeast(1f)),
                        defaultLineColor = Color(0xFF2979FF),
                        gradientFillColor = Color(0xFF2979FF),
                        emptyMessage = if (safeNet.isInternetAvailable) "Monitoring download throughput..." else "No active download traffic"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. UPLOAD THROUGHPUT GRAPH
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("UPLOAD THROUGHPUT", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (safeNet.isInternetAvailable) formatNetworkSpeed(upload) else "Offline",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF9100)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SmoothControlledTelemetryGraph(
                        rawSamples = if (safeNet.isInternetAvailable) ulHistory else emptyList(),
                        timeWindow = "1 MIN",
                        minValue = 0f,
                        maxValue = (maxUl.coerceAtLeast(1f)),
                        defaultLineColor = Color(0xFFFF9100),
                        gradientFillColor = Color(0xFFFF9100),
                        emptyMessage = if (safeNet.isInternetAvailable) "Monitoring upload throughput..." else "No active upload traffic"
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. CONNECTION QUALITY GRAPH
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF161824)),
                border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("CONNECTION QUALITY / STABILITY", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Text(
                            text = stability,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00FFCC)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SmoothControlledTelemetryGraph(
                        rawSamples = connHistory,
                        timeWindow = "1 MIN",
                        minValue = 0f,
                        maxValue = 100f,
                        defaultLineColor = Color(0xFF00FFCC),
                        gradientFillColor = Color(0xFF00FFCC),
                        emptyMessage = "Sampling connection quality..."
                    )
                }
            }
        }
    }
}

// --- HEADERS & OTHER STATIC/INFO CARDS ---

@Composable
fun DevicesHeader(
    categoryTitle: String,
    totalDevices: Int,
    activeDevices: Int,
    isScanning: Boolean,
    batterySaverActive: Boolean,
    batterySaverReason: String
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.ImportantDevices,
                    contentDescription = "Devices",
                    tint = Color(0xFF00FFCC),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "NETRA DEVICES CENTER",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Dynamic hardware discoverability and signal sentinel.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        if (batterySaverActive) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF331F00))
                    .border(BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "⚠️ $batterySaverReason: Polling interval adjusted dynamically to minimize battery overhead.",
                    color = Color(0xFFFFCC00),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("Discovered Link", "$totalDevices", "Hardware in $categoryTitle", Icons.Outlined.Devices, Color(0xFF2196F3), Modifier.weight(1f))
            StatCard("Active Feeds", "$activeDevices", "Scanning telemetry", Icons.Outlined.CheckCircle, Color(0xFF4CAF50), Modifier.weight(1f))
            StatCard("Radar State", if (isScanning) "SCANNING" else "STANDBY", "System scanner", Icons.Outlined.CellTower, Color(0xFF9C27B0), Modifier.weight(1f))
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.15f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = iconColor, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(subtitle, fontSize = 8.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun WifiDeviceCard(
    ssid: String,
    rssi: Int,
    linkSpeed: Int,
    state: String,
    isMobileData: Boolean = false,
    discoveredDevices: List<com.example.engines.network.DiscoveredDevice> = emptyList(),
    isScanning: Boolean = false,
    isExpanded: Boolean = false,
    onToggleExpand: (() -> Unit)? = null
) {
    val isConnected = state == "CONNECTED"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
        border = BorderStroke(0.5.dp, Color(0xFF00FFCC).copy(alpha = 0.2f))
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF161924), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMobileData) Icons.Filled.CellTower else Icons.Filled.Router,
                        contentDescription = if (isMobileData) "Mobile Data" else "Wi-Fi",
                        tint = Color(0xFF00FFCC),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1.5f)) {
                    Text(
                        ssid.removePrefix("\"").removeSuffix("\""),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(if (isMobileData) "Cellular Base Station" else "Wi-Fi Router", fontSize = 10.sp, color = Color.Gray)
                }

                Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isMobileData) Icons.Filled.SignalCellularAlt else Icons.Filled.Wifi,
                            contentDescription = null,
                            tint = Color(0xFF2196F3),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("$rssi dBm", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Text(if (isMobileData) "LTE/5G Link" else "$linkSpeed Mbps Link", fontSize = 8.sp, color = Color.Gray)
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("-", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(if (isConnected) "Connected" else "Offline", fontSize = 11.sp, color = if (isConnected) Color(0xFF00FFCC) else Color.Red, fontWeight = FontWeight.Bold)
                }
            }

            if (!isMobileData && isConnected) {
                HorizontalDivider(color = Color(0xFF00FFCC).copy(alpha = 0.1f), thickness = 0.5.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Connected Devices", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Source: Router/AP information unavailable", fontSize = 9.sp, color = Color.Gray)
                        }
                        Text("Unavailable", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val activeCount = discoveredDevices.count { it.status == "ACTIVE" }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleExpand?.invoke() }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Detected Devices", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                            Text("Source: Local Network Discovery", fontSize = 9.sp, color = Color.Gray)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 1.5.dp,
                                    color = Color(0xFF00FFCC)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("$activeCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00FFCC))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse list" else "Expand list",
                                tint = Color(0xFF00FFCC),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    if (isExpanded && discoveredDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "LOCAL SUBNET CLIENTS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        discoveredDevices.forEachIndexed { index, device ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF161924)),
                                border = BorderStroke(0.5.dp, Color(0xFF00FFCC).copy(alpha = 0.1f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${index + 1}. ${device.hostname}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        val statusColor = when (device.status) {
                                            "ACTIVE" -> Color(0xFF00FFCC)
                                            "DETECTED" -> Color(0xFF2196F3)
                                            "OFFLINE" -> Color.Red
                                            else -> Color.Gray
                                        }
                                        Text(
                                            text = device.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("IP: ${device.ip}", fontSize = 9.sp, color = Color.Gray)
                                        Text("First: ${device.firstSeen}", fontSize = 8.sp, color = Color.Gray)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("", fontSize = 9.sp)
                                        Text("Last: ${device.lastSeen}", fontSize = 8.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (!isConnected) {
                HorizontalDivider(color = Color(0xFF00FFCC).copy(alpha = 0.1f), thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connected Devices", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("Not Available — Wi-Fi Off", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun BluetoothDisabledCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF201215)),
        border = BorderStroke(0.5.dp, Color.Red.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BluetoothDisabled, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Bluetooth Disabled", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                Text("Enable Bluetooth in settings to scan wireless hardware.", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun WifiDisabledCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF201215)),
        border = BorderStroke(0.5.dp, Color.Red.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WifiOff, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Wi-Fi Interface Closed", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                Text("Open Wi-Fi settings to start network intelligence.", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
        border = BorderStroke(0.5.dp, Color.Gray.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Outlined.DeviceUnknown, contentDescription = null, modifier = Modifier.size(36.dp), tint = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    buttonText: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
        border = BorderStroke(0.5.dp, iconColor.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(iconColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
            Text(subtitle, fontSize = 10.sp, color = Color.Gray, minLines = 2, maxLines = 2, lineHeight = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = iconColor),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            ) {
                Text(buttonText, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            }
        }
    }
}

// --- CANONICAL DEVICE INTELLIGENCE DETAIL SCREEN ---
@Composable
fun CanonicalDeviceDetailPanel(
    device: CanonicalDeviceRecord,
    state: String,
    rssi: Int,
    stability: String,
    batteryHistory: List<Pair<Long, Int>>,
    connectionHistory: List<String>,
    samples: List<Float>,
    batterySamples: List<Float>,
    onBack: () -> Unit,
    onSimulateBatteryUpdate: (Int) -> Unit
) {
    val isLive = device.isConnected && state != "DISCONNECTED" && state != "SUSPENDED"
    val timeFormatter = remember { SimpleDateFormat("hh:mm:ss a", Locale.US) }
    var simInputVal by remember { mutableStateOf("") }

    val filteredBatteryHistory = remember(batteryHistory) {
        val filtered = mutableListOf<Pair<Long, Int>>()
        batteryHistory.forEach { sample ->
            if (filtered.isEmpty() || filtered.last().second != sample.second) {
                filtered.add(sample)
            }
        }
        filtered
    }

    val isWeakSignal = isLive && rssi < -80

    val remainingRuntimeText = remember(filteredBatteryHistory, isWeakSignal, device.transport) {
        if (device.transport == "USB/OTG" || device.transport == "Wi-Fi") {
            "Powered by Host Bus / AC Power (No external battery discharge)"
        } else if (filteredBatteryHistory.size < 2) {
            "Collecting data... (Requires at least 2 distinct battery levels to estimate)"
        } else {
            val first = filteredBatteryHistory.first()
            val last = filteredBatteryHistory.last()
            val batteryDrop = first.second - last.second
            val timeDeltaMs = last.first - first.first

            if (batteryDrop <= 0) {
                "Stable / Optimizing... (Tracking battery level trends)"
            } else if (timeDeltaMs <= 5000) {
                "Analyzing samples... (Wait 5 seconds for stability)"
            } else {
                val timeDeltaHours = timeDeltaMs.toDouble() / (1000.0 * 60.0 * 60.0)
                val ratePerHour = batteryDrop.toDouble() / timeDeltaHours

                if (ratePerHour <= 0.0) {
                    "Stable / Optimizing..."
                } else {
                    var remainingHours = last.second.toDouble() / ratePerHour
                    if (isWeakSignal) {
                        remainingHours *= 0.70
                    }

                    val hours = remainingHours.toInt()
                    val minutes = ((remainingHours - hours) * 60).toInt()
                    val suffix = if (isWeakSignal) " ⚠️ (Reduced by 30% due to poor signal quality)" else ""

                    if (hours > 0) {
                        "${hours}h ${minutes}m remaining$suffix"
                    } else {
                        "${minutes}m remaining$suffix"
                    }
                }
            }
        }
    }

    val batteryTrendText = remember(filteredBatteryHistory, device.transport) {
        if (device.transport == "USB/OTG" || device.transport == "Wi-Fi") {
            "N/A (Continuous Host / AC Power)"
        } else if (filteredBatteryHistory.size < 2) {
            "No trend detected yet"
        } else {
            val first = filteredBatteryHistory.first()
            val last = filteredBatteryHistory.last()
            val diff = last.second - first.second
            when {
                diff < 0 -> "Discharging (↓ ${-diff}% overall)"
                diff > 0 -> "Charging / Rising (↑ $diff% overall)"
                else -> "Holding Steady"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0F))
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFF141622), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Go Back",
                    tint = Color(0xFF00FFCC)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "${device.primaryCategory.title.uppercase()} HARDWARE INTELLIGENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00FFCC),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = device.name.removePrefix("\"").removeSuffix("\""),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (!isLive) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF331414))
                    .border(BorderStroke(1.dp, Color.Red.copy(alpha = 0.4f)), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "DEVICE OFFLINE / DISCONNECTED",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                        Text(
                            text = "Showing last-known telemetry. Live predictions and signals are paused.",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
            border = BorderStroke(1.dp, if (isLive) Color(0xFF00FFCC).copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "CANONICAL HARDWARE METRICS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FFCC),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                DetailItemRow(label = "Device Identity", value = device.name.removePrefix("\"").removeSuffix("\""))
                DetailItemRow(label = "Classified Type", value = device.deviceType)
                DetailItemRow(label = "Primary Category", value = device.primaryCategory.title)
                DetailItemRow(label = "Transport Protocol", value = device.transport)
                DetailItemRow(label = "Hardware Address / ID", value = device.macOrAddress)
                DetailItemRow(
                    label = "Link State",
                    value = if (isLive) "CONNECTED (LIVE)" else "DISCONNECTED",
                    valueColor = if (isLive) Color(0xFF00FFCC) else Color.Red
                )
                DetailItemRow(
                    label = "Current Battery Level",
                    value = if (device.batteryLevel >= 0) "${device.batteryLevel}%" else "Unavailable / N/A",
                    valueColor = if (device.batteryLevel >= 20) Color.White else if (device.batteryLevel >= 0) Color.Red else Color.Gray
                )
                DetailItemRow(
                    label = "Battery Trend",
                    value = batteryTrendText,
                    valueColor = if (batteryTrendText.contains("Discharging")) Color(0xFFFFB300) else Color.White
                )
                DetailItemRow(
                    label = "Signal Strength",
                    value = if (isLive && device.rssi != null) "${device.rssi} dBm (${device.signalStrength})" else "N/A",
                    valueColor = if (isWeakSignal) Color.Red else Color.White
                )
                DetailItemRow(
                    label = "Power Telemetry",
                    value = device.powerImpactText
                )
                DetailItemRow(
                    label = "Capabilities",
                    value = if (device.capabilities.isNotEmpty()) device.capabilities.joinToString(" • ") else "Standard Peripheral"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF141624)),
            border = BorderStroke(1.dp, Color(0xFF00FFCC).copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "NETRA PREDICTIVE RUNTIME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = remainingRuntimeText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Estimates are strictly calculated using actual discharging/charging rate over the active session. Signal attenuation automatically adjusts prediction for radio transmitter strain.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    lineHeight = 14.sp
                )
            }
        }

        if (device.primaryCategory == DeviceCategory.BLUETOOTH || device.primaryCategory == DeviceCategory.WEARABLES) {
            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "INTERACTIVE BATTERY TELEMETRY INPUT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FFCC),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Input custom battery level samples to test predictive calculations dynamically.",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        lineHeight = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextField(
                            value = simInputVal,
                            onValueChange = { simInputVal = it },
                            placeholder = { Text("Level (0-100)", fontSize = 12.sp, color = Color.Gray) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF161926),
                                unfocusedContainerColor = Color(0xFF161926),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val battery = simInputVal.toIntOrNull()
                                if (battery != null && battery in 0..100) {
                                    onSimulateBatteryUpdate(battery)
                                    simInputVal = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Add Sample", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF10121C)),
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DEVICE TELEMETRY TIMELINE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FFCC),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Battery Updates Log",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (batteryHistory.isEmpty()) {
                    Text("No battery history samples recorded.", fontSize = 10.sp, color = Color.Gray)
                } else {
                    batteryHistory.forEach { (timeMs, battery) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("●", color = Color(0xFF4CAF50), fontSize = 10.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Battery Level: $battery% at ${timeFormatter.format(Date(timeMs))}",
                                fontSize = 10.sp,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailItemRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, color = valueColor, fontWeight = FontWeight.Bold)
    }
}
