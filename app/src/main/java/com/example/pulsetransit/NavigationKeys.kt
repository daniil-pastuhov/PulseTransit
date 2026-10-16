package com.example.pulsetransit

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data object Settings : NavKey

/** The first-launch choice of [com.example.pulsetransit.data.AppMode]. */
@Serializable data object ModeSelection : NavKey
