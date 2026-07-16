package com.example.pulsetransit.di

import com.example.pulsetransit.data.EnturRepository
import com.example.pulsetransit.data.EnturRepositoryImpl
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.SettingsRepositoryImpl
import com.example.pulsetransit.data.WeatherRepository
import com.example.pulsetransit.data.WeatherRepositoryImpl
import com.example.pulsetransit.data.cache.CacheDatabase
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.CameraMotionDetectorImpl
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import com.example.pulsetransit.ui.sensors.SystemSensorManagerImpl
import com.example.pulsetransit.ui.viewmodel.SettingsViewModel
import com.example.pulsetransit.ui.viewmodel.StopBoardViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        single {
            HttpClient(engineFactory = OkHttp) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            prettyPrint = true
                            isLenient = true
                        },
                    )
                }
            }
        }
        single { CacheDatabase.build(androidContext()) }
        factory { get<CacheDatabase>().departureCacheDao() }
        factory { get<CacheDatabase>().tripCacheDao() }
        factory { get<CacheDatabase>().forecastCacheDao() }

        single<SettingsRepository> { SettingsRepositoryImpl(context = androidContext()) }
        single<EnturRepository> {
            EnturRepositoryImpl(client = get(), departureCache = get(), tripCache = get())
        }
        single<WeatherRepository> { WeatherRepositoryImpl(client = get(), cache = get()) }
        single<SystemSensorManager> { SystemSensorManagerImpl(context = androidContext()) }
        single<CameraMotionDetector> {
            CameraMotionDetectorImpl(context = androidContext(), settingsRepository = get())
        }
        viewModel {
            StopBoardViewModel(
                enturRepository = get(),
                weatherRepository = get(),
                settingsRepository = get(),
                sensorManager = get(),
                cameraMotionDetector = get(),
            )
        }
        viewModel {
            SettingsViewModel(
                enturRepository = get(),
                settingsRepository = get(),
                context = androidContext(),
            )
        }
    }
