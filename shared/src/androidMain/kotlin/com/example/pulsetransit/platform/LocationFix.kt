package com.example.pulsetransit.platform

import android.location.Location

/** A platform [Location] as a [LocationResult.Fix], with its time, accuracy and speed when it has them. */
internal fun Location.toFix() =
    LocationResult.Fix(
        latitude = latitude,
        longitude = longitude,
        timeMillis = time.takeIf { it > 0 },
        accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
        speedMetersPerSecond = if (hasSpeed()) speed.toDouble() else null,
    )
