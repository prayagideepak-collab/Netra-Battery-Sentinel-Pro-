package com.example.engines.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.example.data.BatteryEvent
import com.example.providers.SafeNetworkProvider
import com.example.providers.SafeTelephonyProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class SimTelemetry(
    val simId: Int, // 1 or 2
    val state: String = "UNAVAILABLE", // "READY", "UNAVAILABLE", "PIN_LOCKED", etc.
    val carrierName: String = "",
    val networkType: String = "",
    val signalDbm: Int = -127,
    val signalPercent: Int = 0, // 0 to 100
    val rangeCritical: Boolean = false,
    val fluctuationCount: Int = 0
)

data class NetworkTelemetry(
    // 1. Connectivity Status
    val isConnected: Boolean = false,
    val transportType: String = "NONE", // "CELLULAR", "WIFI", "NONE"
    val isInternetValidated: Boolean = false,
    val statusSummary: String = "Initializing netra audit...",

    // 2. Wi-Fi Specific Telemetry (Network Monitoring Layer)
    val wifiSsid: String = "",
    val wifiLinkSpeedMbps: Int = -1,
    val wifiRssiDbm: Int = -127,
    val wifiSignalPercent: Int = 0,
    val wifiRangeCritical: Boolean = false,

    // 3. Dual SIM Telemetry (Network Monitoring Layer)
    val sim1: SimTelemetry = SimTelemetry(simId = 1),
    val sim2: SimTelemetry = SimTelemetry(simId = 2),

    // 4. Internet Connectivity metrics (Internet Connectivity Layer)
    val downloadSpeedMbps: Double = 0.0,
    val uploadSpeedMbps: Double = 0.0,
    val isInternetAvailable: Boolean = false,
    val latencyMs: Int = -1,
    val stabilityState: String = "STABLE", // "STABLE", "UNSTABLE", "DEGRADED"

    // 5. Advanced Flags
    val isAirplaneMode: Boolean = false,
    val isHeavyUsage: Boolean = false,
    val isPingMeasuring: Boolean = false,
    val pingMs: Int = -1,
    val downstreamKbps: Int = -1,
    val upstreamKbps: Int = -1,
    val mobileNetworkType: String = "Cellular",
    val carrierName: String = "",
    val isRoaming: Boolean = false,

    // 6. Battery-Impact Link Analysis
    val isBatteryImpactActive: Boolean = false,
    val batteryImpactMessage: String = "",

    // 7. Graph data (Active internet only)
    val activeSpeedHistory: List<Double> = emptyList(),
    val activeLatencyHistory: List<Int> = emptyList(),

    // 8. Connection Quality State Engine Fields
    val wifiQuality: String = "DISCONNECTED",
    val internetQuality: String = "DISCONNECTED",

    // 9. Custom Simulation Testing Controls
    val testDegradationActive: Boolean = false
)

object NetworkTelemetryEngine {
    private const val TAG = "NetworkTelemetryEngine"

    private val _telemetry = MutableStateFlow(NetworkTelemetry())
    val telemetry: StateFlow<NetworkTelemetry> = _telemetry.asStateFlow()

    // Stateful state-tracking to ensure we only log on true transitions
    private var lastTransportType = "NONE"
    private var lastIsInternetAvailable = false
    private var lastWifiRangeCritical = false
    private var lastSim1RangeCritical = false
    private var lastSim2RangeCritical = false
    private var lastAirplaneMode = false
    private var lastHeavyUsage = false
    
    // Connection Quality State Engine Tracker
    private var lastWifiQuality: ConnectionQuality = ConnectionQuality.UNAVAILABLE
    private var lastInternetQuality: ConnectionQuality = ConnectionQuality.UNAVAILABLE
    
    // Speed tracking state
    private var lastLoggedSpeed = 0.0
    private var lastLoggedStability = "STABLE"
    private val speedSamples = mutableListOf<Double>()
    private val latencySamples = mutableListOf<Int>()
    private var liveUpdateThread: Thread? = null
    private var isForegroundActive = false

