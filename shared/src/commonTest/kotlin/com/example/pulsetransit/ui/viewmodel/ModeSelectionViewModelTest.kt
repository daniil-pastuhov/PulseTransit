package com.example.pulsetransit.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.example.pulsetransit.data.AppMode
import com.example.pulsetransit.data.IntroPage
import com.example.pulsetransit.data.introPages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** The first-launch choice of mode and the intro that follows it. */
@OptIn(ExperimentalCoroutinesApi::class)
class ModeSelectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsRepository()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun modeTest(body: suspend TestScope.(ModeSelectionViewModel) -> Unit) =
        runTest(dispatcher, timeout = 10.seconds) {
            val vm = ModeSelectionViewModel(settings)
            try {
                runCurrent()
                body(vm)
            } finally {
                vm.viewModelScope.cancel()
            }
        }

    private fun freshInstall() {
        settings.mode = null
        settings.appModeFlow.value = null
    }

    @Test
    fun `a fresh install shows neither a mode nor the intro`() {
        freshInstall()
        modeTest { vm ->
            assertNull(vm.uiState.value.mode)
            assertFalse(vm.uiState.value.showsIntro)
        }
    }

    @Test
    fun `choosing a mode starts the intro for that mode`() {
        freshInstall()
        modeTest { vm ->
            vm.choose(AppMode.ON_SPOT)
            runCurrent()
            assertEquals(AppMode.ON_SPOT, vm.uiState.value.mode)
            assertEquals(introPages(AppMode.ON_SPOT), vm.uiState.value.intro)
            assertTrue(settings.pendingIntro)
        }
    }

    @Test
    fun `finishing the intro ends it for good`() {
        freshInstall()
        modeTest { vm ->
            vm.choose(AppMode.BOARD)
            runCurrent()
            vm.finishIntro()
            runCurrent()
            assertEquals(AppMode.BOARD, vm.uiState.value.mode)
            assertFalse(vm.uiState.value.showsIntro)
            assertFalse(settings.pendingIntro)
        }
        modeTest { vm -> assertFalse(vm.uiState.value.showsIntro) }
    }

    @Test
    fun `an intro left half-way shows again on the next launch`() {
        settings.pendingIntro = true
        modeTest { vm -> assertEquals(introPages(AppMode.BOARD), vm.uiState.value.intro) }
    }

    @Test
    fun `an install that chose a mode before the intro existed goes straight to the board`() {
        modeTest { vm ->
            assertEquals(AppMode.BOARD, vm.uiState.value.mode)
            assertFalse(vm.uiState.value.showsIntro)
        }
    }

    @Test
    fun `the state is right from the first read without waiting for the flows`() {
        settings.pendingIntro = true
        val vm = ModeSelectionViewModel(settings)
        try {
            // No runCurrent(): the first screen is picked from this value before anything emits.
            assertEquals(AppMode.BOARD, vm.uiState.value.mode)
            assertEquals(introPages(AppMode.BOARD), vm.uiState.value.intro)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun `each mode gets its own pages in order`() {
        assertEquals(
            listOf(IntroPage.NEAREST_STOP, IntroPage.ROUTES_FROM_HERE, IntroPage.TAP_FOR_DETAILS, IntroPage.RESIZE),
            introPages(AppMode.ON_SPOT),
        )
        assertEquals(
            listOf(IntroPage.CHOOSE_STOPS, IntroPage.FASTEST_ROUTES, IntroPage.TAP_FOR_DETAILS, IntroPage.SCREEN_CARE),
            introPages(AppMode.BOARD),
        )
    }
}
