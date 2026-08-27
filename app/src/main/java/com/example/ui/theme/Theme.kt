package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.engines.festival.FestivalContextEngine
import java.util.Calendar

// Standard Light
val StandardLightColorScheme = lightColorScheme(
    primary = Color(0xFF0D6D44),
    secondary = Color(0xFF2E7D32),
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF7FAF7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEBF2EB),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F1E16),
    onSurface = Color(0xFF0F1E16),
    onSurfaceVariant = Color(0xFF42564A),
    outline = Color(0xFF728A7A),
    outlineVariant = Color(0xFFD4E2D8)
)

// Sentinel Cyberpunk Neon Green Dark
val SentinelDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00FF66),
    secondary = Color(0xFF10E575),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF080C09),
    surface = Color(0xFF121914),
    surfaceVariant = Color(0xFF1B261F),
    onPrimary = Color(0xFF021609),
    onSecondary = Color(0xFF021609),
    onTertiary = Color(0xFF082030),
    onBackground = Color(0xFFF0FDF4),
    onSurface = Color(0xFFF0FDF4),
    onSurfaceVariant = Color(0xFF9EBAA8),
    outline = Color(0xFF274332),
    outlineVariant = Color(0xFF1A2F22)
)

// AMOLED Pitch Black
val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF00FF66),
    secondary = Color(0xFF10E575),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF141414),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFF5FFF8),
    onSurface = Color(0xFFF5FFF8),
    onSurfaceVariant = Color(0xFFA5B8AC),
    outline = Color(0xFF223328),
    outlineVariant = Color(0xFF152219)
)

// Diwali Festival of Lights (Deep Gold, Amber Flame & Diya Warmth)
val DiwaliColorScheme = darkColorScheme(
    primary = Color(0xFFFFB300), // Rich Diya Gold
    secondary = Color(0xFFFF6D00), // Sacred Saffron Amber
    tertiary = Color(0xFFFFD54F),
    background = Color(0xFF140A02), // Warm Midnight
    surface = Color(0xFF241405), // Glowing Brass
    surfaceVariant = Color(0xFF381F08),
    onPrimary = Color(0xFF3E1F00),
    onSecondary = Color.White,
    onBackground = Color(0xFFFFF8E1),
    onSurface = Color(0xFFFFF8E1),
    onSurfaceVariant = Color(0xFFFFE082),
    outline = Color(0xFF663C00),
    outlineVariant = Color(0xFF422800)
)

// Holi Festival of Colors (Vibrant Magenta, Electric Cyan & Sunlight Yellow)
val HoliColorScheme = darkColorScheme(
    primary = Color(0xFFFF2A85), // Gulal Magenta
    secondary = Color(0xFF00E5FF), // Cyan Splash
    tertiary = Color(0xFFFFEB3B), // Turmeric Yellow
    background = Color(0xFF120516), // Deep Plum
    surface = Color(0xFF230B2A), // Festive Velvet
    surfaceVariant = Color(0xFF381242),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFFDF4FF),
    onSurface = Color(0xFFFDF4FF),
    onSurfaceVariant = Color(0xFFF5D0FE),
    outline = Color(0xFF701A75),
    outlineVariant = Color(0xFF4A044E)
)

// Navratri & Durga Puja (Royal Scarlet, Saffron & Radiant Devi Gold)
val NavratriColorScheme = darkColorScheme(
    primary = Color(0xFFFF3D00), // Vermilion Red
    secondary = Color(0xFFFFD700), // Divine Gold
    tertiary = Color(0xFFE91E63), // Sindoor Pink
    background = Color(0xFF1A0505),
    surface = Color(0xFF2B0A0D),
    surfaceVariant = Color(0xFF401116),
    onPrimary = Color.White,
    onSecondary = Color(0xFF371C00),
    onBackground = Color(0xFFFFF1F2),
    onSurface = Color(0xFFFFF1F2),
    onSurfaceVariant = Color(0xFFFECDD3),
    outline = Color(0xFF881337),
    outlineVariant = Color(0xFF4C0519)
)

