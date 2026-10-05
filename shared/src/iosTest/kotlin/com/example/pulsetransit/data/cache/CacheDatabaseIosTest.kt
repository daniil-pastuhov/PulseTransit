package com.example.pulsetransit.data.cache

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** [CacheDatabaseContract] on the bundled SQLite driver the iOS app uses. */
class CacheDatabaseIosTest : CacheDatabaseContract() {
    override fun createDatabase(): CacheDatabase =
        Room
            .inMemoryDatabaseBuilder<CacheDatabase>()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
}
