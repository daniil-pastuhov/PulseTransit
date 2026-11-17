// Fixture data: the minutes, durations and temperatures are the data, not tuning.
@file:Suppress("MagicNumber")

package com.example.pulsetransit.desktop.gallery

import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DepartureGroup
import com.example.pulsetransit.data.StopDepartures
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.WeatherInfo
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import java.time.Duration
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// The gallery's boards, built as the ViewModel would leave its state, relative to the real clock
// so nothing is dropped as departed: the first route of each destination leaves in about 7 min.

private val OSLO: ZoneId = ZoneId.of("Europe/Oslo")
private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
private val MET_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

private fun iso(time: ZonedDateTime): String = time.format(ISO)

/** A scenario: which saved destinations the board routes to, each with its routes, best first. */
class GalleryScenario(
    val name: String,
    val destinations: List<Pair<StopPlace, List<TripInfo>>>,
    /** Saved destinations whose route is still loading. */
    val loading: List<StopPlace> = emptyList(),
    /** Saved destinations whose route failed to load. */
    val failed: List<StopPlace> = emptyList(),
    /** The destination searched for from the board, with its routes. */
    val temporary: Pair<StopPlace, List<TripInfo>>? = null,
)

/** The states each scenario is rendered in. */
enum class GalleryState(
    val label: String,
) {
    COLLAPSED("collapsed"),
    EXPANDED("expanded"),

    /** Only the last card expanded, so a collapsed card shows beside an expanded one. */
    MIXED("mixed"),
}

// The pieces of a route, laid end to end from its leaving time.
private sealed interface Segment

private data class Walk(
    val minutes: Int,
    val to: String,
) : Segment

private data class Ride(
    val mode: String,
    val line: String,
    val from: String,
    val to: String,
    val minutes: Int,
    /** Waiting at [from] before it leaves. */
    val waitBefore: Int = 1,
    /** What the vehicle shows on its front. */
    val towards: String = to,
    val platform: String? = null,
) : Segment

private const val WALK_METRES_PER_MINUTE = 80

private fun trip(
    destination: String,
    leave: ZonedDateTime,
    vararg segments: Segment,
    origin: String = "Kirkegata 15",
): TripInfo {
    var time = leave
    var place = origin
    val legs =
        segments.map { segment ->
            when (segment) {
                is Walk -> {
                    val end = time.plusMinutes(segment.minutes.toLong())
                    TripLeg(
                        mode = "foot",
                        fromName = place,
                        toName = segment.to,
                        durationMinutes = segment.minutes,
                        expectedStartTime = iso(time),
                        expectedEndTime = iso(end),
                        aimedStartTime = iso(time),
                        distanceMeters = segment.minutes * WALK_METRES_PER_MINUTE,
                    ).also {
                        time = end
                        place = segment.to
                    }
                }
                is Ride -> {
                    val start = time.plusMinutes(segment.waitBefore.toLong())
                    val end = start.plusMinutes(segment.minutes.toLong())
                    TripLeg(
                        mode = segment.mode,
                        lineName = segment.line,
                        fromName = segment.from,
                        toName = segment.to,
                        durationMinutes = segment.minutes,
                        expectedStartTime = iso(start),
                        expectedEndTime = iso(end),
                        aimedStartTime = iso(start),
                        isRealtime = true,
                        platform = segment.platform,
                        destination = segment.towards,
                        authority = "Ruter",
                    ).also {
                        time = end
                        place = segment.to
                    }
                }
            }
        }
    return TripInfo(
        destinationName = destination,
        expectedStartTime = iso(leave),
        expectedEndTime = iso(time),
        lineName = legs.firstOrNull { it.lineName != null }?.lineName ?: "?",
        durationMinutes = Duration.between(leave, time).toMinutes().toInt(),
        originName = origin,
        legs = legs,
    )
}

private val LILLESTROM = StopPlace(id = "NSR:StopPlace:62339", name = "Lillestrøm stasjon", locality = "Lillestrøm")
private val GARDERMOEN = StopPlace(id = "NSR:StopPlace:58211", name = "Oslo lufthavn Gardermoen", locality = "Ullensaker")
private val MAJORSTUEN = StopPlace(id = "NSR:StopPlace:58381", name = "Majorstuen", locality = "Oslo")
private val DRAMMEN = StopPlace(id = "NSR:StopPlace:337", name = "Drammen stasjon", locality = "Drammen")
private val BYGDOY = StopPlace(id = "NSR:StopPlace:58960", name = "Bygdøynes", locality = "Oslo")

