package com.example.pulsetransit.ui.components

import androidx.compose.runtime.saveable.Saver
import com.example.pulsetransit.data.Departure
import com.example.pulsetransit.data.DepartureGroup
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.data.TripInfo
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/*
 * Savers for what an open sheet shows, so rotating the device (which recreates the activity) or
 * returning after process death reopens it. The data is the network's, already @Serializable, so it
 * is kept as JSON; a value that no longer decodes just leaves the sheet closed.
 */

private val sheetJson = Json { ignoreUnknownKeys = true }

private fun <T : Any> jsonSaver(serializer: KSerializer<T>): Saver<T?, String> =
    Saver(
        save = { value -> value?.let { sheetJson.encodeToString(serializer, it) } },
        restore = { json -> runCatching { sheetJson.decodeFromString(serializer, json) }.getOrNull() },
    )

/** A tapped route with its destination's id. */
internal val DestinationTripSaver: Saver<Pair<String, TripInfo>?, String> =
    jsonSaver(PairSerializer(String.serializer(), TripInfo.serializer()))

internal val TripSaver: Saver<TripInfo?, String> = jsonSaver(TripInfo.serializer())

internal val StopPlaceSaver: Saver<StopPlace?, String> = jsonSaver(StopPlace.serializer())

private val departuresSerializer = ListSerializer(Departure.serializer())

/** A tapped board row, kept as its departures. */
internal val DepartureGroupSaver: Saver<DepartureGroup?, String> =
    Saver(
        save = { group -> group?.let { sheetJson.encodeToString(departuresSerializer, it.departures) } },
        restore = { json -> runCatching { DepartureGroup(sheetJson.decodeFromString(departuresSerializer, json)) }.getOrNull() },
    )

/** A tapped board row with its stop's id. */
internal val StopDepartureGroupSaver: Saver<Pair<String, DepartureGroup>?, String> =
    Saver(
        save = { pair -> pair?.let { (stopId, group) -> sheetJson.encodeToString(stopDeparturesSerializer, stopId to group.departures) } },
        restore = { json ->
            runCatching { sheetJson.decodeFromString(stopDeparturesSerializer, json) }
                .getOrNull()
                ?.let { (stopId, departures) -> stopId to DepartureGroup(departures) }
        },
    )

private val stopDeparturesSerializer = PairSerializer(String.serializer(), departuresSerializer)