// Eid Mubarak (Sacred Emerald & Crescent Starlight Gold)
val EidColorScheme = darkColorScheme(
    primary = Color(0xFF00E676), // Bright Crescent Green
    secondary = Color(0xFFFFD54F), // Star Gold
    tertiary = Color(0xFF26A69A),
    background = Color(0xFF03160F), // Deep Emerald Night
    surface = Color(0xFF08261B),
    surfaceVariant = Color(0xFF0F3828),
    onPrimary = Color(0xFF003818),
    onSecondary = Color(0xFF3E2723),
    onBackground = Color(0xFFE8F5E9),
    onSurface = Color(0xFFE8F5E9),
    onSurfaceVariant = Color(0xFFA7F3D0),
    outline = Color(0xFF065F46),
    outlineVariant = Color(0xFF022C22)
)

// Christmas & Winter Solstice (Pine Forest, Berry Crimson & Starlight)
val ChristmasColorScheme = darkColorScheme(
    primary = Color(0xFFFF334B), // Berry Red
    secondary = Color(0xFF00E676), // Pine Green
    tertiary = Color(0xFFFFD700), // Star Yellow
    background = Color(0xFF0C140E),
    surface = Color(0xFF152219),
    surfaceVariant = Color(0xFF1F3325),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color(0xFFF1F8F3),
    onSurface = Color(0xFFF1F8F3),
    onSurfaceVariant = Color(0xFFBBF7D0),
    outline = Color(0xFF166534),
    outlineVariant = Color(0xFF14532D)
)

// Independence & Republic Day (Tiranga Saffron, White & Ashoka Chakra Navy)
val IndependenceColorScheme = darkColorScheme(
    primary = Color(0xFFFF9933), // Kesari Saffron
    secondary = Color(0xFF138808), // India Green
    tertiary = Color(0xFF38BDF8), // Chakra Blue
    background = Color(0xFF080F1E), // Deep Chakra Navy
    surface = Color(0xFF101B30),
    surfaceVariant = Color(0xFF182949),
    onPrimary = Color(0xFF2A1000),
    onSecondary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    onSurfaceVariant = Color(0xFFBAE6FD),
    outline = Color(0xFF0369A1),
    outlineVariant = Color(0xFF0C4A6E)
)

// Makar Sankranti & Pongal (Sky Azure, Sun Gold & Harvest Green)
val MakarSankrantiColorScheme = darkColorScheme(
    primary = Color(0xFFFFC107), // Solar Yellow
    secondary = Color(0xFF00E5FF), // Kite Azure
    tertiary = Color(0xFF66BB6A), // Harvest Sprout
    background = Color(0xFF141103),
    surface = Color(0xFF262007),
    surfaceVariant = Color(0xFF3B320C),
    onPrimary = Color(0xFF332000),
    onSecondary = Color.Black,
    onBackground = Color(0xFFFFFDE7),
    onSurface = Color(0xFFFFFDE7),
    onSurfaceVariant = Color(0xFFFFF59D),
    outline = Color(0xFF856404),
    outlineVariant = Color(0xFF533F03)
)

// Ganesh Chaturthi (Sindoor Orange, Marigold Yellow & Modak Gold)
val GaneshChaturthiColorScheme = darkColorScheme(
    primary = Color(0xFFFF6F00), // Vibrant Marigold
    secondary = Color(0xFFFFD600), // Modak Gold
    tertiary = Color(0xFFD81B60), // Sacred Sindoor
    background = Color(0xFF170902),
    surface = Color(0xFF2B1305),
    surfaceVariant = Color(0xFF421E09),
    onPrimary = Color(0xFF301000),
    onSecondary = Color.Black,
    onBackground = Color(0xFFFFF3E0),
    onSurface = Color(0xFFFFF3E0),
    onSurfaceVariant = Color(0xFFFFCC80),
    outline = Color(0xFF8D3E00),
    outlineVariant = Color(0xFF572500)
)

