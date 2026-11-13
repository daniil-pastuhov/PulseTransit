package com.example.pulsetransit

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object Settings : NavKey

/** Settings' "Travel by": the kinds of transport route searches may use. */
@Serializable data object RouteModes : NavKey

/** Settings' journey alerts: how a followed journey is tracked and which alerts it sends. */
@Serializable data object JourneyAlerts : NavKey

/** Settings' data sources: the transport companies whose timetables reach Entur. */
@Serializable data object DataSources : NavKey

/**
 * First launch: the choice of [com.example.pulsetransit.data.AppMode], the intro to it and, for on
 * the spot, the ask for location, stepped through by `OnboardingFlow`.
 */
@Serializable data object Onboarding : NavKey
