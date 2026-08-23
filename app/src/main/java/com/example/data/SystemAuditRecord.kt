package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_audit_records")
data class SystemAuditRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val totalServicesChecked: Int,
    val healthyServices: Int,
    val restartedServices: Int,
    val failedServices: Int,
    val unsupportedComponents: Int,
    val recoveryActions: String, // Dynamic list of actions taken, e.g. "Restarted BluetoothDeviceMonitor"
    val healthScore: Int // Core health score from 0 to 100
)
