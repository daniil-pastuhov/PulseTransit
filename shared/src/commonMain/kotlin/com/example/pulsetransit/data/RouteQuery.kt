package com.example.pulsetransit.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * The [RouteOptions] variables of a `trip` query and the arguments they fill. A variable left out
 * of the request counts as not given, so Entur applies its own default for it.
 */
internal const val TRIP_OPTION_VARIABLES =
    "\$count: Int!, \$transferPenalty: Int, \$walkReluctance: Float, \$filters: [TripFilterInput!]"
internal const val TRIP_OPTION_ARGUMENTS =
    "numTripPatterns: \$count transferPenalty: \$transferPenalty walkReluctance: \$walkReluctance filters: \$filters"

/**
 * Fills the [TRIP_OPTION_VARIABLES] for [options], asking for [wanted] routes, or more when the
 * preset re-ranks them. [bannedLineIds] are the ids of the options' banned lines, by [LineIdLookup].
 */
internal fun JsonObjectBuilder.putRouteOptions(
    options: RouteOptions,
    wanted: Int,
    bannedLineIds: List<String>,
) {
    val reranked = options.preference != RoutePreference.FASTEST
    put("count", if (reranked) maxOf(wanted, RouteOptions.RANKING_CANDIDATES) else wanted)
    when (options.preference) {
        RoutePreference.FASTEST -> Unit
        RoutePreference.FEWER_TRANSFERS -> put("transferPenalty", RouteOptions.FEWER_TRANSFERS_PENALTY)
        RoutePreference.LESS_WALKING -> put("walkReluctance", RouteOptions.LESS_WALKING_RELUCTANCE)
    }
    // One trip filter excluding the kinds and lines: anything matching a `not` selector is left out.
    // Unlike `modes`, it matches sub-modes per trip, and needs no access modes to keep walking.
    val excludedModes = options.excludedEnturModes
    if (excludedModes.isEmpty() && bannedLineIds.isEmpty()) return
    putJsonArray("filters") {
        addJsonObject {
            putJsonArray("not") {
                if (excludedModes.isNotEmpty()) {
                    addJsonObject {
                        putJsonArray("transportModes") {
                            excludedModes.forEach { selector ->
                                addJsonObject {
                                    put("transportMode", selector.mode)
                                    selector.subModes?.let { subModes ->
                                        putJsonArray("transportSubModes") { subModes.forEach { add(it) } }
                                    }
                                }
                            }
                        }
                    }
                }
                if (bannedLineIds.isNotEmpty()) {
                    addJsonObject { putJsonArray("lines") { bannedLineIds.forEach { add(it) } } }
                }
            }
        }
    }
}

/**
 * Line ids by public code, which is how the board knows a filtered line but not how Entur bans
 * one. Lines are timetable data, so an answer keeps for the session.
 */
internal class LineIdLookup(
    private val client: HttpClient,
    private val clientNameHeader: String,
    private val clientNameValue: String,
) {
    private val idsByCode = mutableMapOf<String, List<String>>()
    private val lock = Mutex()

    /**
     * Every line id with one of [publicCodes], nationwide: the board filters a line by its code
     * wherever it calls, so a route avoids it the same way. Throws when Entur cannot be reached,
     * like the trip request it is for.
     */
    suspend fun lineIds(publicCodes: Set<String>): List<String> {
        if (publicCodes.isEmpty()) return emptyList()
        return lock.withLock {
            val missing = publicCodes - idsByCode.keys
            if (missing.isNotEmpty()) {
                val lines = fetch(missing)
                missing.forEach { code -> idsByCode[code] = lines.filter { it.publicCode == code }.map { it.id } }
            }
            publicCodes.flatMap { idsByCode[it].orEmpty() }
        }
    }

    private suspend fun fetch(publicCodes: Set<String>): List<LineRef> {
        val queryStr =
            """
            query GetLines(${'$'}codes: [String]) {
              lines(publicCodes: ${'$'}codes) {
                id
                publicCode
              }
            }
            """.trimIndent()
        val variables = buildJsonObject { putJsonArray("codes") { publicCodes.forEach { add(it) } } }
        val response: LinesResponse =
            client
                .post("https://api.entur.io/journey-planner/v3/graphql") {
                    header(clientNameHeader, clientNameValue)
                    contentType(ContentType.Application.Json)
                    setBody(LinesRequest(queryStr, variables))
                }.body()
        return response.data?.lines ?: error("Line lookup failed")
    }

    @Serializable
    private data class LinesRequest(
        val query: String,
        val variables: JsonObject,
    )

    @Serializable
    private data class LinesResponse(
        val data: LinesData? = null,
    )

    @Serializable
    private data class LinesData(
        val lines: List<LineRef> = emptyList(),
    )

    @Serializable
    private data class LineRef(
        val id: String,
        val publicCode: String? = null,
    )
}
