package com.example.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UiSessionState(
    val version: Int = 1,
    val activeTab: Int = 0,
    val missionSection: Int = 0,
    val settingsScrollPosition: Int = 0,
    val monitorScrollPosition: Int = 0,
    val settingsSelectedCategory: String = "General",
    val permissionSearchQuery: String = "",
    val permissionSelectedFilter: String = "ALL",
    val intelligenceScoreCategory: String = "PERFORMANCE",
    val intelligenceActiveDialog: String = "",
    val notificationSearchQuery: String = "",
    val notificationSelectedProfile: String = "Default",
    val graphTimeRange: Int = 24,
    val lastStateSavedAt: Long = 0L
)

object UiSessionRepository {
    private const val PREFS_NAME = "netra_ui_session"
    private const val SCHEMA_VERSION = 1

    private val _sessionState = MutableStateFlow(UiSessionState())
    val sessionState: StateFlow<UiSessionState> = _sessionState.asStateFlow()

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        val savedVersion = prefs.getInt("version", 0)
        if (savedVersion != SCHEMA_VERSION && savedVersion != 0) {
            Log.w("UiSessionRepository", "Schema mismatch: $savedVersion vs $SCHEMA_VERSION. Resetting state.")
            prefs.edit().clear().apply()
        }

        val restored = UiSessionState(
            version = SCHEMA_VERSION,
            activeTab = prefs.getInt("activeTab", 0),
            missionSection = prefs.getInt("missionSection", 0),
            settingsScrollPosition = prefs.getInt("settingsScrollPosition", 0),
            monitorScrollPosition = prefs.getInt("monitorScrollPosition", 0),
            settingsSelectedCategory = prefs.getString("settingsSelectedCategory", "General") ?: "General",
            permissionSearchQuery = prefs.getString("permissionSearchQuery", "") ?: "",
            permissionSelectedFilter = prefs.getString("permissionSelectedFilter", "ALL") ?: "ALL",
            intelligenceScoreCategory = prefs.getString("intelligenceScoreCategory", "PERFORMANCE") ?: "PERFORMANCE",
            intelligenceActiveDialog = prefs.getString("intelligenceActiveDialog", "") ?: "",
            notificationSearchQuery = prefs.getString("notificationSearchQuery", "") ?: "",
            notificationSelectedProfile = prefs.getString("notificationSelectedProfile", "Default") ?: "Default",
            graphTimeRange = prefs.getInt("graphTimeRange", 24),
            lastStateSavedAt = prefs.getLong("lastStateSavedAt", 0L)
        )
        
        _sessionState.value = restored
        isInitialized = true
    }

    fun updateSession(context: Context, transform: (UiSessionState) -> UiSessionState) {
        _sessionState.update { current ->
            val updated = transform(current).copy(
                version = SCHEMA_VERSION,
                lastStateSavedAt = System.currentTimeMillis()
            )
            saveToDisk(context, updated)
            updated
        }
    }

    private fun saveToDisk(context: Context, state: UiSessionState) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("version", state.version)
            putInt("activeTab", state.activeTab)
            putInt("missionSection", state.missionSection)
            putInt("settingsScrollPosition", state.settingsScrollPosition)
            putInt("monitorScrollPosition", state.monitorScrollPosition)
            putString("settingsSelectedCategory", state.settingsSelectedCategory)
            putString("permissionSearchQuery", state.permissionSearchQuery)
            putString("permissionSelectedFilter", state.permissionSelectedFilter)
            putString("intelligenceScoreCategory", state.intelligenceScoreCategory)
            putString("intelligenceActiveDialog", state.intelligenceActiveDialog)
            putString("notificationSearchQuery", state.notificationSearchQuery)
            putString("notificationSelectedProfile", state.notificationSelectedProfile)
            putInt("graphTimeRange", state.graphTimeRange)
            putLong("lastStateSavedAt", state.lastStateSavedAt)
            apply()
        }
    }
}
