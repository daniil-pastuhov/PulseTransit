package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NorwayAreaTest {
    @Test
    fun `places across Norway are in it`() {
        listOf(
            "Oslo" to (59.9139 to 10.7522),
            "Lindesnes" to (58.0 to 7.05),
            "Kristiansand" to (58.1467 to 7.9956),
            "Stavanger" to (58.97 to 5.7331),
            "Bergen" to (60.3913 to 5.3221),
            "Ålesund" to (62.4722 to 6.1495),
            "Trondheim" to (63.4305 to 10.3951),
            "Bodø" to (67.2804 to 14.4049),
            "Tromsø" to (69.6492 to 18.9553),
            "Alta" to (69.9689 to 23.2716),
            "Karasjok" to (69.4719 to 25.5112),
            "Kirkenes" to (69.7271 to 30.0451),
            "Halden" to (59.1243 to 11.3875),
            // Enningdalen, which reaches south of Strömstad.
            "Prestebakke" to (58.995 to 11.53),
            "Longyearbyen" to (78.2232 to 15.6267),
            "Bjørnøya" to (74.43 to 19.0),
            "Nordaustlandet" to (80.2 to 22.0),
        ).forEach { (name, point) -> assertTrue(isInNorway(point.first, point.second), name) }
    }

    @Test
    fun `places abroad - neighbours included - are not`() {
        listOf(
            "Stockholm" to (59.3293 to 18.0686),
            "Gothenburg" to (57.7089 to 11.9746),
            "Strömstad" to (58.9395 to 11.1712),
            "Kiruna" to (67.8558 to 20.2253),
            "Helsinki" to (60.1699 to 24.9384),
            "Inari" to (68.9055 to 27.0288),
            "Murmansk" to (68.9585 to 33.0827),
            // Russia just south of the Storskog crossing, east of the Pasvik border.
            "Russia by Storskog" to (69.6 to 30.2),
            "Copenhagen" to (55.6761 to 12.5683),
            "Aberdeen" to (57.1497 to -2.0943),
            "London" to (51.5072 to -0.1276),
            "New York" to (40.7128 to -74.006),
            "Bengaluru" to (12.9716 to 77.5946),
            "Reykjavík" to (64.1466 to -21.9426),
        ).forEach { (name, point) -> assertFalse(isInNorway(point.first, point.second), name) }
    }

    @Test
    fun `every sample place is in Norway`() {
        SamplePlace.entries.forEach { assertTrue(isInNorway(it.latitude, it.longitude), it.name) }
    }
}
