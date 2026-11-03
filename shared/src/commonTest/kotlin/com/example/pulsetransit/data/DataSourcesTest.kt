package com.example.pulsetransit.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings' list of the companies whose timetables reach Entur. */
class DataSourcesTest {
    private fun authority(
        name: String,
        vararg modes: String,
        url: String? = "https://example.no",
    ) = EnturAuthority(name, url, modes.toList())

    @Test
    fun `companies registered several times are merged with the modes of all their lines`() {
        val merged =
            mergeDataSources(
                listOf(
                    authority("Skyss", "bus", "water", url = "https://skyss.no/"),
                    authority("Skyss", "tram", "bus", url = null),
                    authority("skyss ", "bus"),
                ),
            )

        val modes = listOf(DataSourceMode.BUS, DataSourceMode.TRAM, DataSourceMode.BOAT)
        assertEquals(listOf(DataSource("Skyss", "https://skyss.no/", modes)), merged)
    }

    @Test
    fun `companies with no lines at present are left out`() {
        val merged = mergeDataSources(listOf(authority("Arctic Sea Hotel"), authority("Ruter", "metro"), authority("Odd", "unknown")))

        assertEquals(listOf("Ruter"), merged.map { it.name })
    }

    @Test
    fun `the list is sorted by name ignoring case`() {
        val merged = mergeDataSources(listOf(authority("Vy", "rail"), authority("ATB", "bus"), authority("Bygdøyfergen", "water")))

        assertEquals(listOf("ATB", "Bygdøyfergen", "Vy"), merged.map { it.name })
    }

    @Test
    fun `entur modes map to the coarser kinds shown`() {
        assertEquals(DataSourceMode.BOAT, DataSourceMode.fromEntur("water"))
        assertEquals(DataSourceMode.CABLE_CAR, DataSourceMode.fromEntur("funicular"))
        assertEquals(DataSourceMode.ON_DEMAND, DataSourceMode.fromEntur("taxi"))
        assertEquals(DataSourceMode.TRAIN, DataSourceMode.fromEntur("rail"))
        assertNull(DataSourceMode.fromEntur("unknown"))
    }

    @Test
    fun `a blank search shows everything and a search matches part of a name ignoring case`() {
        val sources = mergeDataSources(listOf(authority("NOR-WAY Bussekspress", "coach"), authority("Ruter", "bus")))

        assertEquals(sources, sources.matching("  "))
        assertEquals(listOf("NOR-WAY Bussekspress"), sources.matching("nor-way").map { it.name })
        assertEquals(emptyList(), sources.matching("Sindbad"))
    }

    private fun client(engine: MockEngine) =
        HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val body =
        """
        {"data":{"authorities":[
          {"name":"Ruter","url":"https://ruter.no/","lines":[{"transportMode":"bus"},{"transportMode":"metro"}]},
          {"name":"Go Fjords","url":"http://www.gofjords.com","lines":[]}
        ]}}
        """.trimIndent()

    @Test
    fun `the repository asks entur once and keeps a good answer`() =
        runTest {
            var requests = 0
            var sentClientName: String? = null
            var sentBody = ""
            val engine =
                MockEngine { request ->
                    requests++
                    sentClientName = request.headers["ET-Client-Name"]
                    sentBody = (request.body as TextContent).text
                    respond(body, HttpStatusCode.OK, jsonHeaders)
                }
            val repository = DataSourceRepositoryImpl(client(engine))

            val first = repository.getDataSources()
            val second = repository.getDataSources()

            assertEquals(listOf(DataSource("Ruter", "https://ruter.no/", listOf(DataSourceMode.BUS, DataSourceMode.METRO))), first)
            assertEquals(first, second)
            assertEquals(1, requests)
            assertEquals("personal-pulsetransit", sentClientName)
            assertTrue(sentBody.contains("authorities"))
        }

    @Test
    fun `a failure gives null and the next call asks again`() =
        runTest {
            var requests = 0
            val engine =
                MockEngine {
                    requests++
                    if (requests == 1) throw IOException("offline")
                    respond(body, HttpStatusCode.OK, jsonHeaders)
                }
            val repository = DataSourceRepositoryImpl(client(engine))

            assertNull(repository.getDataSources())
            assertEquals(listOf("Ruter"), repository.getDataSources()?.map { it.name })
            assertEquals(2, requests)
        }
}
