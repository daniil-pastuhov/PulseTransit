package com.example.pulsetransit.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.pulsetransit.data.Intensity
import com.example.pulsetransit.data.Precip
import com.example.pulsetransit.data.Sky
import com.example.pulsetransit.data.WeatherSymbol
import com.example.pulsetransit.theme.PulseIcons
import com.example.pulsetransit.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Human-readable condition, e.g. "Light rain and thunder". */
@Composable
internal fun weatherLabel(symbol: WeatherSymbol): String {
    if (!symbol.isKnown) return stringResource(Res.string.weather_unknown)

    val baseRes =
        when (symbol.precip) {
            Precip.NONE ->
                when (symbol.sky) {
                    Sky.CLEAR -> Res.string.weather_clear
                    Sky.FAIR -> Res.string.weather_fair
                    Sky.PARTLY_CLOUDY -> Res.string.weather_partly_cloudy
                    Sky.CLOUDY -> Res.string.weather_cloudy
                    Sky.FOG -> Res.string.weather_fog
                }
            Precip.RAIN ->
                when (symbol.intensity) {
                    Intensity.LIGHT -> Res.string.weather_light_rain
                    Intensity.MODERATE -> Res.string.weather_rain
                    Intensity.HEAVY -> Res.string.weather_heavy_rain
                }
            Precip.SLEET ->
                when (symbol.intensity) {
                    Intensity.LIGHT -> Res.string.weather_light_sleet
                    Intensity.MODERATE -> Res.string.weather_sleet
                    Intensity.HEAVY -> Res.string.weather_heavy_sleet
                }
            Precip.SNOW ->
                when (symbol.intensity) {
                    Intensity.LIGHT -> Res.string.weather_light_snow
                    Intensity.MODERATE -> Res.string.weather_snow
                    Intensity.HEAVY -> Res.string.weather_heavy_snow
                }
        }

    val base = stringResource(baseRes)
    return if (symbol.thunder) stringResource(Res.string.weather_and_thunder, base) else base
}

/** [symbol] as a Material Symbols weather icon. */
internal fun weatherIcon(symbol: WeatherSymbol): ImageVector =
    when {
        !symbol.isKnown -> PulseIcons.Cloud
        symbol.thunder -> PulseIcons.Thunderstorm
        symbol.precip != Precip.NONE -> precipitationIcon(symbol.precip, symbol.intensity)
        else -> skyIcon(symbol.sky, symbol.isNight)
    }

private fun precipitationIcon(
    precip: Precip,
    intensity: Intensity,
): ImageVector =
    when (precip) {
        Precip.SNOW -> if (intensity == Intensity.HEAVY) PulseIcons.SnowingHeavy else PulseIcons.WeatherSnowy
        Precip.SLEET -> PulseIcons.RainySnow
        else ->
            when (intensity) {
                Intensity.LIGHT -> PulseIcons.RainyLight
                Intensity.MODERATE -> PulseIcons.Rainy
                Intensity.HEAVY -> PulseIcons.RainyHeavy
            }
    }

private fun skyIcon(
    sky: Sky,
    night: Boolean,
): ImageVector =
    when (sky) {
        Sky.CLEAR -> if (night) PulseIcons.ClearNight else PulseIcons.ClearDay
        Sky.FAIR, Sky.PARTLY_CLOUDY -> if (night) PulseIcons.PartlyCloudyNight else PulseIcons.PartlyCloudyDay
        Sky.CLOUDY -> PulseIcons.Cloud
        Sky.FOG -> PulseIcons.Foggy
    }

/** Draws [symbol] in the muted text colour; decorative, the condition is spoken by the label. */
@Composable
internal fun WeatherIcon(
    symbol: WeatherSymbol,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Icon(weatherIcon(symbol), contentDescription = null, modifier = modifier, tint = tint)
}
