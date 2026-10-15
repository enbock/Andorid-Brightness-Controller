package com.ambientbrightness.core.BackgroundServiceUseCase

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessSnapshot
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.BackgroundNotificationBuilder.BackgroundNotificationBuilder
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BackgroundService.StartOutcome
import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.core.StopBrightnessRestorer.StopBrightnessRestorer
import com.ambientbrightness.core.testdoubles.CallLog
import com.ambientbrightness.core.testdoubles.FakeBatteryOptimizationClient
import com.ambientbrightness.core.testdoubles.FakeControllerStateStore
import com.ambientbrightness.core.testdoubles.RecordingAutomaticBrightnessController
import com.ambientbrightness.core.testdoubles.RecordingForegroundServiceHost
import com.ambientbrightness.core.testdoubles.RecordingSystemBrightnessClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundServiceUseCaseTest {

    @Test
    fun `start brings the foreground service up before the controller and mirrors the F2 status`() =
        runTest {
            val scenario = scenario()
            val useCase = scenario.useCase

            val outcome = useCase.start(StartReason.UserRequest)
            runCurrent()

            assertEquals(StartOutcome.Started, outcome)
            assertEquals(1, scenario.host.started.size)
            assertEquals(1, scenario.controller.startCalls)
            assertEquals(
                listOf("host.startForeground", "controller.start"),
                scenario.log.snapshot().take(2),
            )
            assertTrue(scenario.store.desiredRunning)
            assertEquals(StartReason.UserRequest, scenario.store.lastStartReason)

            scenario.controller.publish(
                BrightnessStatus(isRunning = true, appliedBrightness = BrightnessValue(150)),
            )
            runCurrent()

            assertEquals(BackgroundServiceState.Running(BrightnessValue(150)), useCase.state.value)
            assertTrue(scenario.host.updated.isNotEmpty())
            assertTrue(scenario.host.updated.last().text.contains("150"))
        }

    @Test
    fun `a second start is a no-op`() = runTest {
        val scenario = scenario()

        scenario.useCase.start(StartReason.UserRequest)
        scenario.useCase.start(StartReason.SystemRestart)
        runCurrent()

        assertEquals(1, scenario.host.started.size)
        assertEquals(1, scenario.controller.startCalls)
        assertEquals(StartReason.UserRequest, scenario.store.lastStartReason)
    }

    @Test
    fun `stop stops the controller, restores the snapshot and then stops the foreground service`() =
        runTest {
            val scenario = scenario()
            scenario.controller.snapshot = BrightnessSnapshot(BrightnessValue(77))
            scenario.useCase.start(StartReason.UserRequest)
            runCurrent()
            scenario.controller.publish(
                BrightnessStatus(isRunning = true, appliedBrightness = BrightnessValue(200)),
            )
            runCurrent()

            scenario.useCase.stop()
            runCurrent()

            val calls = scenario.log.snapshot()
            val controllerStop = calls.indexOf("controller.stop")
            val restore = calls.indexOf("brightness.write:77")
            val foregroundStop = calls.indexOf("host.stopForeground")

            assertTrue(controllerStop in 0..<restore, "the controller must stop before the restore: $calls")
            assertTrue(restore in 0..<foregroundStop, "the restore must precede the foreground stop: $calls")
            assertEquals(1, scenario.controller.stopCalls)
            assertEquals(1, scenario.host.stopForegroundCalls)
            assertEquals(
                BrightnessValue(77),
                scenario.brightness.lastWrite,
                "the restored value is the one in effect before the controller started",
            )
            assertFalse(scenario.store.desiredRunning)
            assertEquals(BackgroundServiceState.Stopped, scenario.useCase.state.value)
        }

    @Test
    fun `a redundant stop performs no restore`() = runTest {
        val scenario = scenario()
        scenario.controller.snapshot = BrightnessSnapshot(BrightnessValue(77))
        scenario.useCase.start(StartReason.UserRequest)
        runCurrent()

        scenario.useCase.stop()
        scenario.useCase.stop()
        runCurrent()

        assertEquals(1, scenario.controller.stopCalls)
        assertEquals(1, scenario.host.stopForegroundCalls)
        assertEquals(
            1,
            scenario.brightness.writeAttempts,
            "the second stop must not overwrite a brightness the user may have set since",
        )
    }

    @Test
    fun `a stop without a start is a no-op`() = runTest {
        val scenario = scenario()

        scenario.useCase.stop()
        runCurrent()

        assertEquals(0, scenario.controller.stopCalls)
        assertEquals(0, scenario.host.stopForegroundCalls)
        assertEquals(0, scenario.brightness.writeAttempts)
        assertEquals(BackgroundServiceState.Stopped, scenario.useCase.state.value)
    }

    @Test
    fun `a platform stop is reported as StoppedBySystem and still tears the lifecycle down`() =
        runTest {
            val scenario = scenario()
            scenario.controller.snapshot = BrightnessSnapshot(BrightnessValue(77))
            scenario.useCase.start(StartReason.UserRequest)
            runCurrent()

            scenario.useCase.recordStoppedBySystem()
            runCurrent()

            assertEquals(
                BackgroundServiceState.StoppedBySystem,
                scenario.useCase.state.value,
            )
            assertEquals(1, scenario.controller.stopCalls, "the controller must stop writing")
            assertEquals(1, scenario.host.stopForegroundCalls, "the foreground service must come down")
            assertFalse(scenario.store.desiredRunning)
            assertEquals(
                BrightnessValue(77),
                scenario.brightness.lastWrite,
                "a system stop is treated as a stop, so the pre-controller brightness is restored",
            )
        }

    @Test
    fun `a platform stop without a start changes nothing`() = runTest {
        val scenario = scenario()

        scenario.useCase.recordStoppedBySystem()
        runCurrent()

        assertEquals(0, scenario.controller.stopCalls)
        assertEquals(0, scenario.host.stopForegroundCalls)
        assertEquals(
            BackgroundServiceState.Stopped,
            scenario.useCase.state.value,
            "nothing was running, so the honest state stays Stopped",
        )
    }

    @Test
    fun `a platform stop is not overridden by a later user stop`() = runTest {
        val scenario = scenario()
        scenario.useCase.start(StartReason.UserRequest)
        runCurrent()

        scenario.useCase.recordStoppedBySystem()
        runCurrent()
        val stopCallsAfterSystemStop = scenario.controller.stopCalls

        scenario.useCase.stop()
        runCurrent()

        assertEquals(
            stopCallsAfterSystemStop,
            scenario.controller.stopCalls,
            "the redundant user stop must not tear the service down a second time",
        )
        assertEquals(
            BackgroundServiceState.StoppedBySystem,
            scenario.useCase.state.value,
            "the reason for the stop must survive a redundant call",
        )
    }

    @Test
    fun `no status is mirrored after the controller has stopped`() = runTest {
        val scenario = scenario()
        scenario.useCase.start(StartReason.UserRequest)
        runCurrent()
        scenario.useCase.stop()
        runCurrent()
        val updatesWhenStopped = scenario.host.updated.size

        scenario.controller.publish(
            BrightnessStatus(isRunning = true, appliedBrightness = BrightnessValue(9)),
        )
        runCurrent()

        assertEquals(updatesWhenStopped, scenario.host.updated.size)
        assertEquals(BackgroundServiceState.Stopped, scenario.useCase.state.value)
    }

    @Test
    fun `a failed F2 write is reported as Failed without throwing`() = runTest {
        val scenario = scenario()
        scenario.useCase.start(StartReason.UserRequest)
        runCurrent()

        scenario.controller.publish(
            BrightnessStatus(
                isRunning = true,
                writeState = BrightnessWriteResult.Failed("WRITE_SETTINGS revoked"),
                writeError = "WRITE_SETTINGS revoked",
            ),
        )
        runCurrent()

        assertEquals(
            BackgroundServiceState.Failed("WRITE_SETTINGS revoked"),
            scenario.useCase.state.value,
        )
    }

    @Test
    fun `the battery-optimization exemption is forwarded from the client`() = runTest {
        val scenario = scenario()

        assertFalse(scenario.useCase.batteryOptimizationExempt.first())

        scenario.battery.setExempt(true)

        assertTrue(scenario.useCase.batteryOptimizationExempt.first())
    }

    private fun TestScope.scenario(): Scenario {
        val log = CallLog()
        val controller = RecordingAutomaticBrightnessController(log)
        val host = RecordingForegroundServiceHost(log)
        val store = FakeControllerStateStore()
        val battery = FakeBatteryOptimizationClient()
        val brightness = RecordingSystemBrightnessClient(log)

        val useCase = BackgroundServiceUseCase(
            controller = controller,
            host = host,
            controllerStateStore = store,
            batteryOptimizationClient = battery,
            restorer = StopBrightnessRestorer(brightness),
            notificationBuilder = BackgroundNotificationBuilder(),
            scope = backgroundScope,
        )

        return Scenario(
            useCase = useCase,
            controller = controller,
            host = host,
            store = store,
            battery = battery,
            brightness = brightness,
            log = log,
        )
    }

    private data class Scenario(
        val useCase: BackgroundServiceUseCase,
        val controller: RecordingAutomaticBrightnessController,
        val host: RecordingForegroundServiceHost,
        val store: FakeControllerStateStore,
        val battery: FakeBatteryOptimizationClient,
        val brightness: RecordingSystemBrightnessClient,
        val log: CallLog,
    )
}
