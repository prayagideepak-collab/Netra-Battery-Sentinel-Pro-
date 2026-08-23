package com.example.engines.network

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.data.BatteryRepository
import com.example.util.DiagnosticLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiscoveredDevice(
    val ip: String,
    val hostname: String,
    val status: String, // "ACTIVE", "DETECTED", "OFFLINE", "UNKNOWN"
    val firstSeen: String, // "hh:mm:ss.SSS a"
    val lastSeen: String // "hh:mm:ss.SSS a"
)

object WifiDiscoveryEngine {
    private const val TAG = "WifiDiscoveryEngine"

    private var currentSsid: String = ""
    private var lastDiscoveredCount: Int = -1

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    // Store timestamps of first seen for devices
    private val firstSeenTimes = mutableMapOf<String, Long>()

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val ip = address.hostAddress
                        if (ip.startsWith("192.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
        }
        return null
    }

    private fun formatTimestamp(timeMs: Long): String {
        return SimpleDateFormat("hh:mm:ss.SSS a", Locale.US).format(Date(timeMs))
    }

    fun onWifiDisconnected(context: Context) {
        if (currentSsid.isNotEmpty()) {
            val oldSsid = currentSsid
            currentSsid = ""
            _discoveredDevices.value = emptyList()
            _isScanning.value = false
            lastDiscoveredCount = -1
            AuthoritativeNetworkLogger.onWifiConnectionStateChanged(context, false, oldSsid)
        }
    }

    suspend fun triggerDiscovery(context: Context, repository: BatteryRepository?) {
        val safeNet = com.example.providers.SafeNetworkProvider.getNetworkInfo(context)
        if (!safeNet.isWifiConnected) {
            onWifiDisconnected(context)
            return
        }

        val ssid = safeNet.ssid.removePrefix("\"").removeSuffix("\"")
        if (ssid != currentSsid) {
            // Wi-Fi network switched or initialized
            val oldSsid = currentSsid
            currentSsid = ssid
            _discoveredDevices.value = emptyList()
            firstSeenTimes.clear()
            lastDiscoveredCount = -1
            AuthoritativeNetworkLogger.onWifiConnectionStateChanged(context, true, ssid)
        }

        if (_isScanning.value) return
        _isScanning.value = true
        logDiagnostic(context, "DISCOVERY_STARTED", "Device Discovery Started", "Scanning subnet for active client presence.")

        val results = withContext(Dispatchers.IO) {
            val localIp = getLocalIpAddress() ?: return@withContext emptyList<DiscoveredDevice>()
            val subnetBase = localIp.substringBeforeLast(".") + "."

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val dhcpInfo = wifiManager?.dhcpInfo
            val gatewayIp = if (dhcpInfo != null && dhcpInfo.gateway != 0) {
                val gatewayInt = dhcpInfo.gateway
                "${gatewayInt and 0xFF}.${(gatewayInt shr 8) and 0xFF}.${(gatewayInt shr 16) and 0xFF}.${(gatewayInt shr 24) and 0xFF}"
            } else {
                subnetBase + "1"
            }

            val candidates = mutableSetOf<String>()
            candidates.add(localIp)
            candidates.add(gatewayIp)

            // Dynamic scan candidates - limit range to avoid aggressive scanning
            for (i in 1..25) {
                candidates.add(subnetBase + i)
            }
            for (i in 100..115) {
                candidates.add(subnetBase + i)
            }
            candidates.add(subnetBase + "254")

            val now = System.currentTimeMillis()
            val jobs = candidates.map { ip ->
                async {
                    try {
                        val address = InetAddress.getByName(ip)
                        // Timeout 400ms is perfectly non-aggressive and responsive on parallel coroutines
                        val isReachable = address.isReachable(400)
                        if (isReachable) {
                            val hostname = address.canonicalHostName ?: address.hostName
                            val isGateway = ip == gatewayIp
                            val isSelf = ip == localIp
                            val deviceName = when {
                                isSelf -> "This Device (Netra Host)"
                                isGateway -> "Wi-Fi Router Gateway"
                                else -> if (hostname != ip) hostname else "Active Client"
                            }
                            
                            val firstSeenTime = firstSeenTimes.getOrPut(ip) { now }
                            DiscoveredDevice(
                                ip = ip,
                                hostname = deviceName,
                                status = "ACTIVE",
                                firstSeen = formatTimestamp(firstSeenTime),
                                lastSeen = formatTimestamp(now)
                            )
                        } else {
                            // If previously seen but currently not reachable, mark as OFFLINE
                            val prevFirstSeen = firstSeenTimes[ip]
                            if (prevFirstSeen != null) {
                                DiscoveredDevice(
                                    ip = ip,
                                    hostname = if (ip == gatewayIp) "Wi-Fi Router Gateway" else if (ip == localIp) "This Device (Netra Host)" else "Active Client",
                                    status = "OFFLINE",
                                    firstSeen = formatTimestamp(prevFirstSeen),
                                    lastSeen = formatTimestamp(now)
                                )
                            } else {
                                null
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            jobs.awaitAll().filterNotNull()
        }

        val activeCount = results.count { it.status == "ACTIVE" }
        _discoveredDevices.value = results
        _isScanning.value = false
        logDiagnostic(context, "DISCOVERY_COMPLETED", "Device Discovery Completed", "Scan resolved. Active Devices on subnet: $activeCount.")

        if (lastDiscoveredCount != activeCount) {
            lastDiscoveredCount = activeCount
        }
    }

    private fun logDiagnostic(context: Context, category: String, title: String, details: String) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
        val level = batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 50
        
        DiagnosticLogger.logEvent(
            context = context,
            category = category,
            title = title,
            details = details,
            batteryLevel = level,
            temperature = 30.0f, // stable default fallback
            voltage = 3800f,
            status = "ACTIVE"
        )
    }
}
