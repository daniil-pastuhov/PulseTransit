package com.example.pulsetransit.data

/**
 * What the route search optimises for. Entur always lists routes by arrival and its cost weights
 * only prune routes that are clearly worse, so a preset both sets the weights and re-ranks the
 * candidates by [rankedBy]: a fast route with two changes would otherwise stay first.
 */
enum class RoutePreference {
    /** Entur's own choice: the earliest arrival. */
    FASTEST,

    /** Fewest changes, then the earliest arrival. */
    FEWER_TRANSFERS,

    /** Least walking, then the earliest arrival. */
    LESS_WALKING,
}

/** Entur transport [mode], narrowed to [subModes] when given. Sub-modes are matched per trip, not per line. */
data class EnturModeSelector(
    val mode: String,
    val subModes: List<String>? = null,
)

/** How Settings groups the [RouteTransportMode]s, and the Entur modes a whole group stands for. */
enum class RouteModeGroup(
    val enturModes: List<String>,
) {
    BUS(listOf("bus", "coach", "trolleybus")),
    TRAIN(listOf("rail")),
    BOAT(listOf("water")),

    /** Kinds that are a whole Entur mode each, so need nothing extra when all are excluded. */
    OTHER(emptyList()),
}

/**
 * The kinds of transit a route may be kept off, after the transport sub-modes Norwegian timetables
 * use. Entur tags these per trip: a Vy RE line runs both regional and long-distance trips, and a
 * rail replacement bus belongs to its train line. Sub-modes outside every kind stay allowed until
 * their whole group is excluded (see [RouteOptions.excludedEnturModes]).
 */
enum class RouteTransportMode(
    val group: RouteModeGroup,
    val selectors: List<EnturModeSelector>,
) {
    LOCAL_BUS(
        RouteModeGroup.BUS,
        listOf(
            EnturModeSelector(
                "bus",
                listOf(
                    "localBus",
                    "regionalBus",
                    "nightBus",
                    "postBus",
                    "specialNeedsBus",
                    "mobilityBus",
                    "mobilityBusForRegisteredDisabled",
                    "sightseeingBus",
                    "shuttleBus",
                    "highFrequencyBus",
                    "dedicatedLaneBus",
                    "schoolBus",
                    "schoolAndPublicServiceBus",
                ),
            ),
            EnturModeSelector("trolleybus"),
        ),
    ),

    /** Express buses and long-distance coaches, such as NOR-WAY Bussekspress and FlixBus. */
    EXPRESS_BUS(RouteModeGroup.BUS, listOf(EnturModeSelector("bus", listOf("expressBus")), EnturModeSelector("coach"))),
    AIRPORT_BUS(RouteModeGroup.BUS, listOf(EnturModeSelector("bus", listOf("airportLinkBus")))),
    RAIL_REPLACEMENT_BUS(RouteModeGroup.BUS, listOf(EnturModeSelector("bus", listOf("railReplacementBus")))),
    LOCAL_TRAIN(RouteModeGroup.TRAIN, listOf(EnturModeSelector("rail", listOf("local", "suburbanRailway", "railShuttle")))),
    REGIONAL_TRAIN(
        RouteModeGroup.TRAIN,
        listOf(EnturModeSelector("rail", listOf("regionalRail", "interregionalRail", "crossCountryRail"))),
    ),
    LONG_DISTANCE_TRAIN(
        RouteModeGroup.TRAIN,
        listOf(
            EnturModeSelector(
                "rail",
                listOf("longDistance", "international", "highSpeedRail", "nightRail", "sleeperRailService", "carTransportRailService"),
            ),
        ),
    ),

    /** Express trains: Flytoget, the one Norwegian line Entur tags `airportLinkRail`. */
    EXPRESS_TRAIN(RouteModeGroup.TRAIN, listOf(EnturModeSelector("rail", listOf("airportLinkRail")))),
    CAR_FERRY(
        RouteModeGroup.BOAT,
        listOf(
            EnturModeSelector(
                "water",
                listOf(
                    "internationalCarFerry",
                    "nationalCarFerry",
                    "regionalCarFerry",
                    "localCarFerry",
                    "roadFerryLink",
                    "cableFerry",
                    "highSpeedVehicleService",
                ),
            ),
        ),
    ),
    PASSENGER_FERRY(
        RouteModeGroup.BOAT,
        listOf(
            EnturModeSelector(
                "water",
                listOf(
                    "internationalPassengerFerry",
                    "nationalPassengerFerry",
                    "regionalPassengerFerry",
                    "localPassengerFerry",
                    "postBoat",
                    "scheduledFerry",
                    "shuttleFerryService",
                    "riverBus",
                    "schoolBoat",
                    "sightseeingService",
                    "airportBoatLink",
                ),
            ),
        ),
    ),

    /** Hurtigbåt. */
    EXPRESS_BOAT(RouteModeGroup.BOAT, listOf(EnturModeSelector("water", listOf("highSpeedPassengerService")))),
    METRO(RouteModeGroup.OTHER, listOf(EnturModeSelector("metro"))),
    TRAM(RouteModeGroup.OTHER, listOf(EnturModeSelector("tram"))),
    PLANE(RouteModeGroup.OTHER, listOf(EnturModeSelector("air"))),

    /** Bestillingstransport: on-demand taxis and buses. */
    ON_DEMAND(RouteModeGroup.OTHER, listOf(EnturModeSelector("taxi"), EnturModeSelector("bus", listOf("demandAndResponseBus")))),
    CABLE_CAR(RouteModeGroup.OTHER, listOf(EnturModeSelector("cableway"), EnturModeSelector("funicular"), EnturModeSelector("lift"))),
}

