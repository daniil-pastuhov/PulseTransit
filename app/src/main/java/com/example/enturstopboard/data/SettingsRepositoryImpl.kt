package com.example.enturstopboard.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.core.content.edit

class SettingsRepositoryImpl(context: Context) : SettingsRepository {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun getFavorites(): List<StopPlace> {
        val jsonStr = prefs.getString("favorites", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<StopPlace>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun addFavorite(stopPlace: StopPlace) {
        val current = getFavorites().toMutableList()
        if (current.none { it.id == stopPlace.id }) {
            current.add(stopPlace)
            prefs.edit().putString("favorites", json.encodeToString(current)).apply()
        }
    }

    override fun removeFavorite(stopId: String) {
        val current = getFavorites().filter { it.id != stopId }
        prefs.edit().putString("favorites", json.encodeToString(current)).apply()
    }

    override fun reorderFavorites(stopIds: List<String>) {
        val current = getFavorites()
        val reordered = stopIds.mapNotNull { id -> current.find { it.id == id } }
        prefs.edit().putString("favorites", json.encodeToString(reordered)).apply()
    }

    override fun isFavorite(stopId: String): Boolean {
        return getFavorites().any { it.id == stopId }
    }

    override fun getRefreshIntervalSeconds(): Int {
        return prefs.getInt("refresh_interval", 60)
    }

    override fun setRefreshIntervalSeconds(seconds: Int) {
        prefs.edit().putInt("refresh_interval", seconds).apply()
    }

    override fun getThemeMode(): ThemeMode {
        val modeStr = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try {
            ThemeMode.valueOf(modeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    override fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    override fun getBurnInProtectionLevel(): BurnInProtectionLevel {
        val levelStr = prefs.getString("burn_in_protection", BurnInProtectionLevel.MEDIUM.name) 
            ?: BurnInProtectionLevel.MEDIUM.name
        return try {
            BurnInProtectionLevel.valueOf(levelStr)
        } catch (e: Exception) {
            BurnInProtectionLevel.MEDIUM
        }
    }

    override fun setBurnInProtectionLevel(level: BurnInProtectionLevel) {
        prefs.edit().putString("burn_in_protection", level.name).apply()
    }

    override fun getSavedLatitude(): Double? {
        val valStr = prefs.getString("saved_lat", null) ?: return null
        return valStr.toDoubleOrNull()
    }

    override fun getSavedLongitude(): Double? {
        val valStr = prefs.getString("saved_lon", null) ?: return null
        return valStr.toDoubleOrNull()
    }

    override fun setSavedLocation(lat: Double?, lon: Double?) {
        if (lat == null || lon == null) {
            prefs.edit().remove("saved_lat").remove("saved_lon").apply()
        } else {
            prefs.edit()
                .putString("saved_lat", lat.toString())
                .putString("saved_lon", lon.toString())
                .apply()
        }
    }

    override fun isShowDelays(): Boolean = prefs.getBoolean("show_delays", true)
    override fun setShowDelays(show: Boolean) = prefs.edit().putBoolean("show_delays", show).apply()

    override fun isShowMessages(): Boolean = prefs.getBoolean("show_messages", true)
    override fun setShowMessages(show: Boolean) = prefs.edit().putBoolean("show_messages", show).apply()

    override fun getSelectedQuays(stopId: String): Set<String>? {
        return prefs.getStringSet("quays_$stopId", null)
    }

    override fun setSelectedQuays(stopId: String, quayIds: Set<String>?) {
        if (quayIds == null) {
            prefs.edit().remove("quays_$stopId").apply()
        } else {
            prefs.edit().putStringSet("quays_$stopId", quayIds).apply()
        }
    }

    override fun isProximityWakeEnabled(): Boolean = prefs.getBoolean("proximity_wake", false)
    override fun setProximityWakeEnabled(enabled: Boolean) = prefs.edit().putBoolean("proximity_wake", enabled).apply()

    override fun isCameraMotionWakeEnabled(): Boolean = prefs.getBoolean("camera_motion_wake", false)
    override fun setCameraMotionWakeEnabled(enabled: Boolean) = prefs.edit().putBoolean("camera_motion_wake", enabled).apply()

    override fun isDynamicRefreshEnabled(): Boolean = prefs.getBoolean("dynamic_refresh", true)
    override fun setDynamicRefreshEnabled(enabled: Boolean) = prefs.edit().putBoolean("dynamic_refresh", enabled).apply()

    override fun getDestinationStation(): StopPlace? {
        val jsonStr = prefs.getString("destination_station", null) ?: return null
        return try {
            json.decodeFromString<StopPlace>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    override fun setDestinationStation(stopPlace: StopPlace?) {
        if (stopPlace == null) {
            prefs.edit { remove("destination_station") }
        } else {
            prefs.edit { putString("destination_station", json.encodeToString(stopPlace)) }
        }
    }
}
