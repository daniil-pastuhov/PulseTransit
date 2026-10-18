package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.platform.AppUpdate
import com.example.pulsetransit.platform.AppUpdateChecker
import com.example.pulsetransit.platform.ConnectivityMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.hours

/**
 * Tells the board when the store has a newer version of the app, for its "new version" notice.
 *
 * A wall display can run for weeks without being reopened, so the store is asked again every
 * [CHECK_INTERVAL] rather than only at launch, and as soon as the connection comes back. Closing
 * the notice hides that version for good; a later one shows it again.
 */
class AppUpdateViewModel(
    private val checker: AppUpdateChecker,
    private val settingsRepository: SettingsRepository,
    connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {
    companion object {
        val CHECK_INTERVAL = 6.hours
    }

    data class UiState(
        /** The update to offer, or null when there is none or the user closed its notice. */
        val update: AppUpdate? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // The periodic check and a reconnect can coincide; one store request at a time is plenty.
    private val checking = Mutex()

    init {
        viewModelScope.launch {
            while (true) {
                check()
                delay(CHECK_INTERVAL)
            }
        }

        var wasOnline = true
        connectivityMonitor
            .isOnlineFlow()
            .onEach { online ->
                if (online && !wasOnline) check()
                wasOnline = online
            }.launchIn(viewModelScope)
    }

    /** Hides the notice for the offered version; a newer one will show it again. */
    fun dismiss() {
        val update = _uiState.value.update ?: return
        settingsRepository.setDismissedUpdateVersion(update.version)
        _uiState.value = UiState()
    }

    private suspend fun check() =
        checking.withLock {
            val update = checker.availableUpdate()
            _uiState.value = UiState(update?.takeUnless { it.version == settingsRepository.getDismissedUpdateVersion() })
        }
}
