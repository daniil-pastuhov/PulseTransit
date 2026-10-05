package com.example.pulsetransit.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * The client shared by the Entur and MET repositories. Only the engine is platform-specific
 * (OkHttp on Android, Darwin on iOS), so it is the one thing the caller passes in.
 */
fun createHttpClient(engine: HttpClientEngineFactory<*>): HttpClient =
    HttpClient(engine) {
        install(ContentNegotiation) {
            json(
                Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                },
            )
        }
    }
