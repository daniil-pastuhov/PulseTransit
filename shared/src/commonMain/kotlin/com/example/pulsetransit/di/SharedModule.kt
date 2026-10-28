package com.example.pulsetransit.di

import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.EnturRepositoryImpl
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.SettingsRepositoryImpl
import com.example.pulsetransit.data.WeatherRepository
import com.example.pulsetransit.data.WeatherRepositoryImpl
import com.example.pulsetransit.data.cache.CacheDatabase
import com.example.pulsetransit.data.createHttpClient
import com.example.pulsetransit.data.journey.JourneyOpenRequests
import com.example.pulsetransit.data.journey.JourneyStore
import com.example.pulsetransit.data.journey.JourneyStoreImpl
import com.example.pulsetransit.data.journey.JourneyTracker
import com.example.pulsetransit.data.widget.BoardWidgetSource
import com.example.pulsetransit.ui.viewmodel.AppUpdateViewModel
import com.example.pulsetransit.ui.viewmodel.JourneyAlertsViewModel
import com.example.pulsetransit.ui.viewmodel.ModeSelectionViewModel
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import io.ktor.client.engine.HttpClientEngineFactory
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** Names the `LocationProvider` journey alerts use, where it differs from the board's (iOS). */
const val JOURNEY_LOCATION = "journeyLocation"

/**
 * Everything that is the same on every platform. It relies on a platform module to provide:
 * - `HttpClientEngineFactory<*>` (OkHttp on Android, Darwin on iOS)
 * - `Settings`, the key-value store behind [SettingsRepository]
 * - [CacheDatabase]
 * - `ClockFormatter`, `LocationProvider`, `SystemSensorManager`, `CameraMotionDetector` and
 *   `AppUpdateChecker`
 * - `JourneyNotifier`, `JourneyPermissions` and `JourneyLauncher`, for journey alerts, and
 *   optionally a `LocationProvider` named [JOURNEY_LOCATION] where the journey's location must not
 *   share one with the board's (iOS, whose location source serves one caller at a time)
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
        single { BoardWidgetSource(enturRepository = get(), weatherRepository = get(), settingsRepository = get()) }
        single<JourneyStore> { JourneyStoreImpl(prefs = get()) }
        single { JourneyOpenRequests() }
        single {
            JourneyTracker(
                store = get(),
                enturRepository = get(),
                notifier = get(),
                locationProvider = getOrNull(named(JOURNEY_LOCATION)) ?: get(),
            )
        }

        viewModel {
            StopBoardViewModel(
                enturRepository = get(),
                weatherRepository = get(),
                settingsRepository = get(),
                sensorManager = get(),
                cameraMotionDetector = get(),
                clockFormatter = get(),
                connectivityMonitor = get(),
                locationProvider = get(),
            )
        }
        viewModel { ModeSelectionViewModel(settingsRepository = get()) }
        viewModel { JourneyAlertsViewModel(store = get(), permissions = get(), launcher = get(), openRequests = get()) }
        viewModel {
            AppUpdateViewModel(checker = get(), settingsRepository = get(), connectivityMonitor = get())
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
