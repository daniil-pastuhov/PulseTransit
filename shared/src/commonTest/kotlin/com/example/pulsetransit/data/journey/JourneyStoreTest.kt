package com.example.pulsetransit.data.journey

import com.example.pulsetransit.data.journey.JourneyFixtures.journey
import com.example.pulsetransit.data.journey.JourneyFixtures.liveStops
import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JourneyStoreTest {
    private val settings = MapSettings()
    private val store = JourneyStoreImpl(settings)

    @Test
    fun `the active journey survives a restart`() {
        val followed = journey(JourneyTrackingMode.LOCATION, legStops = liveStops(), fired = setOf("walk-0"))
        store.start(followed)

        assertEquals(followed, JourneyStoreImpl(settings).active.value)
    }

    @Test
    fun `updates apply only to the journey they name`() {
        store.start(journey())
        store.update("another") { it.copy(muted = true) }
        assertEquals(false, store.active.value?.muted)

        store.update(journey().id) { it.copy(muted = true) }
        assertEquals(true, store.active.value?.muted)
    }

    @Test
    fun `finishing keeps the journey in history`() {
        store.start(journey())
        store.finish(endedAtMillis = 42)

        assertNull(store.active.value)
        assertNull(JourneyStoreImpl(settings).active.value)
        assertEquals(42L, store.history().single().endedAtMillis)
    }

    @Test
    fun `starting another journey ends the one before`() {
        store.start(journey())
        val next = journey().copy(id = "other", startedAtMillis = 7)
        store.start(next)

        assertEquals("other", store.active.value?.id)
        assertEquals(7L, store.history().single().endedAtMillis)
    }

    @Test
    fun `history keeps the latest few newest first without repeats`() {
        repeat(JourneyStore.HISTORY_SIZE + 2) { n ->
            store.start(journey().copy(id = "j$n"))
            store.finish(endedAtMillis = n.toLong())
        }
        store.start(journey().copy(id = "j6"))
        store.finish(endedAtMillis = 100)

        val history = store.history()
        assertEquals(JourneyStore.HISTORY_SIZE, history.size)
        assertEquals(listOf("j6", "j5", "j4", "j3", "j2"), history.map { it.id })
    }

    @Test
    fun `the preferred mode is unset until chosen`() {
        assertNull(store.preferredMode())

        store.setPreferredMode(JourneyTrackingMode.LOCATION)
        assertEquals(JourneyTrackingMode.LOCATION, JourneyStoreImpl(settings).preferredMode())
    }

    @Test
    fun `a corrupt saved journey reads as none`() {
        settings.putString("journey_active_json", "not json")
        settings.putString("journey_history_json", "[")

        val store = JourneyStoreImpl(settings)
        assertNull(store.active.value)
        assertTrue(store.history().isEmpty())
    }
}
