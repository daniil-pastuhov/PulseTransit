package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.isoOslo

/**
 * One journey through every kind of step, timed from [T0]:
 * - 0: walk 4 min to Jernbanetorget (T0 to T0+4)
 * - 1: metro 5, Jernbanetorget T0+6 to Majorstuen T0+14, through Stortinget and Nationaltheatret
 * - 2: a zero-minute transfer walk, which no alert mentions
 * - 3: bus 20, Majorstuen T0+18 to Skøyen T0+26, through Frognerparken
 * - 4: walk 5 min to the destination, Home (T0+26 to T0+31)
 */
object JourneyFixtures {
    /** 2026-10-28 09:00 Oslo. */
    const val T0 = 1_793_174_400_000L
    const val MINUTE = 60_000L

    fun at(minutes: Int) = T0 + minutes * MINUTE

    fun iso(minutes: Int) = isoOslo(at(minutes))

    const val METRO_JOURNEY = "RUT:ServiceJourney:5"
    const val BUS_JOURNEY = "RUT:ServiceJourney:20"

    // Roughly where these stops are, a few hundred metres to a few kilometres apart.
    val jernbanetorget = Triple("NSR:Quay:1", 59.9118, 10.7500)
    val stortinget = Triple("NSR:Quay:2", 59.9133, 10.7390)
    val nationaltheatret = Triple("NSR:Quay:3", 59.9149, 10.7317)
    val majorstuen = Triple("NSR:Quay:4", 59.9297, 10.7155)
    val frognerparken = Triple("NSR:Quay:5", 59.9260, 10.7040)
    val skoyen = Triple("NSR:Quay:6", 59.9223, 10.6790)
    val home = 59.9200 to 10.6700

    private fun stop(
        quay: Triple<String, Double, Double>,
        name: String,
        minutes: Int,
    ) = JourneyStop(
        quayId = quay.first,
        stopPlaceId = quay.first.replace("Quay", "StopPlace"),
        name = name,
        expectedTime = iso(minutes),
        latitude = quay.second,
        longitude = quay.third,
    )

    /** The metro's whole run, starting a stop before ours, delayed by [delay] minutes. */
    fun metroStops(delay: Int = 0) =
        listOf(
            stop(Triple("NSR:Quay:0", 59.91, 10.76), "Oslo bussterminal", 4 + delay),
            stop(jernbanetorget, "Jernbanetorget", 6 + delay),
            stop(stortinget, "Stortinget", 8 + delay),
            stop(nationaltheatret, "Nationaltheatret", 10 + delay),
            stop(majorstuen, "Majorstuen", 14 + delay),
            stop(Triple("NSR:Quay:9", 59.94, 10.71), "Blindern", 16 + delay),
        )

    fun busStops(delay: Int = 0) =
        listOf(
            stop(majorstuen, "Majorstuen", 18 + delay),
            stop(frognerparken, "Frognerparken", 22 + delay),
            stop(skoyen, "Skøyen", 26 + delay),
        )

    val trip =
        TripInfo(
            destinationName = "Home",
            expectedStartTime = iso(0),
            expectedEndTime = iso(31),
            lineName = "5",
            durationMinutes = 31,
            originName = "Jernbanetorget",
            legs =
                listOf(
                    TripLeg(
                        mode = "foot",
                        fromName = "Origin",
                        toName = "Jernbanetorget",
                        durationMinutes = 4,
                        expectedStartTime = iso(0),
                        expectedEndTime = iso(4),
                        distanceMeters = 300,
                        toLatitude = jernbanetorget.second,
                        toLongitude = jernbanetorget.third,
                    ),
                    TripLeg(
                        mode = "metro",
                        lineName = "5",
                        fromName = "Jernbanetorget",
                        toName = "Majorstuen",
                        durationMinutes = 8,
                        expectedStartTime = iso(6),
                        expectedEndTime = iso(14),
                        aimedStartTime = iso(6),
                        platform = "2",
                        destination = "Vestli",
                        serviceJourneyId = METRO_JOURNEY,
                        serviceDate = "2026-10-28",
                        fromQuayId = jernbanetorget.first,
                        toQuayId = majorstuen.first,
                        fromLatitude = jernbanetorget.second,
                        fromLongitude = jernbanetorget.third,
                        toLatitude = majorstuen.second,
                        toLongitude = majorstuen.third,
                    ),
                    TripLeg(
                        mode = "foot",
                        fromName = "Majorstuen",
                        toName = "Majorstuen",
                        durationMinutes = 0,
                        expectedStartTime = iso(14),
                        expectedEndTime = iso(14),
                    ),
                    TripLeg(
                        mode = "bus",
                        lineName = "20",
                        fromName = "Majorstuen",
                        toName = "Skøyen",
                        durationMinutes = 8,
                        expectedStartTime = iso(18),
                        expectedEndTime = iso(26),
                        aimedStartTime = iso(18),
                        platform = "B",
                        destination = "Skøyen",
                        serviceJourneyId = BUS_JOURNEY,
                        serviceDate = "2026-10-28",
                        fromQuayId = majorstuen.first,
                        toQuayId = skoyen.first,
                        fromLatitude = majorstuen.second,
                        fromLongitude = majorstuen.third,
                        toLatitude = skoyen.second,
                        toLongitude = skoyen.third,
                    ),
                    TripLeg(
                        mode = "foot",
                        fromName = "Skøyen",
                        toName = "Destination",
                        durationMinutes = 5,
                        expectedStartTime = iso(26),
                        expectedEndTime = iso(31),
                        distanceMeters = 400,
                        fromLatitude = skoyen.second,
                        fromLongitude = skoyen.third,
                        toLatitude = home.first,
                        toLongitude = home.second,
                    ),
                ),
        )

    /** The legs' own stops, as the tracker stores them after a fetch. */
    fun liveStops(
        metroDelay: Int = 0,
        busDelay: Int = 0,
    ) = mapOf(
        1 to metroStops(metroDelay).subList(1, 5),
        3 to busStops(busDelay),
    )

    fun journey(
        mode: JourneyTrackingMode = JourneyTrackingMode.TIME_ONLY,
        legStops: Map<Int, List<JourneyStop>> = emptyMap(),
        fired: Set<String> = emptySet(),
        muted: Boolean = false,
    ) = TrackedJourney(
        id = "$METRO_JOURNEY|$BUS_JOURNEY",
        trip = trip,
        mode = mode,
        startedAtMillis = at(-10),
        legStops = legStops,
        firedAlertIds = fired,
        muted = muted,
    )
}
