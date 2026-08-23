package com.example.core

import android.content.Context
import com.example.service.BatteryState
import com.example.engines.*
import com.example.ai.*
import com.example.devices.*
import com.example.analytics.*
import com.example.reports.*
import com.example.cloud.*
import com.example.premium.*

/**
 * Netra Core Kernel - The Unified App Master Controller & Router
 * Made with ❤️ by Prayagi Ji
 * Coordinates all engines cleanly without allowing direct high-coupling.
 */
object NetraCoreKernel {

    // --- BATTERY CORE ROUTER ---
    fun getComfortIndex(state: BatteryState, context: Context): Int {
        return DeviceIntelligenceEngine.getDeviceComfortIndex(state, context)
    }

    fun getLifespanPrediction(healthPct: Int): Double {
        return BatteryPredictionEngine.predictAgingYears(healthPct)
    }

    fun getCoolingAdvice(state: BatteryState): List<String> {
        return ThermalEngine.getCoolingRecommendations(state)
    }

    fun getSaverTips(state: BatteryState, context: Context): List<String> {
        return BatterySaverEngine.suggestOptimizations(state, context)
    }

    // --- AI INTERVIEW LAYER ---
    fun conductDailyStateInterview(state: BatteryState, context: Context): InterviewDiagnosis {
        return NetraInterview.conductDailyInterview(state, context)
    }

    fun diagnoseCellWithDoctor(state: BatteryState): String {
        return NetraDoctor.diagnose(state)
    }

    // --- ACCESORIES LAYER ---
    fun getAccessoriesOverview(context: Context): String {
        return ConnectedDevicesEngine.getConnectedAccessoriesSummary(context)
    }

    // --- REPORTS GENERATOR ---
    fun getHealthCertificateText(state: BatteryState): String {
        return HealthCertificate.issueCertificate(state)
    }

    // --- REWARDS & TOKENS ---
    fun getAvailableRewardsTokens(context: Context): Int {
        return TokenEngine.getTokens(context)
    }
}
