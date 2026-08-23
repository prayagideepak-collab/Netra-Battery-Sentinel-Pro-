package com.example.engines.ieee

enum class ExportSubSection {
    QUICK_EXPORT,
    ADVANCED_EXPORT,
    EXPORT_HISTORY,
    EVIDENCE_PACKAGE,
    SHARE_CENTER,
    DATA_VERIFICATION
}

data class PrivacyFilterOptions(
    val hideDeviceId: Boolean = true,
    val hideBluetoothNames: Boolean = true,
    val hideApproxLocation: Boolean = true,
    val hideUserNotes: Boolean = false
)

data class AdvancedExportFilter(
    val startDateMs: Long = System.currentTimeMillis() - 7 * 86400_000L,
    val endDateMs: Long = System.currentTimeMillis(),
    val category: String = "ALL",
    val module: String = "ALL",
    val severity: String = "ALL",
    val reportType: String = "FULL_TELEMETRY"
)

data class EvidencePackageItem(
    val packageId: String,
    val title: String,
    val includedModules: List<String>, // e.g. ["History", "Logs", "Reports", "Diagnostics", "Health", "Runtime"]
    val totalRecords: Int,
    val packageSizeKb: Int,
    val createdTimestamp: Long = System.currentTimeMillis(),
    val checksumSha256: String = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    val verificationStatus: String = "VERIFIED_VALID"
)

data class DataVerificationCertificate(
    val certificateId: String,
    val integrityStatus: String = "VERIFIED", // "VERIFIED", "CORRUPTED", "PARTIAL"
    val generatedDate: String,
    val checkedRecordsCount: Int,
    val missingRecordsCount: Int = 0,
    val duplicateRecordsCount: Int = 0,
    val checksumResult: String = "PASSED (SHA-256 Valid)"
)

data class ExportHistoryItem(
    val exportId: String,
    val exportName: String,
    val format: String, // "TXT", "CSV", "PDF"
    val exportType: String, // "QUICK", "ADVANCED", "EVIDENCE_PACKAGE"
    val fileSizeKb: Int,
    val recordCount: Int,
    val durationMs: Long,
    val status: String, // "COMPLETED", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

data class ExportAuditRecord(
    val id: String,
    val eventType: String, // "Export Started", "Export Completed", "Export Failed", "Share Completed", "Verification Failed"
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
