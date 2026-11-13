package com.example.pulsetransit.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.ui.platform.SystemBarsAppearance

// Material 3's roles filled from the Fjord tokens. Fjord has fewer surface levels than M3: cards are
// `surface`, fills and tracks `surface-container`, and sheets, dialogs and menus
// `surface-container-high`. Secondary and tertiary reuse the primary and route roles, so stock
// components (selected chips, segmented buttons) stay in the palette.
private val LightColorScheme =
    with(FjordLight) {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = routeAccent,
            onTertiary = onPrimary,
            tertiaryContainer = route,
            onTertiaryContainer = onRoute,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainer,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = onInverseSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = scrim,
            surfaceBright = surface,
            surfaceDim = surfaceContainer,
            surfaceContainerLowest = surface,
            surfaceContainerLow = surfaceContainerHigh,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainer,
        )
    }

private val DarkColorScheme =
    with(FjordDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = inversePrimary,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = routeAccent,
            onTertiary = onPrimary,
            tertiaryContainer = route,
            onTertiaryContainer = onRoute,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceContainer,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            inverseSurface = inverseSurface,
            inverseOnSurface = onInverseSurface,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = scrim,
            surfaceBright = surfaceContainerHigh,
            surfaceDim = background,
            surfaceContainerLowest = background,
            surfaceContainerLow = surfaceContainerHigh,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainer,
        )
    }

/** The app's own colours, for surfaces outside Compose UI (the Glance widgets before Android 12). */
val PulseLightColorScheme: ColorScheme get() = LightColorScheme
val PulseDarkColorScheme: ColorScheme get() = DarkColorScheme

/** Sheets and dialogs dim what is behind them by this much; 40% vanished over OLED black. */
const val SCRIM_ALPHA = 0.6f

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
    SystemBarsAppearance(darkTheme)

    val family = publicSans()
    val typography = remember(family) { pulseTypography(family) }
    val type = remember(family) { pulseType(family) }
    CompositionLocalProvider(
        LocalPulseColors provides if (darkTheme) DarkPulseColors else LightPulseColors,
        LocalPulseType provides type,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = typography,
            content = content,
        )
    }
}
