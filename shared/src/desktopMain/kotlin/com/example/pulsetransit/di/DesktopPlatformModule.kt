package com.example.pulsetransit.di

import com.example.pulsetransit.data.cache.CacheDatabase
import com.example.pulsetransit.data.cache.buildCacheDatabase
import com.example.pulsetransit.data.cache.cacheDatabaseBuilder
import com.example.pulsetransit.platform.AppUpdateChecker
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.platform.ConnectivityMonitor
import com.example.pulsetransit.platform.DesktopClockFormatter
import com.example.pulsetransit.platform.IpLocationProvider
import com.example.pulsetransit.platform.JourneyLauncher
import com.example.pulsetransit.platform.JourneyNotifier
import com.example.pulsetransit.platform.JourneyPermissions
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.NetworkInterfaceConnectivityMonitor
import com.example.pulsetransit.platform.NoAppUpdates
import com.example.pulsetransit.platform.NoCameraMotion
import com.example.pulsetransit.platform.NoJourneyAlerts
import com.example.pulsetransit.platform.NoLightSensor
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.dsl.module
import java.util.prefs.Preferences

/**
 * The desktop half of [sharedModule], for the Compose Hot Reload app. Settings persist in the
 * user's Java preferences, so stops added once stay between runs. Location is by IP address and
 * connectivity by network interface; there is no light sensor, camera, store or journey alerts.
 */
val desktopPlatformModule =
    module {
        single<HttpClientEngineFactory<*>> { OkHttp }
        single<Settings> { PreferencesSettings(Preferences.userRoot().node("com/homieapps/pulsetransit")) }
        single<CacheDatabase> { cacheDatabaseBuilder().buildCacheDatabase() }
        single<ClockFormatter> { DesktopClockFormatter() }
        single<LocationProvider> { IpLocationProvider(client = get()) }
        single<ConnectivityMonitor> { NetworkInterfaceConnectivityMonitor() }
        single<AppUpdateChecker> { NoAppUpdates() }
        single<SystemSensorManager> { NoLightSensor() }
        single<CameraMotionDetector> { NoCameraMotion() }
        single { NoJourneyAlerts() }
        single<JourneyNotifier> { get<NoJourneyAlerts>() }
        single<JourneyPermissions> { get<NoJourneyAlerts>() }
        single<JourneyLauncher> { get<NoJourneyAlerts>() }
    }
