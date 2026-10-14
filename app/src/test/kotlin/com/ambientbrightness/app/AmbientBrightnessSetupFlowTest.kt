package com.ambientbrightness.app

import com.ambientbrightness.app.Composition.AmbientBrightnessComponent
import com.ambientbrightness.core.AppSetupService.MissingReason
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupRequirementId
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.core.Shared.Clock
import com.ambientbrightness.infrastructure.AmbientLightSensing.AmbientLightSensorClient.Dummy.DummyAmbientLightSensorClient
import com.ambientbrightness.infrastructure.AmbientLightSensing.ScreenStateClient.Dummy.DummyScreenStateClient
import com.ambientbrightness.infrastructure.AppSetupService.NotificationPermissionChecker.Dummy.DummyNotificationPermissionChecker
import com.ambientbrightness.infrastructure.AppSetupService.SetupStateStore.Dummy.DummySetupStateStore
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
 * End-to-end F6 verification through the real app composition: the real F5 background service drives
 * the lifecycle, the aggregator observes its state, and the Dummy adapters stand in for the platform
 * grants. The first-run acknowledgement and the two degraded paths (a revoked `WRITE_SETTINGS` and a
 * missing notification permission) are verified over the same graph F7 will consume, so the wiring in
 * [AmbientBrightnessComponent] — not just the :core derivation — is exercised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmbientBrightnessSetupFlowTest {

    @Test
    fun `US1 AC3 - completing and skipping persist the acknowledgement through the component`() =
        runTest {
            val completeStore = DummySetupStateStore()
            val completeScenario = scenario(WorkingBrightnessClient(), setupStateStore = completeStore)
            assertFalse(completeScenario.component.setupService.setupAcknowledged.value)

            completeScenario.component.setupService.completeSetup()

            assertTrue(completeScenario.component.setupService.setupAcknowledged.value)
            assertTrue(completeStore.setupAcknowledged)

            val skipStore = DummySetupStateStore()
            val skipScenario = scenario(WorkingBrightnessClient(), setupStateStore = skipStore)

            skipScenario.component.setupService.skipSetup()

            assertTrue(skipScenario.component.setupService.setupAcknowledged.value)
            assertTrue(skipStore.setupAcknowledged)
        }

    @Test
    fun `US1 AC3 - a fresh component over the persisted store starts acknowledged`() = runTest {
        val store = DummySetupStateStore(setupAcknowledged = true)
        val scenario = scenario(WorkingBrightnessClient(), setupStateStore = store)

        assertTrue(scenario.component.setupService.setupAcknowledged.value)
    }

    @Test
    fun `US4 AC2 - a failed write surfaces as a revoked write settings requirement`() = runTest {
        val scenario = scenario(FailingBrightnessClient())
        scenario.component.backgroundService.start(StartReason.UserRequest)
        runCurrent()
        repeat(SETTLE_TICKS) { tick(scenario, LIGHT_LUX) }

        assertTrue(
            "F5 must report the failed write, was ${scenario.component.backgroundService.state.value}",
            scenario.component.backgroundService.state.value is BackgroundServiceState.Failed,
        )

        runCurrent()
        val requirement = scenario.component.setupService.requirement(SetupRequirementId.WriteSettings)
        assertEquals(RequirementState.Missing(MissingReason.Revoked), requirement.state)
        assertTrue(
            scenario.component.setupService.status.value.blockers
                .contains(SetupRequirementId.WriteSettings),
        )
        assertFalse(scenario.component.setupService.isReadyToStart.value)
    }

    @Test
    fun `US4 AC3 - missing notifications warn while running but never block the start`() = runTest {
        val scenario = scenario(
            brightnessClient = WorkingBrightnessClient(),
            notificationPermissionChecker = DummyNotificationPermissionChecker(initiallyGranted = false),
        )
        scenario.component.backgroundService.start(StartReason.UserRequest)
        runCurrent()
        repeat(SETTLE_TICKS) { tick(scenario, LIGHT_LUX) }
        runCurrent()

        assertTrue(
            "the controller must be running, was ${scenario.component.backgroundService.state.value}",
            scenario.component.backgroundService.state.value is BackgroundServiceState.Running,
        )
        assertTrue(scenario.component.setupService.notificationWarning.value)
        assertEquals(
            RequirementState.Missing(MissingReason.NotGranted),
            scenario.component.setupService.requirement(SetupRequirementId.Notifications).state,
        )
        assertFalse(
            scenario.component.setupService.status.value.blockers
                .contains(SetupRequirementId.Notifications),
        )
        assertTrue(
            "the notification permission is advisory",
            scenario.component.setupService.isReadyToStart.value,
        )
    }

    private fun TestScope.scenario(
        brightnessClient: SystemBrightnessClient,
        notificationPermissionChecker: DummyNotificationPermissionChecker =
            DummyNotificationPermissionChecker(initiallyGranted = true),
        setupStateStore: DummySetupStateStore = DummySetupStateStore(),
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
            settingsStore = InMemorySettingsStore(),
            clock = clock,
            foregroundServiceHost = DummyForegroundServiceHost(),
            controllerStateStore = DummyControllerStateStore(),
            batteryOptimizationClient = DummyBatteryOptimizationClient(initiallyExempt = false),
            notificationPermissionChecker = notificationPermissionChecker,
            setupStateStore = setupStateStore,
        )

        return Scenario(
            component = component,
            clock = clock,
            sensorClient = sensorClient,
        )
    }

    /**
     * One sampling interval: the injected clock and the virtual time advance together, a new reading
     * arrives in between, and the sampling loop is then allowed to process it. Mirrors the F5
     * end-to-end test so the control loop reaches a steady state deterministically.
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
    )

    private class TestClock(private var currentMillis: Long = 0L) : Clock {
        override fun nowMillis(): Long = currentMillis

        fun advanceBy(deltaMillis: Long) {
            currentMillis += deltaMillis
        }
    }

    private class WorkingBrightnessClient : SystemBrightnessClient {

        private var currentValue: Int = SETTLED_SOURCE

        override fun readCurrent(): Int? = currentValue

        override fun write(value: BrightnessValue): BrightnessWriteResult {
            currentValue = value.value
            return BrightnessWriteResult.Applied(value)
        }
    }

    /** Every write fails, as F2 does when `WRITE_SETTINGS` is revoked while the controller runs. */
    private class FailingBrightnessClient : SystemBrightnessClient {

        override fun readCurrent(): Int? = SETTLED_SOURCE

        override fun write(value: BrightnessValue): BrightnessWriteResult =
            BrightnessWriteResult.Failed("WRITE_SETTINGS revoked")
    }

    private companion object {
        const val SAMPLING_INTERVAL_MILLIS = 500L
        const val SETTLE_TICKS = 8
        const val LIGHT_LUX = 5f
        const val SETTLED_SOURCE = 10
    }
}

private fun com.ambientbrightness.core.AppSetupService.AppSetupService.requirement(
    id: SetupRequirementId,
): com.ambientbrightness.core.AppSetupService.SetupRequirement =
    status.value.requirements.single { it.id == id }
