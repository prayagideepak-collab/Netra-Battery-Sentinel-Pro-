package com.example.service

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.engines.network.AuthoritativeNetworkLogger
import com.example.engines.network.ConnectionQuality
import com.example.engines.network.ConnectionQualityEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BluetoothConnectionState {
    UNKNOWN,
    DISCOVERED,
    CONNECTED,
    TELEMETRY_ACTIVE,
    DISCONNECTED,
    OFFLINE
}

data class TrackedBluetoothDevice(
    val name: String,
    val address: String,
    val batteryLevel: Int, // 0-100 or -1 if UNAVAILABLE
    val deviceType: String, // "Watch", "Earbuds", "Headphones", "Speaker", "Stylus", "Other"
    val isCharging: Boolean,
    val profile: String,
    val state: BluetoothConnectionState,
    val firstObservedConnectedAt: Long,
    val lastSeenConnectedAt: Long,
    val disconnectedAt: Long,
    val signalRssi: Int, // Legitimate RSSI or -999 if unavailable
    val batteryCapability: String, // "SUPPORTED", "UNSUPPORTED", "UNAVAILABLE"
    val rssiCapability: String // "SUPPORTED", "UNSUPPORTED", "UNAVAILABLE"
)

class BluetoothStateRepository(private val context: Context, private val eventLogger: Any?) {
    private val TAG = "BluetoothStateRepo"

    private val _bluetoothAdapterState = MutableStateFlow("INITIALIZING")
    val bluetoothAdapterState: StateFlow<String> = _bluetoothAdapterState.asStateFlow()

    private val _bluetoothEnabled = MutableStateFlow(false)
    val bluetoothEnabled: StateFlow<Boolean> = _bluetoothEnabled.asStateFlow()

    private val _permissionsState = MutableStateFlow("NOT_REQUESTED")
    val permissionsState: StateFlow<String> = _permissionsState.asStateFlow()

    private val _scanState = MutableStateFlow("IDLE")
    val scanState: StateFlow<String> = _scanState.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<TrackedBluetoothDevice>>(emptyList())
    val connectedDevices: StateFlow<List<TrackedBluetoothDevice>> = _connectedDevices.asStateFlow()

    private val _previouslyObservedDevices = MutableStateFlow<List<TrackedBluetoothDevice>>(emptyList())
    val previouslyObservedDevices: StateFlow<List<TrackedBluetoothDevice>> = _previouslyObservedDevices.asStateFlow()

    private val _offlineDevices = MutableStateFlow<List<TrackedBluetoothDevice>>(emptyList())
    val offlineDevices: StateFlow<List<TrackedBluetoothDevice>> = _offlineDevices.asStateFlow()

    private val _unsupportedDevices = MutableStateFlow<List<TrackedBluetoothDevice>>(emptyList())
    val unsupportedDevices: StateFlow<List<TrackedBluetoothDevice>> = _unsupportedDevices.asStateFlow()

    private val _telemetryCapabilities = MutableStateFlow<Map<String, String>>(emptyMap())
    val telemetryCapabilities: StateFlow<Map<String, String>> = _telemetryCapabilities.asStateFlow()

    private val _lastUpdate = MutableStateFlow(System.currentTimeMillis())
    val lastUpdate: StateFlow<Long> = _lastUpdate.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState.asStateFlow()

    private val deviceMap = mutableMapOf<String, TrackedBluetoothDevice>()
    private val observedHistoryMap = mutableMapOf<String, TrackedBluetoothDevice>()
    private val lastDeviceQualityMap = mutableMapOf<String, ConnectionQuality>()

    init {
        reconcileBluetoothState()
    }

