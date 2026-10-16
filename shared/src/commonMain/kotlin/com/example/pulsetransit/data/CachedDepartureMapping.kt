package com.example.pulsetransit.data

import com.example.pulsetransit.data.cache.CachedDeparture

// How departures are written to and read back from the offline cache.

internal fun Departure.toCacheEntity(
    stopId: String,
    position: Int,
    fetchedAt: Long,
) = CachedDeparture(
    stopId = stopId,
    position = position,
    line = line,
    destination = destination,
    expectedTime = expectedTime,
    aimedTime = aimedTime,
    expectedAtMillis = parseIsoToMillis(expectedTime) ?: 0L,
    isRealtime = isRealtime,
    isCancelled = isCancelled,
    transportMode = transportMode,
    delayMinutes = delayMinutes,
    notices = notices,
    quayId = quayId,
    platform = platform,
    lineName = lineName,
    authority = authority,
    operator = operator,
    serviceJourneyId = serviceJourneyId,
    serviceDate = serviceDate,
    fetchedAt = fetchedAt,
)

internal fun CachedDeparture.toDeparture() =
    Departure(
        line = line,
        destination = destination,
        expectedTime = expectedTime,
        aimedTime = aimedTime,
        isRealtime = isRealtime,
        isCancelled = isCancelled,
        transportMode = transportMode,
        delayMinutes = delayMinutes,
        notices = notices,
        quayId = quayId,
        platform = platform,
        lineName = lineName,
        authority = authority,
        operator = operator,
        serviceJourneyId = serviceJourneyId,
        serviceDate = serviceDate,
    )
