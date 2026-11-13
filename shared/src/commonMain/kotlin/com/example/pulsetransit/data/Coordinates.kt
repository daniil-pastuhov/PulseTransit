package com.example.pulsetransit.data

/** What is wrong with a typed coordinate, for the field's own error message (T1). */
enum class CoordinateProblem {
    /** Left empty. */
    MISSING,

    /** Not a number, e.g. "north" or "59.9.1". */
    NOT_A_NUMBER,

    /** A number, but no latitude (beyond ±90) or longitude (beyond ±180) is that far. */
    OUT_OF_RANGE,
}

/** A coordinate pair typed by hand: both values, or what is wrong with each field. */
sealed interface TypedCoordinates {
    data class Valid(
        val latitude: Double,
        val longitude: Double,
    ) : TypedCoordinates

    /** At least one of the two is set. */
    data class Invalid(
        val latitude: CoordinateProblem?,
        val longitude: CoordinateProblem?,
    ) : TypedCoordinates
}

private const val MAX_LATITUDE = 90.0
private const val MAX_LONGITUDE = 180.0

/**
 * Reads coordinates typed in Settings. A decimal comma counts as a point, as Norwegian keyboards
 * type one ("59,9111"), and spaces around the number are ignored.
 */
fun parseTypedCoordinates(
    latitude: String,
    longitude: String,
): TypedCoordinates {
    val lat = parseCoordinate(latitude, MAX_LATITUDE)
    val lon = parseCoordinate(longitude, MAX_LONGITUDE)
    val latValue = lat.getOrNull()
    val lonValue = lon.getOrNull()
    return if (latValue != null && lonValue != null) {
        TypedCoordinates.Valid(latValue, lonValue)
    } else {
        TypedCoordinates.Invalid(lat.exceptionOrNull()?.problem(), lon.exceptionOrNull()?.problem())
    }
}

private class CoordinateException(
    val problem: CoordinateProblem,
) : Exception()

private fun Throwable.problem(): CoordinateProblem? = (this as? CoordinateException)?.problem

private fun parseCoordinate(
    text: String,
    limit: Double,
): Result<Double> {
    val trimmed = text.trim()
    val value = trimmed.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    val problem =
        when {
            trimmed.isEmpty() -> CoordinateProblem.MISSING
            value == null -> CoordinateProblem.NOT_A_NUMBER
            value < -limit || value > limit -> CoordinateProblem.OUT_OF_RANGE
            else -> null
        }
    return if (problem == null && value != null) Result.success(value) else Result.failure(CoordinateException(problem ?: CoordinateProblem.NOT_A_NUMBER))
}
