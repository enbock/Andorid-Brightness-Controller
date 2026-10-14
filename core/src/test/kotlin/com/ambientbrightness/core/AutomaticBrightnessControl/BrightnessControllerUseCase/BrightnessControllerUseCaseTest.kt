package com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessControllerUseCase

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.DefaultAmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightUseCase.AmbientLightUseCase
import com.ambientbrightness.core.AmbientLightSensing.ReadingStabiliser.OutlierClampingStabiliser
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorProfile
import com.ambientbrightness.core.AmbientLightSensing.SensorUnavailableReason
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessControllerUseCase.BrightnessControllerUseCase
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessSnapshot
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessTransform
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.BrightnessOffsetService.BrightnessOffsetSource
import com.ambientbrightness.core.BrightnessOffsetService.OffsetApplicationSink
import com.ambientbrightness.core.BrightnessOffsetTransform.BrightnessOffsetTransform
import com.ambientbrightness.core.FakeAmbientLightSensorClient
import com.ambientbrightness.core.FakeClock
import com.ambientbrightness.core.testdoubles.FakeAmbientLightService
import com.ambientbrightness.core.testdoubles.FakeBrightnessMappingCurve
import com.ambientbrightness.core.testdoubles.FakeBrightnessOffsetSource
import com.ambientbrightness.core.testdoubles.FakeBuiltInAdaptiveBrightnessClient
import com.ambientbrightness.core.testdoubles.FakeDeviceBrightnessRangeClient
import com.ambientbrightness.core.testdoubles.FakeHysteresisGate
import com.ambientbrightness.core.testdoubles.FakeScreenStateClient
import com.ambientbrightness.core.testdoubles.FakeSystemBrightnessClient
import com.ambientbrightness.core.testdoubles.FakeWriteSettingsPermissionChecker
import com.ambientbrightness.core.testdoubles.RecordingBrightnessTransform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end behavior of the F2 controller. The fake mapping curve maps one lux to one percent
 * fraction (`lux / 100`), so a reading of `50f` maps to exactly the middle of a `1..255` range
 * and the expected applied values stay easy to read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BrightnessControllerUseCaseTest {

    private val fullRange = BrightnessRange(BrightnessValue(1), BrightnessValue(255))

    @Test
    fun `AC1_1 AC1_3 - applies the mapped brightness and reports it as the current value`() =
        runTest {
            val fixture = Fixture(backgroundScope)

            fixture.controller.start()
            runCurrent()
            fixture.service.publish(50f)
            runCurrent()

            assertEquals(BrightnessValue(128), fixture.brightness.lastWrite)
            val status = fixture.controller.status.value
            assertTrue(status.isRunning)
            assertEquals(BrightnessValue(128), status.appliedBrightness)
            assertEquals(50f, status.lastLux)
            assertEquals(BrightnessWriteResult.Applied(BrightnessValue(128)), status.writeState)
            assertNull(status.writeError)
            assertEquals(1, fixture.service.collectionCount)

            fixture.controller.stop()
        }

    @Test
    fun `AC1_2 - a reading inside the minimum step is suppressed without touching the system`() =
        runTest {
            val fixture = Fixture(backgroundScope)

            fixture.controller.start()
            runCurrent()
            fixture.service.publish(50f)
            runCurrent()
            fixture.service.publish(50f)
            runCurrent()

            assertEquals(1, fixture.brightness.writeAttempts)
            assertEquals(
                BrightnessWriteResult.Suppressed(BrightnessValue(128)),
                fixture.controller.status.value.writeState,
            )
            assertEquals(BrightnessValue(128), fixture.controller.status.value.appliedBrightness)
            assertEquals(128, fixture.brightness.current)

            fixture.controller.stop()
        }

    @Test
    fun `AC2_1 - darkness maps to the positive minimum instead of zero`() = runTest {
        val fixture = Fixture(backgroundScope)

        fixture.controller.start()
        runCurrent()
        fixture.service.publish(0f)
        runCurrent()

        assertEquals(BrightnessValue(1), fixture.brightness.lastWrite)
        assertEquals(BrightnessValue(1), fixture.controller.status.value.appliedBrightness)

        fixture.controller.stop()
    }

    @Test
    fun `AC2_2 - the device range scales the applied value`() = runTest {
        val fixture = Fixture(
            scope = backgroundScope,
            range = BrightnessRange(BrightnessValue(10), BrightnessValue(20)),
        )

        fixture.controller.start()
        runCurrent()
        fixture.service.publish(50f)
        runCurrent()

        assertEquals(BrightnessValue(15), fixture.brightness.lastWrite)

        fixture.controller.stop()
    }

    @Test
    fun `AC2_3 - an unavailable device range falls back to the core default range`() = runTest {
        val fixture = Fixture(scope = backgroundScope, range = null)

        fixture.controller.start()
        runCurrent()
        fixture.service.publish(50f)
        runCurrent()

        assertEquals(BrightnessValue(128), fixture.brightness.lastWrite)

        fixture.controller.stop()
    }

    @Test
    fun `AC2_4 - a failed write is surfaced and stops further writes until re-authorization`() =
        runTest {
            val fixture = Fixture(backgroundScope)

            fixture.controller.start()
            runCurrent()
            fixture.service.publish(50f)
            runCurrent()
            val successfulAttempts = fixture.brightness.writeAttempts

            fixture.brightness.setFailure("WRITE_SETTINGS is not granted.")
            fixture.service.publish(90f)
            runCurrent()

            assertEquals(
                BrightnessWriteResult.Failed("WRITE_SETTINGS is not granted."),
                fixture.controller.status.value.writeState,
            )
            assertEquals("WRITE_SETTINGS is not granted.", fixture.controller.status.value.writeError)
            assertEquals(successfulAttempts + 1, fixture.brightness.writeAttempts)

            fixture.brightness.setFailure(null)
            fixture.service.publish(70f)
            runCurrent()

            assertEquals(successfulAttempts + 1, fixture.brightness.writeAttempts)
            assertEquals(70f, fixture.controller.status.value.lastLux)

            fixture.permission.setGranted(false)
            runCurrent()
            fixture.permission.setGranted(true)
            runCurrent()
            fixture.service.publish(70f)
            runCurrent()

            assertEquals(successfulAttempts + 2, fixture.brightness.writeAttempts)
            assertEquals(BrightnessValue(179), fixture.brightness.lastWrite)
            assertEquals(
                BrightnessWriteResult.Applied(BrightnessValue(179)),
                fixture.controller.status.value.writeState,
            )
            assertNull(fixture.controller.status.value.writeError)

            fixture.controller.stop()
        }

    @Test
    fun `a missing permission never writes`() = runTest {
        val fixture = Fixture(scope = backgroundScope, granted = false)

        fixture.controller.start()
        runCurrent()
        fixture.service.publish(50f)
        runCurrent()

        assertEquals(0, fixture.brightness.writeAttempts)
        assertEquals(50f, fixture.controller.status.value.lastLux)

        fixture.controller.stop()
    }

    @Test
    fun `AC4_1 AC4_2 - the conflict is reported and the controller keeps running`() = runTest {
        val fixture = Fixture(
            scope = backgroundScope,
            adaptiveState = BuiltInAdaptiveBrightnessState.ENABLED,
        )
        assertEquals(
            BuiltInAdaptiveBrightnessState.UNKNOWN,
            fixture.controller.adaptiveBrightnessConflict.value,
        )

        fixture.controller.start()
        runCurrent()

        assertEquals(
            BuiltInAdaptiveBrightnessState.ENABLED,
            fixture.controller.adaptiveBrightnessConflict.value,
        )

        fixture.service.publish(50f)
        runCurrent()
        assertEquals(BrightnessValue(128), fixture.brightness.lastWrite)

        fixture.controller.stop()
    }

    @Test
    fun `AC4_3 - a disabled built-in adaptive brightness reports no conflict`() = runTest {
        val fixture = Fixture(
            scope = backgroundScope,
            adaptiveState = BuiltInAdaptiveBrightnessState.DISABLED,
        )

        fixture.controller.start()
        runCurrent()
        assertEquals(
            BuiltInAdaptiveBrightnessState.DISABLED,
            fixture.controller.adaptiveBrightnessConflict.value,
        )

        fixture.controller.stop()
    }

    @Test
    fun `start captures the restoration snapshot and does not rewrite the current value`() =
        runTest {
            val fixture = Fixture(scope = backgroundScope, currentBrightness = 128)
            assertNull(fixture.controller.snapshot)

            fixture.controller.start()
            runCurrent()

            assertEquals(BrightnessSnapshot(BrightnessValue(128)), fixture.controller.snapshot)

            fixture.service.publish(50f)
            runCurrent()

            assertEquals(0, fixture.brightness.writeAttempts)
            assertEquals(BrightnessValue(128), fixture.controller.status.value.appliedBrightness)
            assertEquals(
                BrightnessWriteResult.Suppressed(BrightnessValue(128)),
                fixture.controller.status.value.writeState,
            )

            fixture.controller.stop()
        }

    @Test
    fun `an unreadable system brightness yields no snapshot`() = runTest {
        val fixture = Fixture(scope = backgroundScope, currentBrightness = null)

        fixture.controller.start()
        runCurrent()

        assertNull(fixture.controller.snapshot)
        assertNull(fixture.controller.status.value.appliedBrightness)

        fixture.controller.stop()
    }

    @Test
    fun `stop halts writes, releases the light stream and both lifecycle calls are idempotent`() =
        runTest {
            val fixture = Fixture(backgroundScope)

            fixture.controller.start()
            fixture.controller.start()
            runCurrent()
            assertEquals(1, fixture.service.collectionCount)

            fixture.service.publish(50f)
            runCurrent()
            val attemptsWhileRunning = fixture.brightness.writeAttempts

            fixture.controller.stop()
            runCurrent()
            fixture.controller.stop()
            runCurrent()

            assertFalse(fixture.controller.status.value.isRunning)
            assertEquals(1, fixture.service.stopCalls)

            fixture.service.publish(90f)
            runCurrent()
            assertEquals(attemptsWhileRunning, fixture.brightness.writeAttempts)
        }

    @Test
    fun `the control loop follows the screen state`() = runTest {
        val fixture = Fixture(scope = backgroundScope, screenOn = false)

        fixture.controller.start()
        runCurrent()
        fixture.service.publish(50f)
        runCurrent()

        assertEquals(0, fixture.service.collectionCount)
        assertEquals(0, fixture.brightness.writeAttempts)

        fixture.screen.setScreenOn(true)
        runCurrent()
        fixture.service.publish(50f)
        runCurrent()

        assertEquals(1, fixture.service.collectionCount)
        assertEquals(BrightnessValue(128), fixture.brightness.lastWrite)

        fixture.controller.stop()
    }

    @Test
    fun `a transform registered while running takes effect on the next reapply`() = runTest {
        val fixture = Fixture(backgroundScope)

        fixture.controller.start()
        runCurrent()
        fixture.service.publish(50f)
        runCurrent()
        assertEquals(BrightnessValue(128), fixture.brightness.lastWrite)

        fixture.controller.registerTransform(RecordingBrightnessTransform(offset = 0.1f))
        fixture.controller.reapply()
        runCurrent()

        assertEquals(BrightnessValue(153), fixture.brightness.lastWrite)
        assertEquals(BrightnessValue(153), fixture.controller.status.value.appliedBrightness)

        fixture.controller.stop()
    }

    @Test
    fun `reapply before the first accepted reading or while stopped writes nothing`() = runTest {
        val fixture = Fixture(backgroundScope)

        fixture.controller.reapply()
        runCurrent()
        assertEquals(0, fixture.brightness.writeAttempts)

        fixture.controller.start()
        runCurrent()
        fixture.controller.reapply()
        runCurrent()
        assertEquals(0, fixture.brightness.writeAttempts)

        fixture.controller.stop()
        runCurrent()
        fixture.controller.reapply()
        runCurrent()
        assertEquals(0, fixture.brightness.writeAttempts)
    }

    @Test
    fun `AC1_1 - an offset change while running re-applies using the last known lux`() = runTest {
        val offsetSource = FakeBrightnessOffsetSource()
        val fixture = Fixture(
            scope = backgroundScope,
            offsetSource = offsetSource,
            transform = BrightnessOffsetTransform(offsetSource, NoOpOffsetApplicationSink),
        )

        fixture.controller.start()
        runCurrent()
        fixture.service.publish(50f)
        runCurrent()
        assertEquals(BrightnessValue(128), fixture.brightness.lastWrite)
        val attemptsAfterSample = fixture.brightness.writeAttempts

        // No new ambient-light sample arrives: the offset change alone must drive a re-apply.
        offsetSource.emit(BrightnessOffset.of(50))
        runCurrent()

        assertEquals(attemptsAfterSample + 1, fixture.brightness.writeAttempts)
        assertEquals(BrightnessValue(255), fixture.brightness.lastWrite)
        assertEquals(50f, fixture.controller.status.value.lastLux)

        fixture.controller.stop()
        runCurrent()
    }

    @Test
    fun `AC1_2 - an offset change before the first reading writes nothing and applies on the first sample`() =
        runTest {
            val offsetSource = FakeBrightnessOffsetSource()
            val fixture = Fixture(
                scope = backgroundScope,
                offsetSource = offsetSource,
                transform = BrightnessOffsetTransform(offsetSource, NoOpOffsetApplicationSink),
            )

            fixture.controller.start()
            runCurrent()

            offsetSource.emit(BrightnessOffset.of(30))
            runCurrent()

            assertEquals(0, fixture.brightness.writeAttempts)

            fixture.service.publish(50f)
            runCurrent()

            // 0.5 fraction + 0.30 of the 1..255 span -> 1 + round(254 * 0.8) = 204.
            assertEquals(BrightnessValue(204), fixture.brightness.lastWrite)

            fixture.controller.stop()
            runCurrent()
        }

    @Test
    fun `stop cancels the offset observation so later changes are no longer applied`() = runTest {
        val offsetSource = FakeBrightnessOffsetSource()
        val fixture = Fixture(
            scope = backgroundScope,
            offsetSource = offsetSource,
            transform = BrightnessOffsetTransform(offsetSource, NoOpOffsetApplicationSink),
        )

        fixture.controller.start()
        runCurrent()
        fixture.service.publish(50f)
        runCurrent()
        fixture.controller.stop()
        runCurrent()

        val attemptsAfterStop = fixture.brightness.writeAttempts
        offsetSource.emit(BrightnessOffset.of(50))
        runCurrent()

        assertEquals(attemptsAfterStop, fixture.brightness.writeAttempts)
    }

    @Test
    fun `a raw sensor reading reaches the system brightness through the full F1 pipeline`() =
        runTest {
            val sensorProfile =
                SensorProfile("Test light sensor", maxRangeLux = 10_000f, minDelayMicros = 0)
            val sensor = FakeAmbientLightSensorClient(SensorAvailability.Available(sensorProfile))
            val lightService = DefaultAmbientLightService(
                sensorClient = sensor,
                useCase = AmbientLightUseCase(
                    sensorClient = sensor,
                    stabiliser = OutlierClampingStabiliser(maxRangeLux = sensorProfile.maxRangeLux),
                    clock = FakeClock(),
                ),
            )
            val brightness = FakeSystemBrightnessClient(current = 10)
            val controller = BrightnessControllerUseCase(
                ambientLightService = lightService,
                screenStateClient = FakeScreenStateClient(isScreenOn = true),
                brightnessClient = brightness,
                deviceRangeClient = FakeDeviceBrightnessRangeClient(fullRange),
                permissionChecker = FakeWriteSettingsPermissionChecker(granted = true),
                builtInAdaptiveBrightnessClient =
                FakeBuiltInAdaptiveBrightnessClient(BuiltInAdaptiveBrightnessState.DISABLED),
                scope = backgroundScope,
                curve = FakeBrightnessMappingCurve(),
                hysteresisGate = FakeHysteresisGate(),
            )

            controller.start()
            runCurrent()
            sensor.push(50f)
            advanceTimeBy(500L)
            runCurrent()

            assertEquals(SensorAvailability.Available(sensorProfile), lightService.availability.value)
            assertEquals(BrightnessValue(128), brightness.lastWrite)
            assertEquals(50f, controller.status.value.lastLux)

            controller.stop()
            runCurrent()

            assertFalse(sensor.isRegistered)
        }

    @Test
    fun `an unavailable sensor never falls back to brightness writes`() = runTest {
        val fixture = Fixture(backgroundScope)
        val unavailable = FakeAmbientLightService(
            SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor),
        )
        val controller = BrightnessControllerUseCase(            ambientLightService = unavailable,
            screenStateClient = fixture.screen,
            brightnessClient = fixture.brightness,
            deviceRangeClient = FakeDeviceBrightnessRangeClient(fullRange),
            permissionChecker = fixture.permission,
            builtInAdaptiveBrightnessClient = fixture.adaptive,
            scope = backgroundScope,
            curve = FakeBrightnessMappingCurve(),
            hysteresisGate = FakeHysteresisGate(),
        )

        controller.start()
        runCurrent()
        unavailable.publish(50f)
        runCurrent()

        assertEquals(0, fixture.brightness.writeAttempts)
        assertEquals(1, unavailable.collectionCount)

        controller.stop()
    }

    private class Fixture(
        scope: CoroutineScope,
        currentBrightness: Int? = 20,
        screenOn: Boolean = true,
        adaptiveState: BuiltInAdaptiveBrightnessState = BuiltInAdaptiveBrightnessState.DISABLED,
        granted: Boolean = true,
        range: BrightnessRange? = BrightnessRange(BrightnessValue(1), BrightnessValue(255)),
        offsetSource: BrightnessOffsetSource? = null,
        transform: BrightnessTransform? = null,
    ) {
        val brightness = FakeSystemBrightnessClient(current = currentBrightness)
        val screen = FakeScreenStateClient(isScreenOn = screenOn)
        val service = FakeAmbientLightService()
        val permission = FakeWriteSettingsPermissionChecker(granted = granted)
        val adaptive = FakeBuiltInAdaptiveBrightnessClient(adaptiveState)

        val controller = BrightnessControllerUseCase(
            ambientLightService = service,
            screenStateClient = screen,
            brightnessClient = brightness,
            deviceRangeClient = FakeDeviceBrightnessRangeClient(range),
            permissionChecker = permission,
            builtInAdaptiveBrightnessClient = adaptive,
            scope = scope,
            curve = FakeBrightnessMappingCurve(),
            hysteresisGate = FakeHysteresisGate(),
        ).also { controller ->
            transform?.let(controller::registerTransform)
            offsetSource?.let(controller::registerOffsetSource)
        }
    }
}

/** Offset sink that ignores publications; the controller tests only assert the applied value. */
private object NoOpOffsetApplicationSink : OffsetApplicationSink {
    override fun publish(application: BrightnessOffsetApplication) = Unit
}
