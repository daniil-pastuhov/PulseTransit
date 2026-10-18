package com.example.pulsetransit.di

import com.example.pulsetransit.data.AppStoreUpdateChecker
import com.example.pulsetransit.data.cache.CacheDatabase
import com.example.pulsetransit.data.cache.buildCacheDatabase
import com.example.pulsetransit.data.cache.cacheDatabaseBuilder
import com.example.pulsetransit.platform.AppUpdateChecker
import com.example.pulsetransit.platform.ConnectivityMonitor
import com.example.pulsetransit.platform.PathConnectivityMonitor
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice

/**
 * The iOS half of [sharedModule] that Kotlin can provide on its own. The clock formatter, location
 * and sensors are implemented in Swift and are added when the iOS app starts Koin.
 */
val iosPlatformModule =
    module {
        single<HttpClientEngineFactory<*>> { Darwin }
        single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
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
