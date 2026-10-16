package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.JourneyStop
import com.example.pulsetransit.data.nowMillis
import kotlin.test.Test
import kotlin.test.assertEquals

/** A departure's details ask the board for the stops its trip has ahead of it. */
class StopBoardJourneyTest : BoardTestBase() {
    private val trip =
        listOf(
            JourneyStop("Q0", "NSR:StopPlace:0", "Mortensrud T", "2026-10-16T20:29:00+02:00"),
            JourneyStop("Q1", oslo.id, "Oslo S", "2026-10-16T20:32:00+02:00"),
            JourneyStop("Q2", "NSR:StopPlace:2", "Holmlia stasjon", "2026-10-16T20:42:00+02:00"),
        )

    @Test
    fun `the stops ahead start where the departure boards`() =
        boardTest {
            entur.journeyStops = trip
            val departure =
                departure("73", "Holmlia stasjon", nowMillis() + 5 * minute)
                    .copy(quayId = "Q1", serviceJourneyId = "RUT:ServiceJourney:1", serviceDate = "2026-10-16")

            assertEquals(trip.drop(1), viewModel().journeyStopsAhead(departure, oslo.id))
        }

    @Test
    fun `a departure without a known trip has no stops and asks nothing`() =
        boardTest {
            entur.journeyStops = trip
            val departure = departure("73", "Holmlia stasjon", nowMillis() + 5 * minute)
            val vm = viewModel()

            assertEquals(emptyList(), vm.journeyStopsAhead(departure, oslo.id))
            assertEquals(emptyList(), vm.journeyStopsAhead(departure.copy(serviceJourneyId = "RUT:ServiceJourney:1"), oslo.id))
            assertEquals(0, entur.journeyStopsCalls)
        }
}