/** Walk 2 min, then a train from Oslo S; alternatives about every 10 min. */
private fun lillestromRoutes(now: ZonedDateTime): List<TripInfo> =
    listOf("R12" to 7L, "R14" to 17L, "R13" to 27L, "RE11" to 37L).map { (line, inMinutes) ->
        trip(
            LILLESTROM.name,
            now.plusMinutes(inMinutes),
            Walk(2, "Oslo S"),
            Ride("rail", line, "Oslo S", LILLESTROM.name, if (line == "RE11") 10 else 11, waitBefore = 2, towards = "Eidsvoll", platform = "11"),
        )
    }

/** A long route with 3 transfers and walks between; alternatives a mix of long, short and direct. */
private fun gardermoenRoutes(now: ZonedDateTime): List<TripInfo> =
    listOf(
        trip(
            GARDERMOEN.name,
            now.plusMinutes(7),
            Walk(4, "Jernbanetorget"),
            Ride("bus", "31", "Jernbanetorget", "Tøyen", 6, towards = "Tonsenhagen", platform = "E"),
            Walk(3, "Tøyen"),
            Ride("metro", "5", "Tøyen", "Storo", 9, waitBefore = 2, towards = "Vestli", platform = "1"),
            Ride("tram", "17", "Storo", "Grefsen stasjon", 4, waitBefore = 2, towards = "Grefsen stasjon"),
            Walk(6, "Grefsen stasjon"),
            Ride("airportRail", "FLY1", "Grefsen stasjon", GARDERMOEN.name, 18, waitBefore = 3, towards = "Oslo lufthavn", platform = "2"),
        ),
        trip(
            GARDERMOEN.name,
            now.plusMinutes(17),
            Walk(5, "Stortorvet"),
            Ride("bus", "37", "Stortorvet", "Carl Berners plass", 7, towards = "Nydalen", platform = "B"),
            Walk(2, "Carl Berners plass"),
            Ride("metro", "4", "Carl Berners plass", "Sinsen", 3, waitBefore = 3, towards = "Vestli", platform = "2"),
            Ride("tram", "11", "Sinsen", "Grefsen stasjon", 5, waitBefore = 2, towards = "Kjelsås"),
            Walk(5, "Grefsen stasjon"),
            Ride("airportRail", "FLY2", "Grefsen stasjon", GARDERMOEN.name, 19, waitBefore = 4, towards = "Oslo lufthavn", platform = "2"),
        ),
        trip(
            GARDERMOEN.name,
            now.plusMinutes(24),
            Walk(3, "Jernbanetorget"),
            Ride("bus", "31", "Jernbanetorget", "Oslo S", 3, towards = "Snarøya", platform = "D"),
            Walk(4, "Oslo S"),
            Ride("airportRail", "FLY1", "Oslo S", GARDERMOEN.name, 22, waitBefore = 4, towards = "Oslo lufthavn", platform = "13"),
        ),
        trip(
            GARDERMOEN.name,
            now.plusMinutes(35),
            Walk(2, "Oslo bussterminal"),
            Ride("coach", "FB2", "Oslo bussterminal", GARDERMOEN.name, 50, waitBefore = 2, towards = "Oslo lufthavn", platform = "1"),
        ),
    )

/** One route only, so the card has no alternatives. */
private fun majorstuenRoutes(now: ZonedDateTime): List<TripInfo> =
    listOf(
        trip(
            MAJORSTUEN.name,
            now.plusMinutes(4),
            Walk(3, "Jernbanetorget"),
            Ride("metro", "2", "Jernbanetorget", MAJORSTUEN.name, 6, towards = "Østerås", platform = "2"),
        ),
    )

/** Walk, a metro then a regional train; alternatives about every 15 min, one direct. */
private fun drammenRoutes(now: ZonedDateTime): List<TripInfo> =
    listOf(9L, 24L, 39L).map { inMinutes ->
        trip(
            DRAMMEN.name,
            now.plusMinutes(inMinutes),
            Walk(3, "Jernbanetorget"),
            Ride("metro", "3", "Jernbanetorget", "Nationaltheatret", 3, towards = "Kolsås", platform = "1"),
            Walk(2, "Nationaltheatret"),
            Ride("rail", if (inMinutes == 24L) "R11" else "RE10", "Nationaltheatret", DRAMMEN.name, 31, waitBefore = 3, towards = "Drammen", platform = "2"),
        )
    } +
        trip(
            DRAMMEN.name,
            now.plusMinutes(48),
            Walk(2, "Oslo bussterminal"),
            Ride("coach", "NW160", "Oslo bussterminal", DRAMMEN.name, 45, waitBefore = 2, towards = "Drammen", platform = "6"),
        )

