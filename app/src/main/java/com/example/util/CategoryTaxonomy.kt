package com.example.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class NetraCategory(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
) {
    BATTERY(
        id = "BATTERY",
        title = "Battery",
        description = "Low battery, predictions, state changes & thresholds",
        icon = Icons.Filled.BatteryAlert,
        color = Color(0xFF4CAF50)
    ),
    CHARGING(
        id = "CHARGING",
        title = "Charging",
        description = "Charger connection, fast charging, session history & full battery",
        icon = Icons.Filled.Power,
        color = Color(0xFF00BCD4)
    ),
    THERMAL(
        id = "THERMAL",
        title = "Thermal",
        description = "Temperature transitions, critical alerts, thermal control & recovery",
        icon = Icons.Filled.Thermostat,
        color = Color(0xFFF44336)
    ),
    NETWORK(
        id = "NETWORK",
        title = "Network",
        description = "Discrete Wi-Fi, handover, internet state, airplane mode & Bluetooth",
        icon = Icons.Filled.Wifi,
        color = Color(0xFF2196F3)
    ),
    APPLICATION_ACTIVITY(
        id = "APPLICATION_ACTIVITY",
        title = "Application Activity",
        description = "Heavy app activity, high data intensity & power attribution",
        icon = Icons.Filled.Apps,
        color = Color(0xFFFF9800)
    ),
    PERMISSIONS(
        id = "PERMISSIONS",
        title = "Permissions",
        description = "Permission status, requests, revocations & access alerts",
        icon = Icons.Filled.AdminPanelSettings,
        color = Color(0xFF9C27B0)
    ),
    SYSTEM_CONTROL(
        id = "SYSTEM_CONTROL",
        title = "System Control",
        description = "Autonomous actions, hardware protection & user assistance",
        icon = Icons.Filled.Tune,
        color = Color(0xFF009688)
    ),
    WATCHDOG(
        id = "WATCHDOG",
        title = "Watchdog & Recovery",
        description = "Self-healing audits, service health & recovery verification",
        icon = Icons.Filled.Shield,
        color = Color(0xFF673AB7)
    ),
    GENERAL(
        id = "GENERAL",
        title = "General / System",
        description = "General system announcements, app lifecycle & preferences",
        icon = Icons.Filled.Info,
        color = Color(0xFF607D8B)
    );

    companion object {
        fun fromId(id: String?): NetraCategory {
            if (id == null) return GENERAL
            return values().firstOrNull { it.id.equals(id, ignoreCase = true) } ?: when (id.uppercase()) {
                "CHARGER", "CHARGING_SESSION" -> CHARGING
                "TEMPERATURE", "OVERHEAT", "HEAT" -> THERMAL
                "WIFI", "BLUETOOTH", "CELLULAR", "AIRPLANE_MODE", "NETRA" -> NETWORK
                "APP", "APPS", "APPLICATION" -> APPLICATION_ACTIVITY
                "PERMISSION", "SECURITY" -> PERMISSIONS
                "CONTROL", "AUTONOMOUS" -> SYSTEM_CONTROL
                "AUDIT", "SELF_AUDIT", "RECOVERY", "SELF_HEALING" -> WATCHDOG
                else -> GENERAL
            }
        }

        fun classifyEvent(
            category: String?,
            eventType: String?,
            title: String?,
            source: String?
        ): NetraCategory {
            val cat = category?.uppercase() ?: ""
            val evt = eventType?.uppercase() ?: ""
            val tit = title?.uppercase() ?: ""
            val src = source?.uppercase() ?: ""

            // 1. Permissions
            if (cat.contains("PERMISSION") || cat.contains("SECURITY_ACCESS") ||
                evt.contains("PERMISSION") || tit.contains("PERMISSION")
            ) {
                return PERMISSIONS
            }

            // 2. Watchdog & Recovery & Audit
            if (cat.contains("AUDIT") || cat.contains("WATCHDOG") || cat.contains("RECOVERY") ||
                evt.contains("AUDIT") || evt.contains("WATCHDOG") || evt.contains("RECOVERY") ||
                src.contains("AUDIT") || src.contains("WATCHDOG") || tit.contains("SELF-AUDIT") || tit.contains("RECOVERY")
            ) {
                return WATCHDOG
            }

            // 3. Thermal
            if (cat.contains("THERMAL") || cat.contains("TEMPERATURE") ||
                evt.contains("THERMAL") || evt.contains("TEMPERATURE") || evt.contains("OVERHEAT") || evt.contains("HEAT") ||
                tit.contains("THERMAL") || tit.contains("TEMPERATURE") || tit.contains("OVERHEAT") || tit.contains("COOLING")
            ) {
                return THERMAL
            }

            // 4. Network
            if (cat.contains("NETWORK") || cat.contains("WIFI") || cat.contains("BLUETOOTH") || cat.contains("CELLULAR") || cat.contains("NETRA") ||
                evt.contains("WIFI") || evt.contains("BLUETOOTH") || evt.contains("CELLULAR") || evt.contains("AIRPLANE") ||
                evt.contains("INTERNET") || evt.contains("TRANSPORT") || evt.contains("HIGH_DATA") ||
                src.contains("WIFI") || src.contains("BLUETOOTH") || src.contains("CELLULAR") || src.contains("NETRA") || src.contains("RADIO") ||
                tit.contains("WI-FI") || tit.contains("WIFI") || tit.contains("BLUETOOTH") || tit.contains("INTERNET") || tit.contains("AIRPLANE") || tit.contains("NETWORK")
            ) {
                return NETWORK
            }

            // 5. System Control
            if (cat.contains("CONTROL") || cat.contains("POLICY") ||
                evt.contains("CONTROL") || evt.contains("RESTORE") || evt.contains("THROTTLE") ||
                tit.contains("CONTROL") || tit.contains("ACTION REQUIRED")
            ) {
                return SYSTEM_CONTROL
            }

            // 6. Application Activity
            if (cat.contains("APP") || evt.contains("APP") || src.contains("APP") || tit.contains("APP ACTIVITY") || tit.contains("HIGH DRAIN")) {
                return APPLICATION_ACTIVITY
            }

            // 7. Charging
            if (cat.contains("CHARG") || evt.contains("CHARG") || tit.contains("CHARG") || tit.contains("PLUGGED") || tit.contains("UNPLUGGED") || tit.contains("FULLY CHARGED")) {
                return CHARGING
            }

            // 8. Battery
            if (cat.contains("BATTERY") || evt.contains("BATTERY") || evt.contains("DRAIN") || evt.contains("PREDICTION") || evt.contains("THRESHOLD") || tit.contains("BATTERY")) {
                return BATTERY
            }

            return GENERAL
        }
    }
}
