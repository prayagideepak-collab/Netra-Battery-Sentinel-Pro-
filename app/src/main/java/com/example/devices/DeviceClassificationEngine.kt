package com.example.devices

import android.bluetooth.BluetoothClass
import android.content.Context
import com.example.service.ConnectedBluetoothDevice

/**
 * Authoritative Device Classification Engine.
 * Implements strict, truthful classification BEFORE UI filtering.
 * Transport does not dictate primary category (e.g. Bluetooth-connected smartwatch -> WEARABLES).
 */
object DeviceClassificationEngine {

    fun classifyBluetoothDevice(
        btDevice: ConnectedBluetoothDevice,
        rawMajorClass: Int? = null,
        rawDeviceClass: Int? = null
    ): CanonicalDeviceRecord {
        val lowerName = btDevice.name.lowercase()

        // Authoritative classification logic
        val isWearableByMetadata = btDevice.deviceType.equals("Watch", ignoreCase = true) ||
                btDevice.deviceType.equals("Smart Watch", ignoreCase = true) ||
                btDevice.deviceType.equals("Smart Band", ignoreCase = true) ||
                btDevice.deviceType.equals("Wearable", ignoreCase = true) ||
                btDevice.profile.contains("Wearable", ignoreCase = true) ||
                rawMajorClass == BluetoothClass.Device.Major.WEARABLE

        val (category, deviceType, capabilities) = when {
            isWearableByMetadata -> {
                val type = if (lowerName.contains("band") || lowerName.contains("tracker") || lowerName.contains("fit")) {
                    "Fitness Band"
                } else {
                    "Smartwatch"
                }
                Triple(
                    DeviceCategory.WEARABLES,
                    type,
                    listOf("Wearable Telemetry", "GATT Profile", "Low Energy Sync")
                )
            }
            btDevice.deviceType.equals("Speaker", ignoreCase = true) || lowerName.contains("speaker") || lowerName.contains("rockerz") || lowerName.contains("soundbar") -> {
                Triple(
                    DeviceCategory.BLUETOOTH,
                    "Bluetooth Speaker",
                    listOf("A2DP Audio Sink", "AVRCP Media Control", "Wireless Audio")
                )
            }
            btDevice.deviceType.equals("Earbuds", ignoreCase = true) || lowerName.contains("buds") || lowerName.contains("airpod") || lowerName.contains("earphone") -> {
                Triple(
                    DeviceCategory.BLUETOOTH,
                    "Bluetooth Headset / Earbuds",
                    listOf("HFP Voice", "A2DP Stereo", "Low Latency Audio")
                )
            }
            btDevice.deviceType.equals("Headphones", ignoreCase = true) || lowerName.contains("headphone") || lowerName.contains("headset") -> {
                Triple(
                    DeviceCategory.BLUETOOTH,
                    "Bluetooth Headphones",
                    listOf("A2DP High-Res Audio", "Noise Cancellation Profile")
                )
            }
            lowerName.contains("desktop") || lowerName.contains("laptop") || lowerName.contains("pc") || rawMajorClass == BluetoothClass.Device.Major.COMPUTER -> {
                Triple(
                    DeviceCategory.BLUETOOTH,
                    "Bluetooth Device / PC",
                    listOf("PAN Network", "Data Transfer", "OBEX File Sharing")
                )
            }
            btDevice.deviceType.equals("Stylus", ignoreCase = true) || lowerName.contains("stylus") || lowerName.contains("pen") -> {
                Triple(
                    DeviceCategory.BLUETOOTH,
                    "Bluetooth Stylus / Pointer",
                    listOf("HID Digitizer", "Pressure Telemetry")
                )
            }
            else -> {
                Triple(
                    DeviceCategory.BLUETOOTH,
                    "Bluetooth Peripheral",
                    listOf("Standard Bluetooth Link", "Generic Transport")
                )
            }
        }

        val signalStrength = when {
            btDevice.connectionState != "LIVE" -> "N/A"
            btDevice.signalRssi >= -60 -> "Excellent"
            btDevice.signalRssi >= -75 -> "Strong"
            btDevice.signalRssi >= -88 -> "Moderate"
            else -> "Weak"
        }

        return CanonicalDeviceRecord(
            id = "bt_${btDevice.address.replace(":", "")}",
            name = btDevice.name.ifBlank { "Bluetooth Device" },
            primaryCategory = category,
            transport = "Bluetooth",
            connectionState = if (btDevice.connectionState == "LIVE") "Connected" else "Disconnected",
            isConnected = btDevice.connectionState == "LIVE",
            deviceType = deviceType,
            batteryLevel = btDevice.batteryLevel,
            isCharging = btDevice.isCharging,
            rssi = if (btDevice.connectionState == "LIVE" && btDevice.signalRssi != -999) btDevice.signalRssi else null,
            signalStrength = signalStrength,
            telemetrySource = "System Bluetooth Adapter",
            powerMa = null, // External device battery, not host OTG discharge
            powerWatts = null,
            powerImpactText = "Power Impact: Managed by Bluetooth LE Subsystem",
            firstObserved = btDevice.firstObservedConnectedAt,
            lastSeen = btDevice.lastSeenConnectedAt,
            disconnectedAt = btDevice.disconnectedAt,
            macOrAddress = btDevice.address,
            manufacturer = "Bluetooth SIG Registered",
            modelOrVendor = btDevice.address,
            capabilities = capabilities
        )
    }

    fun classifyWifiDevice(
        ssid: String,
        rssi: Int,
        linkSpeedMbps: Int,
        isConnected: Boolean,
        isInternetAvailable: Boolean,
        isMobileData: Boolean
    ): CanonicalDeviceRecord {
        val cleanName = ssid.removePrefix("\"").removeSuffix("\"")
        val deviceType = if (isMobileData) "Cellular Base Station" else "Wi-Fi Router / Access Point"
        val transport = if (isMobileData) "Mobile Data" else "Wi-Fi"
        
        val signalStrength = when {
            !isConnected -> "N/A"
            rssi >= -55 -> "Excellent"
            rssi >= -67 -> "Strong"
            rssi >= -80 -> "Moderate"
            else -> "Weak"
        }

        return CanonicalDeviceRecord(
            id = if (isMobileData) "net_cellular_cell" else "wifi_${cleanName.hashCode()}",
            name = if (cleanName.isNotBlank()) cleanName else if (isMobileData) "Mobile Network (LTE/5G)" else "Wi-Fi Network",
            primaryCategory = DeviceCategory.WIFI,
            transport = transport,
            connectionState = if (isConnected) "Connected" else "Disconnected",
            isConnected = isConnected,
            deviceType = deviceType,
            batteryLevel = -1, // Wi-Fi routers do not expose battery percentage
            isCharging = false,
            rssi = if (isConnected && rssi != -1) rssi else null,
            signalStrength = signalStrength,
            telemetrySource = "Android WifiManager / ConnectivityManager",
            powerMa = null,
            powerWatts = null,
            powerImpactText = "Power Impact: Wi-Fi Radio Transceiver",
            firstObserved = System.currentTimeMillis(),
            lastSeen = System.currentTimeMillis(),
            macOrAddress = if (isMobileData) "Cell Tower" else "Wi-Fi AP",
            manufacturer = "Network Access Node",
            modelOrVendor = transport,
            capabilities = listOf(
                if (isInternetAvailable) "Internet Gateway Active" else "Local Intranet Only",
                "$linkSpeedMbps Mbps Link Speed",
                "WPA2/WPA3 Protocol"
            ),
            linkSpeedMbps = linkSpeedMbps,
            isInternetAvailable = isInternetAvailable
        )
    }
}
