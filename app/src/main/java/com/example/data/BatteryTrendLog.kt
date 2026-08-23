package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_trend_logs")
data class BatteryTrendLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val dischargeRate: Float, // rate of discharge in %/hour
    val chargeCycleDuration: Long, // duration of the last completed charge cycle in seconds
    val batteryLevel: Int,
    val temperature: Float,
    val voltage: Int,
    val currentNow: Int = 0
)
