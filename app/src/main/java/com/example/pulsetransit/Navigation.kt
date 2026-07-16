package com.example.pulsetransit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.pulsetransit.ui.screens.MainScreen
import com.example.pulsetransit.ui.screens.SettingsScreen
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainNavigation() {
    val backStack = rememberNavBackStack(Main)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider =
            entryProvider {
                entry<Main> {
                    val viewModel: StopBoardViewModel = koinViewModel()
                    LaunchedEffect(Unit) {
                        viewModel.loadSettings()
                        viewModel.startAutoRefresh()
                    }
                    MainScreen(
                        viewModel = viewModel,
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
                    )
                }
            },
    )
}
