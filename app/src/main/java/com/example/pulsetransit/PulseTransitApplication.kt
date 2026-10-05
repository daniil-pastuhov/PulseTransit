package com.example.pulsetransit

import android.app.Application
import com.example.pulsetransit.di.androidPlatformModule
import com.example.pulsetransit.di.sharedModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PulseTransitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PulseTransitApplication)
            modules(sharedModule, androidPlatformModule)
        }
    }
}
