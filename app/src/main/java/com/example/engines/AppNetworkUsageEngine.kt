package com.example.engines

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import com.example.ui.hasUsageStatsPermission
import java.util.Calendar
import java.util.Locale

/**
 * Netra App Network Usage & Telemetry Engine
 * Authoritative, zero-fabrication collection of per-application network metrics
 * using Android NetworkStatsManager.
 * Made with ❤️ by Prayagi Ji
 */
object AppNetworkUsageEngine {
    private const val TAG = "AppNetworkUsageEngine"

    data class AppNetworkUsageMetrics(
        val uid: Int,
        val mobileRxBytes: Long = 0L,
        val mobileTxBytes: Long = 0L,
        val wifiRxBytes: Long = 0L,
        val wifiTxBytes: Long = 0L,
        val totalRxBytes: Long = 0L,
        val totalTxBytes: Long = 0L,
        val totalNetworkBytes: Long = 0L,
        val isAvailable: Boolean = true
    ) {
        val mobileTotalBytes: Long get() = mobileRxBytes + mobileTxBytes
        val wifiTotalBytes: Long get() = wifiRxBytes + wifiTxBytes
    }

    /**
     * Formats raw bytes to human-readable string (B, KB, MB, GB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

    /**
     * Returns start of current day (00:00:00) in epoch milliseconds.
     */
    fun getStartOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * Authoritative query of per-UID network statistics from Android NetworkStatsManager.
     * Returns empty map if permission is not granted or system query fails.
     */
    fun queryAllAppNetworkUsage(
        context: Context,
        startTime: Long = getStartOfToday(),
        endTime: Long = System.currentTimeMillis()
    ): Map<Int, AppNetworkUsageMetrics> {
        if (!hasUsageStatsPermission(context)) {
            Log.w(TAG, "UsageStats permission not granted. Network stats query unavailable.")
            return emptyMap()
        }

        val attrCtx = com.example.util.getAttributionContext(context)
        val networkStatsManager = attrCtx.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return emptyMap()

        val mobileRxMap = mutableMapOf<Int, Long>()
        val mobileTxMap = mutableMapOf<Int, Long>()
        val wifiRxMap = mutableMapOf<Int, Long>()
        val wifiTxMap = mutableMapOf<Int, Long>()
        val allUids = mutableSetOf<Int>()

        // 1. Query Wi-Fi summary
        try {
            val wifiStats = networkStatsManager.querySummary(ConnectivityManager.TYPE_WIFI, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                val uid = bucket.uid
                if (uid > 0) {
                    allUids.add(uid)
                    wifiRxMap[uid] = (wifiRxMap[uid] ?: 0L) + bucket.rxBytes
                    wifiTxMap[uid] = (wifiTxMap[uid] ?: 0L) + bucket.txBytes
                }
            }
            wifiStats.close()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException querying Wi-Fi network stats: ${e.message}")
            return emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error querying Wi-Fi network stats: ${e.message}", e)
        }

        // 2. Query Mobile summary
        try {
            val mobileStats = networkStatsManager.querySummary(ConnectivityManager.TYPE_MOBILE, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                val uid = bucket.uid
                if (uid > 0) {
                    allUids.add(uid)
                    mobileRxMap[uid] = (mobileRxMap[uid] ?: 0L) + bucket.rxBytes
                    mobileTxMap[uid] = (mobileTxMap[uid] ?: 0L) + bucket.txBytes
                }
            }
            mobileStats.close()
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException querying Mobile network stats: ${e.message}")
            return emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Error querying Mobile network stats: ${e.message}", e)
        }

        // Build result map
        val resultMap = mutableMapOf<Int, AppNetworkUsageMetrics>()
        for (uid in allUids) {
            val mRx = mobileRxMap[uid] ?: 0L
            val mTx = mobileTxMap[uid] ?: 0L
            val wRx = wifiRxMap[uid] ?: 0L
            val wTx = wifiTxMap[uid] ?: 0L
            val totalRx = mRx + wRx
            val totalTx = mTx + wTx
            val totalBytes = totalRx + totalTx

            resultMap[uid] = AppNetworkUsageMetrics(
                uid = uid,
                mobileRxBytes = mRx,
                mobileTxBytes = mTx,
                wifiRxBytes = wRx,
                wifiTxBytes = wTx,
                totalRxBytes = totalRx,
                totalTxBytes = totalTx,
                totalNetworkBytes = totalBytes,
                isAvailable = true
            )
        }

        return resultMap
    }

    /**
     * Authoritative query for a single app UID.
     */
    fun queryAppNetworkUsageForUid(
        context: Context,
        uid: Int,
        startTime: Long = getStartOfToday(),
        endTime: Long = System.currentTimeMillis()
    ): AppNetworkUsageMetrics? {
        if (uid <= 0 || !hasUsageStatsPermission(context)) return null

        val attrCtx = com.example.util.getAttributionContext(context)
        val networkStatsManager = attrCtx.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return null

        var mRx = 0L
        var mTx = 0L
        var wRx = 0L
        var wTx = 0L
        var success = false

        // Wi-Fi
        try {
            val wifiStats = networkStatsManager.queryDetailsForUid(ConnectivityManager.TYPE_WIFI, null, startTime, endTime, uid)
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                wRx += bucket.rxBytes
                wTx += bucket.txBytes
            }
            wifiStats.close()
            success = true
        } catch (e: Exception) {
            Log.d(TAG, "Wi-Fi query for UID $uid exception: ${e.message}")
        }

        // Mobile
        try {
            val mobileStats = networkStatsManager.queryDetailsForUid(ConnectivityManager.TYPE_MOBILE, null, startTime, endTime, uid)
            val bucket = NetworkStats.Bucket()
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                mRx += bucket.rxBytes
                mTx += bucket.txBytes
            }
            mobileStats.close()
            success = true
        } catch (e: Exception) {
            Log.d(TAG, "Mobile query for UID $uid exception: ${e.message}")
        }

        if (!success && wRx == 0L && wTx == 0L && mRx == 0L && mTx == 0L) {
            return null
        }

        val totalRx = mRx + wRx
        val totalTx = mTx + wTx
        val totalBytes = totalRx + totalTx

        return AppNetworkUsageMetrics(
            uid = uid,
            mobileRxBytes = mRx,
            mobileTxBytes = mTx,
            wifiRxBytes = wRx,
            wifiTxBytes = wTx,
            totalRxBytes = totalRx,
            totalTxBytes = totalTx,
            totalNetworkBytes = totalBytes,
            isAvailable = true
        )
    }
}
