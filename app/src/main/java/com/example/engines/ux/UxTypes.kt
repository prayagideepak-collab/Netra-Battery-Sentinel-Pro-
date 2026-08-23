package com.example.engines.ux

enum class AppThemeMode {
    DYNAMIC_MATERIAL_3,
    PURE_AMOLED_BLACK,
    HIGH_CONTRAST_ACCESSIBILITY,
    COLORBLIND_PROTANOPIA,
    COLORBLIND_DEUTERANOPIA
}

data class UxAccessibilitySettings(
    val themeMode: AppThemeMode = AppThemeMode.DYNAMIC_MATERIAL_3,
    val isHapticFeedbackEnabled: Boolean = true,
    val isLowPowerAnimationEnabled: Boolean = false,
    val isLargeTextModeEnabled: Boolean = false,
    val isHighContrastActive: Boolean = false,
    val screenReaderOptimized: Boolean = true
)
