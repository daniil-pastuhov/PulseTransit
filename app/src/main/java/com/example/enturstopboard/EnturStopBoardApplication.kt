package com.example.enturstopboard

import android.app.Application
import com.example.enturstopboard.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class EnturStopBoardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@EnturStopBoardApplication)
            modules(appModule)
        }
    }
}