    fun reconcileBluetoothState() {
        try {
            val appContext = context.applicationContext
            val hasPerm = checkPermissions(appContext)
            _permissionsState.value = if (hasPerm) "GRANTED" else "PERMISSION_REQUIRED"

            val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter

            if (adapter == null) {
                _bluetoothAdapterState.value = "UNSUPPORTED"
                _bluetoothEnabled.value = false
                return
            }

            val isEnabled = adapter.isEnabled
            _bluetoothEnabled.value = isEnabled
            _bluetoothAdapterState.value = if (isEnabled) "BLUETOOTH_ON" else "BLUETOOTH_OFF"
            _lastUpdate.value = System.currentTimeMillis()

            AuthoritativeNetworkLogger.onBluetoothRadioStateChanged(appContext, isEnabled)

            if (!isEnabled || !hasPerm) {
                // Handle Bluetooth OFF or Permission Required state
                val now = System.currentTimeMillis()
                deviceMap.values.forEach { dev ->
                    if (dev.state != BluetoothConnectionState.OFFLINE) {
                        val offline = dev.copy(
                            state = BluetoothConnectionState.OFFLINE,
                            disconnectedAt = if (dev.disconnectedAt == 0L) now else dev.disconnectedAt
                        )
                        deviceMap[addressKey(dev)] = offline
                        observedHistoryMap[dev.address] = offline
                        AuthoritativeNetworkLogger.onBluetoothDeviceDisconnected(appContext, dev.name, dev.address)
                    }
                }
                _connectedDevices.value = emptyList()
                _offlineDevices.value = observedHistoryMap.values.filter { it.state == BluetoothConnectionState.OFFLINE }
                return
            }

            // Perform active connection query using profiles (Connected != Paired)
            val activeDeviceList = BluetoothDeviceMonitor.getConnectedBluetoothDevices(appContext)
            val activeAddresses = activeDeviceList.map { it.address }.toSet()
            val now = System.currentTimeMillis()

            // Update or add active connected devices
            val currentActive = mutableListOf<TrackedBluetoothDevice>()
            for (devInfo in activeDeviceList) {
                val existing = deviceMap[devInfo.address] ?: observedHistoryMap[devInfo.address]
                val batteryCap = if (devInfo.batteryLevel >= 0) "SUPPORTED" else "UNSUPPORTED"
                val rssiCap = "SUPPORTED"

                val tracked = TrackedBluetoothDevice(
                    name = devInfo.name,
                    address = devInfo.address,
                    batteryLevel = devInfo.batteryLevel,
                    deviceType = devInfo.deviceType,
                    isCharging = devInfo.isCharging,
                    profile = devInfo.profile,
                    state = BluetoothConnectionState.TELEMETRY_ACTIVE,
                    firstObservedConnectedAt = existing?.firstObservedConnectedAt ?: now,
                    lastSeenConnectedAt = now,
                    disconnectedAt = 0L,
                    signalRssi = devInfo.signalRssi,
                    batteryCapability = batteryCap,
                    rssiCapability = rssiCap
                )
                deviceMap[devInfo.address] = tracked
                observedHistoryMap[devInfo.address] = tracked
                currentActive.add(tracked)

                if (existing == null || existing.state != BluetoothConnectionState.TELEMETRY_ACTIVE) {
                    AuthoritativeNetworkLogger.onBluetoothDeviceConnected(appContext, devInfo.name, devInfo.address, devInfo.deviceType)
                }

                if (devInfo.batteryLevel >= 0) {
                    AuthoritativeNetworkLogger.onBluetoothBatteryInformation(appContext, devInfo.name, devInfo.address, devInfo.batteryLevel)
                }
            }

            // Detect disconnections for devices previously known as connected
            deviceMap.forEach { (address, dev) ->
                if (!activeAddresses.contains(address) && (dev.state == BluetoothConnectionState.CONNECTED || dev.state == BluetoothConnectionState.TELEMETRY_ACTIVE)) {
                    val offlineDev = dev.copy(
                        state = BluetoothConnectionState.OFFLINE,
                        disconnectedAt = now
                    )
                    deviceMap[address] = offlineDev
                    observedHistoryMap[address] = offlineDev
                    AuthoritativeNetworkLogger.onBluetoothDeviceDisconnected(appContext, dev.name, address)
                }
            }

            // Clean up deviceMap to only keep active ones or recently offline
            deviceMap.entries.removeIf { (_, dev) -> dev.state == BluetoothConnectionState.OFFLINE }

            _connectedDevices.value = currentActive
            _previouslyObservedDevices.value = observedHistoryMap.values.toList()
            _offlineDevices.value = observedHistoryMap.values.filter { it.state == BluetoothConnectionState.OFFLINE }
            
            val caps = mutableMapOf<String, String>()
            caps["BluetoothAdapter"] = "SUPPORTED"
            caps["ProfileA2DP"] = "SUPPORTED"
            caps["ProfileHeadset"] = "SUPPORTED"
            caps["BatteryTelemetry"] = if (currentActive.any { it.batteryCapability == "SUPPORTED" }) "SUPPORTED" else "UNSUPPORTED"
            _telemetryCapabilities.value = caps
            _errorState.value = null

        } catch (e: Exception) {
            Log.e(TAG, "Error reconciling Bluetooth state", e)
            _errorState.value = e.message
            logEvent("TELEMETRY_ERROR", "Bluetooth Reconciliation Error", e.message ?: "Unknown error", "ERROR")
        }
    }

    private fun checkPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun addressKey(dev: TrackedBluetoothDevice): String = dev.address

    private fun logEvent(eventType: String, title: String, details: String, result: String) {
        try {
            if (eventLogger != null) {
                val method = eventLogger.javaClass.getMethod("logBatteryEventSync", String::class.java, String::class.java, String::class.java, String::class.java, String::class.java)
                method.invoke(eventLogger, eventType, title, details, "BLUETOOTH", "BluetoothStateRepository")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event via reflection", e)
        }
    }
}
