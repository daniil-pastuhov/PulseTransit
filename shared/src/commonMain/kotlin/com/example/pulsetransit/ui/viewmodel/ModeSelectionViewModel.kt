package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.IntroPage
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.introPages
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The first-launch choice between [AppMode]s, which both apps show instead of the board until it
 * is made, and the short intro to the chosen mode that follows it.
 */
class ModeSelectionViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /**
     * [mode] is null until the user has chosen. [intro] holds the pages to show before the board;
     * it is empty once the intro is finished or skipped, and on installs that chose a mode before
     * there was one.
     */
    data class UiState(
        val mode: AppMode? = null,
        val intro: List<IntroPage> = emptyList(),
    ) {
        val showsIntro: Boolean get() = intro.isNotEmpty()
    }

    private val introPending = MutableStateFlow(settingsRepository.isIntroPending())

    val uiState: StateFlow<UiState> =
        combine(settingsRepository.getAppModeFlow(), introPending, ::state)
            .stateIn(viewModelScope, SharingStarted.Eagerly, state(settingsRepository.getAppMode(), introPending.value))

    fun choose(mode: AppMode) {
        // Pending before the mode is set, so the board never shows for a moment in between.
        settingsRepository.setIntroPending(true)
        introPending.value = true
        settingsRepository.setAppMode(mode)
    }

    /** Ends the intro, whether it was read to the end or skipped. */
    fun finishIntro() {
        settingsRepository.setIntroPending(false)
        introPending.value = false
    }

    private fun state(
        mode: AppMode?,
        pending: Boolean,
    ) = UiState(mode = mode, intro = if (mode != null && pending) introPages(mode) else emptyList())
}
