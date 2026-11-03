package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.DataSource
import com.example.pulsetransit.data.DataSourceRepository
import com.example.pulsetransit.data.SettingsRepository
import com.example.pulsetransit.data.ThemeMode
import com.example.pulsetransit.data.matching
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Settings' "Data sources": the transport companies whose timetables reach Entur, searchable, so
 * people can check whether one they travel with is covered. Loads once when created.
 */
class DataSourcesViewModel(
    private val repository: DataSourceRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    data class UiState(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val isLoading: Boolean = true,
        /** True when Entur could not be reached; [retry] tries again. */
        val failed: Boolean = false,
        val query: String = "",
        /** Every company, before [query] is applied. */
        val all: List<DataSource> = emptyList(),
    ) {
        /** The companies [query] matches, in name order. */
        val shown: List<DataSource> get() = all.matching(query)

        /** True when there is a list but the search matches none of it. */
        val noMatches: Boolean get() = all.isNotEmpty() && shown.isEmpty()
    }

    private val _uiState = MutableStateFlow(UiState(themeMode = settingsRepository.getThemeMode()))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun retry() {
        if (!_uiState.value.isLoading) load()
    }

    private fun load() {
        _uiState.update { it.copy(isLoading = true, failed = false) }
        viewModelScope.launch {
            val sources = repository.getDataSources()
            _uiState.update {
                it.copy(isLoading = false, failed = sources == null, all = sources.orEmpty())
            }
        }
    }
}
