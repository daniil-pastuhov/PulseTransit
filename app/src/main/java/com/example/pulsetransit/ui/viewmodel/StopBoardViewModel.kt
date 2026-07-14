package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.*
import com.example.pulsetransit.ui.sensors.CameraMotionDetector
import com.example.pulsetransit.ui.sensors.SystemSensorManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class StopBoardViewModel(
    private val enturRepository: EnturRepository,
    private val weatherRepository: WeatherRepository,
    private val settingsRepository: SettingsRepository,
    private val sensorManager: SystemSensorManager,
    private val cameraMotionDetector: CameraMotionDetector
) : ViewModel() {

    data class UiState(
        val favorites: List<StopPlace> = emptyList(),
        val departures: Map<String, List<Departure>> = emptyMap(),
        val weather: List<WeatherInfo> = emptyList(),
        val isLoading: Boolean = false,
        val lastUpdated: String = "",
        val currentTime: String = "",
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val burnInLevel: BurnInProtectionLevel = BurnInProtectionLevel.MEDIUM,
        val refreshIntervalSeconds: Int = 60,
        val isScreenDimmed: Boolean = false,
        val showDelays: Boolean = true,
        val showMessages: Boolean = true,
        val activeTheme: ThemeMode = ThemeMode.SYSTEM,
        val fastestTrip: TripInfo? = null,
        val destinationStation: StopPlace? = null,
        val isFullScreenMode: Boolean = false
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null
    private var lastWeatherUpdateTime: Long = 0
    private val weatherRefreshIntervalMs = 30 * 60 * 1000L // 30 minutes

    init {
        loadSettings()
        startClock()
        startAutoRefresh()
        setupSensors()
        observeSettings()
    }

    private fun observeSettings() {
        settingsRepository.getFullScreenModeFlow()
            .onEach { isFullScreen ->
                _uiState.update { it.copy(isFullScreenMode = isFullScreen) }
            }
            .launchIn(viewModelScope)
    }

    fun loadSettings() {
        val favs = settingsRepository.getFavorites()
        val theme = settingsRepository.getThemeMode()
        val burnIn = settingsRepository.getBurnInProtectionLevel()
        val interval = settingsRepository.getRefreshIntervalSeconds()
        val showDelays = settingsRepository.isShowDelays()
        val showMessages = settingsRepository.isShowMessages()
        val destination = settingsRepository.getDestinationStation()
        val isFullScreen = settingsRepository.isFullScreenMode()

        _uiState.update {
            it.copy(
                favorites = favs,
                themeMode = theme,
                activeTheme = theme,
                burnInLevel = burnIn,
                refreshIntervalSeconds = interval,
                showDelays = showDelays,
                showMessages = showMessages,
                destinationStation = destination,
                fastestTrip = if (destination == null) null else it.fastestTrip,
                isFullScreenMode = isFullScreen
            )
        }
    }

    private fun setupSensors() {
        if (settingsRepository.isProximityWakeEnabled()) {
            sensorManager.getProximityFlow()
                .onEach { isNear ->
                    if (isNear) wakeScreen() else dimScreen()
                }
                .launchIn(viewModelScope)
        }

        if (_uiState.value.themeMode == ThemeMode.AUTO) {
            sensorManager.getLightFlow()
                .onEach { lux ->
                    val autoTheme = if (lux < 20) ThemeMode.DARK else ThemeMode.LIGHT
                    _uiState.update { it.copy(activeTheme = autoTheme) }
                }
                .launchIn(viewModelScope)
        }
    }

    private var dimJob: Job? = null

    fun wakeScreen() {
        dimJob?.cancel()
        _uiState.update { it.copy(isScreenDimmed = false) }
        
        // Auto-dim after 30 seconds of no motion if motion detection is enabled
        if (settingsRepository.isProximityWakeEnabled() || settingsRepository.isCameraMotionWakeEnabled()) {
            dimJob = viewModelScope.launch {
                delay(30000)
                dimScreen()
            }
        }
    }

    fun dimScreen() {
        _uiState.update { it.copy(isScreenDimmed = true) }
    }

    fun isCameraMotionEnabled(): Boolean = settingsRepository.isCameraMotionWakeEnabled()

    fun bindCameraMotion(lifecycleOwner: LifecycleOwner) {
        if (settingsRepository.isCameraMotionWakeEnabled()) {
            cameraMotionDetector.getMotionFlow(lifecycleOwner)
                .onEach { motionDetected ->
                    if (motionDetected) {
                        wakeScreen()
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (true) {
                _uiState.update { it.copy(currentTime = sdf.format(Date())) }
                delay(1000)
            }
        }
    }

    fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                refreshData()
                val seconds = calculateNextRefreshInterval()
                if (seconds <= 0) break
                delay(seconds * 1000L)
            }
        }
    }

    private fun calculateNextRefreshInterval(): Int {
        val base = _uiState.value.refreshIntervalSeconds
        if (!settingsRepository.isDynamicRefreshEnabled()) return base

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        var multiplier = 1.0
        
        // Night mode (00:00 - 05:00)
        if (hour in 0..5) multiplier *= 5.0
        
        // Unoccupied mode (if dimmed)
        if (_uiState.value.isScreenDimmed) multiplier *= 2.0
        
        return (base * multiplier).toInt()
    }

    fun refreshData() {
        refreshDepartures()
        refreshFastestTrip()
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastWeatherUpdateTime >= weatherRefreshIntervalMs) {
            refreshWeather()
            lastWeatherUpdateTime = currentTime
        }
    }

    fun refreshDepartures() {
        val favs = _uiState.value.favorites
        if (favs.isEmpty()) {
            _uiState.update { it.copy(departures = emptyMap(), lastUpdated = "No favorites") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val newDepartures = mutableMapOf<String, List<Departure>>()
            
            for (stop in favs) {
                val quayIds = settingsRepository.getSelectedQuays(stop.id)
                val list = enturRepository.getDepartures(stop.id, limit = 15, quayIds = quayIds)
                newDepartures[stop.id] = list
            }

            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val updateTime = timeFormat.format(Date())

            _uiState.update {
                it.copy(
                    departures = newDepartures,
                    isLoading = false,
                    lastUpdated = updateTime
                )
            }
        }
    }

    private fun refreshFastestTrip() {
        val destination = _uiState.value.destinationStation ?: return
        val favorites = _uiState.value.favorites
        if (favorites.isEmpty()) return

        viewModelScope.launch {
            val trip = enturRepository.getFastestTrip(
                fromIds = favorites.map { it.id },
                toId = destination.id
            )
            _uiState.update { it.copy(fastestTrip = trip?.copy(destinationName = destination.name)) }
        }
    }

    private fun refreshWeather() {
        val lat = settingsRepository.getSavedLatitude()
        val lon = settingsRepository.getSavedLongitude()
        if (lat == null || lon == null) return

        viewModelScope.launch {
            val weather = weatherRepository.getWeather(lat, lon)
            _uiState.update { it.copy(weather = weather) }
        }
    }

    fun removeFavorite(stopId: String) {
        settingsRepository.removeFavorite(stopId)
        loadSettings()
        refreshDepartures()
    }

    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        val currentList = _uiState.value.favorites.toMutableList()
        if (fromIndex !in currentList.indices || toIndex !in currentList.indices) return
        
        val item = currentList.removeAt(fromIndex)
        currentList.add(toIndex, item)
        
        _uiState.update { it.copy(favorites = currentList) }
        settingsRepository.reorderFavorites(currentList.map { it.id })
    }
}
