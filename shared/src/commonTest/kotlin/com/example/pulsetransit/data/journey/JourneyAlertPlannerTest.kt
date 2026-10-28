package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.journey.JourneyFixtures.at
import com.example.pulsetransit.data.journey.JourneyFixtures.journey
import com.example.pulsetransit.data.journey.JourneyFixtures.liveStops
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JourneyAlertPlannerTest {
    private fun scheduleOf(journey: TrackedJourney) = JourneyAlertPlanner.schedule(journey).associate { it.alert.id to it.atMillis }

    @Test
    fun `every step of the journey has its alert at its time`() {
        val schedule = JourneyAlertPlanner.schedule(journey(legStops = liveStops()))

        assertEquals(
            listOf(
                "walk-0" to at(-1),
                "depart-1" to at(5),
                // As the metro leaves Nationaltheatret, the stop before Majorstuen.
                "next-stop-1" to at(10),
                "transfer-1" to at(14),
                "depart-3" to at(17),
                "next-stop-3" to at(22),
                "walk-4" to at(26),
                "arrived" to at(31),
            ),
            schedule.map { it.alert.id to it.atMillis },
        )
    }

    @Test
    fun `alerts say what to do`() {
        val alerts = JourneyAlertPlanner.schedule(journey(legStops = liveStops())).associateBy { it.alert.id }

        assertEquals(
            JourneyAlert.DepartingSoon(1, "5", "Vestli", "Jernbanetorget", "2", departsAtMillis = at(6)),
            alerts.getValue("depart-1").alert,
        )
        assertEquals(JourneyAlert.NextStopIsYours(1, "Majorstuen"), alerts.getValue("next-stop-1").alert)
        assertEquals(JourneyAlert.Transfer(1, "20", "B", departsAtMillis = at(18)), alerts.getValue("transfer-1").alert)
        assertEquals(JourneyAlert.Arrived("Home"), alerts.getValue("arrived").alert)
    }

    @Test
    fun `only the final walk offers directions`() {
        val alerts = JourneyAlertPlanner.schedule(journey()).associateBy { it.alert.id }
        val first = alerts.getValue("walk-0").alert as JourneyAlert.WalkTo
        val last = alerts.getValue("walk-4").alert as JourneyAlert.WalkTo

        assertFalse(first.canNavigate)
        assertEquals("Jernbanetorget", first.name)
        assertTrue(last.canNavigate)
        assertEquals("Home", last.name)
        assertEquals(JourneyFixtures.home, last.latitude to last.longitude)
    }

    @Test
    fun `a final walk without coordinates offers no directions`() {
        val trip = JourneyFixtures.trip
        val noCoords = trip.copy(legs = trip.legs.dropLast(1) + trip.legs.last().copy(toLatitude = null, toLongitude = null))
        val walk =
            JourneyAlertPlanner
                .schedule(journey().copy(trip = noCoords))
                .first { it.alert.id == "walk-4" }
                .alert as JourneyAlert.WalkTo

        assertFalse(walk.canNavigate)
    }

    @Test
    fun `the zero-minute transfer walk has no alert of its own`() {
        assertFalse(JourneyAlertPlanner.schedule(journey()).any { it.alert.id == "walk-2" })
    }

    @Test
    fun `a delay moves the alerts after it and the walk to it`() {
        val schedule = scheduleOf(journey(legStops = liveStops(metroDelay = 3)))

        assertEquals(at(8), schedule["depart-1"])
        assertEquals(at(13), schedule["next-stop-1"])
        // Leaving for a late metro later, rather than waiting on the platform.
        assertEquals(at(2), schedule["walk-0"])
        assertEquals(at(17), schedule["transfer-1"])
        // The bus is on time, so the change is shorter but its departure stays put.
        val alerts = JourneyAlertPlanner.schedule(journey(legStops = liveStops(metroDelay = 3))).associateBy { it.alert.id }
        assertEquals(JourneyAlert.Transfer(1, "20", "B", departsAtMillis = at(18)), alerts.getValue("transfer-1").alert)
        // The late metro's alert names the time it now leaves.
        assertEquals(at(9), (alerts.getValue("depart-1").alert as JourneyAlert.DepartingSoon).departsAtMillis)
    }

    @Test
    fun `without live stops the timetable times are used`() {
        val schedule = scheduleOf(journey())

        assertEquals(at(5), schedule["depart-1"])
        // Two minutes before arrival, not knowing the stops.
        assertEquals(at(12), schedule["next-stop-1"])
        assertEquals(at(31), schedule["arrived"])
    }

    @Test
    fun `on a one-stop ride the next stop is announced on boarding`() {
        val oneStop = mapOf(3 to JourneyFixtures.busStops().let { listOf(it.first(), it.last()) })

        assertEquals(at(18), scheduleOf(journey(legStops = oneStop))["next-stop-3"])
    }

    @Test
    fun `due alerts are shown once`() {
        val due = JourneyAlertPlanner.dueAlerts(journey(legStops = liveStops()), at(5))
        assertEquals(listOf("walk-0", "depart-1"), due.show.map { it.id })
        assertEquals(setOf("walk-0", "depart-1"), due.consumedIds)

        val again = JourneyAlertPlanner.dueAlerts(journey(legStops = liveStops(), fired = due.consumedIds), at(5))
        assertTrue(again.show.isEmpty())
        assertTrue(again.consumedIds.isEmpty())
    }

    @Test
    fun `alerts long overdue are dropped but marked fired`() {
        // As after the app was stopped for a while mid-journey.
        val due = JourneyAlertPlanner.dueAlerts(journey(legStops = liveStops()), at(20))

        assertEquals(listOf("next-stop-1", "transfer-1", "depart-3"), due.show.map { it.id })
        assertTrue("walk-0" in due.consumedIds && "depart-1" in due.consumedIds)
    }

    @Test
    fun `a muted journey shows nothing but still uses up its alerts`() {
        val due = JourneyAlertPlanner.dueAlerts(journey(legStops = liveStops(), muted = true), at(5))

        assertTrue(due.show.isEmpty())
        assertEquals(setOf("walk-0", "depart-1"), due.consumedIds)
    }

    @Test
    fun `upcoming alerts leave out what is past or fired`() {
        val upcoming = JourneyAlertPlanner.upcomingAlerts(journey(legStops = liveStops(), fired = setOf("next-stop-1")), at(5))

        assertEquals(listOf("transfer-1", "depart-3", "next-stop-3", "walk-4", "arrived"), upcoming.map { it.alert.id })
        assertTrue(JourneyAlertPlanner.upcomingAlerts(journey(muted = true), at(5)).isEmpty())
    }

    @Test
    fun `a device where the metro should be is on the journey`() {
        // Halfway between Stortinget (T0+8) and Nationaltheatret (T0+10).
        val onBoard = JourneyPosition.isOffJourney(journey(legStops = liveStops()), at(9), 59.9141, 10.7353)

        assertEquals(false, onBoard)
    }

    @Test
    fun `a device left at the platform after the metro has gone is off the journey`() {
        val (_, lat, lon) = JourneyFixtures.jernbanetorget

        assertEquals(true, JourneyPosition.isOffJourney(journey(legStops = liveStops()), at(10), lat, lon))
    }

    @Test
    fun `being off the journey is only judged on a ride`() {
        val (_, lat, lon) = JourneyFixtures.jernbanetorget
        val journey = journey(legStops = liveStops())

        assertNull(JourneyPosition.isOffJourney(journey, at(2), 0.0, 0.0), "walking to the stop")
        assertNull(JourneyPosition.isOffJourney(journey, at(6), lat, lon), "just departed, within the grace")
        assertNull(JourneyPosition.isOffJourney(journey, at(16), 0.0, 0.0), "changing")
    }

    @Test
    fun `before the first stop with a position the vehicle is taken to be there`() {
        // The boarding stop has no position, so the first known point (Stortinget, T0+8) is ahead.
        val stops = liveStops().toMutableMap()
        stops[1] = stops.getValue(1).mapIndexed { index, stop -> if (index == 0) stop.copy(latitude = null, longitude = null) else stop }
        val (_, lat, lon) = JourneyFixtures.stortinget

        assertEquals(false, JourneyPosition.isOffJourney(journey(legStops = stops), at(7), lat, lon))
    }

    @Test
    fun `without live stops the ends of the leg stand in for them`() {
        val (_, lat, lon) = JourneyFixtures.jernbanetorget

        assertEquals(true, JourneyPosition.isOffJourney(journey(), at(13), lat, lon))
    }

    @Test
    fun `without any positions being off the journey cannot be told`() {
        val trip = JourneyFixtures.trip
        val blind =
            trip.copy(
                legs = trip.legs.map { it.copy(fromLatitude = null, fromLongitude = null, toLatitude = null, toLongitude = null) },
            )

        assertNull(JourneyPosition.isOffJourney(journey().copy(trip = blind), at(10), 0.0, 0.0))
    }

    @Test
    fun `progress follows the journey step by step`() {
        val journey = journey(legStops = liveStops())

        fun phaseAt(minutes: Int) = JourneyPosition.progress(journey, at(minutes), locationLost = false)

        assertEquals(JourneyPhase.WALKING, phaseAt(1).phase)
        assertEquals("Jernbanetorget", phaseAt(1).targetName)

        val waiting = phaseAt(5)
        assertEquals(JourneyPhase.WAITING, waiting.phase)
        assertEquals("5", waiting.lineName)
        assertEquals(at(6), waiting.nextAtMillis)

        val riding = phaseAt(9)
        assertEquals(JourneyPhase.RIDING, riding.phase)
        assertEquals("Majorstuen", riding.targetName)
        assertEquals(2, riding.stopsLeft)
        assertEquals(at(14), riding.nextAtMillis)
        assertEquals(at(31), riding.arrivalAtMillis)

        assertEquals(JourneyPhase.WAITING, phaseAt(15).phase)
        assertEquals("20", phaseAt(15).lineName)
        assertEquals(JourneyPhase.WALKING, phaseAt(28).phase)
        assertEquals("Home", phaseAt(28).targetName)
        assertEquals(JourneyPhase.ARRIVED, phaseAt(32).phase)
    }

    @Test
    fun `progress reports the mode and a lost location`() {
        val progress = JourneyPosition.progress(journey(JourneyTrackingMode.LOCATION), at(1), locationLost = true)

        assertEquals(JourneyTrackingMode.LOCATION, progress.mode)
        assertTrue(progress.locationLost)
    }
}
