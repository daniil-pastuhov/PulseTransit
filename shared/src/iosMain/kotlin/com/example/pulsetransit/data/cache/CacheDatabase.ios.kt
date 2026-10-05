package com.example.pulsetransit.data.cache

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * The cache in the app's Caches directory: it is derived data, so iOS may purge it under storage
 * pressure, and it stays out of device backups.
 */
@OptIn(ExperimentalForeignApi::class)
fun cacheDatabaseBuilder(): RoomDatabase.Builder<CacheDatabase> {
    val cachesDir =
        NSFileManager.defaultManager
            .URLForDirectory(
                directory = NSCachesDirectory,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            )?.path
            ?: error("No Caches directory")
    return Room
        .databaseBuilder<CacheDatabase>(name = "$cachesDir/${CacheDatabase.FILE_NAME}")
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
}
