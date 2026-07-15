package com.example.pulsetransit.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pulsetransit.R

/** Cloud cover of a MET symbol code, ignoring precipitation. */
internal enum class Sky { CLEAR, FAIR, PARTLY_CLOUDY, CLOUDY, FOG }

internal enum class Precip { NONE, RAIN, SLEET, SNOW }

internal enum class Intensity { LIGHT, MODERATE, HEAVY }

/**
 * A MET locationforecast `symbol_code` decomposed into the parts we draw.
 * See https://api.met.no/weatherapi/weathericon/2.0/documentation for the code list.
 */
internal data class WeatherSymbol(
    val sky: Sky = Sky.CLOUDY,
    val precip: Precip = Precip.NONE,
    val intensity: Intensity = Intensity.MODERATE,
    val showers: Boolean = false,
    val thunder: Boolean = false,
    val isNight: Boolean = false,
    val isKnown: Boolean = true
)

/**
 * Parses codes like `lightrainshowersandthunder_night` into its parts. Unrecognised codes
 * come back as [WeatherSymbol.isKnown] = false so the caller can fall back to a neutral icon.
 */
internal fun parseWeatherSymbol(symbolCode: String): WeatherSymbol {
    var code = symbolCode.lowercase()

    val isNight = code.endsWith("_night")
    code = code.substringBefore('_')

    val thunder = code.endsWith("andthunder")
    if (thunder) code = code.removeSuffix("andthunder")

    val showers = code.endsWith("showers")
    if (showers) code = code.removeSuffix("showers")

    val intensity = when {
        code.startsWith("light") -> Intensity.LIGHT
        code.startsWith("heavy") -> Intensity.HEAVY
        else -> Intensity.MODERATE
    }
    code = code.removePrefix("light").removePrefix("heavy")

    val precip = when (code) {
        "rain" -> Precip.RAIN
        "sleet" -> Precip.SLEET
        "snow" -> Precip.SNOW
        else -> Precip.NONE
    }

    // Precipitation without a sky term implies overcast, or broken cloud when it comes in showers.
    if (precip != Precip.NONE) {
        return WeatherSymbol(
            sky = if (showers) Sky.PARTLY_CLOUDY else Sky.CLOUDY,
            precip = precip,
            intensity = intensity,
            showers = showers,
            thunder = thunder,
            isNight = isNight
        )
    }

    val sky = when (code) {
        "clearsky" -> Sky.CLEAR
        "fair" -> Sky.FAIR
        "partlycloudy" -> Sky.PARTLY_CLOUDY
        "cloudy" -> Sky.CLOUDY
        "fog" -> Sky.FOG
        else -> return WeatherSymbol(isNight = isNight, isKnown = false)
    }
    return WeatherSymbol(sky = sky, thunder = thunder, isNight = isNight)
}

/** Human-readable condition, e.g. "Light rain and thunder". */
@Composable
internal fun weatherLabel(symbol: WeatherSymbol): String {
    if (!symbol.isKnown) return stringResource(R.string.weather_unknown)

    @StringRes val baseRes = when (symbol.precip) {
        Precip.NONE -> when (symbol.sky) {
            Sky.CLEAR -> R.string.weather_clear
            Sky.FAIR -> R.string.weather_fair
            Sky.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
            Sky.CLOUDY -> R.string.weather_cloudy
            Sky.FOG -> R.string.weather_fog
        }
        Precip.RAIN -> when (symbol.intensity) {
            Intensity.LIGHT -> R.string.weather_light_rain
            Intensity.MODERATE -> R.string.weather_rain
            Intensity.HEAVY -> R.string.weather_heavy_rain
        }
        Precip.SLEET -> when (symbol.intensity) {
            Intensity.LIGHT -> R.string.weather_light_sleet
            Intensity.MODERATE -> R.string.weather_sleet
            Intensity.HEAVY -> R.string.weather_heavy_sleet
        }
        Precip.SNOW -> when (symbol.intensity) {
            Intensity.LIGHT -> R.string.weather_light_snow
            Intensity.MODERATE -> R.string.weather_snow
            Intensity.HEAVY -> R.string.weather_heavy_snow
        }
    }

    val base = stringResource(baseRes)
    return if (symbol.thunder) stringResource(R.string.weather_and_thunder, base) else base
}

private val SunColor = Color(0xFFFFB300)
/** A pale moon disappears on a light background, so it darkens to indigo there. */
private val MoonColorOnDark = Color(0xFFE0E4EC)
private val MoonColorOnLight = Color(0xFF5C6BC0)
private val RainColor = Color(0xFF42A5F5)
private val SnowColor = Color(0xFF90CAF9)
private val BoltColor = Color(0xFFFFCA28)

