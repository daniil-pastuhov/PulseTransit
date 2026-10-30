package com.example.pulsetransit.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.crashkios.crashlytics.enableCrashlytics
import co.touchlab.crashkios.crashlytics.setCrashlyticsUnhandledExceptionHook
import com.example.pulsetransit.data.journey.JourneyOpenRequests
import com.example.pulsetransit.data.journey.JourneyStore
import com.example.pulsetransit.platform.CameraFrameMotionDetector
import com.example.pulsetransit.platform.CameraFrameSource
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.platform.IosJourneyLauncher
import com.example.pulsetransit.platform.JourneyLauncher
import com.example.pulsetransit.platform.JourneyNotifier
import com.example.pulsetransit.platform.JourneyPermissions
import com.example.pulsetransit.platform.LightSource
import com.example.pulsetransit.platform.LightSourceSensorManager
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.LocationSource
import com.example.pulsetransit.platform.LocationSourceProvider
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import com.example.pulsetransit.ui.viewmodel.AppUpdateViewModel
import com.example.pulsetransit.ui.viewmodel.JourneyAlertsViewModel
import com.example.pulsetransit.ui.viewmodel.ModeSelectionViewModel
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import com.russhwolf.settings.Settings
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/** The iOS app's entry point into the shared code. */
object PulseTransitIos : KoinComponent {
    /**
     * Starts Koin with the shared modules and the services the Swift app implements. Call once, at
     * launch. [crashReporting] is true when the app has configured Firebase; unhandled Kotlin
     * exceptions then reach Crashlytics with Kotlin stack traces rather than as a bare abort.
     *
     * [journeyLocationSource] is a second location source of its own for journey alerts, which may
     * run in the background alongside the board's. A journey saved before the app was closed is
     * followed again at once.
     *
     * One parameter per service Swift implements, as Swift calls it once, by name.
     *
     * [uiTest] swaps in the UI tests' fixed data and settings store (debug builds launched with
     * -PTUITest YES); [uiTestReset] empties that store first.
     */
    @Suppress("LongParameterList")
    fun start(
        crashReporting: Boolean,
        clockFormatter: ClockFormatter,
        locationSource: LocationSource,
        lightSource: LightSource,
        cameraFrameSource: CameraFrameSource,
        journeyLocationSource: LocationSource,
        journeyNotifier: JourneyNotifier,
        journeyPermissions: JourneyPermissions,
        uiTest: Boolean,
        uiTestReset: Boolean,
    ) {
        if (crashReporting) {
            enableCrashlytics()
            setCrashlyticsUnhandledExceptionHook()
        }
        startKoin {
            modules(
                sharedModule,
                iosPlatformModule,
                module {
                    single<ClockFormatter> { clockFormatter }
                    single<LocationProvider> { LocationSourceProvider(locationSource) }
                    single<SystemSensorManager> { LightSourceSensorManager(lightSource) }
                    single<CameraMotionDetector> { CameraFrameMotionDetector(cameraFrameSource, settingsRepository = get()) }
                    single<LocationProvider>(named(JOURNEY_LOCATION)) { LocationSourceProvider(journeyLocationSource) }
                    single<JourneyNotifier> { journeyNotifier }
                    single<JourneyPermissions> { journeyPermissions }
                    single<JourneyLauncher> { IosJourneyLauncher(tracker = get()) }
                },
            )
            if (uiTest) {
                modules(
                    uiTestModule(launchMillis = Clock.System.now().toEpochMilliseconds()),
                    module { single<Settings> { uiTestSettings(reset = uiTestReset) } },
                )
            }
        }
        if (uiTest) seedUiTestSettings(get())
        if (get<JourneyStore>().active.value != null) get<JourneyLauncher>().start()
    }

    /** A tap on a journey notification: the board opens the followed journey's details. */
    fun requestOpenJourney() = get<JourneyOpenRequests>().request()

    /** "Skip next alerts" from a notification: the journey stays followed, silently. */
    fun skipJourneyAlerts() {
        val store = get<JourneyStore>()
        val journey = store.active.value ?: return
        store.update(journey.id) { it.copy(muted = true) }
    }

    fun stopBoardViewModel(owner: ViewModelOwner): StopBoardViewModel = owner.viewModel { get<StopBoardViewModel>() }

    fun settingsViewModel(owner: ViewModelOwner): SettingsViewModel = owner.viewModel { get<SettingsViewModel>() }

    fun modeSelectionViewModel(owner: ViewModelOwner): ModeSelectionViewModel = owner.viewModel { get<ModeSelectionViewModel>() }

    fun appUpdateViewModel(owner: ViewModelOwner): AppUpdateViewModel = owner.viewModel { get<AppUpdateViewModel>() }

    fun journeyAlertsViewModel(owner: ViewModelOwner): JourneyAlertsViewModel = owner.viewModel { get<JourneyAlertsViewModel>() }
}

/**
 * Owns the ViewModels of one SwiftUI screen. Swift must call [clear] when the screen goes away:
 * that cancels `viewModelScope`, which is what stops the board's clock and refresh loops.
 */
class ViewModelOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    internal inline fun <reified T : ViewModel> viewModel(noinline create: () -> T): T =
        ViewModelProvider.create(this, viewModelFactory { initializer { create() } })[T::class]

    fun clear() = viewModelStore.clear()
}
