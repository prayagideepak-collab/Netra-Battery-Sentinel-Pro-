package com.example.engines.charging

enum class ProfileVerificationStatus {
    VERIFIED_OFFICIAL_SPEC,
    UNVERIFIED_GENERIC_PROFILE
}

data class DeviceChargingProfile(
    val manufacturer: String,
    val model: String,
    val hardwareVariant: String,
    val androidVersion: String,
    val designBatteryCapacityMah: Int?,
    val maxOfficialWiredChargingWatts: Float?,
    val supportedChargingStandards: List<String>,
    val verificationStatus: ProfileVerificationStatus,
    val profileSource: String,
    val importedTimestampMs: Long,
    val profileVersion: String = "1.0.0"
)
