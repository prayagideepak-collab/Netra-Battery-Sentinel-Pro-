package com.example.service

import java.util.Locale

data class SafetyIndexInfo(
    val score: Int,
    val label: String, // "Excellent", "Good", "Attention Needed", "Critical"
    val description: String,
    val category: String // "Safe", "Warm", "Risk", "Critical"
)

data class LifespanInfo(
    val expectedRemaining: String,
    val predictionText: String
)

object BatteryIntelligence {

    /**
     * Calculates a composite Battery Safety Index (0 to 100)
     */
    fun calculateSafetyIndex(state: BatteryState): SafetyIndexInfo {
        var score = 100

        // 1. Temperature penalties (Max 50 points deduction)
        val temp = state.temperature
        when {
            temp >= 45f -> score -= 50
            temp >= 42f -> score -= 30
            temp >= 38f -> score -= 15
            temp >= 35f -> score -= 5
        }

        // 2. Voltage penalties (Max 20 points deduction)
        // Normal lithium-ion cell voltage should be between 3.4V (3400mV) and 4.4V (4400mV)
        val volt = state.voltage
        if (volt > 0) {
            when {
                volt >= 4450 -> score -= 20
                volt >= 4350 -> score -= 8
                volt <= 3350 -> score -= 15
                volt <= 3500 -> score -= 5
            }
        }

        // 3. Health Wear penalties (Max 20 points deduction)
        val healthPct = state.healthPercentage
        when {
            healthPct < 80 -> score -= 20
            healthPct < 85 -> score -= 12
            healthPct < 90 -> score -= 6
            healthPct < 95 -> score -= 2
        }

        // 4. Extreme current penalties (Max 15 points deduction)
        val current = state.currentNow
        if (state.isCharging) {
            if (current > 4500) { // Over-current charging
                score -= 15
            } else if (current > 3000) {
                score -= 8
            }
        } else {
            if (current < -1500) { // Heavy system drain (high gaming/stress load)
                score -= 10
            } else if (current < -800) {
                score -= 4
            }
        }

        val finalScore = score.coerceIn(0, 100)

        val (label, category, desc) = when {
            finalScore >= 90 -> Triple("Excellent", "Safe", "Your battery is operating under pristine, ultra-safe conditions.")
            finalScore >= 75 -> Triple("Good", "Warm", "Battery is warm but well within safe operational guidelines.")
            finalScore >= 50 -> Triple("Attention Needed", "Risk", "Slight temperature/voltage elevation. Avoid heavy tasks right now.")
            else -> Triple("Critical Alert", "Critical", "Critical thermal or voltage strain detected! Unplug charger and let it cool down immediately.")
        }

        return SafetyIndexInfo(finalScore, label, desc, category)
    }

    /**
     * Determines Battery Health grade, e.g. A+, A, B, C, D
     */
    fun getHealthGrade(healthPct: Int): String {
        return when {
            healthPct >= 96 -> "A+"
            healthPct >= 92 -> "A"
            healthPct >= 88 -> "B+"
            healthPct >= 84 -> "B"
            healthPct >= 80 -> "C"
            else -> "D (Replace)"
        }
    }

    /**
     * Determines battery condition text
     */
    fun getHealthCondition(healthPct: Int): String {
        return when {
            healthPct >= 95 -> "Excellent"
            healthPct >= 90 -> "Good"
            healthPct >= 85 -> "Fair"
            healthPct >= 80 -> "Needs Attention"
            else -> "Degraded (Replacement Recommended)"
        }
    }

    /**
     * Predicts expected lifespan remaining based on current battery health wear
     */
    fun predictLifespan(healthPct: Int): LifespanInfo {
        val remainingYears: Int
        val remainingMonths: Int

        // Base lifespan calculations assuming 100% is approx 4 years remaining under proper care
        // and standard battery replacement is recommended at 80% (approx 20% wear)
        when {
            healthPct >= 98 -> {
                remainingYears = 3
                remainingMonths = 10
            }
            healthPct >= 95 -> {
                remainingYears = 2
                remainingMonths = 11
            }
            healthPct >= 90 -> {
                remainingYears = 1
                remainingMonths = 9
            }
            healthPct >= 85 -> {
                remainingYears = 0
                remainingMonths = 10
            }
            healthPct >= 81 -> {
                remainingYears = 0
                remainingMonths = 4
            }
            else -> {
                remainingYears = 0
                remainingMonths = 0
            }
        }

        val expectedRemaining = if (remainingYears == 0 && remainingMonths == 0) {
            "Replacement recommended"
        } else if (remainingYears == 0) {
            "$remainingMonths months"
        } else if (remainingMonths == 0) {
            "$remainingYears years"
        } else {
            "$remainingYears years $remainingMonths months"
        }

        val predictionText = if (healthPct >= 90) {
            "Estimated battery lifespan remaining under current usage habits. Outstanding care!"
        } else if (healthPct >= 80) {
            "Slight capacity degradation detected. Lifespan is stable but starting to contract."
        } else {
            "Battery has reached end-of-life status. A replacement is recommended to restore factory screen-on time."
        }

        return LifespanInfo(expectedRemaining, predictionText)
    }

    /**
     * Analyzes charger quality based on wattage, charging speed, and current stability
     */
    fun getChargerQuality(state: BatteryState): String {
        if (!state.isCharging) return "Not Charging"
        
        val watt = state.powerWatt
        return when {
            watt >= 22f -> "Excellent (Ultra-Fast 25W+)"
            watt >= 15f -> "Excellent (Fast Charger 15W+)"
            watt >= 10f -> "Good (Standard Quick Charger)"
            watt >= 4.5f -> "Fair (Normal USB Speed)"
            watt > 0f -> "Slow Charger (Low Current)"
            else -> "Unstable Charger / Port Connection"
        }
    }

    /**
     * Detects probable heat source based on state, charging current and discharge pull
     */
    fun getHeatSource(state: BatteryState): String {
        val temp = state.temperature
        if (temp < 36.5f) return "System Cool / Stable Ambient"

        if (state.isCharging) {
            return if (state.currentNow > 2500) {
                "Intense Fast Charging Thermal Dissipation"
            } else {
                "Charging & Active Display Ambient Absorption"
            }
        } else {
            val currentDrain = -state.currentNow
            return when {
                currentDrain > 800 -> "Heavy Gaming / GPU Rendering Load"
                currentDrain > 450 -> "High CPU Computation / Camera Recording"
                currentDrain > 250 -> "Active Display / Media Streaming"
                else -> "High Ambient Thermal Environment"
            }
        }
    }
}
