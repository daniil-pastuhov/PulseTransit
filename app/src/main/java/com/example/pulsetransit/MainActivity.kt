package com.example.pulsetransit

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.journey.JourneyOpenRequests
import com.example.pulsetransit.data.journey.JourneyStore
import com.example.pulsetransit.platform.JourneyLauncher
import com.example.pulsetransit.widget.WidgetRefresh
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val settingsRepository: SettingsRepository by inject()
    private val journeyStore: JourneyStore by inject()
    private val journeyLauncher: JourneyLauncher by inject()
    private val journeyOpenRequests: JourneyOpenRequests by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        // Only on a fresh start: a recreated activity (rotation) gets the same intent again.
        if (savedInstanceState == null) handleJourneyIntent(intent)

        // Full screen and always-on are wall-board settings; on-spot mode is a phone in the hand.
        val appMode = settingsRepository.getAppModeFlow()
        lifecycleScope.launch {
            combine(settingsRepository.getFullScreenModeFlow(), appMode) { fullScreen, mode ->
                fullScreen && mode == AppMode.BOARD
            }.collectLatest { isFullScreen ->
                if (isFullScreen) {
                    hideSystemBars()
                } else {
                    showSystemBars()
                }
            }
        }

        lifecycleScope.launch {
            combine(settingsRepository.getAlwaysOnEnabledFlow(), appMode) { alwaysOn, mode ->
                alwaysOn && mode == AppMode.BOARD
            }.collectLatest { alwaysOn ->
                if (alwaysOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                MainNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleJourneyIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        // Picks a saved journey up again after its service was stopped (a reboot, or Android
        // refusing to restart it in the background). Started from here, it may read the location.
        if (journeyStore.active.value != null) journeyLauncher.start()
    }

    override fun onStop() {
        super.onStop()
        // Favourites, destinations or filters may have changed while the app was open.
        WidgetRefresh.refreshNow(this)
    }

    /** "Show journey" from a journey notification opens the followed journey's details. */
    private fun handleJourneyIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_SHOW_JOURNEY, false) == true) journeyOpenRequests.request()
    }

    private fun hideSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun showSystemBars() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        const val EXTRA_SHOW_JOURNEY = "show_journey"
    }
}
