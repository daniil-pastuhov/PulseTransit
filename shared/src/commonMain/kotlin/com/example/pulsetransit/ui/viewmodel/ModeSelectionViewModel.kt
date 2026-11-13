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

/** Which first-launch screen shows, if any. */
enum class OnboardingScreen {
    /** The choice of mode: on a fresh install, or again from the intro's first page. */
    MODE,

    /** The intro to the chosen mode. */
    INTRO,

    /** On the spot without location access: why it is needed, before the system prompt. */
    LOCATION,

    /** Onboarding is over; the board shows. */
    NONE,
}

/**
 * The first-launch choice between [AppMode]s, which both apps show instead of the board until it
 * is made, the short intro to the chosen mode that follows it, and, for on the spot, the ask for
 * location access after that.
 */
class ModeSelectionViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /**
     * [mode] is null until the user has chosen. [intro] holds the pages to show before the board;
     * it is empty once the intro is finished or skipped, and on installs that chose a mode before
     * there was one. [choosingAgain] is set while the user went back from the intro to the choice,
     * which then shows [mode] as selected. [asksLocation] is set after the on-spot intro when the
     * app has no location access.
     */
    data class UiState(
        val mode: AppMode? = null,
        val intro: List<IntroPage> = emptyList(),
        val choosingAgain: Boolean = false,
        val asksLocation: Boolean = false,
    ) {
        val showsIntro: Boolean get() = intro.isNotEmpty()

        val screen: OnboardingScreen
            get() =
                when {
                    mode == null || choosingAgain -> OnboardingScreen.MODE
                    showsIntro -> OnboardingScreen.INTRO
                    asksLocation -> OnboardingScreen.LOCATION
                    else -> OnboardingScreen.NONE
                }
    }

    private val introPending = MutableStateFlow(settingsRepository.isIntroPending())

    /** What only lasts while the app runs: going back to the choice, and the location ask. */
    private val session = MutableStateFlow(Session())

    private data class Session(
        val choosingAgain: Boolean = false,
        val asksLocation: Boolean = false,
    )

    val uiState: StateFlow<UiState> =
        combine(settingsRepository.getAppModeFlow(), introPending, session, ::state)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                state(settingsRepository.getAppMode(), introPending.value, session.value),
            )

    fun choose(mode: AppMode) {
        // Pending before the mode is set, so the board never shows for a moment in between.
        settingsRepository.setIntroPending(true)
        introPending.value = true
        session.value = Session()
        settingsRepository.setAppMode(mode)
    }

    /** Back from the intro's first page: the choice again, with the current mode selected. */
    fun chooseAgain() {
        if (settingsRepository.getAppMode() != null) session.value = session.value.copy(choosingAgain = true)
    }

    /**
     * Ends the intro, whether it was read to the end or skipped. On the spot then asks for
     * location access first, unless [locationGranted].
     */
    fun finishIntro(locationGranted: Boolean) {
        settingsRepository.setIntroPending(false)
        introPending.value = false
        val asks = settingsRepository.getAppMode() == AppMode.ON_SPOT && !locationGranted
        session.value = session.value.copy(asksLocation = asks)
    }

    /** Leaves the location ask for the board, granted or not; the board says what is missing. */
    fun finishLocation() {
        session.value = session.value.copy(asksLocation = false)
    }

    private fun state(
        mode: AppMode?,
        pending: Boolean,
        session: Session,
    ) = UiState(
        mode = mode,
        intro = if (mode != null && pending) introPages(mode) else emptyList(),
        choosingAgain = mode != null && session.choosingAgain,
        asksLocation = mode == AppMode.ON_SPOT && session.asksLocation,
    )
}
