package com.example.devices

import java.io.Serializable

/**
 * Authoritative Canonical Device Record Model.
 * Represents a single physical or logical hardware device.
 * Enforces single canonical registration and truthful hardware telemetry.
 */
data class CanonicalDeviceRecord(
    val id: String,                         // Stable unique hardware identifier
    val name: String,                       // Human readable display name
    val primaryCategory: DeviceCategory,    // Authoritative primary category (ALL_DEVICES, BLUETOOTH, WIFI, WEARABLES, OTHER)
    val transport: String,                  // Connection transport: "Bluetooth", "Wi-Fi", "USB/OTG", "Cellular"
    val connectionState: String,            // "Connected", "Disconnected", "Saved", "Offline"
    val isConnected: Boolean,
    val deviceType: String,                 // "Bluetooth Speaker", "Smartwatch", "Wi-Fi Router", "USB Keyboard", etc.
    val batteryLevel: Int = -1,             // -1 if unavailable (Never fake 0%)
    val isCharging: Boolean = false,
    val rssi: Int? = null,                  // Signal in dBm, null if unavailable
    val signalStrength: String = "N/A",     // "Strong", "Moderate", "Weak", "N/A"
    val telemetrySource: String = "System API", // "System Bluetooth", "WifiManager", "UsbManager", etc.
    val powerMa: Int? = null,               // Observed host discharge or null
    val powerWatts: Float? = null,
    val powerImpactText: String = "Power Impact: Not directly measurable",
    val firstObserved: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis(),
    val disconnectedAt: Long = 0L,
    val macOrAddress: String = "",
    val manufacturer: String = "Not Available",
    val modelOrVendor: String = "Not Available",
    val capabilities: List<String> = emptyList(),
    val linkSpeedMbps: Int = 0,
    val ipAddress: String? = null,
    val isInternetAvailable: Boolean = true,
    val usbVendorId: Int = 0,
    val usbProductId: Int = 0,
    val usbClass: Int = 0
) : Serializable