/** Temperature ramp: indigo when freezing through red when hot. Readable on light and dark. */
internal fun temperatureColor(celsius: Double): Color {
    val stops = listOf(
        -20.0 to Color(0xFF5C6BC0),
        -5.0 to Color(0xFF42A5F5),
        2.0 to Color(0xFF26C6DA),
        8.0 to Color(0xFF9CCC65),
        18.0 to Color(0xFFFFA726),
        28.0 to Color(0xFFEF5350)
    )
    if (celsius <= stops.first().first) return stops.first().second
    if (celsius >= stops.last().first) return stops.last().second

    val upperIndex = stops.indexOfFirst { celsius < it.first }
    val (lowT, lowC) = stops[upperIndex - 1]
    val (highT, highC) = stops[upperIndex]
    return lerp(lowC, highC, ((celsius - lowT) / (highT - lowT)).toFloat())
}

/**
 * Draws [symbol] as a themed vector. Clouds follow [MaterialTheme.colorScheme] so the icon
 * flips with light/dark; sun, rain and snow keep their natural colours.
 */
@Composable
internal fun WeatherIcon(symbol: WeatherSymbol, modifier: Modifier = Modifier) {
    val cloudColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    val fogColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    // Read the surface rather than isSystemInDarkTheme(): the AUTO theme mode can put a dark
    // scheme on a light system and vice versa.
    val moonColor = if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) {
        MoonColorOnLight
    } else {
        MoonColorOnDark
    }

    Canvas(modifier = modifier) {
        val s = size.minDimension
        if (!symbol.isKnown) {
            drawCloud(centerX = s * 0.5f, top = s * 0.34f, width = s * 0.72f, color = fogColor)
            return@Canvas
        }

        val hasPrecip = symbol.precip != Precip.NONE || symbol.thunder

        // Sun/moon size and position, and how much cloud sits in front of it, both track the
        // sky: FAIR is mostly sunny with a token cloud, PARTLY_CLOUDY is a genuine mix.
        val body = when (symbol.sky) {
            Sky.CLEAR -> CelestialBody(Offset(s * 0.5f, s * 0.44f), s * 0.19f, rays = true)
            Sky.FAIR -> CelestialBody(Offset(s * 0.36f, s * 0.30f), s * 0.17f, rays = !hasPrecip)
            Sky.PARTLY_CLOUDY -> CelestialBody(Offset(s * 0.32f, s * 0.30f), s * 0.14f, rays = false)
            else -> null
        }
        val cloud = when (symbol.sky) {
            Sky.CLEAR -> null
            Sky.FAIR -> CloudSpec(centerX = s * 0.63f, top = s * 0.46f, width = s * 0.52f)
            Sky.PARTLY_CLOUDY -> CloudSpec(centerX = s * 0.58f, top = s * 0.42f, width = s * 0.64f)
            else -> CloudSpec(centerX = s * 0.5f, top = s * 0.30f, width = s * 0.76f)
        }

        if (body != null) {
            if (symbol.isNight) {
                drawMoon(body.center, body.radius, moonColor)
            } else {
                drawSun(body.center, body.radius, showRays = body.rays)
            }
        }
        cloud?.let { drawCloud(it.centerX, it.top, it.width, cloudColor) }

        val cloudBottom = cloud?.let { it.top + it.width * 0.22f } ?: (s * 0.62f)

        if (symbol.sky == Sky.FOG) {
            drawFogLines(cloudBottom, s, fogColor)
        }
        if (symbol.thunder) {
            drawBolt(cloudBottom, s)
        }
        if (symbol.precip != Precip.NONE) {
            drawPrecipitation(symbol, cloudBottom, s, thunderPresent = symbol.thunder)
        }
    }
}

private data class CelestialBody(val center: Offset, val radius: Float, val rays: Boolean)

private data class CloudSpec(val centerX: Float, val top: Float, val width: Float)

