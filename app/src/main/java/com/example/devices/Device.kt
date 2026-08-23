package com.example.devices

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "devices")
data class Device(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String, // "WIFI" or "BLUETOOTH"
    val macAddress: String, // Unique identifier
    val ipAddress: String? = null,
    val vendor: String? = null,
    var isConnected: Boolean,
    var firstSeen: Long,
    var lastSeen: Long,
    var connectedTime: Long = 0,
    var disconnectTime: Long = 0,
    var totalConnectionCount: Int = 0,
    var totalConnectedDuration: Long = 0,
    var batteryLevel: Int? = null,
    var isCharging: Boolean = false,
    var rssi: Int? = null
)
