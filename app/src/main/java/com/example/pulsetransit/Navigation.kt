package com.example.pulsetransit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.pulsetransit.ui.screens.AddStopScreen
import com.example.pulsetransit.ui.screens.DataSourcesScreen
import com.example.pulsetransit.ui.screens.JourneyAlertsScreen
import com.example.pulsetransit.ui.screens.MainScreen
import com.example.pulsetransit.ui.screens.OnboardingFlow
import com.example.pulsetransit.ui.screens.RouteModesScreen
import com.example.pulsetransit.ui.screens.SettingsScreen
import com.example.pulsetransit.ui.screens.StopDirectionsScreen
import com.example.pulsetransit.ui.viewmodel.AppUpdateViewModel
import com.example.pulsetransit.ui.viewmodel.ModeSelectionViewModel
import com.example.pulsetransit.ui.viewmodel.OnboardingScreen
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import org.koin.androidx.compose.koinViewModel

/**
 * The app's screens. [openRequest] is a widget's request to open Settings ("SETTINGS") or Add stop
 * ("ADD_STOP") over the board; [onOpenHandled] is called once it is opened.
 */
@Composable
fun MainNavigation(
    openRequest: String? = null,
    onOpenHandled: () -> Unit = {},
) {
    // The choice of mode comes first, once, then the intro to it (and for on the spot, the ask for
    // location); after that the app opens on the board.
    val modeSelection: ModeSelectionViewModel = koinViewModel()
    val modeState by modeSelection.uiState.collectAsState()
    val backStack =
        rememberNavBackStack(if (modeState.screen == OnboardingScreen.NONE) Main else Onboarding)

    // Waits for onboarding to finish: a widget can be tapped before a mode is chosen.
    LaunchedEffect(openRequest, modeState.screen) {
        if (openRequest == null || modeState.screen != OnboardingScreen.NONE) return@LaunchedEffect
        while (backStack.size > 1) backStack.removeLastOrNull()
        backStack.add(Settings)
        if (openRequest == "ADD_STOP") backStack.add(AddStop)
        onOpenHandled()
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry<Onboarding> {
                    OnboardingFlow(viewModel = modeSelection)
                    // Over, by its last step or a mode set elsewhere: the board takes its place.
                    LaunchedEffect(modeState.screen) {
                        if (modeState.screen == OnboardingScreen.NONE) {
                            backStack.add(Main)
                            backStack.remove(Onboarding)
                        }
                    }
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
                        onOpenJourneyAlerts = { backStack.add(JourneyAlerts) },
                        onOpenDataSources = { backStack.add(DataSources) },
                        onOpenAddStop = { backStack.add(AddStop) },
                        onOpenDirections = { backStack.add(StopDirections(it)) },
                    )
                }
                entry<AddStop> {
                    // Its own ViewModel reads the settings fresh; Settings re-reads them on return.
                    AddStopScreen(viewModel = koinViewModel(), onBack = { backStack.removeLastOrNull() })
                }
                entry<StopDirections> { key ->
                    StopDirectionsScreen(viewModel = koinViewModel(), stopId = key.stopId, onBack = { backStack.removeLastOrNull() })
                }
                entry<RouteModes> {
                    // Settings re-reads everything when it is shown again, so its summary of these
                    // picks the change up whether or not this is the same ViewModel instance.
                    RouteModesScreen(
                        viewModel = koinViewModel(),
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<DataSources> {
                    DataSourcesScreen(
                        viewModel = koinViewModel(),
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<JourneyAlerts> {
                    // Like RouteModes: Settings re-reads its summary of these when shown again.
                    JourneyAlertsScreen(
                        viewModel = koinViewModel(),
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
    )
}
