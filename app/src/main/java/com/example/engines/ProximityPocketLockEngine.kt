package com.example.engines

import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.telephony.TelephonyManager
import android.util.Log
import com.example.engines.coordinator.Engine
import com.example.receiver.NetraDeviceAdminReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

data class ProximityLockStatus(
    val isEnabled: Boolean = false,
    val isSensorAvailable: Boolean = false,
    val isDeviceAdminActive: Boolean = false,
    val currentProximityState: String = "FAR",
    val currentLightLux: Float = -1f,
    val lastLockTimestamp: Long = 0L,
    val summary: String = "Proximity Pocket Lock Ready"
)

/**
 * ProximityPocketLockEngine (Capability B)
 * Implements sensor-driven automatic device locking when proximity is near, ambient light is dark,
 * device is unlocked, and user is not in an active call. Includes 1-second stabilization delay,
 * revalidation, and graceful fallback / UNSUPPORTED reporting when Device Admin is inactive.
 */
object ProximityPocketLockEngine : Engine, SensorEventListener {
    private const val TAG = "ProximityPocketLockEngine"
    override val name = "ProximityPocketLockEngine"
    override val priority = 12

    private val isInitialized = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private const val PREFS_NAME = "netra_proximity_lock_prefs"
    private const val KEY_ENABLED = "proximity_lock_enabled"

    private val _statusFlow = MutableStateFlow(ProximityLockStatus())
    val statusFlow: StateFlow<ProximityLockStatus> = _statusFlow.asStateFlow()

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var lightSensor: Sensor? = null

    private var latestProximityValue: Float = -1f
    private var latestLightValue: Float = -1f
    private var stabilizationJob: Job? = null
    @Volatile
    private var isListening: Boolean = false
    private var storedContext: Context? = null

    override fun initialize(context: Context) {
        if (isInitialized.getAndSet(true)) return
        storedContext = context.applicationContext
        Log.i(TAG, "Initializing ProximityPocketLockEngine...")

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        val hasProximity = proximitySensor != null
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
        val adminComponent = ComponentName(context, NetraDeviceAdminReceiver::class.java)
        val isAdminActive = try { dpm?.isAdminActive(adminComponent) == true } catch (e: Exception) { false }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ENABLED, false)

        _statusFlow.value = ProximityLockStatus(
            isEnabled = enabled,
            isSensorAvailable = hasProximity,
            isDeviceAdminActive = isAdminActive,
            summary = if (!hasProximity) "Sensor Unsupported" else if (!isAdminActive) "Device Admin Inactive (Lock Unsupported)" else if (enabled) "Active" else "Disabled"
        )

