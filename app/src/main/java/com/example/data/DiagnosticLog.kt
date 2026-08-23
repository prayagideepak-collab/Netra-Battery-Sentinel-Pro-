package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "diagnostic_logs")
data class DiagnosticLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventType: String, // Crash, Recovery, Warning, etc.
    val moduleName: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
