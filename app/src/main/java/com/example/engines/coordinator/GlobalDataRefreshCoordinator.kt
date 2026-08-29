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
        val uniState = UniversalSyncCoordinator.refreshAll(context)
        val tasks = uniState.tasks

        val locOk = tasks["LOCATION"]?.state in listOf(SyncState.SUCCESS, SyncState.UNAVAILABLE, SyncState.SKIPPED_WITH_REASON)
        val weatherOk = tasks["WEATHER"]?.state in listOf(SyncState.SUCCESS, SyncState.UNAVAILABLE, SyncState.SKIPPED_WITH_REASON)
        val batteryOk = tasks["BATTERY_TELEMETRY"]?.state in listOf(SyncState.SUCCESS, SyncState.UNAVAILABLE)
        val netOk = tasks["NETWORK_STATE"]?.state in listOf(SyncState.SUCCESS, SyncState.UNAVAILABLE, SyncState.SKIPPED_WITH_REASON)
        val appOk = (tasks["APP_CONSUMPTION"]?.state in listOf(SyncState.SUCCESS, SyncState.UNAVAILABLE, SyncState.SKIPPED_WITH_REASON)) ||
                    (tasks["APPLICATION_STATE"]?.state in listOf(SyncState.SUCCESS, SyncState.UNAVAILABLE, SyncState.SKIPPED_WITH_REASON))

        val overallStatus = if (uniState.overallPercentage >= 50) RefreshStatus.SUCCESS else RefreshStatus.STALE

        val newState = GlobalRefreshState(
            status = overallStatus,
            lastRefreshTimestamp = uniState.lastRefreshTimestamp,
            locationSuccess = locOk,
            weatherSuccess = weatherOk,
            festivalSuccess = true,
            batterySuccess = batteryOk,
            appConsumptionSuccess = appOk,
            networkUsageSuccess = netOk,
            thermalSuccess = true
        )
        _refreshStateFlow.update { newState }
        Log.i(TAG, "Global data refresh delegated successfully. Overall Pct: ${uniState.overallPercentage}%")
        return newState
    }
}
