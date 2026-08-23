package com.example.engines.idmse

import android.util.Log

object IdmseDataValidator {
    private const val TAG = "IDMSE_Validator"

    fun calculateChecksum(payload: IdmseDataPayload): Int {
        val raw = "${payload.version}:${payload.timestamp}:${payload.chargingSessions.size}:${payload.dischargingSessions.size}:${payload.batteryEvents.size}"
        return raw.hashCode()
    }

    fun validatePayload(payload: IdmseDataPayload): Boolean {
        val now = System.currentTimeMillis()
        
        // 1. Future timestamp validation (allow up to 60s clock skew)
        if (payload.timestamp > now + 60_000L) {
            Log.e(TAG, "Payload rejected: Future timestamp detected (${payload.timestamp} > $now)")
            return false
        }

        // 2. Version sanity check
        if (payload.version < 1) {
            Log.e(TAG, "Payload rejected: Invalid version (${payload.version})")
            return false
        }

        // 3. Checksum verification
        val expectedChecksum = calculateChecksum(payload)
        if (payload.checksum != 0 && payload.checksum != expectedChecksum) {
            Log.e(TAG, "Payload rejected: Checksum mismatch (Expected $expectedChecksum, Actual ${payload.checksum})")
            return false
        }

        return true
    }
}
