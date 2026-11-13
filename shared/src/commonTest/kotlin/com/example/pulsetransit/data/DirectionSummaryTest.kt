package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DirectionSummaryTest {
    private fun quay(
        id: String,
        code: String?,
    ) = Quay(id = id, publicCode = code)

    private val quays = listOf(quay("q1", "1"), quay("q2", "2"), quay("q3", "3"), quay("q4", "4"), quay("qa", null))

    @Test
    fun `no selection shows all directions`() {
        assertEquals(DirectionSummary.All, directionSummary(null, quays))
        assertEquals(DirectionSummary.All, directionSummary(emptySet(), quays))
    }

    @Test
    fun `selecting every quay is all directions too`() {
        assertEquals(DirectionSummary.All, directionSummary(quays.map { it.id }.toSet(), quays))
    }

    @Test
    fun `a consecutive run of numbered platforms reads as a range`() {
        assertEquals(DirectionSummary.Platforms("2–4", 3), directionSummary(setOf("q4", "q2", "q3"), quays))
    }

    @Test
    fun `two numbered platforms are listed rather than ranged`() {
        assertEquals(DirectionSummary.Platforms("1, 2", 2), directionSummary(setOf("q2", "q1"), quays))
    }

    @Test
    fun `gaps in the numbers are listed in natural order`() {
        val many = (1..12).map { quay("p$it", "$it") }
        assertEquals(DirectionSummary.Platforms("2, 10", 2), directionSummary(setOf("p10", "p2"), many))
    }

    @Test
    fun `a quay without a public code makes it a count of the total`() {
        assertEquals(DirectionSummary.Some(2, 5), directionSummary(setOf("q1", "qa"), quays))
    }

    @Test
    fun `unknown quays give a count without a total`() {
        assertEquals(DirectionSummary.Some(3, null), directionSummary(setOf("x", "y", "z"), emptyList()))
    }

    @Test
    fun `lettered platforms are listed`() {
        val lettered = listOf(quay("a", "A"), quay("b", "B"), quay("c", "C"))
        assertEquals(DirectionSummary.Platforms("A, C", 2), directionSummary(setOf("c", "a"), lettered))
    }

    @Test
    fun `numbers with a gap are listed even when there are three`() {
        val many = (1..5).map { quay("p$it", "$it") }
        assertEquals(DirectionSummary.Platforms("1, 3, 4", 3), directionSummary(setOf("p1", "p3", "p4"), many))
    }

    @Test
    fun `numbers mixed with letters are listed`() {
        val mixed = listOf(quay("p1", "1"), quay("p2", "2"), quay("p3", "3"), quay("pa", "A"), quay("pb", "B"))
        assertEquals(DirectionSummary.Platforms("1, 2, 3, A", 4), directionSummary(setOf("p1", "p2", "p3", "pa"), mixed))
    }

    @Test
    fun `a blank public code counts as no code`() {
        val blank = listOf(quay("p1", "1"), quay("p2", " "), quay("p3", "3"))
        assertEquals(DirectionSummary.Some(2, 3), directionSummary(setOf("p1", "p2"), blank))
    }

    @Test
    fun `two quays sharing a code show it once`() {
        val shared = listOf(quay("a1", "A"), quay("a2", "A"), quay("b", "B"))
        assertEquals(DirectionSummary.Platforms("A", 2), directionSummary(setOf("a1", "a2"), shared))
    }
}
