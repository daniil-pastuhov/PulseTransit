package com.example.pulsetransit.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun getFavorites(): List<StopPlace>
    fun addFavorite(stopPlace: StopPlace)
    fun removeFavorite(stopId: String)
    fun reorderFavorites(stopIds: List<String>)
    fun isFavorite(stopId: String): Boolean
    
    fun getRefreshIntervalSeconds(): Int
    fun setRefreshIntervalSeconds(seconds: Int)
    
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    
    fun getBurnInProtectionLevel(): BurnInProtectionLevel
    fun setBurnInProtectionLevel(level: BurnInProtectionLevel)
    
    fun getSavedLatitude(): Double?
    fun getSavedLongitude(): Double?
    fun setSavedLocation(lat: Double?, lon: Double?)

    // New settings
    fun isShowDelays(): Boolean
    fun setShowDelays(show: Boolean)
    fun getShowDelaysFlow(): Flow<Boolean>

    fun isShowMessages(): Boolean
    fun setShowMessages(show: Boolean)
    fun getShowMessagesFlow(): Flow<Boolean>

    fun getSelectedQuays(stopId: String): Set<String>?
    fun setSelectedQuays(stopId: String, quayIds: Set<String>?)

    fun isProximityWakeEnabled(): Boolean
    fun setProximityWakeEnabled(enabled: Boolean)

    fun isCameraMotionWakeEnabled(): Boolean
    fun setCameraMotionWakeEnabled(enabled: Boolean)

    fun isDynamicRefreshEnabled(): Boolean
    fun setDynamicRefreshEnabled(enabled: Boolean)

    fun getDestinationStation(): StopPlace?
    fun setDestinationStation(stopPlace: StopPlace?)

    fun isFullScreenMode(): Boolean
    fun setFullScreenMode(enabled: Boolean)
    fun getFullScreenModeFlow(): Flow<Boolean>

    fun isAlwaysOnEnabled(): Boolean
    fun setAlwaysOnEnabled(enabled: Boolean)
    fun getAlwaysOnEnabledFlow(): Flow<Boolean>

    fun isShowCancelledLines(): Boolean
    fun setShowCancelledLines(show: Boolean)
    fun getShowCancelledLinesFlow(): Flow<Boolean>

    fun getFilteredLines(): Set<String>
    fun addFilteredLine(line: String)
    fun removeFilteredLine(line: String)
    fun getFilteredLinesFlow(): Flow<Set<String>>
}
