package com.example.service

object GeminiClient {
    suspend fun getConnectedDevicesAnalysis(summaryText: String): String {
        return GeminiStudioEngine.generalGeminiTask(
            "Analyze these connected Bluetooth/peripheral devices for battery impact, protocol efficiency, and idle drain mitigation:\n$summaryText"
        )
    }

    suspend fun getAiAnalysis(prompt: String): String {
        return GeminiStudioEngine.fastLiteQuery(prompt)
    }

    suspend fun getBatteryRecommendations(
        percentage: Int = 0,
        temperature: Float = 0f,
        voltage: Any = 0,
        healthPct: Any = 0,
        healthGrade: String = "",
        isCharging: Boolean = false,
        chargingType: String = "",
        watt: Float = 0f,
        cycleCount: Int = 0,
        sessionsCount: Int = 0,
        abnormalStandbyDrain: Boolean = false,
        abnormalTempSpike: Boolean = false
    ): String {
        val prompt = "Provide 3 concise, highly actionable battery care tips for battery level $percentage%, temp $temperature°C, charging: $isCharging ($chargingType at $watt W), health: $healthPct% ($healthGrade)."
        return GeminiStudioEngine.fastLiteQuery(prompt)
    }

    suspend fun generateAiReport(
        reportType: Any? = null,
        percentage: Int = 0,
        temperature: Float = 0f,
        voltage: Any = 0,
        healthPct: Any = 0,
        healthGrade: String = "",
        isCharging: Boolean = false,
        chargingType: String = "",
        watt: Float = 0f,
        cycleCount: Int = 0,
        sessionsCount: Int = 0,
        abnormalStandbyDrain: Boolean = false,
        abnormalTempSpike: Boolean = false
    ): String {
        val prompt = "Generate a comprehensive battery intelligence audit report. Battery: $percentage%, Temp: $temperature°C, Voltage: $voltage mV, Charging: $isCharging ($chargingType, $watt W), Health: $healthPct% ($healthGrade), Cycles: $cycleCount, Abnormal Drain: $abnormalStandbyDrain."
        return GeminiStudioEngine.deepThinkingQuery(prompt)
    }

    suspend fun getSessionAnalysis(sessionSummary: Any?): String {
        return GeminiStudioEngine.fastLiteQuery("Analyze this battery session telemetry and provide electrical summary:\n$sessionSummary")
    }
}
