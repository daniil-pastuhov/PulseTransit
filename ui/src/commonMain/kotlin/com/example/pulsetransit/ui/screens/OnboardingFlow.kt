package com.example.pulsetransit.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.ui.platform.AppPermission
import com.example.pulsetransit.ui.platform.rememberPermissionGranted
import com.example.pulsetransit.ui.viewmodel.ModeSelectionViewModel
import com.example.pulsetransit.ui.viewmodel.OnboardingScreen

/**
 * First launch, as [ModeSelectionViewModel] steps through it: the choice of mode, its intro and,
 * for on the spot without location access, the ask for it. Shows nothing once it is over
 * ([OnboardingScreen.NONE]); the caller then shows the board.
 */
@Composable
fun OnboardingFlow(
    viewModel: ModeSelectionViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val locationGranted = rememberPermissionGranted(AppPermission.LOCATION)
    when (state.screen) {
        OnboardingScreen.MODE ->
            ModeSelectionScreen(
                onChoose = viewModel::choose,
                selected = state.mode.takeIf { state.choosingAgain },
                modifier = modifier,
            )
        // Keyed on the mode, so another choice starts its intro on the first page.
        OnboardingScreen.INTRO ->
            key(state.mode) {
                IntroScreen(
                    pages = state.intro,
                    onFinish = { viewModel.finishIntro(locationGranted) },
                    onBack = viewModel::chooseAgain,
                    modifier = modifier,
                )
            }
        OnboardingScreen.LOCATION ->
            LocationAccessScreen(
                onDone = viewModel::finishLocation,
                onUseBoard = { viewModel.choose(AppMode.BOARD) },
                modifier = modifier,
            )
        OnboardingScreen.NONE -> Unit
    }
}