/**
 * How fast the user walks to, from and between stops. [NORMAL] leaves it to Entur, about 1.3 m/s
 * (4.8 km/h); walks on the map come out slower than the set speed, for crossings and corners.
 */
enum class WalkingSpeed(
    /** Entur's `walkSpeed`, or null for its default. */
    val metersPerSecond: Double?,
) {
    SLOW(1.0),
    NORMAL(null),
    FAST(1.7),
}

/** How routes to the destinations are searched for. The default asks Entur exactly as before options existed. */
data class RouteOptions(
    val preference: RoutePreference = RoutePreference.FASTEST,
    /** Transit kinds routes may not use; empty allows all. Never every kind: Settings keeps one. */
    val excludedModes: Set<RouteTransportMode> = emptySet(),
    /** Public codes of lines routes may not use: the board's filtered-out lines. */
    val bannedLines: Set<String> = emptySet(),
    val walkingSpeed: WalkingSpeed = WalkingSpeed.NORMAL,
) {
    /**
     * What Entur should leave out for [excludedModes], in [RouteTransportMode] order. A group with
     * every kind excluded leaves out its whole Entur modes, so sub-modes no kind names go too,
     * except a mode an allowed kind elsewhere still uses: on-demand buses are `bus` too, so then
     * only the group's named sub-modes go.
     */
    val excludedEnturModes: List<EnturModeSelector>
        get() {
            val stillUsed = (RouteTransportMode.entries - excludedModes).flatMap { kind -> kind.selectors.map { it.mode } }.toSet()
            return RouteModeGroup.entries.flatMap { group ->
                val kinds = RouteTransportMode.entries.filter { it.group == group }
                val excluded = kinds.filter { it in excludedModes }
                val selectors = excluded.flatMap { it.selectors }
                if (group.enturModes.isNotEmpty() && excluded.size == kinds.size) {
                    group.enturModes.flatMap { mode ->
                        if (mode in stillUsed) selectors.filter { it.mode == mode } else listOf(EnturModeSelector(mode))
                    }
                } else {
                    selectors
                }
            }
        }

    /**
     * Appended to a route's cache key so a route found under other options is never served for
     * these. Empty for the default, so routes cached before options existed still match.
     */
    val cacheKeySuffix: String
        get() {
            if (this == RouteOptions()) return ""
            val modes = excludedModes.map { it.name }.sorted().joinToString(",")
            val lines = bannedLines.sorted().joinToString(",")
            return "|${preference.name}|-$modes|-$lines|${walkingSpeed.name}"
        }

    companion object {
        /** Extra cost per change for [RoutePreference.FEWER_TRANSFERS], in Entur's cost units (≈ seconds). */
        const val FEWER_TRANSFERS_PENALTY = 1800

        /** Walking cost multiplier for [RoutePreference.LESS_WALKING]. */
        const val LESS_WALKING_RELUCTANCE = 10.0

        /** Routes asked for when a preset re-ranks them, so there is something to choose from. */
        const val RANKING_CANDIDATES = 5

        /** Walks within this many metres of each other count as equal, so arrival decides. */
        const val WALK_BUCKET_METERS = 100
    }
}

/**
 * These routes, best first for [preference]. [RoutePreference.FASTEST] keeps Entur's order; the
 * others sort by their own measure and then by arrival, keeping Entur's order for ties.
 */
fun List<TripInfo>.rankedBy(preference: RoutePreference): List<TripInfo> {
    val byArrival = compareBy<TripInfo, String?>(nullsLast()) { it.expectedEndTime }
    return when (preference) {
        RoutePreference.FASTEST -> this
        RoutePreference.FEWER_TRANSFERS -> sortedWith(compareBy<TripInfo> { transferCount(it) }.then(byArrival))
        RoutePreference.LESS_WALKING ->
            sortedWith(compareBy<TripInfo> { walkDistanceMeters(it) / RouteOptions.WALK_BUCKET_METERS }.then(byArrival))
    }
}
