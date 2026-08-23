package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_status")
data class HealthStatusEntity(
    @PrimaryKey val moduleName: String,
    val status: String, // Alive, Unresponsive, etc.
    val memoryUsageMb: Long,
    val threadHealth: String,
    val timestamp: Long = System.currentTimeMillis()
)
