package com.example.pulsetransit.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/**
 * A transport company whose timetables reach Entur, and so PulseTransit. Settings lists them so
 * people can tell whether a company they travel with is covered: one that does not report to Entur
 * (some foreign coach lines, charters, small tourist boats) is not shown anywhere in the app.
 */
data class DataSource(
    val name: String,
    /** The company's website, as Entur has it; null when Entur has none. */
    val url: String?,
    /** What it runs, in [DataSourceMode] order; never empty. */
    val modes: List<DataSourceMode>,
)

/** The kinds of transport a [DataSource] runs, coarser than Entur's own modes. */
enum class DataSourceMode {
    BUS,
    COACH,
    TRAIN,
    METRO,
    TRAM,
    BOAT,
    PLANE,
    CABLE_CAR,
    ON_DEMAND,
    ;

    companion object {
        /** Entur's `TransportMode`, or null for one that is not shown (e.g. `unknown`). */
        fun fromEntur(mode: String): DataSourceMode? =
            when (mode) {
                "bus", "trolleybus" -> BUS
                "coach" -> COACH
                "rail", "monorail" -> TRAIN
                "metro" -> METRO
                "tram" -> TRAM
                "water" -> BOAT
                "air" -> PLANE
                "cableway", "funicular", "lift" -> CABLE_CAR
                // Entur files bestillingstransport (on-demand minibuses and taxis) as taxi.
                "taxi" -> ON_DEMAND
                else -> null
            }
    }
}

/** One authority as Entur lists it, before [mergeDataSources]. */
internal data class EnturAuthority(
    val name: String,
    val url: String?,
    /** Entur's transport mode of each of its lines, one entry per line. */
    val lineModes: List<String>,
)

/**
 * Turns Entur's authorities into the list Settings shows: one entry per name, since some companies
 * are registered several times (Skyss, Vy), with the modes of all their lines; companies with no
 * lines at present (registered for a season that is over, or never used) are left out, as nothing
 * of theirs can show up in the app. Sorted by name, ignoring case.
 */
internal fun mergeDataSources(authorities: List<EnturAuthority>): List<DataSource> =
    authorities
        .groupBy { it.name.trim().lowercase() }
        .values
        .mapNotNull { same ->
            val modes =
                same
                    .flatMap { it.lineModes }
                    .mapNotNull(DataSourceMode::fromEntur)
                    .toSet()
            if (modes.isEmpty()) return@mapNotNull null
            DataSource(
                name = same.first().name.trim(),
                url = same.firstNotNullOfOrNull { it.url?.takeIf(String::isNotBlank) },
                modes = DataSourceMode.entries.filter { it in modes },
            )
        }.sortedBy { it.name.lowercase() }

/** The sources whose name contains [query], ignoring case; all of them for a blank query. */
fun List<DataSource>.matching(query: String): List<DataSource> {
    val needle = query.trim()
    return if (needle.isEmpty()) this else filter { it.name.contains(needle, ignoreCase = true) }
}

interface DataSourceRepository {
    /** Every company with timetables in Entur, or null when Entur could not be reached. */
    suspend fun getDataSources(): List<DataSource>?
}

/**
 * Asks Entur's Journey Planner for its authorities and their lines. A good answer is kept for the
 * life of the process: the list changes a few times a year, and Settings may be opened often.
 */
class DataSourceRepositoryImpl(
    private val client: HttpClient,
) : DataSourceRepository {
    private val lock = Mutex()
    private var cached: List<DataSource>? = null

    override suspend fun getDataSources(): List<DataSource>? =
        lock.withLock {
            cached ?: fetch()?.also { cached = it }
        }

    private suspend fun fetch(): List<DataSource>? =
        try {
            val response: AuthoritiesResponse =
                client
                    .post("https://api.entur.io/journey-planner/v3/graphql") {
                        header("ET-Client-Name", "personal-pulsetransit")
                        contentType(ContentType.Application.Json)
                        setBody(AuthoritiesRequest(QUERY))
                    }.body()
            response.data
                ?.authorities
                ?.map { authority ->
                    EnturAuthority(
                        name = authority.name,
                        url = authority.url,
                        lineModes = authority.lines.mapNotNull { it.transportMode },
                    )
                }?.let(::mergeDataSources)
                ?.takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    private companion object {
        const val QUERY = "{ authorities { name url lines { transportMode } } }"
    }

    @Serializable
    private data class AuthoritiesRequest(
        val query: String,
    )

    @Serializable
    private data class AuthoritiesResponse(
        val data: AuthoritiesData? = null,
    )

    @Serializable
    private data class AuthoritiesData(
        val authorities: List<AuthorityDto> = emptyList(),
    )

    @Serializable
    private data class AuthorityDto(
        val name: String,
        val url: String? = null,
        val lines: List<LineDto> = emptyList(),
    )

    @Serializable
    private data class LineDto(
        val transportMode: String? = null,
    )
}
