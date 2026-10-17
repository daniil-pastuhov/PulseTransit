package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RouteOptionsTest {
    private fun foot(meters: Int) = TripLeg(mode = "foot", distanceMeters = meters)

    private fun ride(line: String) = TripLeg(mode = "bus", lineName = line)

    private fun trip(
        name: String,
        arrival: String?,
        vararg legs: TripLeg,
    ) = TripInfo(
        destinationName = "",
        expectedStartTime = "2026-10-17T11:00:00+02:00",
        expectedEndTime = arrival,
        lineName = name,
        durationMinutes = 0,
        originName = "",
        legs = legs.toList(),
    )

    // Entur's order: earliest arrival first.
    private val twoChanges = trip("two changes", "2026-10-17T12:06:00+02:00", foot(300), ride("66"), ride("L1"), ride("31"), foot(200))
    private val direct = trip("direct", "2026-10-17T12:14:00+02:00", foot(700), ride("31"), foot(13))
    private val directLater = trip("direct later", "2026-10-17T12:29:00+02:00", foot(700), ride("31"), foot(13))
    private val shortWalk = trip("short walk", "2026-10-17T12:21:00+02:00", foot(150), ride("390"), ride("31"), foot(50))

    private val enturOrder = listOf(twoChanges, direct, directLater, shortWalk)

    @Test
    fun `fastest keeps Entur's order`() {
        assertEquals(enturOrder, enturOrder.rankedBy(RoutePreference.FASTEST))
    }

    @Test
    fun `fewer transfers puts the fewest changes first and then the earliest arrival`() {
        assertEquals(
            listOf(direct, directLater, shortWalk, twoChanges),
            enturOrder.rankedBy(RoutePreference.FEWER_TRANSFERS),
        )
    }

    @Test
    fun `less walking puts the shortest walk first and treats walks within 100 m as equal`() {
        // 240 m against short walk's 200 m: the same 100 m step, so the earlier arrival goes first.
        val slightlyLonger = trip("slightly longer", "2026-10-17T12:10:00+02:00", foot(240), ride("5"))

        assertEquals(
            listOf(slightlyLonger, shortWalk, twoChanges, direct, directLater),
            (enturOrder + slightlyLonger).rankedBy(RoutePreference.LESS_WALKING),
        )
    }

    @Test
    fun `a route without an arrival time ranks after its equals`() {
        val unknown = trip("unknown arrival", null, foot(700), ride("31"))

        assertEquals(listOf(direct, unknown), listOf(unknown, direct).rankedBy(RoutePreference.FEWER_TRANSFERS))
    }

    @Test
    fun `the default options exclude nothing and keep the old cache key`() {
        assertTrue(RouteOptions().excludedEnturModes.isEmpty())
        assertEquals("", RouteOptions().cacheKeySuffix)
    }

    @Test
    fun `excluding a kind leaves out only its sub-modes`() {
        val excluded = RouteOptions(excludedModes = setOf(RouteTransportMode.EXPRESS_BUS, RouteTransportMode.METRO)).excludedEnturModes

        assertEquals(
            listOf(EnturModeSelector("bus", listOf("expressBus")), EnturModeSelector("coach"), EnturModeSelector("metro")),
            excluded,
        )
    }

    @Test
    fun `excluding every kind in a group leaves out its whole modes`() {
        val trains = RouteTransportMode.entries.filter { it.group == RouteModeGroup.TRAIN }.toSet()

        assertEquals(listOf(EnturModeSelector("rail")), RouteOptions(excludedModes = trains).excludedEnturModes)
    }

    @Test
    fun `excluding every bus kind keeps on-demand buses while on-demand is allowed`() {
        val buses = RouteTransportMode.entries.filter { it.group == RouteModeGroup.BUS }.toSet()

        val onDemandAllowed = RouteOptions(excludedModes = buses).excludedEnturModes
        assertTrue(EnturModeSelector("bus") !in onDemandAllowed)
        assertTrue(onDemandAllowed.none { "demandAndResponseBus" in it.subModes.orEmpty() })
        assertTrue(EnturModeSelector("bus", listOf("expressBus")) in onDemandAllowed)
        assertTrue(EnturModeSelector("coach") in onDemandAllowed)

        val onDemandExcluded = RouteOptions(excludedModes = buses + RouteTransportMode.ON_DEMAND).excludedEnturModes
        assertTrue(EnturModeSelector("bus") in onDemandExcluded)
    }

    @Test
    fun `no sub-mode belongs to two kinds`() {
        val pairs = RouteTransportMode.entries.flatMap { kind -> kind.selectors.flatMap { s -> s.subModes.orEmpty().map { s.mode to it } } }

        assertEquals(pairs.size, pairs.toSet().size)
    }

    @Test
    fun `the cache key tells option sets apart whatever their order`() {
        val a =
            RouteOptions(
                RoutePreference.FEWER_TRANSFERS,
                setOf(RouteTransportMode.LOCAL_BUS, RouteTransportMode.CAR_FERRY),
                setOf("31", "L1"),
            )
        val b =
            RouteOptions(
                RoutePreference.FEWER_TRANSFERS,
                setOf(RouteTransportMode.CAR_FERRY, RouteTransportMode.LOCAL_BUS),
                setOf("L1", "31"),
            )

        assertEquals(a.cacheKeySuffix, b.cacheKeySuffix)
        assertTrue(a.cacheKeySuffix != a.copy(preference = RoutePreference.LESS_WALKING).cacheKeySuffix)
        assertTrue(a.cacheKeySuffix != a.copy(bannedLines = setOf("31")).cacheKeySuffix)
    }
}
