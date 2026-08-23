package com.example.engines.network

import android.content.Context
import android.util.Log
import com.example.BatteryApplication
import com.example.data.BatteryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Netra Battery Sentinel Pro
 * Authoritative Network Event Logging Specification (2026-08-16)
 *
 * Strict Event-Driven Architecture:
 * REAL NETWORK EVENT -> EVENT VALIDATION -> DUPLICATE CHECK -> EVENT LOGGER -> PERSISTENT LOG
 *
 * NO periodic network logging.
 * NO numerical fluctuation logging (speed, RSSI, latency, throughput).
 * NO stability fluctuation logging.
 */
object AuthoritativeNetworkLogger {
    private const val TAG = "AuthNetLogger"

    // Authoritative State Machine Trackers
    @Volatile private var isInitialized = false

    @Volatile private var lastWifiRadioState: Boolean? = null
    @Volatile private var lastWifiConnectionState: Boolean? = null
    @Volatile private var lastWifiSsid: String = ""

    @Volatile private var lastActiveTransport: String? = null
    @Volatile private var lastInternetAccessState: Boolean? = null
    @Volatile private var lastAirplaneModeState: Boolean? = null

    @Volatile private var lastBluetoothRadioState: Boolean? = null
    private val connectedBluetoothDevices = ConcurrentHashMap<String, String>() // MAC -> Device Name
    private val bluetoothBatteryLevels = ConcurrentHashMap<String, Int>() // MAC -> Battery %

    // High Data Intensity tracking
    @Volatile private var lastHighDataLoggedAt = 0L

    /**
     * Initializes state baselines without emitting false transition events on cold startup.
     */
    @Synchronized
    fun initializeBaseline(
        wifiRadio: Boolean?,
        wifiConnected: Boolean?,
        ssid: String?,
        transport: String?,
        internetAccess: Boolean?,
        airplaneMode: Boolean?,
        bluetoothRadio: Boolean?
    ) {
        if (!isInitialized) {
            lastWifiRadioState = wifiRadio
            lastWifiConnectionState = wifiConnected
            lastWifiSsid = ssid?.removePrefix("\"")?.removeSuffix("\"") ?: ""
            lastActiveTransport = transport
            lastInternetAccessState = internetAccess
            lastAirplaneModeState = airplaneMode
            lastBluetoothRadioState = bluetoothRadio
            isInitialized = true
            Log.d(TAG, "Authoritative Network Logger baseline initialized. Ready for discrete event transitions.")
        }
    }

    // ============================================================
    // 1. WI-FI RADIO STATE TRANSITIONS (WIFI_ON, WIFI_OFF)
    // ============================================================
    @Synchronized
    fun onWifiRadioStateChanged(context: Context, isEnabled: Boolean, timestamp: Long = System.currentTimeMillis()) {
        if (lastWifiRadioState == isEnabled) return
        val wasNull = (lastWifiRadioState == null)
        lastWifiRadioState = isEnabled

        if (wasNull) return // Do not fabricate event on cold launch

        val eventType = if (isEnabled) "WIFI_ON" else "WIFI_OFF"
        val title = if (isEnabled) "Wi-Fi Radio Enabled" else "Wi-Fi Radio Disabled"
        val details = """
            Event: $eventType
            Radio State: ${if (isEnabled) "ON" else "OFF"}
            Timestamp: ${formatTimestamp(timestamp)}
        """.trimIndent()

        logEvent(context, eventType, title, details, "NETWORK", "WifiRadioManager")
    }

