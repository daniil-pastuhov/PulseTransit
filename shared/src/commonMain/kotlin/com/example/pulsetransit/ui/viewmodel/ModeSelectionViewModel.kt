package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The first-launch choice between [AppMode]s, which both apps show instead of the board until it is made. */
class ModeSelectionViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /** [mode] is null until the user has chosen. */
    data class UiState(
        val mode: AppMode? = null,
    )

    val uiState: StateFlow<UiState> =
        settingsRepository
            .getAppModeFlow()
            .map { UiState(it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UiState(settingsRepository.getAppMode()))

    fun choose(mode: AppMode) {
        settingsRepository.setAppMode(mode)
    }
}