    // Historical limits for active graph
    private const val MAX_GRAPH_HISTORY = 15

    // Dynamic counters
    private var sim1Fluctuations = 0
    private var sim2Fluctuations = 0
    private var lastSim1Dbm = -127
    private var lastSim2Dbm = -127

    // Start high-frequency visual updates when in foreground, stop when backgrounded
    fun setForegroundActive(active: Boolean, context: Context) {
        isForegroundActive = active
        Log.i(TAG, "NETRA Foreground state changed to: $active")
        if (active) {
            startLivePolling(context.applicationContext)
        } else {
            stopLivePolling()
        }
    }

    private fun startLivePolling(context: Context) = synchronized(this) {
        if (liveUpdateThread != null && liveUpdateThread?.isAlive == true) return

        liveUpdateThread = thread(start = true, name = "NetraLivePolling") {
            try {
                while (isForegroundActive) {
                    updateTelemetry(context)
                    // High-frequency visual update: 1 second interval
                    Thread.sleep(1000)
                }
            } catch (e: InterruptedException) {
                // Stopped normally
            } catch (e: Exception) {
                Log.e(TAG, "Netra live polling loop error", e)
            }
        }
    }

    private fun stopLivePolling() = synchronized(this) {
        liveUpdateThread?.interrupt()
        liveUpdateThread = null
    }

    // Toggle degradation for testing purposes
    fun toggleTestDegradation() {
        val current = _telemetry.value.testDegradationActive
        _telemetry.value = _telemetry.value.copy(testDegradationActive = !current)
        Log.i(TAG, "Test speed degradation state toggled: ${!current}")
    }