/** A short bus ride; alternatives every 10 min. */
private fun bygdoyRoutes(now: ZonedDateTime): List<TripInfo> =
    listOf(5L, 15L, 25L, 35L).map { inMinutes ->
        trip(
            BYGDOY.name,
            now.plusMinutes(inMinutes),
            Walk(1, "Kongens gate"),
            Ride("bus", "30", "Kongens gate", BYGDOY.name, 14, towards = "Bygdøynes", platform = "A"),
        )
    }

fun galleryScenarios(now: ZonedDateTime = galleryNow()): List<GalleryScenario> =
    listOf(
        GalleryScenario("simple", listOf(LILLESTROM to lillestromRoutes(now))),
        GalleryScenario("long", listOf(GARDERMOEN to gardermoenRoutes(now))),
        GalleryScenario("two", listOf(LILLESTROM to lillestromRoutes(now), GARDERMOEN to gardermoenRoutes(now))),
        GalleryScenario(
            "three",
            listOf(LILLESTROM to lillestromRoutes(now), GARDERMOEN to gardermoenRoutes(now), MAJORSTUEN to majorstuenRoutes(now)),
        ),
        // The most saved destinations there can be (SettingsRepository.MAX_DESTINATION_STATIONS), long
        // and short routes, one without alternatives.
        GalleryScenario(
            "five",
            listOf(
                LILLESTROM to lillestromRoutes(now),
                GARDERMOEN to gardermoenRoutes(now),
                MAJORSTUEN to majorstuenRoutes(now),
                DRAMMEN to drammenRoutes(now),
                BYGDOY to bygdoyRoutes(now),
            ),
        ),
        // The cards without a route (loading, failed), and the searched-for destination with its buttons.
        GalleryScenario(
            "states",
            listOf(LILLESTROM to lillestromRoutes(now)),
            loading = listOf(GARDERMOEN),
            failed = listOf(DRAMMEN),
            temporary = MAJORSTUEN to majorstuenRoutes(now),
        ),
    )

/** Now in Norway, to the minute, as the board's times are. */
fun galleryNow(): ZonedDateTime = ZonedDateTime.now(OSLO).truncatedTo(ChronoUnit.MINUTES)

// The favourite stops: each line to a destination with its next few departures, minutes from now.
private class Line(
    val mode: String,
    val line: String,
    val destination: String,
    val inMinutes: List<Int>,
    val platform: String? = null,
    val delay: Int = 0,
)

private val STOPS: List<Pair<StopPlace, List<Line>>> =
    listOf(
        StopPlace(id = "NSR:StopPlace:59516", name = "Oslo bussterminal", locality = "Oslo") to
            listOf(
                Line("coach", "FB2", "Oslo lufthavn", listOf(3, 23), platform = "1"),
                Line("bus", "30", "Bygdøy", listOf(5, 15, 25), platform = "C"),
                Line("bus", "34", "Ekeberg hageby", listOf(8, 18), platform = "D", delay = 2),
                Line("bus", "37", "Nydalen", listOf(11, 26), platform = "B"),
                Line("coach", "NW180", "Bergen", listOf(42), platform = "12"),
            ),
        StopPlace(id = "NSR:StopPlace:58404", name = "Dronningens gate", locality = "Oslo") to
            listOf(
                Line("tram", "12", "Majorstuen", listOf(1, 11, 21)),
                Line("tram", "13", "Bekkestua", listOf(4, 14)),
                Line("tram", "19", "Ljabru", listOf(6, 16, 26), delay = 3),
                Line("tram", "11", "Kjelsås", listOf(9, 19)),
            ),
        StopPlace(id = "NSR:StopPlace:58293", name = "Storgata", locality = "Oslo") to
            listOf(
                Line("tram", "17", "Grefsen stasjon", listOf(2, 12, 22)),
                Line("tram", "18", "Rikshospitalet", listOf(5, 15)),
                Line("bus", "30", "Nydalen", listOf(7, 17, 27)),
                Line("bus", "60", "Vippetangen", listOf(13, 33)),
            ),
        StopPlace(id = "NSR:StopPlace:59872", name = "Oslo S", locality = "Oslo") to
            listOf(
                Line("airportRail", "FLY1", "Oslo lufthavn", listOf(4, 14, 24), platform = "13"),
                Line("rail", "R12", "Kongsberg", listOf(9, 39), platform = "8"),
                Line("rail", "L1", "Spikkestad", listOf(12, 42), platform = "4", delay = 4),
                Line("rail", "R14", "Kongsvinger", listOf(17), platform = "11"),
                Line("rail", "RE11", "Skien", listOf(21, 51), platform = "3"),
            ),
        StopPlace(id = "NSR:StopPlace:58366", name = "Jernbanetorget", locality = "Oslo") to
            listOf(
                Line("metro", "1", "Frognerseteren", listOf(2, 17), platform = "1"),
                Line("metro", "2", "Østerås", listOf(3, 18), platform = "1"),
                Line("metro", "3", "Mortensrud", listOf(5, 20), platform = "2"),
                Line("metro", "4", "Vestli", listOf(6, 21), platform = "2"),
                Line("metro", "5", "Sognsvann", listOf(9, 24), platform = "1"),
            ),
    )

