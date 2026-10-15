package com.ambientbrightness.app

import com.ambientbrightness.app.Composition.AmbientBrightnessComponent
import com.ambientbrightness.app.StatusDashboard.Adapter.DashboardStateCollector
import com.ambientbrightness.app.StatusDashboard.Controller.ControllerCommandHandler
import com.ambientbrightness.app.StatusDashboard.View.ControllerUiState
import com.ambientbrightness.app.StatusDashboard.View.DashboardViewModel
import com.ambientbrightness.app.StatusDashboard.View.LightReadout
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.core.Shared.Clock
import com.ambientbrightness.infrastructure.AmbientLightSensing.AmbientLightSensorClient.Dummy.DummyAmbientLightSensorClient
import com.ambientbrightness.infrastructure.AmbientLightSensing.ScreenStateClient.Dummy.DummyScreenStateClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient.Dummy.DummyBuiltInAdaptiveBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Dummy.DummyDeviceBrightnessRangeClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Dummy.DummyWriteSettingsPermissionChecker
import com.ambientbrightness.infrastructure.BackgroundService.BatteryOptimizationClient.Dummy.DummyBatteryOptimizationClient
import com.ambientbrightness.infrastructure.BackgroundService.ControllerStateStore.Dummy.DummyControllerStateStore
import com.ambientbrightness.infrastructure.BackgroundService.ForegroundServiceHost.DummyForegroundServiceHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * End-to-end F7 verification over the real composition root: the real F5 lifecycle drives the real
 * dashboard ViewModel, and the Dummy adapters stand in for the platform. The two states that must never
 * be faked — a failed write and a platform-initiated stop — are asserted through the whole graph, so the
 * wiring in [AmbientBrightnessComponent], not just the mapper, is exercised (US3, AC2/AC3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardEndToEndTest {

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
    fun `US3 AC2 - a failing brightness write flips the dashboard to the error state`() =
        runTest(dispatcher) {
            val scenario = scenario(
                brightnessClient = FailingSystemBrightnessClient("WRITE_SETTINGS revoked"),
            )
            observe(this, scenario)

            scenario.component.backgroundService.start(StartReason.UserRequest)
            runCurrent()
            settle(scenario)
            repeat(RAMP_TICKS) { tick(scenario, BRIGHT_LUX) }
            runCurrent()

            assertEquals(
                "the service must report the failure instead of pretending to run",
                BackgroundServiceState.Failed("WRITE_SETTINGS revoked"),
                scenario.component.backgroundService.state.value,
            )
            assertEquals(
                ControllerUiState.Failed("WRITE_SETTINGS revoked"),
                scenario.viewModel.uiState.value.controller,
            )
            assertEquals(
                "a failed controller must not show a live reading",
                LightReadout.Unavailable,
                scenario.viewModel.uiState.value.light,
            )
        }

    @Test
    fun `US3 AC3 - a platform stop is shown as StoppedBySystem instead of the last known state`() =
        runTest(dispatcher) {
            val scenario = scenario()
            observe(this, scenario)

            scenario.component.backgroundService.start(StartReason.UserRequest)
            runCurrent()
            settle(scenario)
            repeat(RAMP_TICKS) { tick(scenario, BRIGHT_LUX) }
            runCurrent()
            assertEquals(
                "the dashboard must actually be running before the system stop",
                true,
                scenario.viewModel.uiState.value.controller is ControllerUiState.Running,
            )

            // The platform reports the service gone (F5's host bridge signals it).
            scenario.component.recordSystemStop()
            runCurrent()

            assertEquals(
                ControllerUiState.StoppedBySystem,
                scenario.viewModel.uiState.value.controller,
            )
            assertEquals(
                "a system-stopped controller must not keep showing a reading",
                LightReadout.Unavailable,
                scenario.viewModel.uiState.value.light,
            )
        }

    @Test
    fun `US3 AC3 - a user stop still reads as a deliberate stop`() = runTest(dispatcher) {
        val scenario = scenario()
        observe(this, scenario)

        scenario.component.backgroundService.start(StartReason.UserRequest)
        runCurrent()
        settle(scenario)

        scenario.component.backgroundService.stop()
        runCurrent()

        assertEquals(ControllerUiState.Stopped, scenario.viewModel.uiState.value.controller)
    }

    private fun TestScope.scenario(
        brightnessClient: SystemBrightnessClient = WorkingSystemBrightnessClient(),
    ): Scenario {
        val clock = TestClock()
        val sensorClient = DummyAmbientLightSensorClient()
        val component = AmbientBrightnessComponent(
            sensorClient = sensorClient,
            screenStateClient = DummyScreenStateClient(initiallyScreenOn = true),
            controllerScope = backgroundScope,
            brightnessClient = brightnessClient,
            deviceRangeClient = DummyDeviceBrightnessRangeClient(),
            writeSettingsPermissionChecker =
                DummyWriteSettingsPermissionChecker(initiallyGranted = true),
            builtInAdaptiveBrightnessClient = DummyBuiltInAdaptiveBrightnessClient(),
            clock = clock,
            foregroundServiceHost = DummyForegroundServiceHost(),
            controllerStateStore = DummyControllerStateStore(),
            batteryOptimizationClient = DummyBatteryOptimizationClient(initiallyExempt = false),
        )

        val viewModel = DashboardViewModel(
            provider = DashboardStateCollector(
                ambientLightService = component.ambientLightService,
                backgroundService = component.backgroundService,
                setupService = component.setupService,
                brightnessOffsetSource = component.brightnessOffsetService,
                responseTuningService = component.responseTuningService,
            ),
            commandHandler = ControllerCommandHandler(component.backgroundService),
        )

        return Scenario(component, sensorClient, clock, viewModel)
    }

    /** Keeps the ViewModel's state collected, the way the screen does while it is visible. */
    private fun observe(scope: TestScope, scenario: Scenario) {
        scope.backgroundScope.launch { scenario.viewModel.uiState.collect {} }
        scope.runCurrent()
    }

    /** Runs the pipeline at the low light level until the applied brightness has settled. */
    private fun TestScope.settle(scenario: Scenario) {
        repeat(SETTLE_TICKS) { tick(scenario, LIGHT_LUX) }
    }

    /**
     * One sampling interval: the clock and the virtual time advance together, a new reading arrives in
     * between, and the sampling loop is then allowed to process it.
     */
    private fun TestScope.tick(scenario: Scenario, lux: Float) {
        scenario.clock.advanceBy(SAMPLING_INTERVAL_MILLIS)
        scenario.sensorClient.emit(lux)
        testScheduler.advanceTimeBy(SAMPLING_INTERVAL_MILLIS)
        runCurrent()
    }

    private data class Scenario(
        val component: AmbientBrightnessComponent,
        val sensorClient: DummyAmbientLightSensorClient,
        val clock: TestClock,
        val viewModel: DashboardViewModel,
    )

    private class TestClock(private var currentMillis: Long = 0L) : Clock {
        override fun nowMillis(): Long = currentMillis

        fun advanceBy(deltaMillis: Long) {
            currentMillis += deltaMillis
        }
    }

    private class WorkingSystemBrightnessClient : SystemBrightnessClient {
        private var currentValue: Int? = SETTLED_SOURCE

        override fun readCurrent(): Int? = currentValue

        override fun write(value: BrightnessValue): BrightnessWriteResult {
            currentValue = value.value
            return BrightnessWriteResult.Applied(value)
        }
    }

    private class FailingSystemBrightnessClient(private val reason: String) : SystemBrightnessClient {
        override fun readCurrent(): Int? = SETTLED_SOURCE

        override fun write(value: BrightnessValue): BrightnessWriteResult =
            BrightnessWriteResult.Failed(reason)
    }

    private companion object {
        const val SAMPLING_INTERVAL_MILLIS = 500L
        const val SETTLE_TICKS = 8
        const val RAMP_TICKS = 20
        const val LIGHT_LUX = 5f
        const val BRIGHT_LUX = 5_000f
        const val SETTLED_SOURCE = 10
    }
}
