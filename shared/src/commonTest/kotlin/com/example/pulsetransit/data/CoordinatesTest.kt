package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Coordinates typed by hand in Settings. */
class CoordinatesTest {
    @Test
    fun `a point or a comma both read as decimals`() {
        assertEquals(TypedCoordinates.Valid(59.9111, 10.7528), parseTypedCoordinates("59.9111", "10.7528"))
        assertEquals(TypedCoordinates.Valid(59.9111, 10.7528), parseTypedCoordinates("59,9111", " 10,7528 "))
    }

    @Test
    fun `the edges of the globe are allowed`() {
        assertEquals(TypedCoordinates.Valid(-90.0, 180.0), parseTypedCoordinates("-90", "180"))
        assertEquals(TypedCoordinates.Valid(90.0, -180.0), parseTypedCoordinates("90", "-180"))
    }

    @Test
    fun `each field says what is wrong with it`() {
        assertEquals(
            TypedCoordinates.Invalid(CoordinateProblem.NOT_A_NUMBER, null),
            parseTypedCoordinates("north", "10.7"),
        )
        assertEquals(
            TypedCoordinates.Invalid(null, CoordinateProblem.MISSING),
            parseTypedCoordinates("59.9", "  "),
        )
        assertEquals(
            TypedCoordinates.Invalid(CoordinateProblem.OUT_OF_RANGE, CoordinateProblem.OUT_OF_RANGE),
            parseTypedCoordinates("90.5", "-180.01"),
        )
    }

    @Test
    fun `a number with two separators is not a number`() {
        assertEquals(
            TypedCoordinates.Invalid(CoordinateProblem.NOT_A_NUMBER, null),
            parseTypedCoordinates("59,9.1", "10.7"),
        )
    }

    @Test
    fun `infinity is not a coordinate`() {
        assertEquals(
            TypedCoordinates.Invalid(CoordinateProblem.NOT_A_NUMBER, null),
            parseTypedCoordinates("Infinity", "10.7"),
        )
    }
}