private fun stopDepartures(
    stop: StopPlace,
    lines: List<Line>,
    now: ZonedDateTime,
): StopDepartures =
    StopDepartures(
        stopId = stop.id,
        groups =
            lines
                .map { line ->
                    DepartureGroup(
                        line.inMinutes.mapIndexed { index, minutes ->
                            val delay = if (index == 0) line.delay else 0
                            val expected = now.plusMinutes(minutes.toLong())
                            Departure(
                                line = line.line,
                                destination = line.destination,
                                expectedTime = iso(expected),
                                aimedTime = iso(expected.minusMinutes(delay.toLong())),
                                isRealtime = true,
                                transportMode = line.mode,
                                delayMinutes = delay,
                                quayId = "${stop.id}-${line.platform ?: "A"}".replace("StopPlace", "Quay"),
                                platform = line.platform,
                                authority = "Ruter",
                                serviceJourneyId = "RUT:ServiceJourney:${line.line}-$minutes",
                                serviceDate = now.toLocalDate().toString(),
                            )
                        },
                    )
                }.sortedBy { it.departures.first().expectedTime },
    )

private val WEATHER_SYMBOLS = listOf("cloudy", "partlycloudy_night", "lightrain", "rain", "cloudy", "fair_night", "clearsky_night")

private fun weather(now: ZonedDateTime): List<WeatherInfo> {
    val hour = now.withZoneSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.HOURS)
    return WEATHER_SYMBOLS.mapIndexed { index, symbol ->
        WeatherInfo(
            time = hour.plusHours(index.toLong()).format(MET_TIME),
            temperature = 4.0 - index * 0.6,
            symbolCode = symbol,
            precipitation = if ("rain" in symbol) 0.6 else 0.0,
        )
    }
}

/** The board as the ViewModel leaves it in board mode once everything has loaded, for [scenario] in [state]. */
fun galleryUiState(
    scenario: GalleryScenario,
    state: GalleryState,
    now: ZonedDateTime = galleryNow(),
): StopBoardViewModel.UiState {
    val clock = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    val locale = Locale.getDefault()
    val destinations = scenario.destinations.map { it.first } + scenario.loading + scenario.failed
    val routed = scenario.destinations + listOfNotNull(scenario.temporary)
    return StopBoardViewModel.UiState(
        mode = AppMode.BOARD,
        favorites = STOPS.map { it.first },
        departures = STOPS.map { (stop, lines) -> stopDepartures(stop, lines, now) },
        weather = weather(now),
        lastUpdated = clock,
        currentTime = clock,
        currentDayOfMonth = now.dayOfMonth.toString(),
        currentWeekday = now.format(DateTimeFormatter.ofPattern("EEE", locale)),
        currentMonth = now.format(DateTimeFormatter.ofPattern("MMM", locale)),
        currentWeekdayLong = now.format(DateTimeFormatter.ofPattern("EEEE", locale)),
        currentMonthLong = now.format(DateTimeFormatter.ofPattern("MMMM", locale)),
        fastestTrips = routed.associate { (place, trips) -> place.id to trips },
        loadingTripDestinationIds = scenario.loading.map { it.id }.toSet(),
        failedTripDestinationIds = scenario.failed.map { it.id }.toSet(),
        temporaryDestination = scenario.temporary?.first,
        routeAlternativesShownIds =
            when (state) {
                GalleryState.COLLAPSED -> emptySet()
                GalleryState.EXPANDED -> destinations.map { it.id }.toSet()
                GalleryState.MIXED -> destinations.takeLast(1).map { it.id }.toSet()
            },
        destinationStations = destinations,
        activeDestinationIds = destinations.map { it.id }.toSet(),
    )
}
