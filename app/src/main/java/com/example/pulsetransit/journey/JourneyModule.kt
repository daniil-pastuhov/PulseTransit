package com.example.pulsetransit.journey

import com.example.pulsetransit.platform.JourneyLauncher
import com.example.pulsetransit.platform.JourneyNotifier
import com.example.pulsetransit.platform.JourneyPermissions
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Journey alerts' Android services. In the app module rather than `androidPlatformModule`, since
 * the notifications open [com.example.pulsetransit.MainActivity] and use the UI's strings.
 */
val journeyModule =
    module {
        single { AndroidJourneyNotifier(context = androidContext(), clockFormatter = get()) }
        single<JourneyNotifier> { get<AndroidJourneyNotifier>() }
        single<JourneyPermissions> { AndroidJourneyPermissions(context = androidContext(), settings = get()) }
        single<JourneyLauncher> { JourneyTrackingService.Launcher(context = androidContext()) }
    }
