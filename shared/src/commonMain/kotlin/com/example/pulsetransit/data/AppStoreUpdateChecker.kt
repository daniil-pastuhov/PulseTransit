package com.example.pulsetransit.data

import com.example.pulsetransit.platform.AppUpdate
import com.example.pulsetransit.platform.AppUpdateChecker
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The iOS [AppUpdateChecker]: asks the App Store's lookup API for the version on sale and compares
 * it with the running one. It lives in common code only so it can be tested on the JVM.
 *
 * An update that needs a newer iOS than the device runs is not offered, since the store would not
 * install it. A TestFlight or development build ahead of the store sees no update.
 */
class AppStoreUpdateChecker(
    private val client: HttpClient,
    private val bundleId: String,
    private val installedVersion: String,
    private val osVersion: String,
) : AppUpdateChecker {
    private companion object {
        const val LOOKUP_URL = "https://itunes.apple.com/lookup"

        // The lookup only finds apps sold in the given storefront, and US is the default. The app
        // is about Norwegian public transport, so the Norwegian store is the one it is always in.
        const val STOREFRONT = "no"
    }

    // Apple serves the JSON as text/javascript, which content negotiation does not decode.
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun availableUpdate(): AppUpdate? =
        try {
            val response =
                client.get(LOOKUP_URL) {
                    parameter("bundleId", bundleId)
                    parameter("country", STOREFRONT)
                }
            if (response.status.isSuccess()) {
                json
                    .decodeFromString<LookupResponse>(response.bodyAsText())
                    .results
                    .firstOrNull()
                    ?.takeIf { isNewerVersion(it.version, installedVersion) }
                    ?.takeIf { it.minimumOsVersion == null || !isNewerVersion(it.minimumOsVersion, osVersion) }
                    ?.let { AppUpdate(version = it.version, storeUrl = it.trackViewUrl) }
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

    @Serializable
    private data class LookupResponse(
        val results: List<StoreApp> = emptyList(),
    )

    @Serializable
    private data class StoreApp(
        val version: String,
        val minimumOsVersion: String? = null,
        val trackViewUrl: String? = null,
    )
}

/**
 * Whether the dotted version [candidate] is above [current], compared numerically part by part, so
 * "2.10.0" is above "2.9.1" and "2.1" equals "2.1.0". False if either is not a dotted number.
 */
fun isNewerVersion(
    candidate: String,
    current: String,
): Boolean {
    val candidateParts = candidate.trim().split('.').map { it.toIntOrNull() ?: return false }
    val currentParts = current.trim().split('.').map { it.toIntOrNull() ?: return false }
    for (i in 0 until maxOf(candidateParts.size, currentParts.size)) {
        val a = candidateParts.getOrElse(i) { 0 }
        val b = currentParts.getOrElse(i) { 0 }
        if (a != b) return a > b
    }
    return false
}