private fun DrawScope.drawSun(center: Offset, radius: Float, showRays: Boolean) {
    drawCircle(color = SunColor, radius = radius, center = center)
    if (!showRays) return
    val strokeWidth = radius * 0.22f
    val inner = radius * 1.45f
    val outer = radius * 1.95f
    repeat(8) { i ->
        val angle = (Math.PI / 4.0) * i
        val dx = kotlin.math.cos(angle).toFloat()
        val dy = kotlin.math.sin(angle).toFloat()
        drawLine(
            color = SunColor,
            start = Offset(center.x + dx * inner, center.y + dy * inner),
            end = Offset(center.x + dx * outer, center.y + dy * outer),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawMoon(center: Offset, radius: Float, color: Color) {
    // Crescent = full disc minus a disc offset up and to the right.
    val disc = Path().apply { addOval(rect(center, radius)) }
    val bite = Path().apply {
        addOval(rect(Offset(center.x + radius * 0.55f, center.y - radius * 0.45f), radius * 0.95f))
    }
    drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, color)
}

private fun rect(center: Offset, radius: Float) = Rect(
    offset = Offset(center.x - radius, center.y - radius),
    size = Size(radius * 2, radius * 2)
)

private fun DrawScope.drawCloud(centerX: Float, top: Float, width: Float, color: Color) {
    val baseHeight = width * 0.22f
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - width / 2f, top),
        size = Size(width, baseHeight),
        cornerRadius = CornerRadius(baseHeight / 2f)
    )
    drawCircle(color, width * 0.20f, Offset(centerX - width * 0.22f, top + width * 0.02f))
    drawCircle(color, width * 0.27f, Offset(centerX + width * 0.02f, top - width * 0.06f))
    drawCircle(color, width * 0.17f, Offset(centerX + width * 0.28f, top + width * 0.04f))
}

private fun DrawScope.drawFogLines(cloudBottom: Float, s: Float, color: Color) {
    val widths = listOf(0.62f, 0.48f, 0.56f)
    widths.forEachIndexed { i, w ->
        val y = cloudBottom + s * (0.10f + 0.11f * i)
        drawLine(
            color = color,
            start = Offset(s * (0.5f - w / 2f), y),
            end = Offset(s * (0.5f + w / 2f), y),
            strokeWidth = s * 0.055f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawBolt(cloudBottom: Float, s: Float) {
    val top = cloudBottom + s * 0.04f
    val bolt = Path().apply {
        moveTo(s * 0.52f, top)
        lineTo(s * 0.38f, top + s * 0.17f)
        lineTo(s * 0.47f, top + s * 0.17f)
        lineTo(s * 0.41f, top + s * 0.33f)
        lineTo(s * 0.60f, top + s * 0.13f)
        lineTo(s * 0.50f, top + s * 0.13f)
        close()
    }
    drawPath(bolt, BoltColor)
}

private fun DrawScope.drawPrecipitation(
    symbol: WeatherSymbol,
    cloudBottom: Float,
    s: Float,
    thunderPresent: Boolean
) {
    val count = when (symbol.intensity) {
        // Sleet is drawn as a drop plus a flake, so it needs two marks even when light —
        // otherwise it is indistinguishable from light rain.
        Intensity.LIGHT -> if (symbol.precip == Precip.SLEET) 2 else 1
        Intensity.MODERATE -> 2
        Intensity.HEAVY -> 3
    }
    // A bolt occupies the middle, so shift drops to the flanks.
    val xs = when {
        thunderPresent -> listOf(0.26f, 0.74f, 0.16f).take(count)
        count == 1 -> listOf(0.5f)
        count == 2 -> listOf(0.36f, 0.62f)
        else -> listOf(0.28f, 0.5f, 0.72f)
    }
    val top = cloudBottom + s * 0.08f

    xs.forEachIndexed { i, xf ->
        val x = s * xf
        val y = top + if (i % 2 == 1) s * 0.05f else 0f
        // Sleet alternates a drop and a flake; rain is all drops, snow all flakes.
        val flake = symbol.precip == Precip.SNOW || (symbol.precip == Precip.SLEET && i % 2 == 1)
        if (flake) {
            drawCircle(SnowColor, s * 0.055f, Offset(x, y + s * 0.06f))
        } else {
            drawLine(
                color = RainColor,
                start = Offset(x + s * 0.03f, y),
                end = Offset(x - s * 0.03f, y + s * 0.14f),
                strokeWidth = s * 0.06f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Vertical bar whose height tracks [millimetres] of rainfall, so wet hours read at a glance.
 * The track is always drawn, keeping the forecast row from reflowing as showers come and go.
 */
@Composable
internal fun PrecipitationBar(millimetres: Double, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Canvas(modifier = modifier) {
        val barWidth = size.width.coerceAtMost(6.dp.toPx())
        val x = size.width / 2f
        val radius = barWidth / 2f
        drawLine(
            color = trackColor,
            start = Offset(x, radius),
            end = Offset(x, size.height - radius),
            strokeWidth = barWidth,
            cap = StrokeCap.Round
        )
        if (millimetres <= 0.0) return@Canvas
        // 4 mm/h is a downpour; anything above that pins the bar to full height.
        val fraction = (millimetres / 4.0).coerceIn(0.05, 1.0).toFloat()
        val usable = size.height - barWidth
        drawLine(
            color = RainColor,
            start = Offset(x, size.height - radius),
            end = Offset(x, size.height - radius - usable * fraction),
            strokeWidth = barWidth,
            cap = StrokeCap.Round
        )
    }
}
