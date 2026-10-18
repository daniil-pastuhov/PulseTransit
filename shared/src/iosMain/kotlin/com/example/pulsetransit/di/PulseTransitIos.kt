package com.example.pulsetransit.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import co.touchlab.crashkios.crashlytics.enableCrashlytics
import co.touchlab.crashkios.crashlytics.setCrashlyticsUnhandledExceptionHook
import com.example.pulsetransit.platform.CameraFrameMotionDetector
import com.example.pulsetransit.platform.CameraFrameSource
import com.example.pulsetransit.platform.ClockFormatter
import com.example.pulsetransit.platform.LightSource
import com.example.pulsetransit.platform.LightSourceSensorManager
import com.example.pulsetransit.platform.LocationProvider
import com.example.pulsetransit.platform.LocationSource
import com.example.pulsetransit.platform.LocationSourceProvider
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import com.example.pulsetransit.ui.viewmodel.AppUpdateViewModel
import com.example.pulsetransit.ui.viewmodel.ModeSelectionViewModel
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.dsl.module

/** The iOS app's entry point into the shared code. */
object PulseTransitIos : KoinComponent {
    /**
     * Starts Koin with the shared modules and the services the Swift app implements. Call once, at
     * launch. [crashReporting] is true when the app has configured Firebase; unhandled Kotlin
     * exceptions then reach Crashlytics with Kotlin stack traces rather than as a bare abort.
     */
    fun start(
        crashReporting: Boolean,
        clockFormatter: ClockFormatter,
        locationSource: LocationSource,
        lightSource: LightSource,
        cameraFrameSource: CameraFrameSource,
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
                },
            )
        }
    }

    fun stopBoardViewModel(owner: ViewModelOwner): StopBoardViewModel = owner.viewModel { get<StopBoardViewModel>() }

    fun settingsViewModel(owner: ViewModelOwner): SettingsViewModel = owner.viewModel { get<SettingsViewModel>() }

    fun modeSelectionViewModel(owner: ViewModelOwner): ModeSelectionViewModel = owner.viewModel { get<ModeSelectionViewModel>() }

    fun appUpdateViewModel(owner: ViewModelOwner): AppUpdateViewModel = owner.viewModel { get<AppUpdateViewModel>() }
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
