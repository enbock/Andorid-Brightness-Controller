package com.ambientbrightness.app

import com.ambientbrightness.app.Composition.AmbientBrightnessComponent
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BackgroundService.StartOutcome
import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import com.ambientbrightness.core.SettingsStore.SettingsKeys
import com.ambientbrightness.core.Shared.Clock
import com.ambientbrightness.infrastructure.AmbientLightSensing.AmbientLightSensorClient.Dummy.DummyAmbientLightSensorClient
import com.ambientbrightness.infrastructure.AmbientLightSensing.ScreenStateClient.Dummy.DummyScreenStateClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient.Dummy.DummyBuiltInAdaptiveBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Dummy.DummyDeviceBrightnessRangeClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Dummy.DummyWriteSettingsPermissionChecker
import com.ambientbrightness.infrastructure.BackgroundService.BatteryOptimizationClient.Dummy.DummyBatteryOptimizationClient
import com.ambientbrightness.infrastructure.BackgroundService.ControllerStateStore.Dummy.DummyControllerStateStore
import com.ambientbrightness.infrastructure.BackgroundService.ForegroundServiceHost.DummyForegroundServiceHost
import com.ambientbrightness.infrastructure.SettingsStore.SettingsStore.InMemory.InMemorySettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end F5 verification through the real app composition: the dummy sensor feeds F1, F2's
 * controller pipeline and F4's ramp, a recording brightness client observes what is written, and the
 * background service drives the lifecycle. The injected [Clock] and the virtual time advance
 * together, so start, restart and stop are deterministic without a device.
 *
 * The process-kill (`START_STICKY` restart) and swipe-from-recents aspects cannot be reproduced on a
 * plain JVM: they need the Android platform to reclaim and restart the process, so the restart here
 * is simulated by composing a fresh component over the same persisted state. The platform parts stay
 * compile-verified by the JVM tests here and are covered on a device by the `:infrastructure`
 * instrumented test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmbientBrightnessBackgroundServiceTest {

    @Test
    fun `AC1_1 - start reaches Running with the F2 applied brightness`() = runTest {
        val scenario = scenario()

        val outcome = scenario.component.backgroundService.start(StartReason.UserRequest)
        runCurrent()

        assertEquals(StartOutcome.Started, outcome)
        assertTrue("the foreground host must be promoted", scenario.host.isInForeground)
        assertTrue("the desired-running flag must be persisted", scenario.controllerStateStore.desiredRunning)
        assertEquals(
            StartReason.UserRequest,
            scenario.controllerStateStore.lastStartReason,
        )

        settle(scenario)
        repeat(RAMP_TICKS) { tick(scenario, BRIGHT_LUX) }

        val state = scenario.component.backgroundService.state.value
        assertTrue("the service must reach Running, was $state", state is BackgroundServiceState.Running)
        val applied = (state as BackgroundServiceState.Running).appliedBrightness
        assertTrue("the controller must have applied a brightness", applied != null)
        assertEquals(
            "the service state must carry the brightness F2 applied last",
            scenario.brightnessClient.writes.last(),
            applied?.value,
        )
        assertTrue(
            "the applied brightness must follow the sustained light change: " +
                scenario.brightnessClient.writes,
            scenario.brightnessClient.writes.last() > SETTLED_SOURCE,
        )
    }

    @Test
    fun `AC2_1 - a restart over the persisted state resumes Running with the stored settings`() =
        runTest {
            val settingsStore = InMemorySettingsStore()
            settingsStore.set(SettingsKeys.RESPONSE_SPEED_PERCENT, PERSISTED_SPEED_PERCENT)
            val controllerStateStore = DummyControllerStateStore()

            val beforeKill = scenario(
                store = settingsStore,
                controllerStateStore = controllerStateStore,
            )
            beforeKill.component.backgroundService.start(StartReason.UserRequest)
            runCurrent()
            settle(beforeKill)
            repeat(RAMP_TICKS) { tick(beforeKill, BRIGHT_LUX) }
            val appliedBeforeKill = appliedBrightness(beforeKill)
            assertTrue("the first run must have applied a brightness", appliedBeforeKill != null)
            assertTrue(
                "the desired-running flag must survive the simulated process kill",
                controllerStateStore.desiredRunning,
            )

            // Simulated process kill and START_STICKY restart: a brand-new component over the same
            // persisted settings and controller state.
            val restarted = scenario(
                store = settingsStore,
                controllerStateStore = controllerStateStore,
            )
            val outcome = restarted.component.backgroundService.start(StartReason.SystemRestart)
            runCurrent()
            settle(restarted)
            repeat(RAMP_TICKS) { tick(restarted, BRIGHT_LUX) }

            assertEquals(StartOutcome.Started, outcome)
            assertEquals(
                "the restart must reuse the persisted response speed (AC2.1)",
                ResponseSpeed.of(PERSISTED_SPEED_PERCENT),
                restarted.component.responseTuningService.responseSpeed.value,
            )
            val state = restarted.component.backgroundService.state.value
            assertTrue("the restart must resume Running, was $state", state is BackgroundServiceState.Running)
            assertEquals(
                "the restart must resume with the same applied brightness as before the kill",
                appliedBeforeKill,
                (state as BackgroundServiceState.Running).appliedBrightness,
            )
        }

    @Test
    fun `AC3_1 AC3_2 AC3_3 - stop returns to Stopped and restores the pre-controller brightness`() =
        runTest {
            val scenario = scenario()
            scenario.component.backgroundService.start(StartReason.UserRequest)
            runCurrent()
            settle(scenario)
            repeat(RAMP_TICKS) { tick(scenario, BRIGHT_LUX) }

            val snapshot = scenario.component.controller.snapshot
            assertTrue("the controller must have captured a snapshot", snapshot != null)
            scenario.brightnessClient.writes.clear()

            scenario.component.backgroundService.stop()
            runCurrent()

            assertEquals(
                BackgroundServiceState.Stopped,
                scenario.component.backgroundService.state.value,
            )
            assertFalse("the foreground service must be stopped", scenario.host.isInForeground)
            assertFalse(
                "the stop must clear the desired-running flag",
                scenario.controllerStateStore.desiredRunning,
            )
            assertEquals(
                "the pre-controller brightness must be restored through F2's client",
                snapshot?.brightness?.value,
                scenario.brightnessClient.writes.last(),
            )
        }

    private fun appliedBrightness(scenario: Scenario): BrightnessValue? {
        val state = scenario.component.backgroundService.state.value
        return (state as? BackgroundServiceState.Running)?.appliedBrightness
    }

    private fun TestScope.scenario(
        store: InMemorySettingsStore = InMemorySettingsStore(),
        controllerStateStore: DummyControllerStateStore = DummyControllerStateStore(),
    ): Scenario {
        val clock = TestClock()
        val sensorClient = DummyAmbientLightSensorClient()
        val brightnessClient = RecordingSystemBrightnessClient(initialValue = SETTLED_SOURCE)
        val host = DummyForegroundServiceHost()
        val component = AmbientBrightnessComponent(
            sensorClient = sensorClient,
            screenStateClient = DummyScreenStateClient(initiallyScreenOn = true),
            controllerScope = backgroundScope,
            brightnessClient = brightnessClient,
            deviceRangeClient = DummyDeviceBrightnessRangeClient(),
            writeSettingsPermissionChecker =
                DummyWriteSettingsPermissionChecker(initiallyGranted = true),
            builtInAdaptiveBrightnessClient = DummyBuiltInAdaptiveBrightnessClient(),
            settingsStore = store,
            clock = clock,
            foregroundServiceHost = host,
            controllerStateStore = controllerStateStore,
            batteryOptimizationClient = DummyBatteryOptimizationClient(initiallyExempt = false),
        )

        return Scenario(
            component = component,
            clock = clock,
            sensorClient = sensorClient,
            brightnessClient = brightnessClient,
            host = host,
            controllerStateStore = controllerStateStore,
        )
    }

    /** Runs the pipeline at the low light level until the applied brightness has settled. */
    private fun TestScope.settle(scenario: Scenario) {
        repeat(SETTLE_TICKS) { tick(scenario, LIGHT_LUX) }
    }

    /**
     * One sampling interval: the clock and the virtual time advance together, a new reading arrives
     * in between, and the sampling loop is then allowed to process it.
     */
    private fun TestScope.tick(scenario: Scenario, lux: Float) {
        scenario.clock.advanceBy(SAMPLING_INTERVAL_MILLIS)
        scenario.sensorClient.emit(lux)
        testScheduler.advanceTimeBy(SAMPLING_INTERVAL_MILLIS)
        runCurrent()
    }

    private data class Scenario(
        val component: AmbientBrightnessComponent,
        val clock: TestClock,
        val sensorClient: DummyAmbientLightSensorClient,
        val brightnessClient: RecordingSystemBrightnessClient,
        val host: DummyForegroundServiceHost,
        val controllerStateStore: DummyControllerStateStore,
    )

    private class TestClock(private var currentMillis: Long = 0L) : Clock {
        override fun nowMillis(): Long = currentMillis

        fun advanceBy(deltaMillis: Long) {
            currentMillis += deltaMillis
        }
    }

    private class RecordingSystemBrightnessClient(initialValue: Int) : SystemBrightnessClient {

        val writes: MutableList<Int> = mutableListOf()

        private var currentValue: Int? = initialValue

        override fun readCurrent(): Int? = currentValue

        override fun write(value: BrightnessValue): BrightnessWriteResult {
            writes += value.value
            currentValue = value.value
            return BrightnessWriteResult.Applied(value)
        }
    }

    private companion object {
        const val SAMPLING_INTERVAL_MILLIS = 500L
        const val SETTLE_TICKS = 8
        const val RAMP_TICKS = 20

        const val LIGHT_LUX = 5f
        const val BRIGHT_LUX = 5_000f

        const val SETTLED_SOURCE = 10
        const val PERSISTED_SPEED_PERCENT = 80
    }
}
