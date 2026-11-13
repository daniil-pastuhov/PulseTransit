package com.example.pulsetransit.data.cache

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.example.pulsetransit.data.appGroupContainerPath
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * The cache in a Caches directory: it is derived data, so iOS may purge it under storage pressure,
 * and it stays out of device backups. It is the App Group's, so the widgets share it with the
 * board (and MET is never asked twice before its `Expires` time); without the group, the app's own.
 */
@OptIn(ExperimentalForeignApi::class)
fun cacheDatabaseBuilder(): RoomDatabase.Builder<CacheDatabase> {
    val groupDir = appGroupContainerPath()?.let(::groupCachesDir)
    val builder =
        Room
            .databaseBuilder<CacheDatabase>(name = "${groupDir ?: appCachesDir()}/${CacheDatabase.FILE_NAME}")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
    // iOS kills a suspended app holding a lock on a file in a shared container (0xdead10cc), and WAL
    // keeps its shared-memory lock while the database is open. A rollback journal holds locks only
    // for the length of a transaction.
    return if (groupDir != null) builder.setJournalMode(RoomDatabase.JournalMode.TRUNCATE) else builder
}

@OptIn(ExperimentalForeignApi::class)
private fun appCachesDir(): String =
    NSFileManager.defaultManager
        .URLForDirectory(
            directory = NSCachesDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )?.path
        ?: error("No Caches directory")

/** `Library/Caches` in the group's container, made if missing; null if it cannot be. */
@OptIn(ExperimentalForeignApi::class)
private fun groupCachesDir(container: String): String? {
    val dir = "$container/Library/Caches"
    val made = NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return dir.takeIf { made }
}
