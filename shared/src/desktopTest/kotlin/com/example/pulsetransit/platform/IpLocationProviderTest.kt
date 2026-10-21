package com.example.pulsetransit.platform

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

class IpLocationProviderTest {
    private var requests = 0

    /** Answers each look-up with the next of [locs], repeating the last. */
    private fun provider(
        vararg locs: String,
        override: String? = null,
    ) = IpLocationProvider(
        client =
            HttpClient(
                MockEngine {
                    val loc = locs[minOf(requests++, locs.lastIndex)]
                    respond("""{"city":"Oslo","loc":"$loc"}""")
                },
            ),
        override = override,
        pollInterval = 1.milliseconds,
    )

    @Test
    fun `an approximate look-up reads ipinfo's coordinates`() =
        runTest {
            assertEquals(LocationResult.Fix(59.9127, 10.7461), provider("59.9127,10.7461").currentLocation(LocationAccuracy.APPROXIMATE))
        }

    @Test
    fun `a precise look-up is unavailable and asks nothing`() =
        runTest {
            assertEquals(LocationResult.Unavailable, provider("59.9,10.7").currentLocation(LocationAccuracy.PRECISE))
            assertEquals(0, requests)
        }

    @Test
    fun `the override pins a place and asks nothing`() =
        runTest {
            assertEquals(
                LocationResult.Fix(60.39, 5.32),
                provider("59.9,10.7", override = "60.39, 5.32").currentLocation(LocationAccuracy.APPROXIMATE),
            )
            assertEquals(0, requests)
        }

    @Test
    fun `a failed look-up is unavailable`() =
        runTest {
            val failing = IpLocationProvider(HttpClient(MockEngine { respondError(HttpStatusCode.TooManyRequests) }), override = null)
            assertEquals(LocationResult.Unavailable, failing.currentLocation(LocationAccuracy.APPROXIMATE))
        }

    @Test
    fun `updates come only after moving far enough`() =
        runTest {
            // Oslo S, then 100 m along Karl Johan (ignored at 500 m), then Bergen.
            val updates =
                provider(
                    "59.9111,10.7528",
                    "59.9116,10.7543",
                    "60.3913,5.3221",
                ).locationUpdates(LocationAccuracy.APPROXIMATE, 500f)
            assertEquals(
                listOf(LocationResult.Fix(59.9111, 10.7528), LocationResult.Fix(60.3913, 5.3221)),
                updates.take(2).toList(),
            )
        }
}
