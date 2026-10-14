package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuaySectionTest {
    private fun quay(
        id: String,
        code: String? = null,
        child: String? = null,
        childName: String? = null,
        description: String? = null,
    ) = Quay(id = id, publicCode = code, description = description, stopPlaceId = child, stopPlaceName = childName)

    @Test
    fun `platforms sort by number - not as text`() {
        val stop =
            StopPlace(
                id = "S",
                name = "Oslo S",
                quays = listOf(quay("a", "16"), quay("b", "2"), quay("c", "10"), quay("d", "1")),
            )

        val sections = quaySections(stop)

        assertEquals(listOf("1", "2", "10", "16"), sections.single().quays.map { it.publicCode })
        assertNull(sections.single().title)
    }

    @Test
    fun `a parent stop is split by child with the one named like the parent first`() {
        val stop =
            StopPlace(
                id = "NSR:StopPlace:59872",
                name = "Oslo S",
                quays =
                    listOf(
                        quay("bus42", "42", "NSR:StopPlace:2", "Oslo S Trelastgata"),
                        quay("rail16", "16", "NSR:StopPlace:337", "Oslo S"),
                        quay("busNoCode", null, "NSR:StopPlace:2", "Oslo S Trelastgata", description = "Foran Hotel Opera"),
                        quay("rail3", "3", "NSR:StopPlace:337", "Oslo S"),
                        quay("bus31", "31", "NSR:StopPlace:2", "Oslo S Trelastgata"),
                    ),
            )

        val sections = quaySections(stop)

        assertEquals(listOf("Oslo S", "Oslo S Trelastgata"), sections.map { it.title })
        assertEquals(listOf("rail3", "rail16"), sections[0].quays.map { it.id })
        assertEquals(listOf("bus31", "bus42", "busNoCode"), sections[1].quays.map { it.id })
    }

    @Test
    fun `natural order compares digit runs by value`() {
        val sorted = listOf("R12", "L2x", "FLY2", "L12", "L2", "RE10", "R2", "F6").sortedWith(::naturalCompare)

        assertEquals(listOf("F6", "FLY2", "L2", "L2x", "L12", "R2", "R12", "RE10"), sorted)
        assertTrue(naturalCompare("02", "2") != 0)
    }
}
