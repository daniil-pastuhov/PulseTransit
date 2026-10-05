package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Covers the MET `symbol_code` vocabulary the forecast icons are drawn from. */
class WeatherSymbolTest {
    @Test
    fun `parses clear day and night`() {
        val day = parseWeatherSymbol("clearsky_day")
        assertEquals(Sky.CLEAR, day.sky)
        assertEquals(Precip.NONE, day.precip)
        assertFalse(day.isNight)

        assertTrue(parseWeatherSymbol("clearsky_night").isNight)
        assertFalse(parseWeatherSymbol("clearsky_polartwilight").isNight)
    }

    @Test
    fun `fair and partly cloudy are distinct skies`() {
        // They render differently, so they must not collapse to the same value.
        assertEquals(Sky.FAIR, parseWeatherSymbol("fair_day").sky)
        assertEquals(Sky.PARTLY_CLOUDY, parseWeatherSymbol("partlycloudy_day").sky)
    }

    @Test
    fun `parses precipitation type and intensity`() {
        parseWeatherSymbol("lightrain").let {
            assertEquals(Precip.RAIN, it.precip)
            assertEquals(Intensity.LIGHT, it.intensity)
        }
        parseWeatherSymbol("rain").let {
            assertEquals(Precip.RAIN, it.precip)
            assertEquals(Intensity.MODERATE, it.intensity)
        }
        parseWeatherSymbol("heavysnow").let {
            assertEquals(Precip.SNOW, it.precip)
            assertEquals(Intensity.HEAVY, it.intensity)
        }
        assertEquals(Precip.SLEET, parseWeatherSymbol("sleet").precip)
    }

    @Test
    fun `showers imply broken cloud while steady precipitation implies overcast`() {
        parseWeatherSymbol("lightrainshowers_day").let {
            assertTrue(it.showers)
            assertEquals(Sky.PARTLY_CLOUDY, it.sky)
            assertEquals(Precip.RAIN, it.precip)
        }
        parseWeatherSymbol("lightrain").let {
            assertFalse(it.showers)
            assertEquals(Sky.CLOUDY, it.sky)
        }
    }

    @Test
    fun `parses thunder - including the longest compound code`() {
        assertTrue(parseWeatherSymbol("rainandthunder").thunder)

        // The worst case: intensity + type + showers + thunder + variant.
        parseWeatherSymbol("heavysleetshowersandthunder_night").let {
            assertEquals(Intensity.HEAVY, it.intensity)
            assertEquals(Precip.SLEET, it.precip)
            assertTrue(it.showers)
            assertTrue(it.thunder)
            assertTrue(it.isNight)
            assertTrue(it.isKnown)
        }
    }

    @Test
    fun `parses fog and cloudy`() {
        assertEquals(Sky.FOG, parseWeatherSymbol("fog").sky)
        assertEquals(Sky.CLOUDY, parseWeatherSymbol("cloudy").sky)
    }

    @Test
    fun `unrecognised codes are flagged rather than guessed`() {
        // The repository substitutes "unknown" when MET omits next_1_hours.
        assertFalse(parseWeatherSymbol("unknown").isKnown)
        assertFalse(parseWeatherSymbol("").isKnown)
        assertFalse(parseWeatherSymbol("somethingnew_day").isKnown)
    }

    @Test
    fun `every documented symbol code is recognised`() {
        val skies = listOf("clearsky", "fair", "partlycloudy", "cloudy", "fog")
        val precipitation = listOf("rain", "sleet", "snow")
        val intensities = listOf("light", "", "heavy")
        val variants = listOf("", "_day", "_night", "_polartwilight")

        val codes = mutableListOf<String>()
        for (variant in variants) {
            skies.forEach { codes += "$it$variant" }
            for (intensity in intensities) {
                for (type in precipitation) {
                    for (showers in listOf("", "showers")) {
                        for (thunder in listOf("", "andthunder")) {
                            codes += "$intensity$type$showers$thunder$variant"
                        }
                    }
                }
            }
        }

        val unrecognised = codes.filterNot { parseWeatherSymbol(it).isKnown }
        assertEquals(emptyList<String>(), unrecognised)
    }
}
