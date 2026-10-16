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
