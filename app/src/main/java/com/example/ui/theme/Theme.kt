package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SlateLightPrimary,
    secondary = SlateLightSecondary,
    tertiary = SlateLightTertiary,
    background = SlateLightBackground,
    surface = SlateLightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF131F18),
    onSurface = Color(0xFF131F18)
)

private val DarkColorScheme = darkColorScheme(
    primary = CosmicPrimary,
    secondary = CosmicSecondary,
    tertiary = CosmicTertiary,
    background = CosmicDarkBackground,
    surface = CosmicDarkSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color(0xFFE2F3E7),
    onSurface = Color(0xFFE2F3E7)
)

private val AmoledColorScheme = darkColorScheme(
    primary = CosmicPrimary,
    secondary = CosmicSecondary,
    tertiary = CosmicTertiary,
    background = AmoledBackground,
    surface = AmoledSurface,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MyApplicationTheme(
    themeMode: String = "SYSTEM",
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when (themeMode.uppercase()) {
        "LIGHT" -> LightColorScheme
        "DARK" -> DarkColorScheme
        "AMOLED" -> AmoledColorScheme
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

