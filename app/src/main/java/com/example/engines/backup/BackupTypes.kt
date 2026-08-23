package com.example.engines.backup

data class BackupManifest(
    val backupId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val isEncrypted: Boolean = true,
    val checksumSha256: String,
    val recordCount: Int
)

data class BackupEngineState(
    val lastBackupTimestampMs: Long = 0L,
    val lastRestoreTimestampMs: Long = 0L,
    val isBackupInProgress: Boolean = false,
    val totalBackupsCount: Int = 1,
    val statusMessage: String = "Ready for backup/restore"
)
