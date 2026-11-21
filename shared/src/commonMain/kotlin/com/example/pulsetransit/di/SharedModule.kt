package com.example.pulsetransit.di

import com.example.pulsetransit.data.DataSourceRepository
import com.example.pulsetransit.data.DataSourceRepositoryImpl
import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.EnturRepositoryImpl
import com.example.pulsetransit.data.RouteOptions
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
import com.example.pulsetransit.data.journey.watch.NoWatchLink
import com.example.pulsetransit.data.journey.watch.WatchJourneyNotifier
import com.example.pulsetransit.data.pace.WalkingPaceRecorder
import com.example.pulsetransit.data.widget.BoardWidgetSource
import com.example.pulsetransit.platform.JourneyLauncher
import com.example.pulsetransit.platform.JourneyNotifier
import com.example.pulsetransit.platform.WatchLink
import com.example.pulsetransit.platform.WatchMirror
import com.example.pulsetransit.platform.WatchWording
import com.example.pulsetransit.ui.viewmodel.AppUpdateViewModel
import com.example.pulsetransit.ui.viewmodel.DataSourcesViewModel
import com.example.pulsetransit.ui.viewmodel.JourneyAlertsViewModel
import com.example.pulsetransit.ui.viewmodel.ModeSelectionViewModel
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import io.ktor.client.engine.HttpClientEngineFactory
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.core.scope.Scope
import org.koin.dsl.module

/** Names the `LocationProvider` journey alerts use, where it differs from the board's (iOS). */
const val JOURNEY_LOCATION = "journeyLocation"

/** A `Settings` kept out of backups for where the device last was; only platforms with widgets supply it. */
const val DEVICE_STATE = "deviceState"

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
 * - optionally a `WatchWording` with a `WatchLink` and/or a `WatchMirror`, to show a followed
 *   journey on a watch too
 * - optionally a `Settings` named [DEVICE_STATE], never backed up, where the widgets need to know
 *   where the device last was (Android)
 */
val sharedModule =
    module {
        single { createHttpClient(get<HttpClientEngineFactory<*>>()) }
        factory { get<CacheDatabase>().departureCacheDao() }
        factory { get<CacheDatabase>().tripCacheDao() }
        factory { get<CacheDatabase>().forecastCacheDao() }

        single<SettingsRepository> { SettingsRepositoryImpl(prefs = get(), deviceState = getOrNull(named(DEVICE_STATE))) }
        single<EnturRepository> {
            EnturRepositoryImpl(client = get(), departureCache = get(), tripCache = get())
        }
        single<DataSourceRepository> { DataSourceRepositoryImpl(client = get()) }
        single<WeatherRepository> { WeatherRepositoryImpl(client = get(), cache = get()) }
        single { BoardWidgetSource(enturRepository = get(), weatherRepository = get(), settingsRepository = get()) }
        single<JourneyStore> { JourneyStoreImpl(prefs = get()) }
        single { JourneyOpenRequests() }
        single { WalkingPaceRecorder(settings = get()) }
        single {
            val settings = get<SettingsRepository>()
            JourneyTracker(
                store = get(),
                enturRepository = get(),
                notifier = watchAware(phone = get()),
                locationProvider = getOrNull(named(JOURNEY_LOCATION)) ?: get(),
                // As the board plans routes, so a faster way on avoids what the board's routes avoid.
                routeOptions = {
                    RouteOptions(
                        preference = settings.getRoutePreference(),
                        excludedModes = settings.getExcludedRouteModes(),
                        bannedLines = settings.getFilteredLines(),
                        walkingSpeed = settings.getWalkingSpeed(),
                    )
                },
                walkingPace = get(),
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
                walkingPace = get(),
            )
        }
        viewModel { ModeSelectionViewModel(settingsRepository = get()) }
        viewModel { DataSourcesViewModel(repository = get(), settingsRepository = get()) }
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
                journeyStore = get(),
                journeyPermissions = get(),
                walkingPace = get(),
                watchLink = getOrNull(),
            )
        }
    }

/**
 * The [phone]'s notifier, also showing the journey on a watch where the platform registers a
 * [WatchWording] with a [WatchLink], a [WatchMirror] or both; just the phone's where it has none.
 */
private fun Scope.watchAware(phone: JourneyNotifier): JourneyNotifier {
    val link = getOrNull<WatchLink>()
    val mirror = getOrNull<WatchMirror>()
    val wording = getOrNull<WatchWording>()
    if (wording == null || (link == null && mirror == null)) return phone
    val koin = getKoin()
    return WatchJourneyNotifier(
        phone = phone,
        link = link ?: NoWatchLink,
        wording = wording,
        store = get(),
        // Looked up when used: on iOS the launcher holds the tracker this notifier is built for.
        stopFollowing = { koin.get<JourneyLauncher>().stop() },
        mirror = mirror,
    )
}
