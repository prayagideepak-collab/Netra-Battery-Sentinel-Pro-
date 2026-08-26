package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_consumption")
data class AppConsumptionEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val uid: Int = 0,
    val foregroundTimeMs: Long = 0L,
    val backgroundTimeMs: Long = 0L,
    val consumedMah: Float = 0f,
    val estimatedDrainRate: Float = 0f, // in mAh/h
    val drainRating: String = "UNAVAILABLE", // "Extreme", "High", "Medium", "Low", "UNAVAILABLE"
    val isRunning: Boolean = false,
    val lastActiveTime: Long = 0L,
    val mobileRxBytes: Long = 0L,
    val mobileTxBytes: Long = 0L,
    val wifiRxBytes: Long = 0L,
    val wifiTxBytes: Long = 0L,
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L,
    val totalNetworkBytes: Long = 0L,
    val networkStatsAvailable: Boolean = false,
    val batteryAttributionAvailable: Boolean = false,
    val activityState: String = "Inactive" // "Running", "Background", "Inactive"
)
