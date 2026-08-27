package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_tasks")
data class SyncTaskEntity(
    @PrimaryKey
    val taskId: String,
    val displayName: String,
    val category: String,
    val state: String, // PENDING, RUNNING, SUCCESS, FAILED, UNAVAILABLE, SKIPPED_WITH_REASON
    val startTimestamp: Long,
    val completionTimestamp: Long,
    val errorReason: String?,
    val progress: Int,
    val isApplicable: Boolean,
    val lastSuccessfulTimestamp: Long
)
