package com.example.pulsetransit.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    companion object {
        const val MAX_DESTINATION_STATIONS = 5

        /** [getTripOptionsCount] value meaning "as many as fit the screen". */
        const val TRIP_OPTIONS_AUTO = 0
        const val MAX_TRIP_OPTIONS = 5
    }

    /**
     * The chosen mode, or null if the user has not picked one yet. Installs from before modes
     * existed that already have stops or destinations count as [AppMode.BOARD].
     */
    fun getAppMode(): AppMode?

    fun setAppMode(mode: AppMode)

    fun getAppModeFlow(): Flow<AppMode?>

    /** Route alternatives per destination in on-spot mode: [TRIP_OPTIONS_AUTO] or 1..[MAX_TRIP_OPTIONS]. */
    fun getTripOptionsCount(): Int

    fun setTripOptionsCount(count: Int)

    /** The on-spot board's split between routes and the nearby stop; see [OnSpotSplit]. */
    fun getOnSpotSplit(): Float

    fun setOnSpotSplit(split: Float)

    /** How routes to the destinations are ranked. */
    fun getRoutePreference(): RoutePreference

    fun setRoutePreference(preference: RoutePreference)

    fun getWalkingSpeed(): WalkingSpeed

    fun setWalkingSpeed(speed: WalkingSpeed)

    /** Transit kinds routes to the destinations may not use; empty allows all. */
    fun getExcludedRouteModes(): Set<RouteTransportMode>

    fun setExcludedRouteModes(modes: Set<RouteTransportMode>)

    fun getFavorites(): List<StopPlace>

    fun addFavorite(stopPlace: StopPlace)

    fun removeFavorite(stopId: String)

    fun reorderFavorites(stopIds: List<String>)

    fun isFavorite(stopId: String): Boolean

    fun getRefreshIntervalSeconds(): Int

    fun setRefreshIntervalSeconds(seconds: Int)

    fun getAutoDimSeconds(): Int

    fun setAutoDimSeconds(seconds: Int)

    fun getAutoDimSecondsFlow(): Flow<Int>

    fun getThemeMode(): ThemeMode

    fun setThemeMode(mode: ThemeMode)

    fun getAutoThemeDarkLux(): Float

    fun setAutoThemeDarkLux(lux: Float)

    fun getAutoThemeDarkLuxFlow(): Flow<Float>

    fun getAutoThemeLightLux(): Float

    fun setAutoThemeLightLux(lux: Float)

    fun getAutoThemeLightLuxFlow(): Flow<Float>

    fun getBurnInProtectionLevel(): BurnInProtectionLevel

    fun setBurnInProtectionLevel(level: BurnInProtectionLevel)

    fun getDateDisplayStyle(): DateDisplayStyle

    fun setDateDisplayStyle(style: DateDisplayStyle)

    fun getSavedLatitude(): Double?

    fun getSavedLongitude(): Double?

    fun setSavedLocation(
        lat: Double?,
        lon: Double?,
    )

    // New settings
    fun isShowDelays(): Boolean

    fun setShowDelays(show: Boolean)

    fun getShowDelaysFlow(): Flow<Boolean>

    fun isShowMessages(): Boolean

    fun setShowMessages(show: Boolean)

    fun getShowMessagesFlow(): Flow<Boolean>

    fun getSelectedQuays(stopId: String): Set<String>?

    fun setSelectedQuays(
        stopId: String,
        quayIds: Set<String>?,
    )

    fun isCameraMotionWakeEnabled(): Boolean

    fun setCameraMotionWakeEnabled(enabled: Boolean)

    fun getCameraMotionWakeFlow(): Flow<Boolean>

    fun getCameraSensitivity(): CameraSensitivity

    fun setCameraSensitivity(sensitivity: CameraSensitivity)

    fun isDynamicRefreshEnabled(): Boolean

    fun setDynamicRefreshEnabled(enabled: Boolean)

    fun getDestinationStations(): List<StopPlace>

    fun addDestinationStation(stopPlace: StopPlace)

    fun removeDestinationStation(stopId: String)

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

    /** The store version whose "new version" notice the user closed, so it isn't shown again. */
    fun getDismissedUpdateVersion(): String?

    fun setDismissedUpdateVersion(version: String)
}
