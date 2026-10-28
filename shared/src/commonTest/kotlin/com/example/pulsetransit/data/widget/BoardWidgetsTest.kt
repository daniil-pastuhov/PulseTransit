package com.example.pulsetransit.data.widget

import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DestinationSchedule
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import com.example.pulsetransit.data.TripLeg
import com.example.pulsetransit.data.isoOslo
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoardWidgetsTest {
    private val now = 1_800_000_000_000L

    private fun departure(
        line: String,
        destination: String,
        inMinutes: Int,
        cancelled: Boolean = false,
    ) = Departure(
        line = line,
        destination = destination,
        expectedTime = isoOslo(now + inMinutes * 60_000L),
        aimedTime = isoOslo(now + inMinutes * 60_000L),
        isRealtime = true,
        isCancelled = cancelled,
        platform = "A",
    )

    private fun rows(
        departures: List<Departure>,
        hiddenLines: Set<String> = emptySet(),
        showCancelled: Boolean = false,
        maxRows: Int = 4,
        timesPerRow: Int = 3,
    ) = widgetDepartureRows(departures, now, hiddenLines, showCancelled, maxRows, timesPerRow)

    @Test
    fun `every departure of a line to one destination joins one row`() {
        val result =
            rows(
                listOf(
                    departure("31", "Tonsenhagen", 1),
                    departure("1", "Frognerseteren", 2),
                    departure("31", "Tonsenhagen", 5),
                    departure("31", "Snarøya", 6),
                ),
            )
        assertEquals(
            listOf("31" to "Tonsenhagen", "1" to "Frognerseteren", "31" to "Snarøya"),
            result.map { it.line to it.destination },
        )
        assertEquals(2, result[0].departures.size)
        assertEquals("A", result[0].platform)
    }

    @Test
    fun `rows and times per row are capped`() {
        val departures = (0 until 10).map { departure("${it % 5}", "X", it + 1) }
        val result = rows(departures, maxRows = 2, timesPerRow = 1)
        assertEquals(listOf("0", "1"), result.map { it.line })
        assertTrue(result.all { it.departures.size == 1 })
    }

    @Test
    fun `departed and filtered and cancelled departures are left out`() {
        val result =
            rows(
                listOf(
                    departure("1", "Gone", -2),
                    departure("2", "Hidden", 3),
                    departure("3", "Cancelled", 4, cancelled = true),
                    departure("4", "Kept", 5),
                ),
                hiddenLines = setOf("2"),
            )
        assertEquals(listOf("4"), result.map { it.line })
    }

    @Test
    fun `cancelled departures stay when the board shows them`() {
        val result = rows(listOf(departure("3", "Cancelled", 4, cancelled = true)), showCancelled = true)
        assertEquals(listOf("3"), result.map { it.line })
    }

    @Test
    fun `departure times are given in epoch millis`() {
        val result = rows(listOf(departure("1", "X", 5)))
        assertEquals(listOf(now + 5 * 60_000L), result.single().departureMillis)
    }

    @Test
    fun `nothing is shown for no room`() {
        assertTrue(rows(listOf(departure("1", "X", 5)), maxRows = 0).isEmpty())
    }

    private val oslo = StopPlace("NSR:StopPlace:59872", "Oslo S")
    private val nationaltheatret = StopPlace("NSR:StopPlace:58404", "Nationaltheatret")

    @Test
    fun `a configured place is picked while it is saved`() {
        assertEquals(nationaltheatret, pickWidgetPlace(listOf(oslo, nationaltheatret), nationaltheatret.id))
    }

    @Test
    fun `a removed or unset place falls back to the first`() {
        assertEquals(oslo, pickWidgetPlace(listOf(oslo, nationaltheatret), "NSR:StopPlace:gone"))
        assertEquals(oslo, pickWidgetPlace(listOf(oslo, nationaltheatret), null))
        assertNull(pickWidgetPlace(emptyList(), null))
    }

    // 2026-10-19 is a Monday.
    private val mondayEvening = LocalDateTime(2026, 10, 19, 18, 0)
    private val mornings = DestinationSchedule(startMinute = 7 * 60, endMinute = 9 * 60)

    @Test
    fun `an unset destination is the first one whose schedule is active`() {
        val schedules = mapOf(oslo.id to mornings)
        assertEquals(nationaltheatret, pickWidgetDestination(listOf(oslo, nationaltheatret), schedules, null, mondayEvening))
    }

    @Test
    fun `a configured destination is shown outside its schedule`() {
        val schedules = mapOf(oslo.id to mornings)
        assertEquals(oslo, pickWidgetDestination(listOf(oslo, nationaltheatret), schedules, oslo.id, mondayEvening))
    }

    @Test
    fun `with no schedule active the first destination is shown`() {
        val schedules = mapOf(oslo.id to mornings, nationaltheatret.id to mornings)
        assertEquals(oslo, pickWidgetDestination(listOf(oslo, nationaltheatret), schedules, null, mondayEvening))
    }

    @Test
    fun `a route lists its ridden legs and the first one's boarding`() {
        val trip =
            TripInfo(
                destinationName = "Majorstuen",
                expectedStartTime = isoOslo(now),
                expectedEndTime = isoOslo(now + 20 * 60_000L),
                lineName = "2",
                durationMinutes = 20,
                originName = "Home",
                legs =
                    listOf(
                        TripLeg(mode = "foot", durationMinutes = 4),
                        TripLeg(
                            mode = "metro",
                            lineName = "2",
                            expectedStartTime = isoOslo(now + 4 * 60_000L),
                            platform = "2",
                            isRealtime = true,
                        ),
                        TripLeg(mode = "bus", lineName = "20"),
                    ),
            )
        val route = assertNotNull(widgetRoute(trip))
        assertEquals(now, route.leaveAtMillis)
        assertEquals(now + 4 * 60_000L, route.boardAtMillis)
        assertEquals(now + 20 * 60_000L, route.arriveAtMillis)
        assertEquals("2", route.platform)
        assertEquals(listOf(WidgetRouteLeg("metro", "2"), WidgetRouteLeg("bus", "20")), route.legs)
        assertTrue(route.isRealtime)
    }

    @Test
    fun `a trip cached without legs keeps its line summary`() {
        val trip = TripInfo("Majorstuen", isoOslo(now), null, "2", 20, "Home")
        val route = assertNotNull(widgetRoute(trip))
        assertEquals(listOf(WidgetRouteLeg(null, "2")), route.legs)
        assertNull(route.boardAtMillis)
        assertNull(route.arriveAtMillis)
    }

    @Test
    fun `a trip whose start cannot be read has no route`() {
        assertNull(widgetRoute(TripInfo("Majorstuen", "soon", null, "2", 20, "Home")))
    }
}
