package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_version_table")
data class AppVersionEntity(
    @PrimaryKey
    val id: Int = 1,
    val versionCode: Int = 312,
    val versionName: String = "3.5.2-authoritative-telemetry-compliance",
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
    val changeDescription: String = "Defect Fix: Final Compliance Audit, Battery History Unification, 100% TTS Safety, Accurate Timestamps & No-Destructive-Migration"
)