        if (enabled && hasProximity) {
            startListening(context)
        }
    }

    override fun shutdown() {
        Log.i(TAG, "Shutting down ProximityPocketLockEngine...")
        stopListening()
        isInitialized.set(false)
    }

    override fun getStatus(): String {
        val s = _statusFlow.value
        return "ProximityLock: Enabled=${s.isEnabled}, Available=${s.isSensorAvailable}, Admin=${s.isDeviceAdminActive}, State=${s.currentProximityState}"
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _statusFlow.value = _statusFlow.value.copy(isEnabled = enabled)

        if (enabled) {
            startListening(context)
        } else {
            stopListening()
        }
    }

    private fun startListening(context: Context) {
        if (isListening || proximitySensor == null) return
        try {
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            lightSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            isListening = true
            Log.i(TAG, "Proximity & Light sensor listeners registered successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register proximity/light sensors", e)
        }
    }

    private fun stopListening() {
        if (!isListening) return
        try {
            sensorManager?.unregisterListener(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister sensors", e)
        }
        isListening = false
        stabilizationJob?.cancel()
        stabilizationJob = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val context = storedContext ?: return

        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            latestProximityValue = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val isNear = latestProximityValue < maxRange && latestProximityValue < 5f
            val stateStr = if (isNear) "NEAR" else "FAR"

            _statusFlow.value = _statusFlow.value.copy(currentProximityState = stateStr)

            if (isNear) {
                // Proximity became NEAR -> initiate stabilization delay (~1 second)
                if (stabilizationJob == null || stabilizationJob?.isActive != true) {
                    Log.i(TAG, "Proximity detected NEAR. Starting 1-second stabilization window...")
                    stabilizationJob = scope.launch {
                        delay(1000L) // 1 second stabilization delay

                        // Revalidation checks:
                        // 1. Proximity still near
                        val stillNear = latestProximityValue < maxRange && latestProximityValue < 5f
                        // 2. Ambient light below threshold (< 10 lux, if light sensor available)
                        val isDark = latestLightValue < 0f || latestLightValue < 10.0f
                        // 3. Device currently unlocked (Keyguard not locked)
                        val km = context.getSystemService("keyguard") as? KeyguardManager
                        val isLocked = try { km?.isKeyguardLocked == true } catch (e: Exception) { false }
                        val isUnlocked = !isLocked
                        // 4. Not in active call
                        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                        val callState = try { tm?.callState ?: TelephonyManager.CALL_STATE_IDLE } catch (e: Exception) { TelephonyManager.CALL_STATE_IDLE }
                        val notInCall = callState == TelephonyManager.CALL_STATE_IDLE
                        // 5. Feature enabled & Device Admin active
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val enabled = prefs.getBoolean(KEY_ENABLED, false)

                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
                        val adminComponent = ComponentName(context, NetraDeviceAdminReceiver::class.java)
                        val isAdminActive = try { dpm?.isAdminActive(adminComponent) == true } catch (e: Exception) { false }

                        _statusFlow.value = _statusFlow.value.copy(isDeviceAdminActive = isAdminActive)

                        Log.i(TAG, "Proximity Revalidation: stillNear=$stillNear, isDark=$isDark (lux=$latestLightValue), isUnlocked=$isUnlocked, notInCall=$notInCall, enabled=$enabled, isAdminActive=$isAdminActive")

                        if (enabled && stillNear && isDark && isUnlocked && notInCall) {
                            if (isAdminActive) {
                                Log.i(TAG, "All proximity pocket lock conditions passed. Executing secure device lock...")
                                try {
                                    dpm?.lockNow()
                                    _statusFlow.value = _statusFlow.value.copy(
                                        lastLockTimestamp = System.currentTimeMillis(),
                                        summary = "Device locked successfully via Proximity Sensor"
                                    )
                                    com.example.util.DiagnosticLogger.logEvent(
                                        context, "PROXIMITY_POCKET_LOCKED",
                                        "Device Locked (Proximity Pocket)",
                                        "Proximity near and light dark verified. Device securely locked.",
                                        0, 0f, 0f, "ProximityPocketLockEngine"
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to execute lockNow()", e)
                                }
                            } else {
                                Log.w(TAG, "Proximity pocket lock UNSUPPORTED: Device Administrator permission not activated by user.")
                                _statusFlow.value = _statusFlow.value.copy(
                                    summary = "Lock UNSUPPORTED: Enable Device Admin in Settings"
                                )
                                com.example.util.DiagnosticLogger.logEvent(
                                    context, "PROXIMITY_LOCK_UNSUPPORTED",
                                    "Pocket Lock Unsupported (No Admin)",
                                    "Proximity near conditions met, but Device Admin is not active. Operation skipped safely.",
                                    0, 0f, 0f, "ProximityPocketLockEngine"
                                )
                            }
                        } else {
                            Log.i(TAG, "Proximity lock conditions not fully met after stabilization. Skipping lock.")
                        }
                    }
                }
            } else {
                // Proximity became FAR -> cancel pending stabilization
                if (stabilizationJob?.isActive == true) {
                    Log.i(TAG, "Proximity became FAR during stabilization window. Cancelling lock action.")
                    stabilizationJob?.cancel()
                    stabilizationJob = null
                }
            }
        } else if (event.sensor.type == Sensor.TYPE_LIGHT) {
            latestLightValue = event.values[0]
            _statusFlow.value = _statusFlow.value.copy(currentLightLux = latestLightValue)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
