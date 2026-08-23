package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sensorDataStore by preferencesDataStore(name = "sensor_configurations_prefs")

data class SensorConfigState(
    val magneticBaselineOffset: Float = 0.0f,
    val magneticInterferenceThreshold: Float = 50.0f,
    val pocketDetectionEnabled: Boolean = true,
    val pocketLightThreshold: Float = 5.0f,
    val isSensorCalibrated: Boolean = false,
    val lastCalibrationTimestamp: Long = 0L
)

class SensorDataStoreManager(private val context: Context) {

    private object PreferencesKeys {
        val MAGNETIC_BASELINE_OFFSET = floatPreferencesKey("magnetic_baseline_offset")
        val MAGNETIC_INTERFERENCE_THRESHOLD = floatPreferencesKey("magnetic_interference_threshold")
        val POCKET_DETECTION_ENABLED = booleanPreferencesKey("pocket_detection_enabled")
        val POCKET_LIGHT_THRESHOLD = floatPreferencesKey("pocket_light_threshold")
        val IS_SENSOR_CALIBRATED = booleanPreferencesKey("is_sensor_calibrated")
        val LAST_CALIBRATION_TIMESTAMP = longPreferencesKey("last_calibration_timestamp")
    }

    val sensorConfigFlow: Flow<SensorConfigState> = context.sensorDataStore.data
        .map { preferences ->
            SensorConfigState(
                magneticBaselineOffset = preferences[PreferencesKeys.MAGNETIC_BASELINE_OFFSET] ?: 0.0f,
                magneticInterferenceThreshold = preferences[PreferencesKeys.MAGNETIC_INTERFERENCE_THRESHOLD] ?: 50.0f,
                pocketDetectionEnabled = preferences[PreferencesKeys.POCKET_DETECTION_ENABLED] ?: true,
                pocketLightThreshold = preferences[PreferencesKeys.POCKET_LIGHT_THRESHOLD] ?: 5.0f,
                isSensorCalibrated = preferences[PreferencesKeys.IS_SENSOR_CALIBRATED] ?: false,
                lastCalibrationTimestamp = preferences[PreferencesKeys.LAST_CALIBRATION_TIMESTAMP] ?: 0L
            )
        }

    suspend fun updateMagneticBaselineOffset(offset: Float) {
        context.sensorDataStore.edit { preferences ->
            preferences[PreferencesKeys.MAGNETIC_BASELINE_OFFSET] = offset
        }
    }

    suspend fun updateMagneticInterferenceThreshold(threshold: Float) {
        context.sensorDataStore.edit { preferences ->
            preferences[PreferencesKeys.MAGNETIC_INTERFERENCE_THRESHOLD] = threshold
        }
    }

    suspend fun updatePocketDetectionEnabled(enabled: Boolean) {
        context.sensorDataStore.edit { preferences ->
            preferences[PreferencesKeys.POCKET_DETECTION_ENABLED] = enabled
        }
    }

    suspend fun updatePocketLightThreshold(threshold: Float) {
        context.sensorDataStore.edit { preferences ->
            preferences[PreferencesKeys.POCKET_LIGHT_THRESHOLD] = threshold
        }
    }

    suspend fun saveCalibrationData(isCalibrated: Boolean, baselineOffset: Float, timestamp: Long) {
        context.sensorDataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_SENSOR_CALIBRATED] = isCalibrated
            preferences[PreferencesKeys.MAGNETIC_BASELINE_OFFSET] = baselineOffset
            preferences[PreferencesKeys.LAST_CALIBRATION_TIMESTAMP] = timestamp
        }
    }
}
