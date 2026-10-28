package com.example.pulsetransit.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.serialization.json.Json

/** The prefs file every release so far has used; renaming it would reset everyone's settings. */
private const val PREFS_NAME = "app_settings"

private const val LEGACY_FILTERED_LINES = "filtered_lines"
private const val LEGACY_QUAYS_PREFIX = "quays_"

/** Device state that must not leave the device; excluded from backup in both backup rule files. */
private const val DEVICE_STATE_PREFS_NAME = "device_state"

/** Where the device last was, for the widgets: a SharedPreferences file of its own, never backed up. */
fun createDeviceStateSettings(context: Context): Settings =
    SharedPreferencesSettings(context.getSharedPreferences(DEVICE_STATE_PREFS_NAME, Context.MODE_PRIVATE))

/** The app's [Settings], backed by the same SharedPreferences file as before the KMP move. */
fun createAppSettings(context: Context): Settings {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    migrateLegacyStringSets(prefs)
    return SharedPreferencesSettings(prefs)
}

/**
 * Rewrites the string sets that older builds stored with `putStringSet` as the JSON strings that
 * [SettingsRepositoryImpl] now reads, then removes the originals. A no-op once nothing is left.
 */
internal fun migrateLegacyStringSets(prefs: SharedPreferences) {
    val legacy =
        prefs.all.filter { (key, value) ->
            value is Set<*> && (key == LEGACY_FILTERED_LINES || key.startsWith(LEGACY_QUAYS_PREFIX))
        }
    if (legacy.isEmpty()) return

    prefs.edit {
        for ((key, value) in legacy) {
            val newKey =
                if (key == LEGACY_FILTERED_LINES) {
                    SettingsRepositoryImpl.Keys.FILTERED_LINES
                } else {
                    SettingsRepositoryImpl.Keys.selectedQuays(key.removePrefix(LEGACY_QUAYS_PREFIX))
                }
            val values = (value as Set<*>).filterIsInstance<String>()
            putString(newKey, Json.encodeToString(values))
            remove(key)
        }
    }
}