// New Year Gala (Midnight Blue & Champagne Gold)
val NewYearColorScheme = darkColorScheme(
    primary = Color(0xFFF59E0B), // Champagne Gold
    secondary = Color(0xFFA855F7), // Neon Violet
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF0B0F19), // Midnight Starlight
    surface = Color(0xFF141C2E),
    surfaceVariant = Color(0xFF1E293B),
    onPrimary = Color(0xFF2E1A00),
    onSecondary = Color.White,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155)
)

// Oceanic Blue
val OceanBlueColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    secondary = Color(0xFF0284C7),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF04111D),
    surface = Color(0xFF081F33),
    surfaceVariant = Color(0xFF0E2C48),
    onPrimary = Color(0xFF00293B),
    onSecondary = Color.White,
    onBackground = Color(0xFFE0F2FE),
    onSurface = Color(0xFFE0F2FE),
    onSurfaceVariant = Color(0xFF7DD3FC),
    outline = Color(0xFF0369A1),
    outlineVariant = Color(0xFF0C4A6E)
)

// Solar Gold
val SolarGoldColorScheme = darkColorScheme(
    primary = Color(0xFFFBBF24),
    secondary = Color(0xFFF59E0B),
    tertiary = Color(0xFFEAB308),
    background = Color(0xFF140E02),
    surface = Color(0xFF241A05),
    surfaceVariant = Color(0xFF382909),
    onPrimary = Color(0xFF332000),
    onSecondary = Color.White,
    onBackground = Color(0xFFFEF3C7),
    onSurface = Color(0xFFFEF3C7),
    onSurfaceVariant = Color(0xFFFDE68A),
    outline = Color(0xFFB45309),
    outlineVariant = Color(0xFF78350F)
)

// Aurora Purple
val AuroraPurpleColorScheme = darkColorScheme(
    primary = Color(0xFFC084FC),
    secondary = Color(0xFF9333EA),
    tertiary = Color(0xFF38BDF8),
    background = Color(0xFF0F071D),
    surface = Color(0xFF1C0E36),
    surfaceVariant = Color(0xFF2C1652),
    onPrimary = Color(0xFF240046),
    onSecondary = Color.White,
    onBackground = Color(0xFFF3E8FF),
    onSurface = Color(0xFFF3E8FF),
    onSurfaceVariant = Color(0xFFD8B4FE),
    outline = Color(0xFF7E22CE),
    outlineVariant = Color(0xFF581C87)
)

// Forest Emerald
val ForestEmeraldColorScheme = darkColorScheme(
    primary = Color(0xFF10B981),
    secondary = Color(0xFF059669),
    tertiary = Color(0xFF34D399),
    background = Color(0xFF041710),
    surface = Color(0xFF08261B),
    surfaceVariant = Color(0xFF0D3B2A),
    onPrimary = Color(0xFF012B1B),
    onSecondary = Color.White,
    onBackground = Color(0xFFD1FAE5),
    onSurface = Color(0xFFD1FAE5),
    onSurfaceVariant = Color(0xFFA7F3D0),
    outline = Color(0xFF047857),
    outlineVariant = Color(0xFF064E3B)
)

// Low Battery Red Theme Palette (< 20%)
val LowBatteryDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF3548),
    secondary = Color(0xFFFF6B6B),
    tertiary = Color(0xFFFF8E8E),
    background = Color(0xFF1C0607),
    surface = Color(0xFF2A0A0C),
    surfaceVariant = Color(0xFF3D1013),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFFFECEC),
    onSurface = Color(0xFFFFECEC),
    outline = Color(0xFF7F1D1D)
)

