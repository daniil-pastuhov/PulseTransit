package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.platform.AppUpdate
import com.example.pulsetransit.platform.AppUpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** When the board's "new version" notice shows, and that closing it sticks. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsRepository()
    private val connectivity = FakeConnectivityMonitor()
    private val checker = FakeAppUpdateChecker()

    private val v21 = AppUpdate(version = "21", storeUrl = null)
    private val v22 = AppUpdate(version = "22", storeUrl = null)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Like `boardTest`: the periodic check is a `while (true)` loop, so the scope must be cancelled. */
    private fun updateTest(body: suspend TestScope.(AppUpdateViewModel) -> Unit) =
        runTest(dispatcher, timeout = 10.seconds) {
            val vm = AppUpdateViewModel(checker, settings, connectivity)
            try {
                runCurrent()
                body(vm)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    @Test
    fun `an update the store offers is shown at once`() {
        checker.update = v21
        updateTest { vm ->
            assertEquals(v21, vm.uiState.value.update)
            assertEquals(1, checker.calls)
        }
    }

    @Test
    fun `the store is asked again after the check interval`() =
        updateTest { vm ->
            assertNull(vm.uiState.value.update)

            checker.update = v21
            advanceTimeBy(AppUpdateViewModel.CHECK_INTERVAL - 1.minutes)
            runCurrent()
            assertNull(vm.uiState.value.update)

            advanceTimeBy(2.minutes)
            runCurrent()
            assertEquals(v21, vm.uiState.value.update)
            assertEquals(2, checker.calls)
        }

    @Test
    fun `coming back online asks the store again`() =
        updateTest { vm ->
            connectivity.online.value = false
            runCurrent()
            checker.update = v21

            connectivity.online.value = true
            runCurrent()

            assertEquals(v21, vm.uiState.value.update)
        }

    @Test
    fun `a closed notice stays closed for that version but not for a newer one`() {
        checker.update = v21
        updateTest { vm ->
            vm.dismiss()
            assertNull(vm.uiState.value.update)
            assertEquals("21", settings.dismissedUpdate)

            advanceTimeBy(AppUpdateViewModel.CHECK_INTERVAL)
            runCurrent()
            assertNull(vm.uiState.value.update)

            checker.update = v22
            advanceTimeBy(AppUpdateViewModel.CHECK_INTERVAL)
            runCurrent()
            assertEquals(v22, vm.uiState.value.update)
        }
    }

    @Test
    fun `a version closed in an earlier session is not shown`() {
        settings.dismissedUpdate = "21"
        checker.update = v21
        updateTest { vm -> assertNull(vm.uiState.value.update) }
    }

    @Test
    fun `the notice goes away once the update is installed`() {
        checker.update = v21
        updateTest { vm ->
            checker.update = null
            advanceTimeBy(AppUpdateViewModel.CHECK_INTERVAL)
            runCurrent()

            assertNull(vm.uiState.value.update)
        }
    }
}

class FakeAppUpdateChecker : AppUpdateChecker {
    var update: AppUpdate? = null
    var calls = 0
        private set

    override suspend fun availableUpdate(): AppUpdate? {
        calls++
        return update
    }
}
