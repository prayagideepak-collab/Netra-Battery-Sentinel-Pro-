package com.example.engines.network

import androidx.compose.ui.graphics.Color

enum class ConnectionQuality(
    val label: String,
    val text: String,
    val colorHex: Long,
    val emoji: String,
    val colorName: String
) {
    STABLE("STABLE", "STABLE", 0xFF4CAF50, "🟢", "GREEN"),
    DEGRADED("DEGRADED", "DEGRADED", 0xFFFBC02D, "🟡", "YELLOW"),
    WEAK("WEAK", "WEAK", 0xFFFF9800, "🟠", "ORANGE"),
    DISCONNECTED("DISCONNECTED", "DISCONNECTED", 0xFFF44336, "🔴", "RED"),
    UNAVAILABLE("UNAVAILABLE", "UNAVAILABLE", 0xFF9E9E9E, "⚪", "GRAY")
}

object ConnectionQualityEngine {
    fun getWifiQuality(isConnected: Boolean, signalPercent: Int): ConnectionQuality {
        if (!isConnected) return ConnectionQuality.DISCONNECTED
        return when {
            signalPercent >= 70 -> ConnectionQuality.STABLE
            signalPercent >= 35 -> ConnectionQuality.DEGRADED
            else -> ConnectionQuality.WEAK
        }
    }

    fun getInternetQuality(isConnected: Boolean, isInternetAvailable: Boolean, speedMbps: Double, latencyMs: Int): ConnectionQuality {
        if (!isConnected || !isInternetAvailable) return ConnectionQuality.DISCONNECTED
        return when {
            speedMbps >= 25.0 && (latencyMs in 0..60) -> ConnectionQuality.STABLE
            speedMbps >= 5.0 && (latencyMs in 0..150) -> ConnectionQuality.DEGRADED
            else -> ConnectionQuality.WEAK
        }
    }

    fun getBluetoothQuality(isEnabled: Boolean, isConnected: Boolean, rssi: Int): ConnectionQuality {
        if (!isEnabled) return ConnectionQuality.UNAVAILABLE
        if (!isConnected) return ConnectionQuality.DISCONNECTED
        return when {
            rssi >= -65 -> ConnectionQuality.STABLE
            rssi >= -85 -> ConnectionQuality.DEGRADED
            else -> ConnectionQuality.WEAK
        }
    }
}
