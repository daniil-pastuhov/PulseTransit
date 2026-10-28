package com.example.pulsetransit.data.journey

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Show journey" from a notification, waiting for the board to open the followed journey's
 * details. Set by the platform when the app is opened from an alert, cleared by the board.
 */
class JourneyOpenRequests {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    fun request() {
        _pending.value = true
    }

    fun consume() {
        _pending.value = false
    }
}
