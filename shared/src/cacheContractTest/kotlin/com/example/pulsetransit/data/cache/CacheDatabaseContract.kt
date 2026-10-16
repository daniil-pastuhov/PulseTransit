package com.example.pulsetransit.data.cache

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

/**
 * Exercises the real Room queries. The repository tests use fake DAOs that reproduce these
 * semantics by hand, so this is what actually pins the SQL down. It runs against each platform's
 * real SQLite: Android's framework database on a device, and the bundled driver on iOS, through a
 * subclass per platform that only says how to open an in-memory database.
 *
 * It lives in its own source directory, compiled into iosTest and androidDeviceTest, rather than in
 * commonTest: commonTest's sentence-style test names cannot be dexed for a device below API 30.
 */
abstract class CacheDatabaseContract {
    protected abstract fun createDatabase(): CacheDatabase

    private val db by lazy { createDatabase() }
    private val departures by lazy { db.departureCacheDao() }
    private val forecasts by lazy { db.forecastCacheDao() }
    private val trips by lazy { db.tripCacheDao() }

    private val stopId = "NSR:StopPlace:59872"
    private val now = Clock.System.now().toEpochMilliseconds()
    private val minute = 60 * 1000L

    @AfterTest
    fun tearDown() = db.close()

    private fun departure(
        line: String,
        destination: String,
        atMillis: Long,
        position: Int,
        notices: List<String> = emptyList(),
    ) = CachedDeparture(
        stopId = stopId,
        position = position,
        line = line,
        destination = destination,
        expectedTime = "iso-$position",
        aimedTime = "iso-$position",
        expectedAtMillis = atMillis,
        isRealtime = true,
        isCancelled = false,
        transportMode = "rail",
        delayMinutes = 0,
        notices = notices,
        quayId = "NSR:Quay:1",
        fetchedAt = now,
    )

    private fun trip(
        routeKey: String,
        position: Int,
        startAtMillis: Long,
        line: String = "L$position",
    ) = CachedTrip(
        routeKey = routeKey,
        position = position,
        destinationName = "Majorstuen",
        expectedStartTime = "iso-$position",
        expectedStartAtMillis = startAtMillis,
        lineName = line,
        durationMinutes = 20,
        originName = "Oslo S",
        legs = emptyList(),
        fetchedAt = now,
    )

    @Test
    fun tripGetUpcoming_ordersByPositionAndDropsStartedTrips() =
        runTest {
            val key = "59.9100,10.7500>NSR:StopPlace:3"
            trips.replaceRoute(
                key,
                listOf(
                    trip(key, 0, now + 9 * minute),
                    trip(key, 1, now - minute),
                    trip(key, 2, now + 3 * minute),
                ),
            )

            assertEquals(listOf("L0", "L2"), trips.getUpcoming(key, now).map { it.lineName })
        }

    @Test
    fun tripReplaceRoute_replacesOnlyThatRoute() =
        runTest {
            val key = "a>NSR:StopPlace:3"
            val other = "b>NSR:StopPlace:3"
            trips.replaceRoute(key, listOf(trip(key, 0, now + minute), trip(key, 1, now + 2 * minute)))
            trips.replaceRoute(other, listOf(trip(other, 0, now + minute)))

            trips.replaceRoute(key, listOf(trip(key, 0, now + 5 * minute, line = "NEW")))

            assertEquals(listOf("NEW"), trips.getUpcoming(key, now).map { it.lineName })
            assertEquals(1, trips.getUpcoming(other, now).size)
        }

    @Test
    fun getUpcoming_dropsDeparturesAlreadyDue() =
        runTest {
            departures.replaceStop(
                stopId,
                listOf(
                    departure("R13", "Drammen", now - 2 * minute, position = 0),
                    departure("L1", "Lillestrøm", now + 4 * minute, position = 1),
                ),
            )

            val result = departures.getUpcoming(stopId, now)

            assertEquals(listOf("L1"), result.map { it.line })
        }

    @Test
    fun getUpcoming_ordersByPositionNotByTime() =
        runTest {
            val at = now + 20 * minute
            // Identical timestamps: ordering by time would break the tie arbitrarily, which regroups
            // the board. Insertion order is deliberately scrambled relative to position.
            departures.insertAll(
                listOf(
                    departure("L1", "Haugenstua", at, position = 2),
                    departure("L1", "Lillestrøm", at, position = 0),
                    departure("L1", "Lillestrøm", at, position = 3),
                    departure("L1", "Lillestrøm", at, position = 1),
                ),
            )

            val result = departures.getUpcoming(stopId, now)

            assertEquals(
                listOf("Lillestrøm", "Lillestrøm", "Haugenstua", "Lillestrøm"),
                result.map { it.destination },
            )
        }

