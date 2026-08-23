package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "root_cause_logs")
data class RootCauseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val moduleName: String,
    val failureType: String,
    val rootCause: String,
    val threadDump: String,
    val exception: String,
    val memorySnapshot: String,
    val cpuSnapshot: String,
    val recommendedRecovery: String,
    val recoveryExecuted: String,
    val recoveryResult: String
)
