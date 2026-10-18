package com.example.pulsetransit.data

import com.example.pulsetransit.platform.AppUpdate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The App Store lookup behind the iOS "new version" notice, and the version comparison it uses. */
class AppStoreUpdateCheckerTest {
    private val storeUrl = "https://apps.apple.com/no/app/pulsetransit/id123?uo=4"

    private fun lookupBody(
        version: String,
        minimumOsVersion: String = "15.0",
    ) = """{"resultCount":1,"results":[{"version":"$version","minimumOsVersion":"$minimumOsVersion","trackViewUrl":"$storeUrl"}]}"""

    // Apple sends text/javascript, not JSON; the checker must decode it anyway.
    private val appleHeaders = headersOf(HttpHeaders.ContentType, "text/javascript; charset=utf-8")

    private fun checker(
        engine: MockEngine,
        installed: String = "2.0.0",
        os: String = "17.4",
    ) = AppStoreUpdateChecker(HttpClient(engine), "com.homieapps.pulsetransit", installed, os)

    @Test
    fun `a newer store version is offered with its store page`() =
        runTest {
            var requested: Url? = null
            val engine =
                MockEngine { request ->
                    requested = request.url
                    respond(lookupBody("2.1.0"), HttpStatusCode.OK, appleHeaders)
                }

            assertEquals(AppUpdate(version = "2.1.0", storeUrl = storeUrl), checker(engine).availableUpdate())
            assertEquals("com.homieapps.pulsetransit", requested?.parameters?.get("bundleId"))
            assertEquals("no", requested?.parameters?.get("country"))
        }

    @Test
    fun `the installed version or an older one is not an update`() =
        runTest {
            val engine = MockEngine { respond(lookupBody("2.0.0"), HttpStatusCode.OK, appleHeaders) }

            assertNull(checker(engine, installed = "2.0.0").availableUpdate())
            assertNull(checker(engine, installed = "2.1.0").availableUpdate())
        }

    @Test
    fun `an update needing a newer iOS than the device runs is not offered`() =
        runTest {
            val engine = MockEngine { respond(lookupBody("3.0.0", minimumOsVersion = "18.0"), HttpStatusCode.OK, appleHeaders) }

            assertNull(checker(engine, os = "17.4").availableUpdate())
            assertEquals("3.0.0", checker(engine, os = "18.0").availableUpdate()?.version)
        }

    @Test
    fun `an app missing from the store is no update`() =
        runTest {
            val engine = MockEngine { respond("""{"resultCount":0,"results":[]}""", HttpStatusCode.OK, appleHeaders) }

            assertNull(checker(engine).availableUpdate())
        }

    @Test
    fun `a failed or garbled lookup is no update rather than an error`() =
        runTest {
            assertNull(checker(MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }).availableUpdate())
            assertNull(checker(MockEngine { throw IOException("offline") }).availableUpdate())
            assertNull(checker(MockEngine { respond("<html>", HttpStatusCode.OK, appleHeaders) }).availableUpdate())
        }

    @Test
    fun `an error status is no update even when its body parses`() =
        runTest {
            val engine = MockEngine { respond(lookupBody("2.1.0"), HttpStatusCode.ServiceUnavailable, appleHeaders) }

            assertNull(checker(engine).availableUpdate())
        }

    @Test
    fun `versions compare numerically part by part`() {
        assertTrue(isNewerVersion("2.10.0", "2.9.1"))
        assertTrue(isNewerVersion("2.0.1", "2.0"))
        assertTrue(isNewerVersion("3", "2.9.9"))
        assertFalse(isNewerVersion("2.1", "2.1.0"))
        assertFalse(isNewerVersion("2.0.0", "2.0.1"))
    }

    @Test
    fun `a version that is not dotted numbers is never newer`() {
        assertFalse(isNewerVersion("2.1.0-beta", "2.0.0"))
        assertFalse(isNewerVersion("2.1.0", ""))
    }
}
