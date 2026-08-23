package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_alerts")
data class BatteryAlert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batteryLevel: Int,
    val isBelow: Boolean, // true if below level, false if exactly level (e.g. 100)
    val voicePrompt: String,
    val enabled: Boolean = true
)
