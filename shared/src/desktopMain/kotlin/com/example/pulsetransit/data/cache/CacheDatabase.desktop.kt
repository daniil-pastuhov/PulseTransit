package com.example.pulsetransit.data.cache

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/** The cache in the system temp directory: it is derived data, safe to lose between runs. */
fun cacheDatabaseBuilder(): RoomDatabase.Builder<CacheDatabase> {
    val dir = File(System.getProperty("java.io.tmpdir"), "pulsetransit").apply { mkdirs() }
    return Room
        .databaseBuilder<CacheDatabase>(name = File(dir, CacheDatabase.FILE_NAME).absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
}
