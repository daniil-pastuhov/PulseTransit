package com.example.pulsetransit.data.cache

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/** The cache in the app's databases dir, on the platform's own SQLite. */
fun cacheDatabaseBuilder(context: Context): RoomDatabase.Builder<CacheDatabase> {
    val appContext = context.applicationContext
    return Room.databaseBuilder<CacheDatabase>(
        context = appContext,
        name = appContext.getDatabasePath(CacheDatabase.FILE_NAME).absolutePath,
    )
}
