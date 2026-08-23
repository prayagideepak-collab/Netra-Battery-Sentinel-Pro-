package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_consumption")
data class AppConsumptionEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long = 0L,
    val backgroundTimeMs: Long = 0L,
    val consumedMah: Float = 0f,
    val estimatedDrainRate: Float = 0f, // in mAh/h
    val drainRating: String = "Low", // "Extreme", "High", "Medium", "Low"
    val isRunning: Boolean = false,
    val lastActiveTime: Long = System.currentTimeMillis()
)
