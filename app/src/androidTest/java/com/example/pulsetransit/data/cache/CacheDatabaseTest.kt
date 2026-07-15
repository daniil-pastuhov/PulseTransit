package com.example.pulsetransit.data.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Room queries. The JVM tests use fake DAOs that reproduce these semantics by
 * hand, so this is what actually pins the SQL down.
 */
@RunWith(AndroidJUnit4::class)
class CacheDatabaseTest {

    private lateinit var db: CacheDatabase
    private lateinit var departures: DepartureCacheDao
    private lateinit var forecasts: ForecastCacheDao

    private val stopId = "NSR:StopPlace:59872"
    private val now = System.currentTimeMillis()
    private val minute = 60 * 1000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            CacheDatabase::class.java
        ).build()
        departures = db.departureCacheDao()
        forecasts = db.forecastCacheDao()
    }

    @After
    fun tearDown() = db.close()

    private fun departure(
        line: String,
        destination: String,
        atMillis: Long,
        position: Int,
        notices: List<String> = emptyList()
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
        fetchedAt = now
    )

    @Test
    fun getUpcoming_dropsDeparturesAlreadyDue() = runBlocking {
        departures.replaceStop(
            stopId,
            listOf(
                departure("R13", "Drammen", now - 2 * minute, position = 0),
                departure("L1", "Lillestrøm", now + 4 * minute, position = 1)
            )
        )

        val result = departures.getUpcoming(stopId, now)

        assertEquals(listOf("L1"), result.map { it.line })
    }

    @Test
    fun getUpcoming_ordersByPositionNotByTime() = runBlocking {
        val at = now + 20 * minute
        // Identical timestamps: ordering by time would break the tie arbitrarily, which regroups
        // the board. Insertion order is deliberately scrambled relative to position.
        departures.insertAll(
            listOf(
                departure("L1", "Haugenstua", at, position = 2),
                departure("L1", "Lillestrøm", at, position = 0),
                departure("L1", "Lillestrøm", at, position = 3),
                departure("L1", "Lillestrøm", at, position = 1)
            )
        )

        val result = departures.getUpcoming(stopId, now)

        assertEquals(
            listOf("Lillestrøm", "Lillestrøm", "Haugenstua", "Lillestrøm"),
            result.map { it.destination }
        )
    }

    @Test
    fun replaceStop_mirrorsLastResponseWithoutAccumulating() = runBlocking {
        val rows = listOf(departure("R13", "Drammen", now + 5 * minute, position = 0))
        departures.replaceStop(stopId, rows)
        departures.replaceStop(stopId, rows)
        departures.replaceStop(stopId, listOf(departure("L1", "Lillestrøm", now + 6 * minute, position = 0)))

        val result = departures.getUpcoming(stopId, now)

        assertEquals("repeated refreshes must not pile up duplicates", 1, result.size)
        assertEquals("L1", result.single().line)
    }

    @Test
    fun replaceStop_leavesOtherStopsAlone() = runBlocking {
        val other = "NSR:StopPlace:58404"
        departures.replaceStop(stopId, listOf(departure("R13", "Drammen", now + 5 * minute, 0)))
        departures.replaceStop(other, listOf(departure("R13", "Drammen", now + 5 * minute, 0).copy(stopId = other)))

        departures.replaceStop(stopId, emptyList())

        assertEquals(0, departures.getUpcoming(stopId, now).size)
        assertEquals(1, departures.getUpcoming(other, now).size)
    }

    @Test
    fun noticesSurviveTheJsonConverter() = runBlocking {
        departures.replaceStop(
            stopId,
            listOf(departure("R13", "Drammen", now + 5 * minute, 0, notices = listOf("Delayed", "Short train")))
        )

        assertEquals(
            listOf("Delayed", "Short train"),
            departures.getUpcoming(stopId, now).single().notices
        )
    }

    @Test
    fun pruneDepartedBefore_onlyRemovesOldRows() = runBlocking {
        departures.insertAll(
            listOf(
                departure("R13", "Drammen", now - 7 * 60 * minute, position = 0),
                departure("L1", "Lillestrøm", now + 5 * minute, position = 1)
            )
        )

        departures.pruneDepartedBefore(now - 6 * 60 * minute)

        assertEquals(listOf("L1"), departures.getUpcoming(stopId, now).map { it.line })
    }

    @Test
    fun upsertForecast_keepsStoredHours_asA304Requires() = runBlocking {
        val key = "59.915,10.7322"
        val meta = CachedForecast(key, "Wed, 15 Jul 2026 13:18:24 GMT", now + 30 * minute, now)
        forecasts.replaceForecast(
            meta,
            listOf(CachedForecastHour(key, "t1", now + 60 * minute, 21.0, "clearsky_day", 0.2))
        )

        // A 304 updates only the metadata; the hours must stay.
        forecasts.upsertForecast(meta.copy(expiresAtMillis = now + 90 * minute))

        assertEquals(1, forecasts.getUpcomingHours(key, now).size)
        assertEquals(now + 90 * minute, forecasts.getForecast(key)?.expiresAtMillis)
    }

    @Test
    fun replaceForecast_swapsHoursWholesale() = runBlocking {
        val key = "59.915,10.7322"
        val meta = CachedForecast(key, null, now + 30 * minute, now)
        forecasts.replaceForecast(
            meta,
            listOf(CachedForecastHour(key, "t1", now + 60 * minute, 21.0, "clearsky_day", 0.0))
        )
        forecasts.replaceForecast(
            meta,
            listOf(CachedForecastHour(key, "t2", now + 120 * minute, 15.0, "rain", 1.0))
        )

        val hours = forecasts.getUpcomingHours(key, now)
        assertEquals(1, hours.size)
        assertEquals(15.0, hours.single().temperature, 0.001)
    }

    @Test
    fun getUpcomingHours_dropsPastHours() = runBlocking {
        val key = "59.915,10.7322"
        forecasts.replaceForecast(
            CachedForecast(key, null, now + 30 * minute, now),
            listOf(
                CachedForecastHour(key, "past", now - 120 * minute, 15.0, "cloudy", 0.0),
                CachedForecastHour(key, "future", now + 60 * minute, 21.0, "clearsky_day", 0.0)
            )
        )

        assertEquals(listOf("future"), forecasts.getUpcomingHours(key, now).map { it.time })
    }

    @Test
    fun forecastHoursAreRemovedWithTheirParent() = runBlocking {
        val key = "59.915,10.7322"
        forecasts.replaceForecast(
            CachedForecast(key, null, now + 30 * minute, now),
            listOf(CachedForecastHour(key, "t1", now + 60 * minute, 21.0, "clearsky_day", 0.0))
        )
        assertNotNull(forecasts.getForecast(key))

        forecasts.clearHours(key)

        assertEquals(0, forecasts.getUpcomingHours(key, now).size)
        assertNull(forecasts.getForecast("someone-else"))
    }
}
