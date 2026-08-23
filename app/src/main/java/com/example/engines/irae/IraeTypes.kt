package com.example.engines.irae

enum class ReportCategory {
    DASHBOARD,
    BATTERY,
    CHARGING,
    THERMAL,
    HEALTH,
    RECOVERY,
    PERFORMANCE,
    EXPORT
}

data class AnalyticsInsight(
    val id: String,
    val title: String,
    val summary: String,
    val whyThisInsight: String,
    val supportingMetric: String,
    val category: ReportCategory,
    val isPositiveTrend: Boolean = true,
    val confidencePercent: Int = 96
)

data class GeneratedReportItem(
    val reportId: String,
    val title: String,
    val category: ReportCategory,
    val period: String, // "Daily", "Weekly", "Monthly", "Custom"
    val dataCompletenessPercent: Int = 100,
    val confidenceLevel: String = "HIGH (98%)",
    val keyFinding: String,
    val generatedTimestamp: Long = System.currentTimeMillis()
)

data class ComparativeAnalyticsSummary(
    val periodLabel: String, // "Today vs Yesterday", "Last 7 Days", "Last 30 Days"
    val batteryDrainDelta: String,
    val chargingEfficiencyDelta: String,
    val thermalStabilityDelta: String,
    val runtimeUptimePercent: Float = 99.98f
)

data class ReportExportRecord(
    val exportId: String,
    val format: String, // "TXT", "CSV", "PDF"
    val categoryName: String,
    val recordCount: Int,
    val fileSizeKb: Int,
    val status: String, // "COMPLETED", "IN_PROGRESS", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

data class ReportAuditLog(
    val id: String,
    val eventType: String, // "Report Generated", "Report Exported", "Report Deleted", "Export Completed"
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
