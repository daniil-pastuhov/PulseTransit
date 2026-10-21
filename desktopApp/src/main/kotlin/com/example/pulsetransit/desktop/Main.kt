package com.example.pulsetransit.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.StopPlace
import com.example.pulsetransit.di.desktopPlatformModule
import com.example.pulsetransit.di.sharedModule
import com.example.pulsetransit.ui.screens.MainScreen
import com.example.pulsetransit.ui.screens.ModeSelectionScreen
import com.example.pulsetransit.ui.screens.RouteModesScreen
import com.example.pulsetransit.ui.screens.SettingsScreen
import com.example.pulsetransit.ui.viewmodel.AppUpdateViewModel
import com.example.pulsetransit.ui.viewmodel.ModeSelectionViewModel
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

// A 13-inch iPad in portrait, in dp, so the board opens at a tablet size. Resize the window to
// see the layout switch: below 640 dp wide the stops stack as on a phone.
private val INITIAL_WINDOW = DpSize(1032.dp, 1200.dp)

fun main() {
    startKoin { modules(sharedModule, desktopPlatformModule) }
    seedFirstRun(KoinPlatform.getKoin().get())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PulseTransit",
            state = rememberWindowState(size = INITIAL_WINDOW),
        ) {
            DesktopApp()
        }
    }
}

/** The desktop app's screens, in place of the Android app's Navigation 3 back stack. */
private enum class Screen { BOARD, SETTINGS, ROUTE_MODES }

/** The app as Android shows it: the mode prompt once, then the board, Settings and transport modes. */
@Composable
private fun DesktopApp() {
    val koin = remember { KoinPlatform.getKoin() }
    val modeSelection = remember { koin.get<ModeSelectionViewModel>() }
    val modeState by modeSelection.uiState.collectAsState()
    if (modeState.mode == null) {
        ModeSelectionScreen(onChoose = modeSelection::choose)
        return
    }
    // Created once, for the life of the app: Koin's viewModel definitions hand out a new instance on
    // every get(), and with no ViewModelStoreOwner nothing would ever clear one. A board recreated
    // on each return from Settings would leave the old one's clock and refresh loops running.
    val boardViewModel = remember { koin.get<StopBoardViewModel>() }
    val updateViewModel = remember { koin.get<AppUpdateViewModel>() }
    // One instance for Settings and transport modes, so Settings' summary of them agrees.
    val settingsViewModel = remember { koin.get<SettingsViewModel>() }
    var screen by remember { mutableStateOf(Screen.BOARD) }
    when (screen) {
        Screen.BOARD -> {
            // Settings are not reactive: the board re-reads them each time it is shown, as on Android.
            LaunchedEffect(Unit) {
                boardViewModel.loadSettings()
                boardViewModel.startAutoRefresh()
            }
            MainScreen(viewModel = boardViewModel, updateViewModel = updateViewModel, onNavigateToSettings = { screen = Screen.SETTINGS })
        }
        Screen.SETTINGS -> {
            LaunchedEffect(Unit) { settingsViewModel.loadSettings() }
            SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { screen = Screen.BOARD },
                onOpenRouteModes = { screen = Screen.ROUTE_MODES },
            )
        }
        Screen.ROUTE_MODES -> RouteModesScreen(viewModel = settingsViewModel, onBack = { screen = Screen.SETTINGS })
    }
}

/**
 * On first run, the setup the App Store screenshots use: Oslo S and Jernbanetorget, weather for
 * central Oslo and the fastest route to Lillestrøm, so the board has something to show before
 * Settings is opened. Settings persist in the user's Java preferences, so this runs once.
 */
private fun seedFirstRun(settings: SettingsRepository) {
    if (settings.getFavorites().isNotEmpty() || settings.getAppMode() != null) return
    settings.addFavorite(StopPlace(id = "NSR:StopPlace:59872", name = "Oslo S", locality = "Oslo"))
    settings.addFavorite(StopPlace(id = "NSR:StopPlace:58366", name = "Jernbanetorget", locality = "Oslo"))
    settings.addDestinationStation(StopPlace(id = "NSR:StopPlace:62339", name = "Lillestrøm stasjon", locality = "Lillestrøm"))
    settings.setSavedLocation(lat = 59.9111, lon = 10.7528)
    settings.setAppMode(AppMode.BOARD)
}
