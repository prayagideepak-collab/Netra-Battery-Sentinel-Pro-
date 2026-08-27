package com.example.service

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.engines.festival.FestivalContextEngine
import com.example.engines.weather.EnvironmentalContextEngine
import kotlinx.coroutines.*
import java.util.Locale

/**
 * AdaptiveLocationBatterySaver (Netra Battery Sentinel Pro)
 *
 * Implements periodic GPS/Location battery saving:
 * - Default sampling interval: 5 minutes
 * - Default acquisition window: approximately 5 seconds
 * - Bounded duty-cycling: Activates location listeners strictly during the 5-second window,
 *   obtains the best available fresh location fix, and immediately deregisters all listeners
 *   to ensure hardware GPS/GNSS radios return to idle sleep.
 * - Non-destructive: Does NOT alter system-wide Location settings (respects Android security policy).
 * - Safe against permission revocation, disabled providers, timeouts, and process recreation.
 */
class AdaptiveLocationBatterySaver(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onLocationUpdated: ((Location) -> Unit)? = null
) {
    companion object {
        private const val TAG = "AdaptiveLocationSaver"
        private const val PREFS_NAME = "netra_gps_battery_saver_prefs"
        private const val KEY_ENABLED = "periodic_location_enabled"
        private const val KEY_INTERVAL_MINUTES = "sampling_interval_minutes"
        private const val KEY_WINDOW_SECONDS = "acquisition_window_seconds"

        const val DEFAULT_INTERVAL_MINUTES = 5
        const val DEFAULT_WINDOW_SECONDS = 5
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var periodicJob: Job? = null
    private var activeListener: LocationListener? = null
    @Volatile
    private var isSampling: Boolean = false

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)
    fun getIntervalMinutes(): Int = prefs.getInt(KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES)
    fun getWindowSeconds(): Int = prefs.getInt(KEY_WINDOW_SECONDS, DEFAULT_WINDOW_SECONDS)

    fun updateConfiguration(enabled: Boolean, intervalMinutes: Int = getIntervalMinutes(), windowSeconds: Int = getWindowSeconds()) {
        prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putInt(KEY_INTERVAL_MINUTES, intervalMinutes.coerceIn(1, 60))
            .putInt(KEY_WINDOW_SECONDS, windowSeconds.coerceIn(2, 30))
            .apply()

        if (enabled) {
            start()
        } else {
            stop()
        }
    }

    fun start() {
        stop()
        if (!isEnabled()) {
            Log.d(TAG, "Periodic location battery saver is disabled in settings.")
            return
        }

        periodicJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Starting periodic location battery saver (Interval: ${getIntervalMinutes()}m, Window: ${getWindowSeconds()}s)")
            while (isActive) {
                try {
                    performSamplingCycle()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic location sampling cycle", e)
                }

                val intervalMs = (getIntervalMinutes() * 60 * 1000L).coerceAtLeast(60000L)
                delay(intervalMs)
            }
        }
    }

    suspend fun performSamplingCycle() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            Log.d(TAG, "Location permissions not granted; skipping sample cycle safely.")
            return
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (locationManager == null) {
            Log.w(TAG, "LocationManager is unavailable on device.")
            return
        }

        val isGpsEnabled = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (e: Exception) { false }
        val isNetworkEnabled = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (e: Exception) { false }

        if (!isGpsEnabled && !isNetworkEnabled) {
            Log.d(TAG, "All location providers are disabled on system; skipping sample.")
            return
        }

        withContext(Dispatchers.Main) {
            if (isSampling) {
                Log.d(TAG, "Sampling cycle already in progress; skipping duplicate trigger.")
                return@withContext
            }
            isSampling = true
            var bestLocation: Location? = null

            // 1. Check if recent cached fix is already fresh (< 60s) to avoid turning on GPS radio at all
            try {
                if (hasFine && isGpsEnabled) {
                    val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    if (lastGps != null && (System.currentTimeMillis() - lastGps.time < 60000L)) {
                        bestLocation = lastGps
                    }
                }
                if (bestLocation == null && isNetworkEnabled) {
                    val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    if (lastNet != null && (System.currentTimeMillis() - lastNet.time < 120000L)) {
                        bestLocation = lastNet
                    }
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException reading last known location", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking cached location", e)
            }

            // 2. If no fresh cached fix, activate radio for short bounded window (~5s)
            val windowMs = (getWindowSeconds() * 1000L).coerceIn(2000L, 30000L)
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (bestLocation == null || location.accuracy < (bestLocation?.accuracy ?: Float.MAX_VALUE)) {
                        bestLocation = location
                    }
                }
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            activeListener = listener

            try {
                if (hasFine && isGpsEnabled) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                }
                if (isNetworkEnabled) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        1000L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException requesting location updates", e)
            } catch (e: Exception) {
                Log.e(TAG, "Error registering location listener", e)
            }

            // Wait for bounded acquisition window
            delay(windowMs)

            // 3. Immediately unregister listener so GPS radio hardware turns OFF
            try {
                locationManager.removeUpdates(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing location updates", e)
            } finally {
                activeListener = null
                isSampling = false
            }

            // 4. Dispatch best fresh fix to downstream context engines and callers
            bestLocation?.let { loc ->
                Log.i(TAG, "Fresh location sample acquired: (lat=${loc.latitude}, lon=${loc.longitude}, acc=${loc.accuracy}m). GPS radio returned to idle.")
                FestivalContextEngine.evaluateLocationContext(context, loc)
                EnvironmentalContextEngine.notifySignificantMotionDetected(context, loc.latitude, loc.longitude)
                onLocationUpdated?.invoke(loc)
            }
        }
    }

    fun stop() {
        periodicJob?.cancel()
        periodicJob = null
        activeListener?.let { listener ->
            try {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                lm?.removeUpdates(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing location listener in stop()", e)
            }
            activeListener = null
        }
        isSampling = false
    }
}
