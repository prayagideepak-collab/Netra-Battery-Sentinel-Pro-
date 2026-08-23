package com.example.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Calendar

/**
 * BatteryHistoryEntity
 * Stores granular historical battery snapshots (timestamps, levels, charging states,
 * and electrical parameters) to enable advanced charging pattern analysis and ML insights.
 */
@Entity(
    tableName = "battery_history",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["isCharging"]),
        Index(value = ["hourOfDay"]),
        Index(value = ["dayOfWeek"])
    ]
)
data class BatteryHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val batteryLevel: Int,                     // 0 - 100 percentage
    val isCharging: Boolean = false,           // true if charging
    val chargingType: String = "NONE",          // "AC", "USB", "WIRELESS", "NONE", etc.
    val temperature: Float = 0.0f,             // Temperature in Celsius
    val voltageMv: Int = 0,                    // Millivolts
    val currentNowMa: Int = 0,                 // Current flow in mA (negative for discharge)
    val batteryHealth: String = "GOOD",        // "GOOD", "OVERHEAT", "DEAD", etc.
    val batteryStatus: String = "DISCHARGING", // "CHARGING", "DISCHARGING", "FULL", "NOT_CHARGING"
    val hourOfDay: Int = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY), // 0-23
    val dayOfWeek: Int = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.DAY_OF_WEEK)   // 1 (Sunday) - 7 (Saturday)
)
