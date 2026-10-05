package com.example.pulsetransit.data

/** Cloud cover of a MET symbol code, ignoring precipitation. */
enum class Sky { CLEAR, FAIR, PARTLY_CLOUDY, CLOUDY, FOG }

enum class Precip { NONE, RAIN, SLEET, SNOW }

enum class Intensity { LIGHT, MODERATE, HEAVY }

/**
 * A MET locationforecast `symbol_code` decomposed into the parts we draw.
 * See https://api.met.no/weatherapi/weathericon/2.0/documentation for the code list.
 */
data class WeatherSymbol(
    val sky: Sky = Sky.CLOUDY,
    val precip: Precip = Precip.NONE,
    val intensity: Intensity = Intensity.MODERATE,
    val showers: Boolean = false,
    val thunder: Boolean = false,
    val isNight: Boolean = false,
    val isKnown: Boolean = true,
)

/**
 * Parses codes like `lightrainshowersandthunder_night` into its parts. Unrecognised codes
 * come back as [WeatherSymbol.isKnown] = false so the caller can fall back to a neutral icon.
 *
 * MET packs cloud cover, precipitation, intensity, thunder and time of day into the one code,
 * so unpacking it is a run of independent suffix checks rather than nested logic.
 */
@Suppress("CyclomaticComplexMethod")
fun parseWeatherSymbol(symbolCode: String): WeatherSymbol {
    var code = symbolCode.lowercase()

    val isNight = code.endsWith("_night")
    code = code.substringBefore('_')

    val thunder = code.endsWith("andthunder")
    if (thunder) code = code.removeSuffix("andthunder")

    val showers = code.endsWith("showers")
    if (showers) code = code.removeSuffix("showers")

    val intensity =
        when {
            code.startsWith("light") -> Intensity.LIGHT
            code.startsWith("heavy") -> Intensity.HEAVY
            else -> Intensity.MODERATE
        }
    code = code.removePrefix("light").removePrefix("heavy")

    val precip =
        when (code) {
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
            isNight = isNight,
        )
    }

    val sky =
        when (code) {
            "clearsky" -> Sky.CLEAR
            "fair" -> Sky.FAIR
            "partlycloudy" -> Sky.PARTLY_CLOUDY
            "cloudy" -> Sky.CLOUDY
            "fog" -> Sky.FOG
            else -> return WeatherSymbol(isNight = isNight, isKnown = false)
        }
    return WeatherSymbol(sky = sky, thunder = thunder, isNight = isNight)
}
