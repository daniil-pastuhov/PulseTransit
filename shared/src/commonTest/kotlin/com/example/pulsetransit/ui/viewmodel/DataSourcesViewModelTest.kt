package com.example.pulsetransit.ui.viewmodel

import com.example.pulsetransit.data.DataSource
import com.example.pulsetransit.data.DataSourceMode
import com.example.pulsetransit.data.DataSourceRepository
import com.example.pulsetransit.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Settings' "Data sources" screen: loading, failure and retry, and the search. */
@OptIn(ExperimentalCoroutinesApi::class)
class DataSourcesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val ruter = DataSource("Ruter", "https://ruter.no/", listOf(DataSourceMode.BUS))
    private val norway = DataSource("NOR-WAY Bussekspress", null, listOf(DataSourceMode.COACH))

    private class FakeRepository(
        var answers: MutableList<List<DataSource>?>,
    ) : DataSourceRepository {
        var calls = 0

        override suspend fun getDataSources(): List<DataSource>? {
            calls++
            return answers.removeAt(0)
        }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `it loads on creation and opens in the theme of the board`() =
        runTest(dispatcher) {
            val settings = FakeSettingsRepository().apply { setThemeMode(ThemeMode.DARK) }
            val vm = DataSourcesViewModel(FakeRepository(mutableListOf(listOf(norway, ruter))), settings)

            assertTrue(vm.uiState.value.isLoading)
            assertEquals(ThemeMode.DARK, vm.uiState.value.themeMode)
            runCurrent()

            assertFalse(vm.uiState.value.isLoading)
            assertFalse(vm.uiState.value.failed)
            assertEquals(listOf(norway, ruter), vm.uiState.value.shown)
        }

    @Test
    fun `a failure can be retried`() =
        runTest(dispatcher) {
            val repository = FakeRepository(mutableListOf(null, listOf(ruter)))
            val vm = DataSourcesViewModel(repository, FakeSettingsRepository())
            runCurrent()
            assertTrue(vm.uiState.value.failed)

            vm.retry()
            runCurrent()

            assertFalse(vm.uiState.value.failed)
            assertEquals(listOf(ruter), vm.uiState.value.shown)
            assertEquals(2, repository.calls)
        }

    @Test
    fun `the search narrows the list and says when nothing matches`() =
        runTest(dispatcher) {
            val vm = DataSourcesViewModel(FakeRepository(mutableListOf(listOf(norway, ruter))), FakeSettingsRepository())
            runCurrent()

            vm.setQuery("rut")
            assertEquals(listOf(ruter), vm.uiState.value.shown)
            assertFalse(vm.uiState.value.noMatches)

            vm.setQuery("Sindbad")
            assertTrue(vm.uiState.value.noMatches)
        }
}
