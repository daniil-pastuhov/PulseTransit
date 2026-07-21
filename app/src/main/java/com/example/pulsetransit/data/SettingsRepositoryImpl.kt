package com.example.pulsetransit.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class SettingsRepositoryImpl(
    context: Context,
) : SettingsRepository {
    private companion object {
        const val DEFAULT_REFRESH_INTERVAL_SECONDS = 60
        const val DEFAULT_AUTO_DIM_SECONDS = 30
        const val DEFAULT_AUTO_THEME_DARK_LUX = 10f
        const val DEFAULT_AUTO_THEME_LIGHT_LUX = 100f
    }

    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
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
        val jsonStr = prefs.getString("favorites", null) ?: return emptyList()
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
            prefs.edit {
                putString("favorites", json.encodeToString(current))
            }
        }
    }

    override fun removeFavorite(stopId: String) {
        val current = getFavorites().filter { it.id != stopId }
        prefs.edit {
            putString("favorites", json.encodeToString(current))
        }
    }

    override fun reorderFavorites(stopIds: List<String>) {
        val current = getFavorites()
        val reordered = stopIds.mapNotNull { id -> current.find { it.id == id } }
        prefs.edit {
            putString("favorites", json.encodeToString(reordered))
        }
    }

    override fun isFavorite(stopId: String): Boolean = getFavorites().any { it.id == stopId }

    override fun getRefreshIntervalSeconds(): Int = prefs.getInt("refresh_interval", DEFAULT_REFRESH_INTERVAL_SECONDS)

    override fun setRefreshIntervalSeconds(seconds: Int) {
        prefs.edit { putInt("refresh_interval", seconds) }
    }

    override fun getAutoDimSeconds(): Int = prefs.getInt("auto_dim_seconds", DEFAULT_AUTO_DIM_SECONDS)

    override fun setAutoDimSeconds(seconds: Int) {
        prefs.edit { putInt("auto_dim_seconds", seconds) }
        _autoDimSeconds.value = seconds
    }

    override fun getThemeMode(): ThemeMode {
        val modeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(modeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString("theme_mode", mode.name) }
    }

    override fun getAutoThemeDarkLux(): Float = prefs.getFloat("auto_theme_dark_lux", DEFAULT_AUTO_THEME_DARK_LUX)

    override fun setAutoThemeDarkLux(lux: Float) {
        prefs.edit { putFloat("auto_theme_dark_lux", lux) }
        _autoThemeDarkLux.value = lux
    }

    override fun getAutoThemeLightLux(): Float = prefs.getFloat("auto_theme_light_lux", DEFAULT_AUTO_THEME_LIGHT_LUX)

    override fun setAutoThemeLightLux(lux: Float) {
        prefs.edit { putFloat("auto_theme_light_lux", lux) }
        _autoThemeLightLux.value = lux
    }

    override fun getBurnInProtectionLevel(): BurnInProtectionLevel {
        val levelStr =
            prefs.getString("burn_in_protection", BurnInProtectionLevel.MEDIUM.name)
                ?: BurnInProtectionLevel.MEDIUM.name
        return try {
            BurnInProtectionLevel.valueOf(levelStr)
        } catch (_: Exception) {
            BurnInProtectionLevel.MEDIUM
        }
    }

    override fun setBurnInProtectionLevel(level: BurnInProtectionLevel) {
        prefs.edit { putString("burn_in_protection", level.name) }
    }

    override fun getSavedLatitude(): Double? {
        val valStr = prefs.getString("saved_lat", null) ?: return null
        return valStr.toDoubleOrNull()
    }

    override fun getSavedLongitude(): Double? {
        val valStr = prefs.getString("saved_lon", null) ?: return null
        return valStr.toDoubleOrNull()
    }

    override fun setSavedLocation(
        lat: Double?,
        lon: Double?,
    ) {
        if ((lat == null) || (lon == null)) {
            prefs.edit {
                remove("saved_lat")
                remove("saved_lon")
            }
        } else {
            prefs.edit {
                putString("saved_lat", lat.toString())
                putString("saved_lon", lon.toString())
            }
        }
    }

    override fun isShowDelays(): Boolean = prefs.getBoolean("show_delays", true)

    override fun setShowDelays(show: Boolean) {
        prefs.edit { putBoolean("show_delays", show) }
        _showDelays.value = show
    }

    override fun isShowMessages(): Boolean = prefs.getBoolean("show_messages", true)

    override fun setShowMessages(show: Boolean) {
        prefs.edit { putBoolean("show_messages", show) }
        _showMessages.value = show
    }

    override fun getSelectedQuays(stopId: String): Set<String>? = prefs.getStringSet("quays_$stopId", null)

    override fun setSelectedQuays(
        stopId: String,
        quayIds: Set<String>?,
    ) {
        if (quayIds == null) {
            prefs.edit { remove("quays_$stopId") }
        } else {
            prefs.edit { putStringSet("quays_$stopId", quayIds) }
        }
    }

    override fun isCameraMotionWakeEnabled(): Boolean = prefs.getBoolean("camera_motion_wake", false)

    override fun setCameraMotionWakeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("camera_motion_wake", enabled) }
        _cameraMotionWake.value = enabled
    }

    override fun getCameraSensitivity(): CameraSensitivity {
        val str =
            prefs.getString("camera_sensitivity", CameraSensitivity.MEDIUM.name)
                ?: CameraSensitivity.MEDIUM.name
        return try {
            CameraSensitivity.valueOf(str)
        } catch (_: Exception) {
            CameraSensitivity.MEDIUM
        }
    }

    override fun setCameraSensitivity(sensitivity: CameraSensitivity) {
        prefs.edit { putString("camera_sensitivity", sensitivity.name) }
    }

    override fun isDynamicRefreshEnabled(): Boolean = prefs.getBoolean("dynamic_refresh", true)

    override fun setDynamicRefreshEnabled(enabled: Boolean) = prefs.edit { putBoolean("dynamic_refresh", enabled) }

    override fun getDestinationStations(): List<StopPlace> {
        val jsonStr = prefs.getString("destination_stations", null) ?: return emptyList()
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
            prefs.edit { putString("destination_stations", json.encodeToString(current)) }
        }
    }

    override fun removeDestinationStation(stopId: String) {
        val current = getDestinationStations().filter { it.id != stopId }
        prefs.edit { putString("destination_stations", json.encodeToString(current)) }
    }

    override fun isFullScreenMode(): Boolean = prefs.getBoolean("full_screen_mode", false)

    override fun setFullScreenMode(enabled: Boolean) {
        prefs.edit { putBoolean("full_screen_mode", enabled) }
        _fullScreenMode.value = enabled
    }

    override fun isAlwaysOnEnabled(): Boolean = prefs.getBoolean("always_on_enabled", false)

    override fun setAlwaysOnEnabled(enabled: Boolean) {
        prefs.edit { putBoolean("always_on_enabled", enabled) }
        _alwaysOnEnabled.value = enabled
    }

    override fun isShowCancelledLines(): Boolean = prefs.getBoolean("show_cancelled_lines", false)

    override fun setShowCancelledLines(show: Boolean) {
        prefs.edit { putBoolean("show_cancelled_lines", show) }
        _showCancelledLines.value = show
    }

    override fun getFilteredLines(): Set<String> = prefs.getStringSet("filtered_lines", emptySet()) ?: emptySet()

    override fun addFilteredLine(line: String) {
        val current = getFilteredLines().toMutableSet()
        if (current.add(line)) {
            prefs.edit { putStringSet("filtered_lines", current) }
            _filteredLines.value = current
        }
    }

    override fun removeFilteredLine(line: String) {
        val current = getFilteredLines().toMutableSet()
        if (current.remove(line)) {
            prefs.edit { putStringSet("filtered_lines", current) }
            _filteredLines.value = current
        }
    }
}
