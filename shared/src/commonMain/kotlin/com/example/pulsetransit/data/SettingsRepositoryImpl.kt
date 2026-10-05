package com.example.pulsetransit.data

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Settings in a key-value [Settings] store: SharedPreferences on Android, NSUserDefaults on iOS.
 *
 * Sets of strings are stored as JSON under the keys in [Keys], because NSUserDefaults has no
 * string-set type. Android builds with string sets from before that change are migrated once, by
 * [migrateLegacyStringSets].
 */
class SettingsRepositoryImpl(
    private val prefs: Settings,
) : SettingsRepository {
    object Keys {
        const val FILTERED_LINES = "filtered_lines_json"

        fun selectedQuays(stopId: String) = "quays_json_$stopId"
    }

    private companion object {
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 60
        const val DEFAULT_AUTO_DIM_SECONDS = 30
        const val DEFAULT_AUTO_THEME_DARK_LUX = 10f
        const val DEFAULT_AUTO_THEME_LIGHT_LUX = 100f
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val _fullScreenMode = MutableStateFlow(isFullScreenMode())

    override fun getFullScreenModeFlow(): Flow<Boolean> = _fullScreenMode.asStateFlow()

    private val _alwaysOnEnabled = MutableStateFlow(isAlwaysOnEnabled())

    override fun getAlwaysOnEnabledFlow(): Flow<Boolean> = _alwaysOnEnabled.asStateFlow()

    private val _showCancelledLines = MutableStateFlow(isShowCancelledLines())

    override fun getShowCancelledLinesFlow(): Flow<Boolean> = _showCancelledLines.asStateFlow()

    private val _showDelays = MutableStateFlow(isShowDelays())

    override fun getShowDelaysFlow(): Flow<Boolean> = _showDelays.asStateFlow()

    private val _showMessages = MutableStateFlow(isShowMessages())

    override fun getShowMessagesFlow(): Flow<Boolean> = _showMessages.asStateFlow()

    private val _autoDimSeconds = MutableStateFlow(getAutoDimSeconds())

    override fun getAutoDimSecondsFlow(): Flow<Int> = _autoDimSeconds.asStateFlow()

    private val _autoThemeDarkLux = MutableStateFlow(getAutoThemeDarkLux())

    override fun getAutoThemeDarkLuxFlow(): Flow<Float> = _autoThemeDarkLux.asStateFlow()

    private val _autoThemeLightLux = MutableStateFlow(getAutoThemeLightLux())

    override fun getAutoThemeLightLuxFlow(): Flow<Float> = _autoThemeLightLux.asStateFlow()

    private val _cameraMotionWake = MutableStateFlow(isCameraMotionWakeEnabled())

    override fun getCameraMotionWakeFlow(): Flow<Boolean> = _cameraMotionWake.asStateFlow()

    private val _filteredLines = MutableStateFlow(getFilteredLines())

    override fun getFilteredLinesFlow(): Flow<Set<String>> = _filteredLines.asStateFlow()

    override fun getFavorites(): List<StopPlace> {
        val jsonStr = prefs.getStringOrNull("favorites") ?: return emptyList()
        return try {
            json.decodeFromString<List<StopPlace>>(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun addFavorite(stopPlace: StopPlace) {
        val current = getFavorites().toMutableList()
        if (current.none { it.id == stopPlace.id }) {
            current.add(stopPlace)
            prefs.putString("favorites", json.encodeToString(current))
        }
    }

    override fun removeFavorite(stopId: String) {
        val current = getFavorites().filter { it.id != stopId }
        prefs.putString("favorites", json.encodeToString(current))
    }

    override fun reorderFavorites(stopIds: List<String>) {
        val current = getFavorites()
        val reordered = stopIds.mapNotNull { id -> current.find { it.id == id } }
        prefs.putString("favorites", json.encodeToString(reordered))
    }

    override fun isFavorite(stopId: String): Boolean = getFavorites().any { it.id == stopId }

    override fun getRefreshIntervalSeconds(): Int = prefs.getInt("refresh_interval", DEFAULT_REFRESH_INTERVAL_SECONDS)

    override fun setRefreshIntervalSeconds(seconds: Int) {
        prefs.putInt("refresh_interval", seconds)
    }

    override fun getAutoDimSeconds(): Int = prefs.getInt("auto_dim_seconds", DEFAULT_AUTO_DIM_SECONDS)

    override fun setAutoDimSeconds(seconds: Int) {
        prefs.putInt("auto_dim_seconds", seconds)
        _autoDimSeconds.value = seconds
    }

    override fun getThemeMode(): ThemeMode {
        val modeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(modeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    override fun setThemeMode(mode: ThemeMode) {
        prefs.putString("theme_mode", mode.name)
    }

    override fun getAutoThemeDarkLux(): Float = prefs.getFloat("auto_theme_dark_lux", DEFAULT_AUTO_THEME_DARK_LUX)

    override fun setAutoThemeDarkLux(lux: Float) {
        prefs.putFloat("auto_theme_dark_lux", lux)
        _autoThemeDarkLux.value = lux
    }

    override fun getAutoThemeLightLux(): Float = prefs.getFloat("auto_theme_light_lux", DEFAULT_AUTO_THEME_LIGHT_LUX)

    override fun setAutoThemeLightLux(lux: Float) {
        prefs.putFloat("auto_theme_light_lux", lux)
        _autoThemeLightLux.value = lux
    }

    override fun getBurnInProtectionLevel(): BurnInProtectionLevel {
        val levelStr = prefs.getString("burn_in_protection", BurnInProtectionLevel.MEDIUM.name)
        return try {
            BurnInProtectionLevel.valueOf(levelStr)
        } catch (_: Exception) {
            BurnInProtectionLevel.MEDIUM
        }
    }

    override fun setBurnInProtectionLevel(level: BurnInProtectionLevel) {
        prefs.putString("burn_in_protection", level.name)
    }

    override fun getDateDisplayStyle(): DateDisplayStyle {
        val styleStr = prefs.getString("date_display_style", DateDisplayStyle.BESIDE.name)
        return try {
            DateDisplayStyle.valueOf(styleStr)
        } catch (_: Exception) {
            DateDisplayStyle.BESIDE
        }
    }

    override fun setDateDisplayStyle(style: DateDisplayStyle) {
        prefs.putString("date_display_style", style.name)
    }

    override fun getSavedLatitude(): Double? {
        val valStr = prefs.getStringOrNull("saved_lat") ?: return null
        return valStr.toDoubleOrNull()
    }

    override fun getSavedLongitude(): Double? {
        val valStr = prefs.getStringOrNull("saved_lon") ?: return null
        return valStr.toDoubleOrNull()
    }

    override fun setSavedLocation(
        lat: Double?,
        lon: Double?,
    ) {
        if ((lat == null) || (lon == null)) {
            prefs.remove("saved_lat")
            prefs.remove("saved_lon")
        } else {
            prefs.putString("saved_lat", lat.toString())
            prefs.putString("saved_lon", lon.toString())
        }
    }

    override fun isShowDelays(): Boolean = prefs.getBoolean("show_delays", true)

    override fun setShowDelays(show: Boolean) {
        prefs.putBoolean("show_delays", show)
        _showDelays.value = show
    }

    override fun isShowMessages(): Boolean = prefs.getBoolean("show_messages", true)

    override fun setShowMessages(show: Boolean) {
        prefs.putBoolean("show_messages", show)
        _showMessages.value = show
    }

    override fun getSelectedQuays(stopId: String): Set<String>? = getStringSet(Keys.selectedQuays(stopId))

    override fun setSelectedQuays(
        stopId: String,
        quayIds: Set<String>?,
    ) {
        if (quayIds == null) {
            prefs.remove(Keys.selectedQuays(stopId))
        } else {
            putStringSet(Keys.selectedQuays(stopId), quayIds)
        }
    }

    override fun isCameraMotionWakeEnabled(): Boolean = prefs.getBoolean("camera_motion_wake", false)

    override fun setCameraMotionWakeEnabled(enabled: Boolean) {
        prefs.putBoolean("camera_motion_wake", enabled)
        _cameraMotionWake.value = enabled
    }

    override fun getCameraSensitivity(): CameraSensitivity {
        val str = prefs.getString("camera_sensitivity", CameraSensitivity.MEDIUM.name)
        return try {
            CameraSensitivity.valueOf(str)
        } catch (_: Exception) {
            CameraSensitivity.MEDIUM
        }
    }

    override fun setCameraSensitivity(sensitivity: CameraSensitivity) {
        prefs.putString("camera_sensitivity", sensitivity.name)
    }

    override fun isDynamicRefreshEnabled(): Boolean = prefs.getBoolean("dynamic_refresh", true)

    override fun setDynamicRefreshEnabled(enabled: Boolean) = prefs.putBoolean("dynamic_refresh", enabled)

    override fun getDestinationStations(): List<StopPlace> {
        val jsonStr = prefs.getStringOrNull("destination_stations") ?: return emptyList()
        return try {
            json.decodeFromString<List<StopPlace>>(jsonStr)
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun addDestinationStation(stopPlace: StopPlace) {
        val current = getDestinationStations().toMutableList()
        if (current.none { it.id == stopPlace.id } &&
            current.size < SettingsRepository.MAX_DESTINATION_STATIONS
        ) {
            current.add(stopPlace)
            prefs.putString("destination_stations", json.encodeToString(current))
        }
    }

    override fun removeDestinationStation(stopId: String) {
        val current = getDestinationStations().filter { it.id != stopId }
        prefs.putString("destination_stations", json.encodeToString(current))
    }

    override fun isFullScreenMode(): Boolean = prefs.getBoolean("full_screen_mode", false)

    override fun setFullScreenMode(enabled: Boolean) {
        prefs.putBoolean("full_screen_mode", enabled)
        _fullScreenMode.value = enabled
    }

    override fun isAlwaysOnEnabled(): Boolean = prefs.getBoolean("always_on_enabled", false)

    override fun setAlwaysOnEnabled(enabled: Boolean) {
        prefs.putBoolean("always_on_enabled", enabled)
        _alwaysOnEnabled.value = enabled
    }

    override fun isShowCancelledLines(): Boolean = prefs.getBoolean("show_cancelled_lines", false)

    override fun setShowCancelledLines(show: Boolean) {
        prefs.putBoolean("show_cancelled_lines", show)
        _showCancelledLines.value = show
    }

    override fun getFilteredLines(): Set<String> = getStringSet(Keys.FILTERED_LINES) ?: emptySet()

    override fun addFilteredLine(line: String) {
        val current = getFilteredLines().toMutableSet()
        if (current.add(line)) {
            putStringSet(Keys.FILTERED_LINES, current)
            _filteredLines.value = current
        }
    }

    override fun removeFilteredLine(line: String) {
        val current = getFilteredLines().toMutableSet()
        if (current.remove(line)) {
            putStringSet(Keys.FILTERED_LINES, current)
            _filteredLines.value = current
        }
    }

    private fun getStringSet(key: String): Set<String>? {
        val jsonStr = prefs.getStringOrNull(key) ?: return null
        return try {
            json.decodeFromString<List<String>>(jsonStr).toSet()
        } catch (_: Exception) {
            null
        }
    }

    private fun putStringSet(
        key: String,
        values: Set<String>,
    ) {
        prefs.putString(key, json.encodeToString(values.toList()))
    }
}
