package com.example.providers

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

data class SafeNetworkInfo(
    val isWifiConnected: Boolean = false,
    val isCellularConnected: Boolean = false,
    val isInternetAvailable: Boolean = false,
    val ssid: String = "Unavailable / Location Permission Required",
    val bssid: String = "Restricted",
    val rssi: Int = -127,
    val linkSpeedMbps: Int = 0,
    val isMetered: Boolean = false,
    val isSupportedOnDevice: Boolean = true,
    val restrictionMessage: String? = null
)

object SafeNetworkProvider {
    private const val TAG = "SafeNetworkProvider"

    fun getNetworkInfo(context: Context): SafeNetworkInfo {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm == null) {
                return SafeNetworkInfo(
                    isSupportedOnDevice = false,
                    restrictionMessage = "ConnectivityManager unavailable on this device"
                )
            }

            val activeNetwork = cm.activeNetwork
            val caps = if (activeNetwork != null) cm.getNetworkCapabilities(activeNetwork) else null

            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val isInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false

            var ssid = "Not Connected"
            var bssid = "N/A"
            var rssi = -127
            var linkSpeed = 0
            var restrictionMsg: String? = null

            if (isWifi) {
                val wifiInfo = getWifiInfoSafely(context)
                if (wifiInfo != null) {
                    ssid = wifiInfo.first
                    bssid = wifiInfo.second
                    rssi = wifiInfo.third
                    linkSpeed = wifiInfo.fourth
                } else {
                    ssid = "Wi-Fi Active (Location Permission Restricted)"
                    restrictionMsg = "Android 14 Wi-Fi SSID details restricted without FINE_LOCATION permission"
                }
            }

            SafeNetworkInfo(
                isWifiConnected = isWifi,
                isCellularConnected = isCellular,
                isInternetAvailable = isInternet,
                ssid = ssid,
                bssid = bssid,
                rssi = rssi,
                linkSpeedMbps = linkSpeed,
                isMetered = isMetered,
                isSupportedOnDevice = true,
                restrictionMessage = restrictionMsg
            )
        } catch (e: Exception) {
            Log.w(TAG, "SafeNetworkProvider exception caught and isolated: ${e.message}")
            SafeNetworkInfo(
                isSupportedOnDevice = false,
                restrictionMessage = "Network telemetry isolated due to system exception: ${e.javaClass.simpleName}"
            )
        }
    }

    private fun getWifiInfoSafely(context: Context): Tuple4<String, String, Int, Int>? {
        return try {
            val hasLocationPerm = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null

            @Suppress("DEPRECATION")
            val connectionInfo = wm.connectionInfo ?: return null

            val rawSsid = connectionInfo.ssid
            val cleanSsid = if (rawSsid != null && rawSsid != "<unknown ssid>") {
                rawSsid.replace("\"", "")
            } else if (hasLocationPerm) {
                "Connected Wi-Fi"
            } else {
                "Wi-Fi Network (Location Off)"
            }

            val bssid = if (hasLocationPerm) (connectionInfo.bssid ?: "N/A") else "Restricted"
            val rssi = connectionInfo.rssi
            val linkSpeed = connectionInfo.linkSpeed

            Tuple4(cleanSsid, bssid, rssi, linkSpeed)
        } catch (e: Exception) {
            Log.w(TAG, "Safe Wi-Fi query trapped exception: ${e.message}")
            null
        }
    }

    private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
