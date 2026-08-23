package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun testThemeToggleBetweenSentinelDarkAndStandardLight() {
        val currentThemeDark = "DARK"
        val nextFromDark = if (currentThemeDark.uppercase() == "LIGHT") "DARK" else "LIGHT"
        assertEquals("LIGHT", nextFromDark)

        val currentThemeLight = "LIGHT"
        val nextFromLight = if (currentThemeLight.uppercase() == "LIGHT") "DARK" else "LIGHT"
        assertEquals("DARK", nextFromLight)
    }

    @Test
    fun testSentinelDarkAliases() {
        val darkAliases = listOf("DARK", "SENTINEL")
        for (alias in darkAliases) {
            val isDark = when (alias.uppercase()) {
                "LIGHT" -> false
                "DARK", "SENTINEL", "AMOLED" -> true
                else -> false
            }
            assertEquals(true, isDark)
        }
    }
}
