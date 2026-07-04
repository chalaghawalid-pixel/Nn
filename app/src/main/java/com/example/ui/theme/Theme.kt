package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryGreen,
    secondary = DarkSecondarySage,
    tertiary = DarkAccentGold,
    background = DarkBg,
    surface = DarkSurface,
    onPrimary = OnPrimaryDark,
    onBackground = DarkSecondarySage,
    onSurface = DarkSecondarySage
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    secondary = SecondarySage,
    tertiary = AccentGold,
    background = LightBg,
    surface = LightSurface,
    onPrimary = OnPrimaryLight,
    onBackground = SecondarySage,
    onSurface = SecondarySage
)

@Composable
fun MyApplicationTheme(
    appTheme: String = "مصحف أخضر",
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Keep dynamic color disabled to preserve the custom theme across all devices
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = darkTheme || appTheme == "كلاسيكي غامق"

    val colorScheme = if (isDark) {
        when (appTheme) {
            "أزرق سماوي" -> darkColorScheme(
                primary = DarkPrimaryBlue,
                secondary = DarkSecondaryBlue,
                background = Color(0xFF111318),
                surface = Color(0xFF1A1C22),
                onPrimary = Color(0xFF003258),
                onBackground = Color(0xFFE2E2E9),
                onSurface = Color(0xFFE2E2E9)
            )
            "ذهبي دافئ" -> darkColorScheme(
                primary = DarkPrimaryGold,
                secondary = DarkSecondaryGold,
                background = Color(0xFF14130D),
                surface = Color(0xFF1E1C14),
                onPrimary = Color(0xFF3E2F00),
                onBackground = Color(0xFFE8E2D4),
                onSurface = Color(0xFFE8E2D4)
            )
            "كلاسيكي غامق" -> darkColorScheme(
                primary = Color(0xFFD0BCFF),
                secondary = Color(0xFFCCC2DC),
                background = Color(0xFF1C1B1F),
                surface = Color(0xFF25242A),
                onPrimary = Color(0xFF381E72),
                onBackground = Color(0xFFE6E1E5),
                onSurface = Color(0xFFE6E1E5)
            )
            else -> DarkColorScheme // "مصحف أخضر"
        }
    } else {
        when (appTheme) {
            "أزرق سماوي" -> lightColorScheme(
                primary = PrimaryBlue,
                secondary = SecondaryBlue,
                background = Color(0xFFF8F9FF),
                surface = Color(0xFFFFFFFF),
                onPrimary = Color(0xFFFFFFFF),
                onBackground = Color(0xFF191C20),
                onSurface = Color(0xFF191C20)
            )
            "ذهبي دافئ" -> lightColorScheme(
                primary = PrimaryGold,
                secondary = SecondaryGold,
                background = Color(0xFFFFFDF5),
                surface = Color(0xFFFFFFFF),
                onPrimary = Color(0xFFFFFFFF),
                onBackground = Color(0xFF1E1B10),
                onSurface = Color(0xFF1E1B10)
            )
            else -> LightColorScheme // "مصحف أخضر"
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
