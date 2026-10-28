package com.example.pulsetransit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.ui.platform.SystemBarsAppearance

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkPrimary,
        background = DarkBackground,
        surface = DarkSurface,
        onBackground = DarkTextPrimary,
        onSurface = DarkTextPrimary,
        surfaceVariant = DarkCard,
        onSurfaceVariant = DarkTextSecondary,
        error = Color(0xFFFFB4AB), // M3 Dark Error
        onError = Color(0xFF690005),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = LightPrimary,
        background = LightBackground,
        surface = LightSurface,
        onBackground = LightTextPrimary,
        onSurface = LightTextPrimary,
        surfaceVariant = LightCard,
        onSurfaceVariant = LightTextSecondary,
        error = Color(0xFFBA1A1A), // M3 Light Error
        onError = Color(0xFFFFFFFF),
    )

/** The app's own colours, for surfaces outside Compose UI (the Glance widgets before Android 12). */
val PulseLightColorScheme: ColorScheme get() = LightColorScheme
val PulseDarkColorScheme: ColorScheme get() = DarkColorScheme

@Composable
fun PulseTransitTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO -> isSystemInDarkTheme() // Default to system if not handled externally
        }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    SystemBarsAppearance(darkTheme)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
