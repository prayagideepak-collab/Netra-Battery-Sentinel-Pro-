package com.example.providers

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log

data class SafeTelephonyInfo(
    val isCallActive: Boolean = false,
    val networkOperatorName: String = "Unknown / No SIM",
    val networkType: String = "Unknown",
    val isRoaming: Boolean = false,
    val simState: String = "UNKNOWN",
    val isDualSimConfigured: Boolean = true,
    val isSupportedOnDevice: Boolean = true,
    val restrictionMessage: String? = null
)

object SafeTelephonyProvider {
    private const val TAG = "SafeTelephonyProvider"

    fun getTelephonyInfo(context: Context): SafeTelephonyInfo {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            if (tm == null) {
                return SafeTelephonyInfo(
                    isSupportedOnDevice = false,
                    restrictionMessage = "TelephonyManager service unavailable on this device"
                )
            }

            val isCallActive = try {
                tm.callState != TelephonyManager.CALL_STATE_IDLE
            } catch (e: SecurityException) {
                Log.w(TAG, "READ_PHONE_STATE permission restricted call state query")
                false
            } catch (e: Exception) {
                false
            }

            val opName = try {
                tm.networkOperatorName.ifBlank { "Carrier" }
            } catch (e: Exception) {
                "Carrier"
            }

            val netType = try {
                val hasPhoneState = context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasPhoneState) {
                    @Suppress("DEPRECATION")
                    when (tm.networkType) {
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                        else -> "Cellular"
                    }
                } else {
                    "Cellular (Permission Restricted)"
                }
            } catch (e: Exception) {
                "Cellular"
            }

            val isRoaming = try {
                tm.isNetworkRoaming
            } catch (e: Exception) {
                false
            }

            val simStateStr = try {
                when (tm.simState) {
                    TelephonyManager.SIM_STATE_READY -> "READY"
                    TelephonyManager.SIM_STATE_ABSENT -> "ABSENT"
                    TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN_LOCKED"
                    TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK_LOCKED"
                    TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "NETWORK_LOCKED"
                    else -> "NOT_READY"
                }
            } catch (e: Exception) {
                "UNKNOWN"
            }

            SafeTelephonyInfo(
                isCallActive = isCallActive,
                networkOperatorName = opName,
                networkType = netType,
                isRoaming = isRoaming,
                simState = simStateStr,
                isDualSimConfigured = true,
                isSupportedOnDevice = true
            )
        } catch (e: Exception) {
            Log.w(TAG, "SafeTelephonyProvider exception isolated: ${e.message}")
            SafeTelephonyInfo(
                isSupportedOnDevice = false,
                restrictionMessage = "Telephony telemetry isolated due to system exception: ${e.javaClass.simpleName}"
            )
        }
    }

    fun isCallActive(context: Context): Boolean {
        return getTelephonyInfo(context).isCallActive
    }
}
