package com.example.pulsetransit.di

import com.example.pulsetransit.data.AppStoreUpdateChecker
import com.example.pulsetransit.data.appSettingsDefaults
import com.example.pulsetransit.data.cache.CacheDatabase
import com.example.pulsetransit.data.cache.buildCacheDatabase
import com.example.pulsetransit.data.cache.cacheDatabaseBuilder
import com.example.pulsetransit.data.deviceStateSettings
import com.example.pulsetransit.platform.AppUpdateChecker
import com.example.pulsetransit.platform.ConnectivityMonitor
import com.example.pulsetransit.platform.PathConnectivityMonitor
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

/**
 * The iOS half of [sharedModule] that Kotlin can provide on its own. The clock formatter, location
 * and sensors are implemented in Swift and are added when the iOS app starts Koin.
 */
val iosPlatformModule =
    module {
        single<HttpClientEngineFactory<*>> { Darwin }
        // In the App Group when the build has it, so the widgets read the same settings.
        single<Settings> { NSUserDefaultsSettings(appSettingsDefaults()) }
        // Where on-spot mode last found the device, for the widgets, which cannot locate it
        // themselves: kept out of backups, as on Android. Only with the App Group, which the
        // widgets need to read it.
        deviceStateSettings()?.let { deviceState -> single<Settings>(named(DEVICE_STATE)) { deviceState } }
        single<CacheDatabase> { cacheDatabaseBuilder().buildCacheDatabase() }
        single<ConnectivityMonitor> { PathConnectivityMonitor() }
        single<AppUpdateChecker> {
            val info = NSBundle.mainBundle.infoDictionary
            AppStoreUpdateChecker(
                client = get(),
                bundleId = NSBundle.mainBundle.bundleIdentifier.orEmpty(),
                installedVersion = info?.get("CFBundleShortVersionString") as? String ?: "",
                osVersion = UIDevice.currentDevice.systemVersion,
            )
        }
    }
