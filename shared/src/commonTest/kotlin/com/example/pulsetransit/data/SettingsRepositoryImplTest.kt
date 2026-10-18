package com.example.pulsetransit.data

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The parts of the settings store that are more than a plain key lookup. */
class SettingsRepositoryImplTest {
    private val store = MapSettings()
    private val repo = SettingsRepositoryImpl(store)

    @Test
    fun `filtered lines round-trip and reach the flow`() {
        repo.addFilteredLine("L1")
        repo.addFilteredLine("31")
        repo.removeFilteredLine("L1")

        assertEquals(setOf("31"), repo.getFilteredLines())
        assertEquals(setOf("31"), SettingsRepositoryImpl(store).getFilteredLines())
    }

    @Test
    fun `selected quays distinguish unset from an explicit choice`() {
        val stop = "NSR:StopPlace:1"
        assertNull(repo.getSelectedQuays(stop), message = "nothing chosen means all quays")

        repo.setSelectedQuays(stop, setOf("NSR:Quay:1", "NSR:Quay:2"))
        assertEquals(setOf("NSR:Quay:1", "NSR:Quay:2"), repo.getSelectedQuays(stop))

        repo.setSelectedQuays(stop, null)
        assertNull(repo.getSelectedQuays(stop))
    }

    @Test
    fun `a corrupt stored set reads as unset rather than throwing`() {
        store.putString(SettingsRepositoryImpl.Keys.FILTERED_LINES, "not json")

        assertEquals(emptySet<String>(), repo.getFilteredLines())
    }

    @Test
    fun `saved location is cleared when either coordinate is missing`() {
        repo.setSavedLocation(59.91, 10.75)
        assertEquals(59.91, repo.getSavedLatitude()!!, absoluteTolerance = 0.0)

        repo.setSavedLocation(59.91, null)
        assertNull(repo.getSavedLatitude())
        assertNull(repo.getSavedLongitude())
    }

    @Test
    fun `a fresh install has no mode until one is chosen`() {
        assertNull(repo.getAppMode())

        repo.setAppMode(AppMode.ON_SPOT)

        assertEquals(AppMode.ON_SPOT, SettingsRepositoryImpl(store).getAppMode())
    }

    @Test
    fun `an install from before modes with stops set up is a board`() {
        repo.addDestinationStation(StopPlace(id = "NSR:StopPlace:1", name = "Oslo S"))

        assertEquals(AppMode.BOARD, SettingsRepositoryImpl(store).getAppMode())
    }

    @Test
    fun `route alternatives default to auto and stay within range`() {
        assertEquals(SettingsRepository.TRIP_OPTIONS_AUTO, repo.getTripOptionsCount())

        repo.setTripOptionsCount(3)
        assertEquals(3, repo.getTripOptionsCount())

        repo.setTripOptionsCount(99)
        assertEquals(SettingsRepository.MAX_TRIP_OPTIONS, repo.getTripOptionsCount())
    }

    @Test
    fun `the on-spot split survives a relaunch and stays within range`() {
        assertEquals(OnSpotSplit.DEFAULT, repo.getOnSpotSplit())

        repo.setOnSpotSplit(OnSpotSplit.ROUTES_COLLAPSED)
        assertEquals(OnSpotSplit.ROUTES_COLLAPSED, SettingsRepositoryImpl(store).getOnSpotSplit())

        repo.setOnSpotSplit(0.4f)
        assertEquals(0.4f, SettingsRepositoryImpl(store).getOnSpotSplit())

        repo.setOnSpotSplit(7f)
        assertEquals(OnSpotSplit.STOP_COLLAPSED, repo.getOnSpotSplit())
    }

    @Test
    fun `route options round-trip and default to Entur's own choice`() {
        assertEquals(RoutePreference.FASTEST, repo.getRoutePreference())
        assertEquals(WalkingSpeed.NORMAL, repo.getWalkingSpeed())
        assertEquals(emptySet(), repo.getExcludedRouteModes())

        repo.setRoutePreference(RoutePreference.LESS_WALKING)
        repo.setWalkingSpeed(WalkingSpeed.FAST)
        repo.setExcludedRouteModes(setOf(RouteTransportMode.EXPRESS_TRAIN, RouteTransportMode.PLANE))

        val reread = SettingsRepositoryImpl(store)
        assertEquals(RoutePreference.LESS_WALKING, reread.getRoutePreference())
        assertEquals(WalkingSpeed.FAST, reread.getWalkingSpeed())
        assertEquals(setOf(RouteTransportMode.EXPRESS_TRAIN, RouteTransportMode.PLANE), reread.getExcludedRouteModes())
    }

    @Test
    fun `excluded route modes never exclude every kind or keep unknown names`() {
        store.putString(SettingsRepositoryImpl.Keys.EXCLUDED_ROUTE_MODES, """["EXPRESS_BUS","HOVERCRAFT"]""")
        assertEquals(setOf(RouteTransportMode.EXPRESS_BUS), repo.getExcludedRouteModes())

        repo.setExcludedRouteModes(RouteTransportMode.entries.toSet())
        assertEquals(emptySet(), repo.getExcludedRouteModes())
    }
}
