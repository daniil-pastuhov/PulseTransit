package com.example.pulsetransit.data.cache

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

/** [CacheDatabaseContract] on Android's framework SQLite, which the app uses. */
@RunWith(AndroidJUnit4::class)
class CacheDatabaseTest : CacheDatabaseContract() {
    override fun createDatabase(): CacheDatabase =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CacheDatabase::class.java)
            .build()
}
