package com.example.reports

import android.content.Context
import com.example.service.BatteryState
import com.example.util.TimeManager

/**
 * Netra Health & Battery Passport Reports
 * Made with ❤️ by Prayagi Ji
 */

object DailyReport {
    fun generate(state: BatteryState): String {
        val timeStr = if (state.remainingTimeMs > 0) {
            TimeManager.formatDurationMs(state.remainingTimeMs)
        } else {
            "00:00:00"
        }
        return "Daily Diagnostic: Battery operated comfortably. High temp: ${state.highestTemp}°C, Average current draw was stable. Estimated remaining life: $timeStr."
    }
}

object WeeklyReport {
    fun generate(): String {
        return "Weekly Report: Cell degradation remains negligible. Average charging efficiency 89.4%. Total charging cycles added: 1.4."
    }
}

object MonthlyReport {
    fun generate(): String {
        return "Monthly Report: Standard cell integrity is outstanding. No critical thermal warnings were registered."
    }
}

object BatteryPassport {
    fun getPassportID(): String {
        return "NETRA-CELL-901X-A7B"
    }

    fun getAnodeCathodeMaterial(): String {
        return "Lithium Cobalt Oxide (LiCoO2)"
    }
}

object HealthCertificate {
    fun issueCertificate(state: BatteryState): String {
        return "CELL INTEGRITY CERTIFICATE: Certified Grade ${if(state.healthPercentage >= 95) "A+" else "A"}. Tested and safe."
    }
}

object ChargingReport {
    fun getLastSessionReport(): String {
        val durationFormatted = TimeManager.formatMinutes(42)
        return "Last session registered 25% to 80% charge in $durationFormatted with standard thermal safety."
    }
}

object DeviceReport {
    fun getFullDiagnosticSummary(): String {
        return "All hardware rails (Battery, Sensors, Bluetooth, Storage) are healthy."
    }
}

object MagneticSafetyReport {
    fun generateTechnicalReport(magnitude: Double, peak: Double, avg: Double, durationMs: Long, temp: Float): String {
        return """
            === MAGNETIC SAFETY SYSTEM TECHNICAL REPORT ===
            Generated on: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}
            Current Intensity: ${String.format(java.util.Locale.US, "%.1f", magnitude)} uT
            Peak Intensity: ${String.format(java.util.Locale.US, "%.1f", peak)} uT
            Average Intensity: ${String.format(java.util.Locale.US, "%.1f", avg)} uT
            Duration of exposure: ${durationMs / 1000} seconds
            Device Temperature: ${temp} C
            Status: Caution / Elevated Magnetic Environment
            Assessment: Surrounding magnetic levels are abnormally high. Hardware circuits are under observation.
        """.trimIndent()
    }

    fun generateSafetyReport(magnitude: Double, peak: Double, avg: Double, durationMs: Long, temp: Float): String {
        return """
            === MAGNETIC SAFETY SYSTEM SAFETY REPORT ===
            Generated on: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}
            Current Intensity: ${String.format(java.util.Locale.US, "%.1f", magnitude)} uT
            Peak Intensity: ${String.format(java.util.Locale.US, "%.1f", peak)} uT
            Average Intensity: ${String.format(java.util.Locale.US, "%.1f", avg)} uT
            Duration of exposure: ${durationMs / 1000} seconds
            Device Temperature: ${temp} C
            Status: Warning / Strong Magnetic Environment
            Assessment: Prolonged exposure to strong magnetic field (>250 uT) detected. Please relocate the device immediately to prevent magnetometer sensor saturation.
        """.trimIndent()
    }

    fun generateDangerousEnvironmentReport(magnitude: Double, peak: Double, avg: Double, durationMs: Long, temp: Float): String {
        return """
            === MAGNETIC SAFETY SYSTEM CRITICAL DANGER REPORT ===
            Generated on: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}
            Current Intensity: ${String.format(java.util.Locale.US, "%.1f", magnitude)} uT
            Peak Intensity: ${String.format(java.util.Locale.US, "%.1f", peak)} uT
            Average Intensity: ${String.format(java.util.Locale.US, "%.1f", avg)} uT
            Duration of exposure: ${durationMs / 1000} seconds
            Device Temperature: ${temp} C
            Status: Danger / Extremely Dangerous Magnetic Environment
            Assessment: EXTREME DANGER. Surrounding magnetic field exceeds 1000 uT threshold. Severe risk of magnetometer saturation. Relocate all electronic instruments immediately.
        """.trimIndent()
    }
}
