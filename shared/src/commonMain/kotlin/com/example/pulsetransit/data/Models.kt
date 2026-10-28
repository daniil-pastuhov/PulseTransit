package com.example.pulsetransit.data

import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Serializable
data class StopPlace(
    val id: String,
    val name: String,
    val locality: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val quays: List<Quay> = emptyList(),
    /** Only destinations are ever anything other than a stop; saved JSON without it is a stop. */
    val kind: PlaceKind = PlaceKind.STOP,
)

/** Geocoder layer a place came from. Anything but [STOP] has no NSR id, so is routed to by coordinates. */
@Serializable
enum class PlaceKind {
    STOP,
    ADDRESS,
    POI,
    PLACE,
}

@Serializable
data class Quay(
    val id: String,
    val publicCode: String? = null,
    val name: String? = null,
    // `description` clashes with NSObject's in Swift, so it gets a name of its own there.
    @OptIn(ExperimentalObjCName::class)
    @ObjCName(swiftName = "quayDescription")
    val description: String? = null,
    /** Public codes of every line scheduled to call here, naturally sorted. */
    val lines: List<String> = emptyList(),
    /** Distinct destinations of the next departures; a platform usually serves several. */
    val destinations: List<String> = emptyList(),
    /**
     * The stop place the quay belongs to. A parent stop such as Oslo S has quays from several
     * children (the station and a bus terminal), and this is what tells them apart.
     */
    val stopPlaceId: String? = null,
    val stopPlaceName: String? = null,
)

@Serializable
data class Departure(
    val line: String,
    val destination: String,
    val expectedTime: String, // ISO timestamp
    val aimedTime: String, // ISO timestamp
    val isRealtime: Boolean,
    val isCancelled: Boolean = false,
    val transportMode: String? = null,
    val delayMinutes: Int = 0,
    val notices: List<String> = emptyList(),
    val quayId: String? = null,
    /** The quay's public code ("2", "A") when it has one, shown as the platform. */
    val platform: String? = null,
    /** The line's full name, e.g. "Holmlia stasjon - Brenna". */
    val lineName: String? = null,
    /** The transport authority whose line this is, e.g. "Ruter". */
    val authority: String? = null,
    /** The company running the line for [authority], e.g. "Connect Bus AS". */
    val operator: String? = null,
    /** The trip, with [serviceDate], as Entur names it: what its stops are asked for by. */
    val serviceJourneyId: String? = null,
    /** The operating day of [serviceJourneyId] as yyyy-MM-dd, which can be before a night trip's date. */
    val serviceDate: String? = null,
)

/** One stop on a trip, from [EnturRepository.getJourneyStops]. */
@Serializable
data class JourneyStop(
    val quayId: String?,
    val stopPlaceId: String?,
    val name: String,
    /** When the trip reaches this stop (leaves it, at the first), as ISO-8601. */
    val expectedTime: String,
    val isCancelled: Boolean = false,
    /** The quay's position, which journey alerts measure the device against. */
    val latitude: Double? = null,
    val longitude: Double? = null,
)

data class DepartureGroup(
    val departures: List<Departure>,
) {
    /**
     * The departures a row shows: the next one plus a few after it. A busy single line can have
     * dozens in one group, which would turn a wall-display row into a timetable.
     */
    val visibleDepartures: List<Departure> get() = departures.take(MAX_VISIBLE_DEPARTURES)

    /** How many of [departures] [visibleDepartures] leaves out, shown as "+K" after the times. */
    val hiddenDepartureCount: Int get() = (departures.size - MAX_VISIBLE_DEPARTURES).coerceAtLeast(0)

    /**
     * Every departure in [groups] of this group's line to its destination, in board order: what the
     * row's details list. At a stop served by several lines one line splits into many groups, so
     * this gathers them. Falls back to this group's own departures when a refresh has replaced
     * them all, so details opened on a row that just left still show something.
     */
    fun departuresLike(groups: List<DepartureGroup>): List<Departure> {
        val first = departures.firstOrNull() ?: return departures
        return groups
            .flatMap { it.departures }
            .filter { it.line == first.line && it.destination == first.destination }
            .ifEmpty { departures }
    }

    companion object {
        /** The primary time and four secondary ones. */
        const val MAX_VISIBLE_DEPARTURES = 5
    }
}