val LowBatteryLightColorScheme = lightColorScheme(
    primary = Color(0xFFD32F2F),
    secondary = Color(0xFFE53935),
    tertiary = Color(0xFFEF5350),
    background = Color(0xFFFFF0F0),
    surface = Color(0xFFFFE5E5),
    surfaceVariant = Color(0xFFFFD6D6),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF2C0505),
    onSurface = Color(0xFF2C0505)
)

private fun blendColors(color1: Color, color2: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = color1.red + (color2.red - color1.red) * f,
        green = color1.green + (color2.green - color1.green) * f,
        blue = color1.blue + (color2.blue - color1.blue) * f,
        alpha = color1.alpha + (color2.alpha - color1.alpha) * f
    )
}

private data class DynamicPalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color
)

fun getDynamicBatteryColorScheme(batteryLevel: Int, darkTheme: Boolean = true): ColorScheme {
    val level = batteryLevel.coerceIn(0, 100)

    if (darkTheme) {
        val palette = when {
            level <= 20 -> {
                val fraction = level / 20f
                DynamicPalette(
                    primary = blendColors(Color(0xFFFF3548), Color(0xFFFF8C00), fraction),
                    secondary = blendColors(Color(0xFFFF6B6B), Color(0xFFFFA726), fraction),
                    tertiary = blendColors(Color(0xFFFF8E8E), Color(0xFFFFCA28), fraction),
                    background = blendColors(Color(0xFF1C0607), Color(0xFF1A1104), fraction),
                    surface = blendColors(Color(0xFF2A0A0C), Color(0xFF291B07), fraction),
                    surfaceVariant = blendColors(Color(0xFF3D1013), Color(0xFF3A270B), fraction),
                    onBackground = blendColors(Color(0xFFFFECEC), Color(0xFFFFF3E0), fraction)
                )
            }
            level <= 50 -> {
                val fraction = (level - 20) / 30f
                DynamicPalette(
                    primary = blendColors(Color(0xFFFF8C00), Color(0xFF00D2FF), fraction),
                    secondary = blendColors(Color(0xFFFFA726), Color(0xFF38BDF8), fraction),
                    tertiary = blendColors(Color(0xFFFFCA28), Color(0xFF818CF8), fraction),
                    background = blendColors(Color(0xFF1A1104), Color(0xFF06121E), fraction),
                    surface = blendColors(Color(0xFF291B07), Color(0xFF0B1E30), fraction),
                    surfaceVariant = blendColors(Color(0xFF3A270B), Color(0xFF122C44), fraction),
                    onBackground = blendColors(Color(0xFFFFF3E0), Color(0xFFF0F9FF), fraction)
                )
            }
            level <= 80 -> {
                val fraction = (level - 50) / 30f
                DynamicPalette(
                    primary = blendColors(Color(0xFF00D2FF), Color(0xFF10B981), fraction),
                    secondary = blendColors(Color(0xFF38BDF8), Color(0xFF34D399), fraction),
                    tertiary = blendColors(Color(0xFF818CF8), Color(0xFF2DD4BF), fraction),
                    background = blendColors(Color(0xFF06121E), Color(0xFF051811), fraction),
                    surface = blendColors(Color(0xFF0B1E30), Color(0xFF0A271C), fraction),
                    surfaceVariant = blendColors(Color(0xFF122C44), Color(0xFF113A2B), fraction),
                    onBackground = blendColors(Color(0xFFF0F9FF), Color(0xFFECFDF5), fraction)
                )
            }
            else -> {
                val fraction = (level - 80) / 20f
                DynamicPalette(
                    primary = blendColors(Color(0xFF10B981), Color(0xFF00FF87), fraction),
                    secondary = blendColors(Color(0xFF34D399), Color(0xFF00F5D4), fraction),
                    tertiary = blendColors(Color(0xFF2DD4BF), Color(0xFFA855F7), fraction),
                    background = blendColors(Color(0xFF051811), Color(0xFF04140D), fraction),
                    surface = blendColors(Color(0xFF0A271C), Color(0xFF082216), fraction),
                    surfaceVariant = blendColors(Color(0xFF113A2B), Color(0xFF0F3523), fraction),
                    onBackground = blendColors(Color(0xFFECFDF5), Color(0xFFFFFFFF), fraction)
                )
            }
        }

        return darkColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceVariant,
            onPrimary = Color.Black,
            onSecondary = Color.Black,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground
        )
    } else {
        val palette = when {
            level <= 20 -> {
                val fraction = level / 20f
                DynamicPalette(
                    primary = blendColors(Color(0xFFD32F2F), Color(0xFFE65100), fraction),
                    secondary = blendColors(Color(0xFFE53935), Color(0xFFF57C00), fraction),
                    tertiary = blendColors(Color(0xFFEF5350), Color(0xFFFFB300), fraction),
                    background = blendColors(Color(0xFFFFF0F0), Color(0xFFFFF8F0), fraction),
                    surface = blendColors(Color(0xFFFFE5E5), Color(0xFFFFEEDC), fraction),
                    surfaceVariant = blendColors(Color(0xFFFFD6D6), Color(0xFFFFE4C4), fraction),
                    onBackground = blendColors(Color(0xFF2C0505), Color(0xFF2E1904), fraction)
                )
            }
            level <= 50 -> {
                val fraction = (level - 20) / 30f
                DynamicPalette(
                    primary = blendColors(Color(0xFFE65100), Color(0xFF0284C7), fraction),
                    secondary = blendColors(Color(0xFFF57C00), Color(0xFF0369A1), fraction),
                    tertiary = blendColors(Color(0xFFFFB300), Color(0xFF4338CA), fraction),
                    background = blendColors(Color(0xFFFFF8F0), Color(0xFFF0F9FF), fraction),
                    surface = blendColors(Color(0xFFFFEEDC), Color(0xFFE0F2FE), fraction),
                    surfaceVariant = blendColors(Color(0xFFFFE4C4), Color(0xFFBAE6FD), fraction),
                    onBackground = blendColors(Color(0xFF2E1904), Color(0xFF0C2A3A), fraction)
                )
            }
            level <= 80 -> {
                val fraction = (level - 50) / 30f
                DynamicPalette(
                    primary = blendColors(Color(0xFF0284C7), Color(0xFF059669), fraction),
                    secondary = blendColors(Color(0xFF0369A1), Color(0xFF047857), fraction),
                    tertiary = blendColors(Color(0xFF4338CA), Color(0xFF0D9488), fraction),
                    background = blendColors(Color(0xFFF0FFF), Color(0xFFF0FDF4), fraction),
                    surface = blendColors(Color(0xFFE0F2FE), Color(0xFFDCFCE7), fraction),
                    surfaceVariant = blendColors(Color(0xFFBAE6FD), Color(0xFFBBF7D0), fraction),
                    onBackground = blendColors(Color(0xFF0C2A3A), Color(0xFF062E1B), fraction)
                )
            }
            else -> {
                val fraction = (level - 80) / 20f
                DynamicPalette(
                    primary = blendColors(Color(0xFF059669), Color(0xFF00A859), fraction),
                    secondary = blendColors(Color(0xFF047857), Color(0xFF00897B), fraction),
                    tertiary = blendColors(Color(0xFF0D9488), Color(0xFF7C3AED), fraction),
                    background = blendColors(Color(0xFFF0FDF4), Color(0xFFEBFDF3), fraction),
                    surface = blendColors(Color(0xFFDCFCE7), Color(0xFFD3FAF0), fraction),
                    surfaceVariant = blendColors(Color(0xFFBBF7D0), Color(0xFFB8F5DD), fraction),
                    onBackground = blendColors(Color(0xFF062E1B), Color(0xFF022013), fraction)
                )
            }
        }

        return lightColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceVariant,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = palette.onBackground,
            onSurface = palette.onBackground
        )
    }
}

