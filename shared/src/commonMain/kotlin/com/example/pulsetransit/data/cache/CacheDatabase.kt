package com.example.pulsetransit.data.cache

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.json.Json

/**
 * Offline cache for everything the board fetches from the network, so a failed request shows the
 * last good data instead of a blank screen. It holds only derived data — nothing here is a source
 * of truth, and dropping the file costs at most one refresh.
 */
@Database(
    entities = [
        CachedDeparture::class,
        CachedTrip::class,
        CachedForecast::class,
        CachedForecastHour::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(CacheConverters::class)
@ConstructedBy(CacheDatabaseConstructor::class)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun departureCacheDao(): DepartureCacheDao

    abstract fun tripCacheDao(): TripCacheDao

    abstract fun forecastCacheDao(): ForecastCacheDao

    companion object {
        /** The file name every release has used; on Android it sits in the app's databases dir. */
        const val FILE_NAME = "pulse_transit_cache.db"
    }
}

/** Implemented by Room's compiler for each platform. */
@Suppress("KotlinNoActualForExpect")
expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
    override fun initialize(): CacheDatabase
}

/**
 * Finishes a platform's database builder with the settings every platform shares. The cache is
 * disposable, so a schema change may throw it away rather than require a migration.
 */
fun RoomDatabase.Builder<CacheDatabase>.buildCacheDatabase(): CacheDatabase = fallbackToDestructiveMigration(dropAllTables = true).build()

/** Stores the small nested lists on cache rows as JSON, matching how settings are persisted. */
class CacheConverters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun stringListToJson(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToStringList(value: String): List<String> =
        try {
            json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }

    @TypeConverter
    fun legsToJson(value: List<CachedTripLeg>): String = json.encodeToString(value)

    @TypeConverter
    fun jsonToLegs(value: String): List<CachedTripLeg> =
        try {
            json.decodeFromString(value)
        } catch (_: Exception) {
            emptyList()
        }
}
