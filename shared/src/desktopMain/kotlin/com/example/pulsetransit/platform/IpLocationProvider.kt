package com.example.pulsetransit.platform

import com.example.pulsetransit.data.distanceMeters
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * [LocationProvider] for the desktop, which has no location API on the JVM: the city the machine's
 * IP address belongs to, from ipinfo.io. That is approximate only, so [LocationAccuracy.PRECISE]
 * reports [LocationResult.Unavailable] and callers fall back to approximate, as they do on a phone
 * without a satellite fix.
 *
 * [override] ("lat,lon", from `-Dpulsetransit.location`) pins a place instead and sends nothing,
 * for trying the board somewhere else.
 */
class IpLocationProvider(
    private val client: HttpClient,
    private val override: String? = System.getProperty(OVERRIDE_PROPERTY),
    private val pollInterval: Duration = 5.minutes,
) : LocationProvider {
    override suspend fun currentLocation(accuracy: LocationAccuracy): LocationResult =
        if (accuracy == LocationAccuracy.PRECISE) LocationResult.Unavailable else lookUp()

    /** Emits the location, then again whenever a later look-up has moved [minDistanceMeters]. */
    override fun locationUpdates(
        accuracy: LocationAccuracy,
        minDistanceMeters: Float,
    ): Flow<LocationResult> =
        flow {
            if (accuracy == LocationAccuracy.PRECISE) {
                emit(LocationResult.Unavailable)
                return@flow
            }
            var last: LocationResult.Fix? = null
            while (true) {
                when (val result = lookUp()) {
                    is LocationResult.Fix ->
                        if (last == null ||
                            distanceMeters(last.latitude, last.longitude, result.latitude, result.longitude) >= minDistanceMeters
                        ) {
                            last = result
                            emit(result)
                        }
                    else -> if (last == null) emit(result)
                }
                delay(pollInterval)
            }
        }

    @Suppress("TooGenericExceptionCaught") // Any network or parse failure means no location this time.
    private suspend fun lookUp(): LocationResult {
        parse(override)?.let { return it }
        return try {
            parse(json.decodeFromString<IpInfo>(client.get(IPINFO_URL).bodyAsText()).loc) ?: LocationResult.Unavailable
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            LocationResult.Unavailable
        }
    }

    private fun parse(latLon: String?): LocationResult.Fix? {
        val parts = latLon?.split(',')?.mapNotNull { it.trim().toDoubleOrNull() } ?: return null
        return if (parts.size == 2) LocationResult.Fix(parts[0], parts[1]) else null
    }

    @Serializable
    private data class IpInfo(
        /** "59.9127,10.7461". */
        val loc: String? = null,
    )

    companion object {
        const val OVERRIDE_PROPERTY = "pulsetransit.location"
        private const val IPINFO_URL = "https://ipinfo.io/json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
