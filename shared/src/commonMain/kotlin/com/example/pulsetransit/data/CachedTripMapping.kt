package com.example.pulsetransit.data

import com.example.pulsetransit.data.cache.CachedTrip
import com.example.pulsetransit.data.cache.CachedTripLeg

// How routes are written to and read back from the offline cache.

internal fun TripInfo.toCacheEntity(
    routeKey: String,
    position: Int,
    fetchedAt: Long,
) = CachedTrip(
    routeKey = routeKey,
    position = position,
    destinationName = destinationName,
    expectedStartTime = expectedStartTime,
    expectedStartAtMillis = parseIsoToMillis(expectedStartTime) ?: 0L,
    expectedEndTime = expectedEndTime,
    lineName = lineName,
    durationMinutes = durationMinutes,
    originName = originName,
    legs = legs.map { it.toCacheEntity() },
    fetchedAt = fetchedAt,
)

internal fun CachedTrip.toTripInfo() =
    TripInfo(
        destinationName = destinationName,
        expectedStartTime = expectedStartTime,
        expectedEndTime = expectedEndTime,
        lineName = lineName,
        durationMinutes = durationMinutes,
        originName = originName,
        legs = legs.map { it.toTripLeg() },
    )

internal fun TripLeg.toCacheEntity() =
    CachedTripLeg(
        mode = mode,
        lineName = lineName,
        fromName = fromName,
        durationMinutes = durationMinutes,
        toName = toName,
        expectedStartTime = expectedStartTime,
        expectedEndTime = expectedEndTime,
        aimedStartTime = aimedStartTime,
        distanceMeters = distanceMeters,
        isRealtime = isRealtime,
        platform = platform,
        destination = destination,
        lineFullName = lineFullName,
        authority = authority,
        notices = notices,
        serviceJourneyId = serviceJourneyId,
        serviceDate = serviceDate,
        fromQuayId = fromQuayId,
        toQuayId = toQuayId,
        fromLatitude = fromLatitude,
        fromLongitude = fromLongitude,
        toLatitude = toLatitude,
        toLongitude = toLongitude,
    )

internal fun CachedTripLeg.toTripLeg() =
    TripLeg(
        mode = mode,
        lineName = lineName,
        fromName = fromName,
        durationMinutes = durationMinutes,
        toName = toName,
        expectedStartTime = expectedStartTime,
        expectedEndTime = expectedEndTime,
        aimedStartTime = aimedStartTime,
        distanceMeters = distanceMeters,
        isRealtime = isRealtime,
        platform = platform,
        destination = destination,
        lineFullName = lineFullName,
        authority = authority,
        notices = notices,
        serviceJourneyId = serviceJourneyId,
        serviceDate = serviceDate,
        fromQuayId = fromQuayId,
        toQuayId = toQuayId,
        fromLatitude = fromLatitude,
        fromLongitude = fromLongitude,
        toLatitude = toLatitude,
        toLongitude = toLongitude,
    )
