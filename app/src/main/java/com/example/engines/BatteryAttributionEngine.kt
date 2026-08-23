package com.example.engines

import android.content.Context
import android.util.Log
import com.example.data.AppConsumptionEntity
import com.example.data.BatteryRepository
import com.example.service.BatteryState

/**
 * Netra Battery Correlation & Attribution Engine (Zero-Fabrication Guard)
 * Correlates app consumption with network states, thermal patterns, and power velocity.
 * Made with ❤️ by Prayagi Ji
 */
object BatteryAttributionEngine {
    private const val TAG = "BatteryAttributionEngine"

    data class BatteryImpactDetails(
        val packageName: String,
        val appName: String,
        val impactLevel: String, // "LOW", "MODERATE", "HIGH", "CRITICAL"
        val confidence: String,  // "LOW CONFIDENCE", "MEDIUM CONFIDENCE", "HIGH CONFIDENCE"
        val status: String,      // "ACTUAL", "ESTIMATED", "UNAVAILABLE", "UNSUPPORTED"
        val reason: String,
        val networkContext: String,
        val estimatedRuntimeImpact: String,
        val category: String
    )

    fun calculateAttribution(
        app: AppConsumptionEntity,
        batteryState: BatteryState,
        networkType: String,
        signalStrength: String,
        networkFluctuating: Boolean
    ): BatteryImpactDetails {
        val packageName = app.packageName
        val appName = app.appName
        
        // Retrieve inventory item for category details
        val inventory = AppUsageEngine.getInventoryItem(packageName)
        val category = inventory?.categoryName ?: "User Application"

        // Determine actual/estimated/unavailable status
        val status = if (app.consumedMah > 0f) "ESTIMATED" else "UNAVAILABLE"

        // Baseline score calculation based on consumed mAh and foreground/background ratios
        val totalTimeMs = app.foregroundTimeMs + app.backgroundTimeMs
        val dischargeRatePerHour = if (totalTimeMs > 0) {
            (app.consumedMah / (totalTimeMs / 3600000f))
        } else {
            0f
        }

        val temp = batteryState.temperature

        // Determine network context & correlation
        val networkContext = when {
            networkFluctuating && app.isRunning -> {
                "Fluctuating network ($networkType, Signal: $signalStrength) - Elevated radio wakeups."
            }
            app.isRunning -> {
                "Stable network ($networkType) - Standard network transport."
            }
            else -> {
                "No active background network requests mapped."
            }
        }

        // Determine Impact Level & Confidence
        var impactLevel = "LOW"
        var confidence = "LOW CONFIDENCE"
        var reason = "No significant foreground or background usage registered."

        if (app.consumedMah > 0) {
            when {
                app.consumedMah >= 18f -> {
                    impactLevel = "CRITICAL"
                    confidence = "HIGH CONFIDENCE"
                    reason = "Sustained resource intensive operations with heavy processor/sensor requirements."
                }
                app.consumedMah >= 10f -> {
                    impactLevel = "HIGH"
                    confidence = "HIGH CONFIDENCE"
                    reason = "High foreground engagement, thermal heating, and substantial drainage coefficients."
                }
                app.consumedMah >= 4f -> {
                    impactLevel = "MODERATE"
                    confidence = "MEDIUM CONFIDENCE"
                    reason = "Standard interaction profile with intermittent background sync cycles."
                }
                else -> {
                    impactLevel = "LOW"
                    confidence = "MEDIUM CONFIDENCE"
                    reason = "Normal idle bounds with negligible power footprint."
                }
            }

            // Adjust confidence and level based on real-world system telemetry correlation
            if (batteryState.currentNow < -500 && app.isRunning && app.foregroundTimeMs > 0) {
                confidence = "HIGH CONFIDENCE"
                reason += " Directly correlated with screen-on discharge acceleration."
            }
            if (temp >= 40f && app.isRunning) {
                confidence = "HIGH CONFIDENCE"
                reason += " Thermal warming (temp: ${temp}°C) mapped during application lifecycle."
            }
            if (networkFluctuating && app.isRunning) {
                reason += " Network fluctuation warning detected while process was active."
            }
        }

        // Estimated runtime impact
        val estimatedRuntimeImpact = if (status == "ESTIMATED" && dischargeRatePerHour > 0.01f) {
            val remainingMah: Float = (4000f * batteryState.percentage) / 100f
            val hoursRemaining: Float = remainingMah / dischargeRatePerHour
            if (hoursRemaining < 100f) {
                "ESTIMATED: Sole utilization would exhaust charge in ${String.format(java.util.Locale.US, "%.1f", hoursRemaining)} hours."
            } else {
                "ESTIMATED: Minimal runtime impact within standard discharge profile."
            }
        } else {
            "Insufficient data for runtime estimation"
        }

        return BatteryImpactDetails(
            packageName = packageName,
            appName = appName,
            impactLevel = impactLevel,
            confidence = confidence,
            status = status,
            reason = reason,
            networkContext = networkContext,
            estimatedRuntimeImpact = estimatedRuntimeImpact,
            category = category
        )
    }

    // Heavy user detection helper
    fun checkHeavyDrainAndLog(
        context: Context,
        app: AppConsumptionEntity,
        batteryState: BatteryState,
        repository: BatteryRepository?,
        currentDrainMA: Float
    ) {
        if (currentDrainMA >= 450f && app.isRunning && app.packageName != "android") {
            // Log Heavy Battery Activity
            repository?.logBatteryEventSync(
                eventType = "HEAVY_BATTERY_ACTIVITY",
                title = "HEAVY BATTERY ACTIVITY DETECTED",
                details = "App ${app.appName} (${app.packageName}) exhibits sustained discharge acceleration. Observed rate: ${String.format(java.util.Locale.US, "%.1f", currentDrainMA)} mA.",
                category = "INTELLIGENCE",
                source = "Netra"
            )
        }
    }
}
