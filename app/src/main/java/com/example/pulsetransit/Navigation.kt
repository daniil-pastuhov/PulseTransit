package com.example.pulsetransit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.pulsetransit.ui.screens.MainScreen
import com.example.pulsetransit.ui.screens.ModeSelectionScreen
import com.example.pulsetransit.ui.screens.RouteModesScreen
import com.example.pulsetransit.ui.screens.SettingsScreen
import com.example.pulsetransit.ui.viewmodel.AppUpdateViewModel
import com.example.pulsetransit.ui.viewmodel.ModeSelectionViewModel
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainNavigation() {
    // The choice of mode comes first, once; after that the app opens on the board.
    val modeSelection: ModeSelectionViewModel = koinViewModel()
    val modeState by modeSelection.uiState.collectAsState()
    val backStack = rememberNavBackStack(if (modeState.mode == null) ModeSelection else Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry<ModeSelection> {
                    ModeSelectionScreen(
                        onChoose = { mode ->
                            modeSelection.choose(mode)
                            backStack.add(Main)
                            backStack.remove(ModeSelection)
                        },
                    )
                }
                entry<Main> {
                    val viewModel: StopBoardViewModel = koinViewModel()
                    LaunchedEffect(Unit) {
                        viewModel.loadSettings()
                        viewModel.startAutoRefresh()
                    }
                    val updateViewModel: AppUpdateViewModel = koinViewModel()
                    MainScreen(
                        viewModel = viewModel,
                        updateViewModel = updateViewModel,
                        journeyViewModel = koinViewModel(),
                        onNavigateToSettings = { backStack.add(Settings) },
                    )
                }
                entry<Settings> {
                    val viewModel: SettingsViewModel = koinViewModel()
                    LaunchedEffect(Unit) {
                        viewModel.loadSettings()
                    }
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenRouteModes = { backStack.add(RouteModes) },
                    )
                }
                entry<RouteModes> {
                    // Settings re-reads everything when it is shown again, so its summary of these
                    // picks the change up whether or not this is the same ViewModel instance.
                    RouteModesScreen(
                        viewModel = koinViewModel(),
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
    )
}
