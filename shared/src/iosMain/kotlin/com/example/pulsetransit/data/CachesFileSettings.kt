package com.example.pulsetransit.data

import com.russhwolf.settings.Settings
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.Json
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * A small [Settings] kept as JSON in a file under a `Library/Caches` directory, which iOS never
 * backs up. It holds where on-spot mode last found the device (`DEVICE_STATE`), which must stay on
 * the device, as Android keeps it out of backups; NSUserDefaults offers no such exclusion. Caches
 * may be purged, which only makes the widgets route from the saved location until the next fix.
 *
 * Values are stored as strings; it is meant for a handful of keys, read and written rarely. The
 * function count is the [Settings] interface's: a getter, a nullable getter and a putter per type.
 */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class CachesFileSettings(
    private val path: String,
) : Settings {
    private val json = Json { ignoreUnknownKeys = true }

    private fun read(): Map<String, String> {
        val text = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(text) }.getOrDefault(emptyMap())
    }

    private fun write(values: Map<String, String>) {
        @Suppress("CAST_NEVER_SUCCEEDS")
        val text = NSString.create(string = json.encodeToString(values))
        text.writeToFile(path, atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }

    private fun update(change: MutableMap<String, String>.() -> Unit) = write(read().toMutableMap().apply(change))

    private fun put(
        key: String,
        value: Any,
    ) = update { put(key, value.toString()) }

    override val keys: Set<String> get() = read().keys

    override val size: Int get() = read().size

    override fun clear() {
        NSFileManager.defaultManager.removeItemAtPath(path, error = null)
    }

    override fun remove(key: String) = update { remove(key) }

    override fun hasKey(key: String): Boolean = key in read()

    override fun putInt(
        key: String,
        value: Int,
    ) = put(key, value)

    override fun getInt(
        key: String,
        defaultValue: Int,
    ): Int = getIntOrNull(key) ?: defaultValue

    override fun getIntOrNull(key: String): Int? = read()[key]?.toIntOrNull()

    override fun putLong(
        key: String,
        value: Long,
    ) = put(key, value)

    override fun getLong(
        key: String,
        defaultValue: Long,
    ): Long = getLongOrNull(key) ?: defaultValue

    override fun getLongOrNull(key: String): Long? = read()[key]?.toLongOrNull()

    override fun putString(
        key: String,
        value: String,
    ) = put(key, value)

    override fun getString(
        key: String,
        defaultValue: String,
    ): String = getStringOrNull(key) ?: defaultValue

    override fun getStringOrNull(key: String): String? = read()[key]

    override fun putFloat(
        key: String,
        value: Float,
    ) = put(key, value)

    override fun getFloat(
        key: String,
        defaultValue: Float,
    ): Float = getFloatOrNull(key) ?: defaultValue

    override fun getFloatOrNull(key: String): Float? = read()[key]?.toFloatOrNull()

    override fun putDouble(
        key: String,
        value: Double,
    ) = put(key, value)

    override fun getDouble(
        key: String,
        defaultValue: Double,
    ): Double = getDoubleOrNull(key) ?: defaultValue

    override fun getDoubleOrNull(key: String): Double? = read()[key]?.toDoubleOrNull()

    override fun putBoolean(
        key: String,
        value: Boolean,
    ) = put(key, value)

    override fun getBoolean(
        key: String,
        defaultValue: Boolean,
    ): Boolean = getBooleanOrNull(key) ?: defaultValue

    override fun getBooleanOrNull(key: String): Boolean? = read()[key]?.toBooleanStrictOrNull()
}

/**
 * Where on-spot mode last found the device, shared with the widgets but kept out of backups: a
 * file in the App Group's `Library/Caches`. Null without the group, as then no widget can read it.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun deviceStateSettings(): Settings? {
    val container = appGroupContainerPath() ?: return null
    val dir = "$container/Library/Caches"
    NSFileManager.defaultManager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
    return CachesFileSettings("$dir/device_state.json")
}
