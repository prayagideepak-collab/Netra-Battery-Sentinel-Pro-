package com.example.engines.coordinator

import android.content.Context
import android.util.Log
import com.example.engines.festival.FestivalContextEngine
import com.example.engines.weather.EnvironmentalContextEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class RefreshStatus {
    SUCCESS,
    REFRESHING,
    FAILED,
    STALE,
    UNAVAILABLE
}

data class GlobalRefreshState(
    val status: RefreshStatus = RefreshStatus.SUCCESS,
    val lastRefreshTimestamp: Long = System.currentTimeMillis(),
    val locationSuccess: Boolean = true,
    val weatherSuccess: Boolean = true,
    val festivalSuccess: Boolean = true,
    val batterySuccess: Boolean = true,
    val appConsumptionSuccess: Boolean = true,
    val networkUsageSuccess: Boolean = true,
    val thermalSuccess: Boolean = true,
    val failureReason: String? = null
)

object GlobalDataRefreshCoordinator : Engine {
    private const val TAG = "GlobalDataRefreshCoordinator"
    override val name: String = "GlobalDataRefreshCoordinator"
    override val priority: Int = 90

    private val _refreshStateFlow = MutableStateFlow(GlobalRefreshState())
    val refreshStateFlow: StateFlow<GlobalRefreshState> = _refreshStateFlow.asStateFlow()

    override fun initialize(context: Context) {
        Log.i(TAG, "GlobalDataRefreshCoordinator initialized.")
    }

    override fun shutdown() {
        Log.i(TAG, "GlobalDataRefreshCoordinator shutdown.")
    }

    override fun getStatus(): String {
        val st = _refreshStateFlow.value
        return "RefreshStatus: ${st.status} | LastRefresh: ${st.lastRefreshTimestamp}"
    }

    suspend fun refreshAll(context: Context): GlobalRefreshState {
        _refreshStateFlow.update { it.copy(status = RefreshStatus.REFRESHING, failureReason = null) }
        val now = System.currentTimeMillis()

        var locOk = true
        var weatherOk = true
        var festivalOk = true
        var batteryOk = true
        var appOk = true
        var netOk = true
        var thermalOk = true

        try {
            EnvironmentalContextEngine.evaluateSyncEligibility(context)
        } catch (e: Exception) {
            Log.e(TAG, "Location/Weather refresh error", e)
            weatherOk = false
            locOk = false
        }

        try {
            FestivalContextEngine.evaluateTodayFestival()
        } catch (e: Exception) {
            Log.e(TAG, "Festival refresh error", e)
            festivalOk = false
        }

        try {
            // Reconcile battery and thermal telemetry state safely
            val dataset = EnvironmentalContextEngine.datasetFlow.value
            if (dataset.currentTemp <= 0f) {
                thermalOk = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Battery/Thermal refresh error", e)
            batteryOk = false
            thermalOk = false
        }

        try {
            // Reconcile app and network usage state safely
            val activeFest = FestivalContextEngine.currentFestival.value
            if (activeFest != null && activeFest.festivalName.isBlank()) {
                appOk = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "App/Network consumption refresh error", e)
            appOk = false
            netOk = false
        }

        val overallStatus = if (locOk && weatherOk && festivalOk && batteryOk) RefreshStatus.SUCCESS else RefreshStatus.STALE

        val newState = GlobalRefreshState(
            status = overallStatus,
            lastRefreshTimestamp = now,
            locationSuccess = locOk,
            weatherSuccess = weatherOk,
            festivalSuccess = festivalOk,
            batterySuccess = batteryOk,
            appConsumptionSuccess = appOk,
            networkUsageSuccess = netOk,
            thermalSuccess = thermalOk
        )
        _refreshStateFlow.update { newState }
        Log.i(TAG, "Global data refresh completed with status: $overallStatus")
        return newState
    }
}
