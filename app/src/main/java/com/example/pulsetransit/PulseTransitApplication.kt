package com.example.pulsetransit

import android.app.Application
import com.example.pulsetransit.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PulseTransitApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PulseTransitApplication)
            modules(appModule)
        }
    }
}