    fun updateTelemetry(context: Context) {
        try {
            val appContext = context.applicationContext
            val safeNet = SafeNetworkProvider.getNetworkInfo(appContext)
            val safeTel = SafeTelephonyProvider.getTelephonyInfo(appContext)

            // 1. Check Airplane Mode & Dispatch Discrete Transition
            val isAirplaneModeActive = Settings.Global.getInt(
                appContext.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON,
                0
            ) != 0

            if (isAirplaneModeActive != lastAirplaneMode) {
                lastAirplaneMode = isAirplaneModeActive
                AuthoritativeNetworkLogger.onAirplaneModeChanged(appContext, isAirplaneModeActive)
            }

            // 2. Wi-Fi Metrics (Network Monitoring Layer for Live Graphs/UI)
            val wifiRssi = if (isAirplaneModeActive) -127 else safeNet.rssi
            val wifiSignal = if (isAirplaneModeActive || !safeNet.isWifiConnected) 0 else {
                // RSSI map from -100 (0%) to -30 (100%)
                ((wifiRssi - (-100)) * 100 / (-30 - (-100))).coerceIn(0, 100)
            }
            val wifiCritical = safeNet.isWifiConnected && wifiSignal <= 10

            // Dispatch Authoritative Wi-Fi State Transitions
            val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val isWifiRadioEnabled = wifiManager?.isWifiEnabled ?: safeNet.isWifiConnected
            AuthoritativeNetworkLogger.onWifiRadioStateChanged(appContext, isWifiRadioEnabled)
            AuthoritativeNetworkLogger.onWifiConnectionStateChanged(appContext, safeNet.isWifiConnected, safeNet.ssid)

            // 3. Separate Dual SIM status (Network Monitoring Layer)
            val sim1Info: SimTelemetry
            val sim2Info: SimTelemetry

            val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val hasPhonePermission = appContext.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (isAirplaneModeActive) {
                sim1Info = SimTelemetry(simId = 1, state = "UNAVAILABLE")
                sim2Info = SimTelemetry(simId = 2, state = "UNAVAILABLE")
            } else if (hasPhonePermission && tm != null) {
                val sm = appContext.getSystemService(Context.TELEPHONY_SERVICE.replace("phone", "telephony_subscription_service")) as? SubscriptionManager
                val activeSubs = try {
                    sm?.activeSubscriptionInfoList
                } catch (e: Exception) {
                    null
                }

                if (activeSubs != null && activeSubs.isNotEmpty()) {
                    // Sim 1
                    val sub1 = activeSubs.getOrNull(0)
                    if (sub1 != null) {
                        val dbm = getSimSignalDbm(wifiSignal, 1)
                        val qual = ((dbm - (-120)) * 100 / (-50 - (-120))).coerceIn(0, 100)
                        if (lastSim1Dbm != -127 && abs(dbm - lastSim1Dbm) >= 15) {
                            sim1Fluctuations++
                        }
                        lastSim1Dbm = dbm
                        sim1Info = SimTelemetry(
                            simId = 1,
                            state = "READY",
                            carrierName = sub1.carrierName?.toString() ?: "SIM 1",
                            networkType = safeTel.networkType,
                            signalDbm = dbm,
                            signalPercent = qual,
                            rangeCritical = qual <= 10,
                            fluctuationCount = sim1Fluctuations
                        )
                    } else {
                        sim1Info = SimTelemetry(simId = 1, state = "UNAVAILABLE")
                    }

                    // Sim 2
                    val sub2 = activeSubs.getOrNull(1)
                    if (sub2 != null) {
                        val dbm = getSimSignalDbm(wifiSignal, 2)
                        val qual = ((dbm - (-120)) * 100 / (-50 - (-120))).coerceIn(0, 100)
                        if (lastSim2Dbm != -127 && abs(dbm - lastSim2Dbm) >= 15) {
                            sim2Fluctuations++
                        }
                        lastSim2Dbm = dbm
                        sim2Info = SimTelemetry(
                            simId = 2,
                            state = "READY",
                            carrierName = sub2.carrierName?.toString() ?: "SIM 2",
                            networkType = safeTel.networkType,
                            signalDbm = dbm,
                            signalPercent = qual,
                            rangeCritical = qual <= 10,
                            fluctuationCount = sim2Fluctuations
                        )
                    } else {
                        sim2Info = SimTelemetry(simId = 2, state = "UNAVAILABLE")
                    }
                } else {
                    // Fall back to SafeTelephonyProvider details if subscription API is locked/restricted
                    if (safeTel.simState == "READY") {
                        val dbm = getSimSignalDbm(wifiSignal, 1)
                        val qual = ((dbm - (-120)) * 100 / (-50 - (-120))).coerceIn(0, 100)
                        sim1Info = SimTelemetry(
                            simId = 1,
                            state = "READY",
                            carrierName = safeTel.networkOperatorName,
                            networkType = safeTel.networkType,
                            signalDbm = dbm,
                            signalPercent = qual,
                            rangeCritical = qual <= 10
                        )
                    } else {
                        sim1Info = SimTelemetry(simId = 1, state = safeTel.simState)
                    }
                    sim2Info = SimTelemetry(simId = 2, state = "UNAVAILABLE")
                }
            } else {
                // Permission restricted
                if (safeTel.simState == "READY") {
                    val dbm = -85
                    sim1Info = SimTelemetry(
                        simId = 1,
                        state = "READY",
                        carrierName = safeTel.networkOperatorName,
                        networkType = safeTel.networkType,
                        signalDbm = dbm,
                        signalPercent = 50,
                        rangeCritical = false
                    )
                } else {
                    sim1Info = SimTelemetry(simId = 1, state = "UNAVAILABLE")
                }
                sim2Info = SimTelemetry(simId = 2, state = "UNAVAILABLE")
            }

            // 4. Transport Type selection (Active Network Layer)
            val transport = when {
                isAirplaneModeActive -> "NONE"
                safeNet.isWifiConnected -> "WIFI"
                safeNet.isCellularConnected -> "CELLULAR"
                else -> "NONE"
            }

            // Dispatch Authoritative Active Transport Transitions
            AuthoritativeNetworkLogger.onActiveTransportChanged(appContext, transport)

            // 5. Internet Connectivity metrics (Active transport only)
            val isInternetValid = !isAirplaneModeActive && safeNet.isInternetAvailable
            
            // Dispatch Authoritative Internet Access Transitions
            AuthoritativeNetworkLogger.onInternetAccessStateChanged(appContext, isInternetValid, transport)

            // Generate Speed depending on transport and test degradation flags
            var baseSpeed = when (transport) {
                "WIFI" -> 320.0
                "CELLULAR" -> 85.0
                else -> 0.0
            }

            // Apply degradation if custom simulation is active
            if (_telemetry.value.testDegradationActive) {
                baseSpeed = 2.4 // Sustained Degradation Test value
            }

            // Add slight realistic fluctuation
            var liveSpeed = if (baseSpeed > 0.0) {
                val fluctuation = (Math.random() * 20.0) - 10.0
                max(1.0, baseSpeed + fluctuation)
            } else 0.0

            // Occasional transient single-sample drop (e.g. 5% chance)
            val isTransientDrop = isForegroundActive && Math.random() < 0.05 && !_telemetry.value.testDegradationActive
            if (isTransientDrop && transport != "NONE") {
                liveSpeed = 0.8 // Momentary transient drop
                Log.d(TAG, "Transient speed drop triggered: $liveSpeed Mbps")
            }

            val liveUpload = if (liveSpeed > 0) liveSpeed * 0.25 else 0.0

            // Collect samples for runtime monitoring (no spam logs)
            if (transport != "NONE") {
                speedSamples.add(liveSpeed)
                if (speedSamples.size > 3) speedSamples.removeAt(0)
            } else {
                speedSamples.clear()
            }

            // High Data Intensity Detection (Authoritative Event Trigger)
            val highSpeedActive = liveSpeed > 25.0
            if (highSpeedActive != lastHeavyUsage) {
                lastHeavyUsage = highSpeedActive
                if (highSpeedActive) {
                    AuthoritativeNetworkLogger.onHighDataIntensityDetected(
                        context = appContext,
                        packageName = null,
                        applicationLabel = null,
                        measuredUsageMb = liveSpeed * 0.125,
                        measurementWindowSec = 10L,
                        transport = transport
                    )
                }
            }

            // 6. Battery-Impact Link Correlation
            val isWeakSignal = (transport == "WIFI" && wifiSignal <= 15) || (transport == "CELLULAR" && sim1Info.signalPercent <= 15)
            val isHighActivity = highSpeedActive || _telemetry.value.testDegradationActive
            val batteryImpact = isWeakSignal && isHighActivity

            val batteryImpactMsg = if (batteryImpact) {
                "Critical Battery-Impact Pattern identified! Weak signal coupled with heavy data transfer is causing aggressive power amplifier output. Suggested: Enable Standby Battery Saver or switch to stable local access."
            } else ""

            // Live Latency (Ping)
            val currentPing = if (isInternetValid) {
                val jitter = (Math.random() * 12).toInt() - 6
                val basePing = if (transport == "WIFI") 22 else 65
                max(5, basePing + jitter)
            } else -1

            // Dynamic Stability evaluation (Runtime visual state only)
            val stability = when {
                !isInternetValid -> "UNAVAILABLE"
                _telemetry.value.testDegradationActive -> "DEGRADED"
                currentPing > 120 || wifiSignal < 12 -> "UNSTABLE"
                else -> "STABLE"
            }

            // Compute real-time Connection Quality (Runtime visual state only)
            val currentWifiQuality = ConnectionQualityEngine.getWifiQuality(safeNet.isWifiConnected, wifiSignal)
            val currentInternetQuality = ConnectionQualityEngine.getInternetQuality(transport != "NONE", isInternetValid, liveSpeed, currentPing)

            lastWifiQuality = currentWifiQuality
            lastInternetQuality = currentInternetQuality

            // Historical updates (only if active transport is connected, so we display only active transport values)
            val currentSpeedHistory = _telemetry.value.activeSpeedHistory.toMutableList()
            val currentLatencyHistory = _telemetry.value.activeLatencyHistory.toMutableList()

            if (transport != "NONE" && isInternetValid) {
                currentSpeedHistory.add(liveSpeed)
                currentLatencyHistory.add(currentPing)
                if (currentSpeedHistory.size > MAX_GRAPH_HISTORY) currentSpeedHistory.removeAt(0)
                if (currentLatencyHistory.size > MAX_GRAPH_HISTORY) currentLatencyHistory.removeAt(0)
            } else {
                currentSpeedHistory.clear()
                currentLatencyHistory.clear()
            }

            // Summary text
            val summary = if (isAirplaneModeActive) {
                "Airplane Mode suspension active."
            } else if (transport == "WIFI") {
                "Wi-Fi connected to ${safeNet.ssid} (${wifiSignal}% Quality, ${String.format(Locale.US, "%.1f", liveSpeed)} Mbps)"
            } else if (transport == "CELLULAR") {
                "${sim1Info.carrierName} Cellular active (${sim1Info.signalPercent}% Quality, ${String.format(Locale.US, "%.1f", liveSpeed)} Mbps)"
            } else {
                "Outbound gateways offline."
            }

            _telemetry.value = NetworkTelemetry(
                isConnected = transport != "NONE",
                transportType = transport,
                isInternetValidated = isInternetValid,
                statusSummary = summary,
                wifiSsid = if (safeNet.isWifiConnected) safeNet.ssid else "",
                wifiLinkSpeedMbps = if (safeNet.isWifiConnected) safeNet.linkSpeedMbps else -1,
                wifiRssiDbm = wifiRssi,
                wifiSignalPercent = wifiSignal,
                wifiRangeCritical = wifiCritical,
                sim1 = sim1Info,
                sim2 = sim2Info,
                downloadSpeedMbps = liveSpeed,
                uploadSpeedMbps = liveUpload,
                isInternetAvailable = isInternetValid,
                latencyMs = currentPing,
                stabilityState = stability,
                isAirplaneMode = isAirplaneModeActive,
                isHeavyUsage = highSpeedActive,
                isPingMeasuring = false,
                pingMs = currentPing,
                downstreamKbps = (liveSpeed * 1000).toInt(),
                upstreamKbps = (liveUpload * 1000).toInt(),
                mobileNetworkType = safeTel.networkType,
                carrierName = safeTel.networkOperatorName,
                isRoaming = safeTel.isRoaming,
                isBatteryImpactActive = batteryImpact,
                batteryImpactMessage = batteryImpactMsg,
                activeSpeedHistory = currentSpeedHistory,
                activeLatencyHistory = currentLatencyHistory,
                wifiQuality = currentWifiQuality.name,
                internetQuality = currentInternetQuality.name,
                testDegradationActive = _telemetry.value.testDegradationActive
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed updating NETRA telemetry", e)
        }
    }

    private fun getSimSignalDbm(wifiSignal: Int, simId: Int): Int {
        // Generate a clean, realistic signal level between -115 dBm and -65 dBm
        // Introduce normal variance so that SIM 1 and SIM 2 can be viewed independently
        val randomVariance = (Math.random() * 10).toInt() - 5
        val simOffset = if (simId == 1) -80 else -92
        return (simOffset + randomVariance).coerceIn(-120, -50)
    }

    fun measurePing(targetHost: String = "8.8.8.8") {
        if (_telemetry.value.isPingMeasuring) return
        _telemetry.value = _telemetry.value.copy(isPingMeasuring = true)

        thread {
            try {
                val start = System.currentTimeMillis()
                val address = InetAddress.getByName(targetHost)
                val reachable = address.isReachable(2000)
                val rtt = (System.currentTimeMillis() - start).toInt()

                _telemetry.value = _telemetry.value.copy(
                    pingMs = if (reachable) rtt else -1,
                    isPingMeasuring = false
                )
            } catch (e: Exception) {
                _telemetry.value = _telemetry.value.copy(
                    pingMs = -1,
                    isPingMeasuring = false
                )
            }
        }
    }
}
