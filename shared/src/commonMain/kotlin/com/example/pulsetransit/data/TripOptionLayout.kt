package com.example.pulsetransit.data

/**
 * How many route alternatives to show per destination when the setting is
 * [SettingsRepository.TRIP_OPTIONS_AUTO]: as many rows as fit [availableHeight] once every
 * destination has its [headerHeight], shared out evenly, clamped to 1..[SettingsRepository.MAX_TRIP_OPTIONS].
 *
 * Heights are in any one unit (dp on Android, points on iOS). Both apps call this so they agree.
 */
fun autoTripOptionCount(
    availableHeight: Float,
    destinationCount: Int,
    rowHeight: Float,
    headerHeight: Float,
): Int {
    if (destinationCount <= 0 || rowHeight <= 0f) return 1
    val perDestination = availableHeight / destinationCount - headerHeight
    return (perDestination / rowHeight).toInt().coerceIn(1, SettingsRepository.MAX_TRIP_OPTIONS)
}

/** The number of alternatives to show: the setting if fixed, else [autoCount]. */
fun visibleTripOptionCount(
    setting: Int,
    autoCount: Int,
): Int = if (setting == SettingsRepository.TRIP_OPTIONS_AUTO) autoCount else setting