/**
 * Returns the corresponding festival color scheme for the given calendar month/day or festival key.
 */
fun getAutoFestivalColorScheme(): ColorScheme {
    val cal = Calendar.getInstance()
    val month = cal.get(Calendar.MONTH) // 0-indexed: 0=Jan, 7=Aug, 9=Oct, 10=Nov, 11=Dec
    val day = cal.get(Calendar.DAY_OF_MONTH)

    return when {
        // August 15 (Independence Day) or August Janmashtami / Raksha Bandhan
        month == Calendar.AUGUST && day in 14..16 -> IndependenceColorScheme
        // October / November (Diwali & Navratri Season)
        month == Calendar.OCTOBER || (month == Calendar.NOVEMBER && day <= 15) -> DiwaliColorScheme
        month == Calendar.SEPTEMBER -> GaneshChaturthiColorScheme
        month == Calendar.MARCH -> HoliColorScheme
        month == Calendar.DECEMBER && day >= 20 -> ChristmasColorScheme
        month == Calendar.JANUARY && day in 13..16 -> MakarSankrantiColorScheme
        month == Calendar.JANUARY && day in 25..27 -> IndependenceColorScheme
        month == Calendar.APRIL -> EidColorScheme
        else -> DiwaliColorScheme
    }
}

fun resolveThemeColorScheme(themeMode: String, darkTheme: Boolean, batteryLevel: Int = 100): ColorScheme {
    return when (themeMode.uppercase().trim()) {
        "LIGHT" -> StandardLightColorScheme
        "DARK", "SENTINEL" -> SentinelDarkColorScheme
        "AMOLED" -> AmoledColorScheme
        "DYNAMIC" -> getDynamicBatteryColorScheme(batteryLevel = batteryLevel, darkTheme = darkTheme)
        "FESTIVAL_AUTO", "FESTIVAL" -> getAutoFestivalColorScheme()
        "DIWALI" -> DiwaliColorScheme
        "HOLI" -> HoliColorScheme
        "NAVRATRI" -> NavratriColorScheme
        "EID" -> EidColorScheme
        "CHRISTMAS" -> ChristmasColorScheme
        "INDEPENDENCE", "TIRANGA" -> IndependenceColorScheme
        "MAKAR_SANKRANTI", "PONGAL" -> MakarSankrantiColorScheme
        "GANESH_CHATURTHI" -> GaneshChaturthiColorScheme
        "NEW_YEAR" -> NewYearColorScheme
        "OCEAN_BLUE" -> OceanBlueColorScheme
        "SOLAR_GOLD" -> SolarGoldColorScheme
        "AURORA_PURPLE" -> AuroraPurpleColorScheme
        "FOREST_EMERALD" -> ForestEmeraldColorScheme
        "CRIMSON_ALERT" -> LowBatteryDarkColorScheme
        else -> if (darkTheme) SentinelDarkColorScheme else StandardLightColorScheme
    }
}

@Composable
fun MyApplicationTheme(
    themeMode: String = "SYSTEM",
    batteryLevel: Int = 100,
    lowBatteryRedThemeEnabled: Boolean = true,
    dynamicColorEngineEnabled: Boolean = false,
    temperature: Float = 30f,
    isCharging: Boolean = false,
    health: Int = 100,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val isDynamic = dynamicColorEngineEnabled || themeMode.uppercase() == "DYNAMIC"
    val isLowBattery = lowBatteryRedThemeEnabled && batteryLevel in 1..19 && !isDynamic && themeMode.uppercase() == "SYSTEM"
    val isOverheated = temperature >= 45f && themeMode.uppercase() == "SYSTEM"

    val colorScheme = when {
        isOverheated -> if (darkTheme) LowBatteryDarkColorScheme else LowBatteryLightColorScheme
        isLowBattery -> if (darkTheme) LowBatteryDarkColorScheme else LowBatteryLightColorScheme
        else -> GlobalThemeCoordinator.resolveAuthoritativeTheme(themeMode = themeMode, darkTheme = darkTheme, batteryLevel = batteryLevel)
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