    @Test
    fun replaceStop_mirrorsLastResponseWithoutAccumulating() =
        runTest {
            val rows = listOf(departure("R13", "Drammen", now + 5 * minute, position = 0))
            departures.replaceStop(stopId, rows)
            departures.replaceStop(stopId, rows)
            departures.replaceStop(stopId, listOf(departure("L1", "Lillestrøm", now + 6 * minute, position = 0)))

            val result = departures.getUpcoming(stopId, now)

            assertEquals(1, result.size, message = "repeated refreshes must not pile up duplicates")
            assertEquals("L1", result.single().line)
        }

    @Test
    fun replaceStop_leavesOtherStopsAlone() =
        runTest {
            val other = "NSR:StopPlace:58404"
            departures.replaceStop(stopId, listOf(departure("R13", "Drammen", now + 5 * minute, 0)))
            departures.replaceStop(other, listOf(departure("R13", "Drammen", now + 5 * minute, 0).copy(stopId = other)))

            departures.replaceStop(stopId, emptyList())

            assertEquals(0, departures.getUpcoming(stopId, now).size)
            assertEquals(1, departures.getUpcoming(other, now).size)
        }

    @Test
    fun noticesSurviveTheJsonConverter() =
        runTest {
            departures.replaceStop(
                stopId,
                listOf(departure("R13", "Drammen", now + 5 * minute, 0, notices = listOf("Delayed", "Short train"))),
            )

            assertEquals(
                listOf("Delayed", "Short train"),
                departures.getUpcoming(stopId, now).single().notices,
            )
        }

    @Test
    fun pruneDepartedBefore_onlyRemovesOldRows() =
        runTest {
            departures.insertAll(
                listOf(
                    departure("R13", "Drammen", now - 7 * 60 * minute, position = 0),
                    departure("L1", "Lillestrøm", now + 5 * minute, position = 1),
                ),
            )

            departures.pruneDepartedBefore(now - 6 * 60 * minute)

            assertEquals(listOf("L1"), departures.getUpcoming(stopId, now).map { it.line })
        }

    @Test
    fun upsertForecast_keepsStoredHours_asA304Requires() =
        runTest {
            val key = "59.915,10.7322"
            val meta = CachedForecast(key, "Wed, 15 Jul 2026 13:18:24 GMT", now + 30 * minute, now)
            forecasts.replaceForecast(
                meta,
                listOf(CachedForecastHour(key, "t1", now + 60 * minute, 21.0, "clearsky_day", 0.2)),
            )

            // A 304 updates only the metadata; the hours must stay.
            forecasts.upsertForecast(meta.copy(expiresAtMillis = now + 90 * minute))

            assertEquals(1, forecasts.getUpcomingHours(key, now).size)
            assertEquals(now + 90 * minute, forecasts.getForecast(key)?.expiresAtMillis)
        }

    @Test
    fun replaceForecast_swapsHoursWholesale() =
        runTest {
            val key = "59.915,10.7322"
            val meta = CachedForecast(key, null, now + 30 * minute, now)
            forecasts.replaceForecast(
                meta,
                listOf(CachedForecastHour(key, "t1", now + 60 * minute, 21.0, "clearsky_day", 0.0)),
            )
            forecasts.replaceForecast(
                meta,
                listOf(CachedForecastHour(key, "t2", now + 120 * minute, 15.0, "rain", 1.0)),
            )

            val hours = forecasts.getUpcomingHours(key, now)
            assertEquals(1, hours.size)
            assertEquals(15.0, hours.single().temperature, absoluteTolerance = 0.001)
        }

    @Test
    fun getUpcomingHours_dropsPastHours() =
        runTest {
            val key = "59.915,10.7322"
            forecasts.replaceForecast(
                CachedForecast(key, null, now + 30 * minute, now),
                listOf(
                    CachedForecastHour(key, "past", now - 120 * minute, 15.0, "cloudy", 0.0),
                    CachedForecastHour(key, "future", now + 60 * minute, 21.0, "clearsky_day", 0.0),
                ),
            )

            assertEquals(listOf("future"), forecasts.getUpcomingHours(key, now).map { it.time })
        }

    @Test
    fun forecastHoursAreRemovedWithTheirParent() =
        runTest {
            val key = "59.915,10.7322"
            forecasts.replaceForecast(
                CachedForecast(key, null, now + 30 * minute, now),
                listOf(CachedForecastHour(key, "t1", now + 60 * minute, 21.0, "clearsky_day", 0.0)),
            )
            assertNotNull(forecasts.getForecast(key))

            forecasts.clearHours(key)

            assertEquals(0, forecasts.getUpcomingHours(key, now).size)
            assertNull(forecasts.getForecast("someone-else"))
        }
}
