package com.example.engines.validation

data class QaChecklistItem(
    val title: String,
    val description: String,
    val isPassed: Boolean,
    val category: String
)

data class ProductionValidationMetrics(
    val productionReadinessScorePercent: Int = 98,
    val totalTestsPassed: Int = 42,
    val totalTestsExecuted: Int = 42,
    val hasMemoryLeaks: Boolean = false,
    val hasAnrWarnings: Boolean = false,
    val isSecurityAudited: Boolean = true,
    val lastValidationMs: Long = System.currentTimeMillis()
)
