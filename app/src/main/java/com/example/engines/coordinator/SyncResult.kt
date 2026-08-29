package com.example.engines.coordinator

sealed class SyncResult {
    data class Running(val progressValue: Int = 0) : SyncResult()
    data class Success(val details: String? = null, val timestamp: Long = System.currentTimeMillis()) : SyncResult()
    data class EmptySuccess(val details: String? = null, val timestamp: Long = System.currentTimeMillis()) : SyncResult()
    data class Failed(val errorReason: String, val timestamp: Long = System.currentTimeMillis()) : SyncResult()
    data class Unavailable(val reason: String, val timestamp: Long = System.currentTimeMillis()) : SyncResult()
    data class Skipped(val reason: String, val timestamp: Long = System.currentTimeMillis()) : SyncResult()

    fun toSyncState(): SyncState = when (this) {
        is Running -> SyncState.RUNNING
        is Success, is EmptySuccess -> SyncState.SUCCESS
        is Failed -> SyncState.FAILED
        is Unavailable -> SyncState.UNAVAILABLE
        is Skipped -> SyncState.SKIPPED_WITH_REASON
    }

    fun getReasonOrDetails(): String? = when (this) {
        is Running -> null
        is Success -> details
        is EmptySuccess -> details ?: "Successfully synchronized with 0 records found"
        is Failed -> errorReason
        is Unavailable -> reason
        is Skipped -> reason
    }

    fun resolveProgress(): Int = when (this) {
        is Running -> progressValue
        is Success, is EmptySuccess -> 100
        is Failed, is Unavailable, is Skipped -> 0
    }
}
