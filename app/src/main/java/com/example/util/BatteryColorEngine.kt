package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp

/**
 * Netra Dynamic Battery Colour Engine (1% Precision)
 *
 * Provides a continuous, 100-step unique color calculation for every 1% battery state.
 * Implements smooth LERP transitions between 10 primary color families with dynamic glow,
 * charging highlights, low-battery pulse timing, and 100% deep emerald green effects.
 */
object BatteryColorEngine {

    // 10 Primary Colour Families Anchor Points (0% to 100%)
    private val colorAnchors = listOf(
        0f to Color(0xFF800000),    // 0%: Ultra Deep Crimson
        1f to Color(0xFF990000),    // 1%: Deep Red (End of 1-10% Family)
        5f to Color(0xFFC70A00),    // 5%: Intense Red
        10f to Color(0xFFE51100),   // 10%: Bright Red
        11f to Color(0xFFFF1F00),   // 11%: Orange Red Family Start
        15f to Color(0xFFFF3800),   // 15%: Vivid Orange Red
        20f to Color(0xFFFF5200),   // 20%: Warm Orange Red
        21f to Color(0xFFFF6A00),   // 21%: Orange Family Start
        25f to Color(0xFFFF8200),   // 25%: Pure Orange
        30f to Color(0xFFFF9800),   // 30%: Rich Orange
        31f to Color(0xFFFFAC00),   // 31%: Amber Family Start
        35f to Color(0xFFFFC100),   // 35%: Golden Amber
        40f to Color(0xFFFFD500),   // 40%: Bright Amber
        41f to Color(0xFFEFE000),   // 41%: Yellow Family Start
        45f to Color(0xFFE4EB00),   // 45%: Warm Yellow
        50f to Color(0xFFD7F40D),   // 50%: Electric Yellow
        51f to Color(0xFFCAF11E),   // 51%: Lime Family Start
        55f to Color(0xFFBCEE2F),   // 55%: Vibrant Lime
        60f to Color(0xFFACEA3F),   // 60%: Bright Lime
        61f to Color(0xFF9DE74F),   // 61%: Yellow Green Family Start
        65f to Color(0xFF8CE35E),   // 65%: Soft Yellow Green
        70f to Color(0xFF74DE6C),   // 70%: Spring Yellow Green
        71f to Color(0xFF62D66D),   // 71%: Light Green Family Start
        75f to Color(0xFF50CC66),   // 75%: Fresh Light Green
        80f to Color(0xFF3FC15F),   // 80%: Rich Light Green
        81f to Color(0xFF35B256),   // 81%: Green Family Start
        85f to Color(0xFF2CA34D),   // 85%: True Green
        90f to Color(0xFF229243),   // 90%: Deep Green
        91f to Color(0xFF1B823B),   // 91%: Dark Green Family Start
        95f to Color(0xFF137032),   // 95%: Rich Dark Green
        99f to Color(0xFF0D5E2A),   // 99%: Near Full Dark Green
        100f to Color(0xFF074B20)   // 100%: Deep Emerald Green (Full Saturation)
    )

    /**
     * Get the dynamic unique color for a given battery percentage (0.0 to 100.0).
     * Every integer percentage from 1 to 100 produces a unique, distinct color state.
     */
    fun getColor(pct: Float): Color {
        val clampedPct = pct.coerceIn(0f, 100f)

        if (clampedPct <= colorAnchors.first().first) return colorAnchors.first().second
        if (clampedPct >= colorAnchors.last().first) return colorAnchors.last().second

        for (i in 0 until colorAnchors.size - 1) {
            val (p1, c1) = colorAnchors[i]
            val (p2, c2) = colorAnchors[i + 1]
            if (clampedPct >= p1 && clampedPct <= p2) {
                val fraction = (clampedPct - p1) / (p2 - p1)
                return lerp(c1, c2, fraction)
            }
        }
        return colorAnchors.last().second
    }

    /**
     * Get the dynamic color for integer percentage.
     */
    fun getColor(pct: Int): Color = getColor(pct.toFloat())

    /**
     * Get the 10 Primary Color Family Name according to Rule 2 specification.
     */
    fun getFamilyName(pct: Float): String {
        val p = pct.coerceIn(1f, 100f).toInt()
        return when (p) {
            in 91..100 -> "Dark Green"
            in 81..90 -> "Green"
            in 71..80 -> "Light Green"
            in 61..70 -> "Yellow Green"
            in 51..60 -> "Lime"
            in 41..50 -> "Yellow"
            in 31..40 -> "Amber"
            in 21..30 -> "Orange"
            in 11..20 -> "Orange Red"
            else -> "Deep Red"
        }
    }

    /**
     * Get subtle glow color derived from current dynamic battery color.
     */
    fun getGlowColor(pct: Float): Color {
        val baseColor = getColor(pct)
        return baseColor.copy(alpha = getGlowAlpha(pct, false))
    }

    /**
     * Get glow alpha intensity depending on percentage & charging state.
     */
    fun getGlowAlpha(pct: Float, isCharging: Boolean): Float {
        val baseAlpha = when {
            pct >= 100f -> 0.45f
            pct >= 90f -> 0.35f
            pct <= 10f -> 0.40f
            else -> 0.25f
        }
        return if (isCharging) (baseAlpha + 0.20f).coerceAtMost(0.85f) else baseAlpha
    }

    /**
     * Pulse period in milliseconds for low battery indication.
     * Below 5%: 800ms fast pulse
     * Below 10%: 1500ms slow pulse
     * Otherwise: 0ms (no pulse)
     */
    fun getPulsePeriodMs(pct: Float): Int {
        return when {
            pct < 5f -> 800
            pct < 10f -> 1500
            else -> 0
        }
    }

    /**
     * Generates a multi-stop brush gradient centered around the dynamic battery color.
     */
    fun getGradient(pct: Float): Brush {
        val mainColor = getColor(pct)
        val lighterShade = lerp(mainColor, Color.White, 0.25f)
        val darkerShade = lerp(mainColor, Color.Black, 0.20f)

        return Brush.horizontalGradient(
            colors = listOf(darkerShade, mainColor, lighterShade, mainColor)
        )
    }

    /**
     * Utility method returning all 100 unique color states for 1%..100%
     */
    fun getAll100Colors(): List<Pair<Int, Color>> {
        return (1..100).map { pct ->
            pct to getColor(pct.toFloat())
        }
    }
}