    // ============================================================
    // 2. WI-FI CONNECTION TRANSITIONS (WIFI_CONNECTED, WIFI_DISCONNECTED)
    // ============================================================
    @Synchronized
    fun onWifiConnectionStateChanged(
        context: Context,
        isConnected: Boolean,
        ssid: String?,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val cleanSsid = ssid?.removePrefix("\"")?.removeSuffix("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        val currentSsid = cleanSsid ?: lastWifiSsid

        if (lastWifiConnectionState == isConnected && (isConnected && currentSsid == lastWifiSsid)) {
            return
        }

        val wasNull = (lastWifiConnectionState == null)
        val prevConnected = lastWifiConnectionState == true
        lastWifiConnectionState = isConnected
        if (cleanSsid != null) {
            lastWifiSsid = cleanSsid
        }

        if (wasNull) return // Do not fabricate event on startup

        if (isConnected) {
            val networkName = if (lastWifiSsid.isNotBlank()) lastWifiSsid else "Wi-Fi Network"
            val details = """
                Event: WIFI_CONNECTED
                Network Name: $networkName
                Connection: CONNECTED
                Timestamp: ${formatTimestamp(timestamp)}
            """.trimIndent()

            logEvent(context, "WIFI_CONNECTED", "Wi-Fi Connected", details, "NETWORK", "WifiManager")
        } else if (prevConnected) {
            val networkName = if (lastWifiSsid.isNotBlank()) lastWifiSsid else "Wi-Fi Network"
            val details = """
                Event: WIFI_DISCONNECTED
                Network Name: $networkName
                Connection: DISCONNECTED
                Timestamp: ${formatTimestamp(timestamp)}
            """.trimIndent()

            logEvent(context, "WIFI_DISCONNECTED", "Wi-Fi Disconnected", details, "NETWORK", "WifiManager")
        }
    }

    // ============================================================
    // 3. ACTIVE INTERNET TRANSPORT SWITCH (WIFI <-> CELLULAR)
    // ============================================================
    @Synchronized
    fun onActiveTransportChanged(
        context: Context,
        newTransport: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val prev = lastActiveTransport
        if (prev == newTransport) return
        lastActiveTransport = newTransport

        if (prev == null) return // Cold start baseline

        // Only log actual transport switch between WIFI and CELLULAR
        if ((prev == "WIFI" && newTransport == "CELLULAR") || (prev == "CELLULAR" && newTransport == "WIFI")) {
            val details = """
                Event: ACTIVE_INTERNET_TRANSPORT_CHANGED
                Previous: $prev
                Current: $newTransport
                Timestamp: ${formatTimestamp(timestamp)}
            """.trimIndent()

            logEvent(
                context,
                "ACTIVE_INTERNET_TRANSPORT_CHANGED",
                "Internet Transport Switched ($prev → $newTransport)",
                details,
                "NETWORK",
                "ConnectivityManager"
            )
        }
    }

    // ============================================================
    // 4. INTERNET ACCESS STATE (LOST / RESTORED)
    // ============================================================
    @Synchronized
    fun onInternetAccessStateChanged(
        context: Context,
        isAvailable: Boolean,
        activeTransport: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (lastInternetAccessState == isAvailable) return
        val wasNull = (lastInternetAccessState == null)
        lastInternetAccessState = isAvailable

        if (wasNull) return // Cold startup baseline

        if (!isAvailable) {
            // Internet Access Lost (Mobile Data lost or Complete Internet lost)
            val details = """
                Event: INTERNET_ACCESS_LOST
                Previous Transport: $activeTransport
                Internet Access: LOST
                Timestamp: ${formatTimestamp(timestamp)}
            """.trimIndent()

            logEvent(
                context,
                "INTERNET_ACCESS_LOST",
                "Internet Access Lost",
                details,
                "NETWORK",
                "NetworkMonitor"
            )
        } else {
            // Internet Access Available / Restored
            val details = """
                Event: INTERNET_ACCESS_RESTORED
                Transport: $activeTransport
                Internet Access: AVAILABLE
                Timestamp: ${formatTimestamp(timestamp)}
            """.trimIndent()

            logEvent(
                context,
                "INTERNET_ACCESS_RESTORED",
                "Internet Access Restored",
                details,
                "NETWORK",
                "NetworkMonitor"
            )
        }
    }

    // ============================================================
    // 5. AIRPLANE MODE TRANSITIONS (AIRPLANE_MODE_ON, AIRPLANE_MODE_OFF)
    // ============================================================
    @Synchronized
    fun onAirplaneModeChanged(
        context: Context,
        isAirplaneModeOn: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (lastAirplaneModeState == isAirplaneModeOn) return
        val wasNull = (lastAirplaneModeState == null)
        lastAirplaneModeState = isAirplaneModeOn

        if (wasNull) return // Cold start

        val eventType = if (isAirplaneModeOn) "AIRPLANE_MODE_ON" else "AIRPLANE_MODE_OFF"
        val title = if (isAirplaneModeOn) "Airplane Mode Enabled" else "Airplane Mode Disabled"
        val details = """
            Event: $eventType
            State: ${if (isAirplaneModeOn) "ON" else "OFF"}
            Timestamp: ${formatTimestamp(timestamp)}
        """.trimIndent()

        logEvent(context, eventType, title, details, "SYSTEM", "RadioController")
    }

    // ============================================================
    // 6. BLUETOOTH RADIO TRANSITIONS (BLUETOOTH_ON, BLUETOOTH_OFF)
    // ============================================================
    @Synchronized
    fun onBluetoothRadioStateChanged(
        context: Context,
        isEnabled: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (lastBluetoothRadioState == isEnabled) return
        val wasNull = (lastBluetoothRadioState == null)
        lastBluetoothRadioState = isEnabled

        if (wasNull) return

        val eventType = if (isEnabled) "BLUETOOTH_ON" else "BLUETOOTH_OFF"
        val title = if (isEnabled) "Bluetooth Radio Enabled" else "Bluetooth Radio Disabled"
        val details = """
            Event: $eventType
            State: ${if (isEnabled) "ON" else "OFF"}
            Timestamp: ${formatTimestamp(timestamp)}
        """.trimIndent()

        logEvent(context, eventType, title, details, "BLUETOOTH", "BluetoothAdapter")
    }

    // ============================================================
    // 7. BLUETOOTH DEVICE CONNECTED & DISCONNECTED
    // ============================================================
    @Synchronized
    fun onBluetoothDeviceConnected(
        context: Context,
        name: String,
        address: String,
        deviceType: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (connectedBluetoothDevices.containsKey(address)) {
            return // Already tracked as connected
        }
        val cleanName = if (name.isNotBlank()) name else "Bluetooth Device"
        connectedBluetoothDevices[address] = cleanName

        val details = """
            Event: BLUETOOTH_DEVICE_CONNECTED
            Device Name: $cleanName
            Device Type: $deviceType
            Connection: CONNECTED
            Timestamp: ${formatTimestamp(timestamp)}
        """.trimIndent()

        logEvent(context, "BLUETOOTH_DEVICE_CONNECTED", "Bluetooth Device Connected: $cleanName", details, "BLUETOOTH", "BluetoothMonitor")
    }

    @Synchronized
    fun onBluetoothDeviceDisconnected(
        context: Context,
        name: String,
        address: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val lastKnownName = connectedBluetoothDevices.remove(address) ?: name.takeIf { it.isNotBlank() } ?: "Bluetooth Device"
        bluetoothBatteryLevels.remove(address)

        val details = """
            Event: BLUETOOTH_DEVICE_DISCONNECTED
            Device Name: $lastKnownName
            Connection: DISCONNECTED
            Timestamp: ${formatTimestamp(timestamp)}
        """.trimIndent()

        logEvent(context, "BLUETOOTH_DEVICE_DISCONNECTED", "Bluetooth Device Disconnected: $lastKnownName", details, "BLUETOOTH", "BluetoothMonitor")
    }

    // ============================================================
    // 8. BLUETOOTH BATTERY INFORMATION (MEANINGFUL UPDATES ONLY)
    // ============================================================
    @Synchronized
    fun onBluetoothBatteryInformation(
        context: Context,
        name: String,
        address: String,
        batteryPercentage: Int,
        timestamp: Long = System.currentTimeMillis()
    ) {
        if (batteryPercentage < 0 || batteryPercentage > 100) return

        val prevBattery = bluetoothBatteryLevels[address]
        // Anti-spam rule: Only log if first reading, or significant delta >= 10%, or low battery threshold <= 20%
        val isSignificant = prevBattery == null ||
                abs(batteryPercentage - prevBattery) >= 10 ||
                (batteryPercentage <= 20 && prevBattery > 20)

        if (!isSignificant) return

        bluetoothBatteryLevels[address] = batteryPercentage
        val cleanName = name.takeIf { it.isNotBlank() } ?: connectedBluetoothDevices[address] ?: "Bluetooth Device"

        val details = """
            Event: BLUETOOTH_BATTERY_INFORMATION
            Device: $cleanName
            Battery: $batteryPercentage%
            Timestamp: ${formatTimestamp(timestamp)}
        """.trimIndent()

        logEvent(context, "BLUETOOTH_BATTERY_INFORMATION", "Bluetooth Battery: $cleanName ($batteryPercentage%)", details, "BLUETOOTH", "BluetoothMonitor")
    }

    // ============================================================
    // 9. HIGH DATA INTENSITY DETECTION (SUSTAINED THRESHOLD ONLY)
    // ============================================================
    @Synchronized
    fun onHighDataIntensityDetected(
        context: Context,
        packageName: String?,
        applicationLabel: String?,
        measuredUsageMb: Double,
        measurementWindowSec: Long,
        transport: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val now = System.currentTimeMillis()
        // Rate limit high data intensity logs to at most once per 60 seconds
        if (now - lastHighDataLoggedAt < 60000L) return
        lastHighDataLoggedAt = now

        val appName = applicationLabel ?: packageName ?: "Foreground Process"
        val details = """
            Event: HIGH_DATA_INTENSITY_DETECTED
            Application: $appName
            Usage: ${String.format(Locale.US, "%.1f", measuredUsageMb)} MB
            Time Window: ${measurementWindowSec}s
            Transport: $transport
            Timestamp: ${formatTimestamp(timestamp)}
        """.trimIndent()

        logEvent(
            context,
            "HIGH_DATA_INTENSITY_DETECTED",
            "High Data Intensity Detected: $appName",
            details,
            "NETWORK",
            "DataIntensityEngine"
        )
    }

    // ============================================================
    // CORE EVENT DISPATCH
    // ============================================================
    private fun logEvent(
        context: Context,
        eventType: String,
        title: String,
        details: String,
        category: String,
        source: String
    ) {
        try {
            val repo: BatteryRepository? = (context.applicationContext as? BatteryApplication)?.repository
            if (repo != null) {
                repo.logBatteryEventSync(
                    eventType = eventType,
                    title = title,
                    details = details,
                    category = category,
                    source = source
                )
                Log.i(TAG, "Authoritative Network Event Logged: $eventType - $title")
            } else {
                Log.w(TAG, "BatteryRepository unavailable for event $eventType")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed logging authoritative network event $eventType", e)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
    }

    // Expose internal state for testing and diagnostics
    fun getTrackedState(): Map<String, Any?> = mapOf(
        "wifiRadio" to lastWifiRadioState,
        "wifiConnected" to lastWifiConnectionState,
        "wifiSsid" to lastWifiSsid,
        "activeTransport" to lastActiveTransport,
        "internetAccess" to lastInternetAccessState,
        "airplaneMode" to lastAirplaneModeState,
        "bluetoothRadio" to lastBluetoothRadioState,
        "connectedBtDevicesCount" to connectedBluetoothDevices.size
    )

    fun resetForTesting() {
        isInitialized = false
        lastWifiRadioState = null
        lastWifiConnectionState = null
        lastWifiSsid = ""
        lastActiveTransport = null
        lastInternetAccessState = null
        lastAirplaneModeState = null
        lastBluetoothRadioState = null
        connectedBluetoothDevices.clear()
        bluetoothBatteryLevels.clear()
        lastHighDataLoggedAt = 0L
    }
}
