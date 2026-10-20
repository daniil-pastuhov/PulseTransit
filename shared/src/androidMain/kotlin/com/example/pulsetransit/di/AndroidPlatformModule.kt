package com.example.pulsetransit.di

import android.content.Context
import com.example.pulsetransit.data.cache.CacheDatabase
import com.example.pulsetransit.data.cache.buildCacheDatabase
import com.example.pulsetransit.data.cache.cacheDatabaseBuilder
import com.example.pulsetransit.data.createAppSettings
import com.example.pulsetransit.platform.AppUpdateChecker
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.platform.ConnectivityMonitor
import com.example.pulsetransit.platform.ConnectivityMonitorImpl
import com.example.pulsetransit.platform.FusedLocationProvider
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.PlayAppUpdateChecker
import com.example.pulsetransit.platform.SystemClockFormatter
import com.example.pulsetransit.platform.SystemLocationProvider
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.CameraMotionDetectorImpl
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import com.example.pulsetransit.ui.sensors.SystemSensorManagerImpl
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** The Android half of [sharedModule]: everything that needs a `Context` or an Android API. */
val androidPlatformModule =
    module {
        single<HttpClientEngineFactory<*>> { OkHttp }
        single<Settings> { createAppSettings(androidContext()) }
        single<CacheDatabase> { cacheDatabaseBuilder(androidContext()).buildCacheDatabase() }
        single<ClockFormatter> { SystemClockFormatter() }
        single<LocationProvider> { createLocationProvider(androidContext()) }
        single<ConnectivityMonitor> { ConnectivityMonitorImpl(context = androidContext()) }
        single<AppUpdateChecker> { PlayAppUpdateChecker(context = androidContext()) }
        single<SystemSensorManager> { SystemSensorManagerImpl(context = androidContext()) }
        single<CameraMotionDetector> {
            CameraMotionDetectorImpl(context = androidContext(), settingsRepository = get())
        }
    }

/**
 * The fused provider where Play Services can serve it, else the platform's `LocationManager`
 * (devices without Play Services, or with a version too old for the fused provider).
 */
private fun createLocationProvider(context: Context): LocationProvider =
    if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS) {
        FusedLocationProvider(context = context)
    } else {
        SystemLocationProvider(context = context)
    }