data class StopDepartures(
    val stopId: String,
    val groups: List<DepartureGroup>,
)

@Serializable
data class WeatherInfo(
    val time: String,
    val temperature: Double,
    val symbolCode: String,
    val precipitation: Double = 0.0,
)

@Serializable
data class TripLeg(
    val mode: String? = null, // "foot", "bus", "metro", ...
    val lineName: String? = null, // null for foot legs
    val fromName: String? = null,
    val durationMinutes: Int = 0,
    /** Where the leg ends; Entur names a coordinate destination just "Destination". */
    val toName: String? = null,
    /** When the leg leaves and arrives, ISO-8601. Null on trips cached before they were kept. */
    val expectedStartTime: String? = null,
    val expectedEndTime: String? = null,
    /** The timetabled start, which [expectedStartTime] differs from when the leg runs late. */
    val aimedStartTime: String? = null,
    val distanceMeters: Int = 0,
    val isRealtime: Boolean = false,
    /** The boarding quay's public code ("2", "A") when it has one, shown as the platform. */
    val platform: String? = null,
    /** What the vehicle shows on its front, e.g. "Ringen via Majorstuen". */
    val destination: String? = null,
    /** The line's full name, e.g. "Sognsvann - Vestli". */
    val lineFullName: String? = null,
    val authority: String? = null,
    val notices: List<String> = emptyList(),
    /** The trip ridden, with [serviceDate], as Entur names it: what the leg's stops are asked for by. */
    val serviceJourneyId: String? = null,
    val serviceDate: String? = null,
    val fromQuayId: String? = null,
    val toQuayId: String? = null,
    /** Where the leg starts and ends, for journey alerts and walking directions. */
    val fromLatitude: Double? = null,
    val fromLongitude: Double? = null,
    val toLatitude: Double? = null,
    val toLongitude: Double? = null,
)

@Serializable
data class TripInfo(
    val destinationName: String,
    val expectedStartTime: String,
    /** When the trip reaches the destination, ISO-8601 like [expectedStartTime]; null if Entur omitted it. */
    val expectedEndTime: String? = null,
    val lineName: String,
    val durationMinutes: Int,
    val originName: String,
    val legs: List<TripLeg> = emptyList(),
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AUTO,
}

/** How the date is shown next to the header clock. Tapping the clock cycles through these in order. */
enum class DateDisplayStyle {
    /** Time only. */
    HIDDEN,

    /** Large day of month beside the time, weekday and month stacked small next to it. */
    BESIDE,

    /** A small "Wed 23 Sep" line under the time, with the day in bold. */
    STACKED,

    /** A tear-off calendar tile: month in a header strip above a large day of month. */
    TILE,

    /** Spelled out beside the time: "Wednesday 23 September", with the day enlarged. */
    LONG,
    ;

    fun next(): DateDisplayStyle = entries[(ordinal + 1) % entries.size]
}

enum class BurnInProtectionLevel(
    val maxOffsetDp: Float,
) {
    OFF(0f),
    LOW(3f),
    MEDIUM(6f),
    HIGH(12f),
}

/**
 * Front-camera motion sensitivity. [motionPixelCountThreshold] is how many changed pixels
 * (at full resolution) are required to register motion — lower means more sensitive.
 */
enum class CameraSensitivity(
    val motionPixelCountThreshold: Int,
) {
    LOW(10000),
    MEDIUM(5000),
    HIGH(2000),
}

/**
 * What the app is for. Chosen on first launch and changeable in Settings.
 */
enum class AppMode {
    /** Follows the device: nearest stop, and route options from where the user is standing. */
    ON_SPOT,

    /** The always-on wall board: favourite stops and routes from a saved location. */
    BOARD,
}
