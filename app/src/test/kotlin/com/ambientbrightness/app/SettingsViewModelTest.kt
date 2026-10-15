package com.ambientbrightness.app

import com.ambientbrightness.app.StatusDashboard.View.SettingsViewModel
import com.ambientbrightness.app.testdoubles.FakeBrightnessOffsetService
import com.ambientbrightness.app.testdoubles.FakeResponseTuningService
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetRange
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** The settings ViewModel pre-fills from F3/F4, clamps intent input and writes through the services (US4). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `US4 AC1 - the state is pre-filled from the service flows`() = runTest(dispatcher) {
        val offset = FakeBrightnessOffsetService(
            offsetPercent = -17,
            application = BrightnessOffsetApplication.ClampedToMinimum,
        )
        val tuning = FakeResponseTuningService(speedPercent = 80)
        val viewModel = SettingsViewModel(offset, tuning)
        val subscription = launch { viewModel.uiState.collect {} }
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("the offset must come from the service", -17, state.offsetPercent)
        assertEquals("the speed must come from the service", 80, state.responseSpeedPercent)
        assertEquals(BrightnessOffsetApplication.ClampedToMinimum, state.offsetApplication)
        assertEquals(true, state.canResetOffset)
        assertEquals(true, state.canResetResponseSpeed)
        subscription.cancel()
        settleSharing()
    }

    @Test
    fun `a change to the service flows is reflected without recreating the ViewModel`() =
        runTest(dispatcher) {
            val offset = FakeBrightnessOffsetService(offsetPercent = BrightnessOffsetRange.DEFAULT)
            val tuning = FakeResponseTuningService(speedPercent = ResponseSpeed.DEFAULT)
            val viewModel = SettingsViewModel(offset, tuning)
            val subscription = launch { viewModel.uiState.collect {} }
            runCurrent()

            offset.publishOffsetPercent(-30)
            tuning.setSpeedPercent(20)
            runCurrent()

            assertEquals(-30, viewModel.uiState.value.offsetPercent)
            assertEquals(20, viewModel.uiState.value.responseSpeedPercent)
            subscription.cancel()
            settleSharing()
        }

    @Test
    fun `setOffsetPercent clamps a continuous value to the offset range before writing`() =
        runTest(dispatcher) {
            val offset = FakeBrightnessOffsetService()
            val viewModel = SettingsViewModel(offset, FakeResponseTuningService())
            val subscription = launch { viewModel.uiState.collect {} }
            runCurrent()

            viewModel.setOffsetPercent(120)
            runCurrent()
            assertEquals(
                "an above-range value must snap to the maximum",
                BrightnessOffset.of(BrightnessOffsetRange.MAX),
                offset.lastSetOffset,
            )
            assertEquals(BrightnessOffsetRange.MAX, viewModel.uiState.value.offsetPercent)

            viewModel.setOffsetPercent(-999)
            runCurrent()
            assertEquals(
                "a below-range value must snap to the minimum",
                BrightnessOffset.of(BrightnessOffsetRange.MIN),
                offset.lastSetOffset,
            )
            assertEquals(BrightnessOffsetRange.MIN, viewModel.uiState.value.offsetPercent)

            viewModel.setOffsetPercent(7)
            runCurrent()
            assertEquals(BrightnessOffset.of(7), offset.lastSetOffset)
            assertEquals(3, offset.setOffsetCalls)
            subscription.cancel()
            settleSharing()
        }

    @Test
    fun `resetOffset returns the offset to the default and canResetOffset follows it`() =
        runTest(dispatcher) {
            val offset = FakeBrightnessOffsetService(offsetPercent = -17)
            val viewModel = SettingsViewModel(offset, FakeResponseTuningService())
            val subscription = launch { viewModel.uiState.collect {} }
            runCurrent()
            assertEquals(
                "a non-default offset must offer the reset affordance",
                true,
                viewModel.uiState.value.canResetOffset,
            )

            viewModel.resetOffset()
            runCurrent()

            assertEquals(1, offset.resetCalls)
            assertEquals(BrightnessOffset.DEFAULT, offset.offset.value)
            assertEquals(
                "the default offset must not offer the reset affordance",
                false,
                viewModel.uiState.value.canResetOffset,
            )
            subscription.cancel()
            settleSharing()
        }

    @Test
    fun `setResponseSpeedPercent clamps to the range and resetResponseSpeed writes the default`() =
        runTest(dispatcher) {
            val tuning = FakeResponseTuningService(speedPercent = 30)
            val viewModel = SettingsViewModel(FakeBrightnessOffsetService(), tuning)
            val subscription = launch { viewModel.uiState.collect {} }
            runCurrent()
            assertEquals(true, viewModel.uiState.value.canResetResponseSpeed)

            viewModel.setResponseSpeedPercent(150)
            runCurrent()
            assertEquals(
                "an above-range value must clamp to the slowest speed",
                ResponseSpeed.of(ResponseSpeed.SLOWEST_PERCENT),
                tuning.lastSetSpeed,
            )
            assertEquals(100, viewModel.uiState.value.responseSpeedPercent)

            viewModel.resetResponseSpeed()
            runCurrent()
            assertEquals(ResponseSpeed.of(ResponseSpeed.DEFAULT), tuning.lastSetSpeed)
            assertEquals(ResponseSpeed.DEFAULT, viewModel.uiState.value.responseSpeedPercent)
            assertEquals(false, viewModel.uiState.value.canResetResponseSpeed)
            subscription.cancel()
            settleSharing()
        }

    @Test
    fun `US4 AC4 - a change while nothing else runs is persisted by the service`() =
        runTest(dispatcher) {
            val offset = FakeBrightnessOffsetService()
            val tuning = FakeResponseTuningService()
            val viewModel = SettingsViewModel(offset, tuning)

            viewModel.setOffsetPercent(25)
            viewModel.setResponseSpeedPercent(70)
            runCurrent()

            assertEquals("the offset write must reach the service", 1, offset.setOffsetCalls)
            assertEquals("a fresh read must report the stored offset", 25, offset.offset.value.percent)
            assertEquals("the speed write must reach the service", 1, tuning.setCalls)
            assertEquals("a fresh read must report the stored speed", 70, tuning.responseSpeed.value.percent)
        }

    /** Lets the `WhileSubscribed` sharing stop once the last subscriber is gone. */
    private suspend fun TestScope.settleSharing() {
        advanceTimeBy(SHARING_STOP_TIMEOUT_MILLIS + 1L)
        runCurrent()
    }

    private companion object {
        const val SHARING_STOP_TIMEOUT_MILLIS = 5_000L
    }
}
