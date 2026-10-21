package com.example.pulsetransit.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArrivalRadiusTest {
    private val place = StopPlace(id = "NSR:StopPlace:1", name = "Jernbanetorget", latitude = 59.9115, longitude = 10.7505)

    // A degree of latitude is about 111 km, so 0.0018 is about 200 m.
    @Test
    fun `an origin on the spot or a block away has arrived`() {
        assertTrue(place.isArrivedFrom(59.9115, 10.7505))
        assertTrue(place.isArrivedFrom(59.9115 + 0.0015, 10.7505))
    }

    @Test
    fun `an origin a few hundred metres away has not`() {
        assertFalse(place.isArrivedFrom(59.9115 + 0.0019, 10.7505))
        assertFalse(place.isArrivedFrom(59.9115 + 0.005, 10.7505))
    }

    @Test
    fun `a place without coordinates is never arrived at`() {
        assertFalse(place.copy(latitude = null).isArrivedFrom(59.9115, 10.7505))
        assertFalse(place.copy(longitude = null).isArrivedFrom(59.9115, 10.7505))
    }
}
