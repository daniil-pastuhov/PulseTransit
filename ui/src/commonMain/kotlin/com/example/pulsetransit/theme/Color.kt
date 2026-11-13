package com.example.pulsetransit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The Fjord colour roles Material 3's [androidx.compose.material3.ColorScheme] has no slot for:
 * status colours for times, the route banner, the callout containers and the line colours. Read
 * them with [PulseTheme.colors]; the values come from docs/design/tokens.json (FjordTokens.kt).
 */
@Immutable
data class PulseColors(
    /** Delayed times and late countdowns. */
    val late: Color,
    /** "Now" and realtime times. */
    val now: Color,
    /** The fastest-route banner, which stays dark on the wall at night. */
    val route: Color,
    val onRoute: Color,
    val routeAccent: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val lines: LineColors,
)

/** Line pill colours per transport mode; white text passes AA on each, in both themes. */
@Immutable
data class LineColors(
    val bus: Color,
    val tram: Color,
    val train: Color,
    val express: Color,
    val metro: Color,
    val ferry: Color,
    val other: Color,
    val onLine: Color,
) {
    /** The pill colour for an Entur transport mode ("bus", "rail", "airportRail"…). */
    fun forMode(mode: String?): Color =
        when (mode?.lowercase()) {
            "bus", "coach" -> bus
            "tram" -> tram
            "metro" -> metro
            "rail" -> train
            "airportrail" -> express
            "water" -> ferry
            else -> other
        }
}

internal val LightPulseColors =
    with(FjordLight) {
        PulseColors(
            late = late,
            now = now,
            route = route,
            onRoute = onRoute,
            routeAccent = routeAccent,
            warningContainer = warningContainer,
            onWarningContainer = onWarningContainer,
            successContainer = successContainer,
            onSuccessContainer = onSuccessContainer,
            infoContainer = infoContainer,
            onInfoContainer = onInfoContainer,
            lines =
                LineColors(
                    bus = lineBus,
                    tram = lineTram,
                    train = lineTrain,
                    express = lineExpress,
                    metro = lineMetro,
                    ferry = lineFerry,
                    other = lineOther,
                    onLine = onLineBus,
                ),
        )
    }

internal val DarkPulseColors =
    with(FjordDark) {
        PulseColors(
            late = late,
            now = now,
            route = route,
            onRoute = onRoute,
            routeAccent = routeAccent,
            warningContainer = warningContainer,
            onWarningContainer = onWarningContainer,
            successContainer = successContainer,
            onSuccessContainer = onSuccessContainer,
            infoContainer = infoContainer,
            onInfoContainer = onInfoContainer,
            lines =
                LineColors(
                    bus = lineBus,
                    tram = lineTram,
                    train = lineTrain,
                    express = lineExpress,
                    metro = lineMetro,
                    ferry = lineFerry,
                    other = lineOther,
                    onLine = onLineBus,
                ),
        )
    }

/** Line colours outside Compose UI (the Glance widgets), for the light and the dark theme. */
val LightLineColors: LineColors get() = LightPulseColors.lines
val DarkLineColors: LineColors get() = DarkPulseColors.lines

internal val LocalPulseColors = staticCompositionLocalOf { LightPulseColors }

/** Access to the Fjord roles that sit beside MaterialTheme's. */
object PulseTheme {
    val colors: PulseColors
        @Composable @ReadOnlyComposable
        get() = LocalPulseColors.current
}
