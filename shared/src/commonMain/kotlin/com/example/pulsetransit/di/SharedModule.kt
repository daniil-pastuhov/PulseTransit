package com.example.pulsetransit.di

import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.EnturRepositoryImpl
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.SettingsRepositoryImpl
import com.example.pulsetransit.data.WeatherRepository
import com.example.pulsetransit.data.WeatherRepositoryImpl
import com.example.pulsetransit.data.cache.CacheDatabase
import com.example.pulsetransit.data.createHttpClient
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import io.ktor.client.engine.HttpClientEngineFactory
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Everything that is the same on every platform. It relies on a platform module to provide:
 * - `HttpClientEngineFactory<*>` (OkHttp on Android, Darwin on iOS)
 * - `Settings`, the key-value store behind [SettingsRepository]
 * - [CacheDatabase]
 * - `ClockFormatter`, `LocationProvider`, `SystemSensorManager` and `CameraMotionDetector`
 */
val sharedModule =
    module {
        single { createHttpClient(get<HttpClientEngineFactory<*>>()) }
        factory { get<CacheDatabase>().departureCacheDao() }
        factory { get<CacheDatabase>().tripCacheDao() }
        factory { get<CacheDatabase>().forecastCacheDao() }

        single<SettingsRepository> { SettingsRepositoryImpl(prefs = get()) }
        single<EnturRepository> {
            EnturRepositoryImpl(client = get(), departureCache = get(), tripCache = get())
        }
        single<WeatherRepository> { WeatherRepositoryImpl(client = get(), cache = get()) }

        viewModel {
            StopBoardViewModel(
                enturRepository = get(),
                weatherRepository = get(),
                settingsRepository = get(),
                sensorManager = get(),
                cameraMotionDetector = get(),
                clockFormatter = get(),
                connectivityMonitor = get(),
            )
        }
        viewModel {
            SettingsViewModel(
                enturRepository = get(),
                settingsRepository = get(),
                sensorManager = get(),
                locationProvider = get(),
            )
        }
    }
