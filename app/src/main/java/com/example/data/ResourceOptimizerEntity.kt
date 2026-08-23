package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resource_optimizations")
data class ResourceOptimizerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val cpuLoad: Double,
    val ramUsage: Long,
    val optimizationApplied: String,
    val beforeState: String,
    val afterState: String,
    val result: String
)
