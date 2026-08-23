package com.example.devices

import java.io.Serializable

data class NetraConnectedDevice(
    val id: String,
    val name: String,
    val deviceType: String, // "Smartphone", "Smart Watch", "Earbuds", "Speaker", "Smart Band", "Tablet", "Vehicle", "Other"
    val isWifi: Boolean,
    val manufacturer: String = "Not Available",
    val model: String = "Not Available",
    val batteryLevel: Int, // 0 to 100, -1 if unavailable
    val isCharging: Boolean = false,
    val batteryHealth: String = "Not Available", // e.g. "95%" or "Not Available"
    val batteryTemperature: String = "Not Available", // e.g. "32°C" or "Not Available"
    val connectionStatus: String = "Connected", // "Connected", "Disconnected", "Saved"
    val signalStrength: String = "Good", // e.g. "Excellent", "Good", "Fair", "Weak", or "-65 dBm"
    val firmwareVersion: String = "Not Available",
    val lastConnectedTime: Long = System.currentTimeMillis(),
    val disconnectTime: Long = 0L,
    
    // Wi-Fi Specific Parameters
    val ipAddress: String = "Not Available",
    val macAddress: String,
    val storageUsage: String = "Not Available", // e.g. "45GB / 128GB"
    val memoryUsage: String = "Not Available"   // e.g. "4.2GB / 8GB"
) : Serializable
