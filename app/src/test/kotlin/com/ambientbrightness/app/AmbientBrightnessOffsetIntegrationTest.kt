package com.ambientbrightness.app

import com.ambientbrightness.app.Composition.AmbientBrightnessComponent
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffset
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.Shared.Clock
import com.ambientbrightness.infrastructure.AmbientLightSensing.AmbientLightSensorClient.Dummy.DummyAmbientLightSensorClient
import com.ambientbrightness.infrastructure.AmbientLightSensing.ScreenStateClient.Dummy.DummyScreenStateClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessClient.Dummy.DummyBuiltInAdaptiveBrightnessClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.DeviceBrightnessRangeClient.Dummy.DummyDeviceBrightnessRangeClient
import com.ambientbrightness.infrastructure.AutomaticBrightnessControl.WriteSettingsPermissionChecker.Dummy.DummyWriteSettingsPermissionChecker
import com.ambientbrightness.infrastructure.SettingsStore.SettingsStore.InMemory.InMemorySettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end F3 verification through the real app composition: the dummy sensor feeds the F1
 * stabiliser, F2's controller pipeline (with F3's offset transform registered before F4's ramp) and a
 * recording brightness client observes what is written. The injected [Clock] and the virtual time
 * advance together, so a change made through the offset service is applied deterministically without
 * a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmbientBrightnessOffsetIntegrationTest {

    @Test
    fun `AC1_1 - changing the offset is applied within the 1 s budget with no new sample`() =
        runTest {
            val scenario = scenario()
            settle(scenario)
            val settledWrite = scenario.brightnessClient.writes.last()
            scenario.brightnessClient.writes.clear()

            scenario.component.brightnessOffsetService.setOffset(BrightnessOffset.of(50))
            runCurrent()

            // No ambient-light sample is emitted here: the offset change alone must drive the write.
            var elapsedMillis = 0L
            while (scenario.brightnessClient.writes.isEmpty() && elapsedMillis < OFFSET_BUDGET_MILLIS) {
                advance(scenario, SAMPLING_INTERVAL_MILLIS)
                elapsedMillis += SAMPLING_INTERVAL_MILLIS
            }

            assertTrue(
                "the offset change must reach the system within the 1 s budget: " +
                    scenario.brightnessClient.writes,
                scenario.brightnessClient.writes.isNotEmpty(),
            )
            assertTrue("the write must arrive within the budget: $elapsedMillis ms", elapsedMillis <=
                OFFSET_BUDGET_MILLIS)
            assertTrue(
                "a positive offset must raise the applied brightness above the settled value " +
                    "($settledWrite): ${scenario.brightnessClient.writes}",
                scenario.brightnessClient.writes.max() > settledWrite,
            )
        }

    @Test
    fun `AC2_3 - a most-negative offset clamps to the range minimum and is reported`() = runTest {
        val scenario = scenario()
        settle(scenario)

        scenario.component.brightnessOffsetService.setOffset(BrightnessOffset.of(-50))
        runCurrent()
        repeat(CLAMP_TICKS) { tick(scenario, LIGHT_LUX) }
        runCurrent()

        assertEquals(
            BrightnessOffsetApplication.ClampedToMinimum,
            scenario.component.brightnessOffsetService.application.value,
        )
        assertTrue(
            "the applied value must never be 0: ${scenario.brightnessClient.writes}",
            scenario.brightnessClient.writes.all { it >= BrightnessValue.MINIMUM },
        )
        assertEquals(
            "the offset must drive the screen to the range minimum: " +
                scenario.brightnessClient.writes,
            BrightnessValue.MINIMUM,
            scenario.brightnessClient.writes.last(),
        )
    }

    private suspend fun TestScope.scenario(): Scenario {
        val clock = TestClock()
        val sensorClient = DummyAmbientLightSensorClient()
        val brightnessClient =
            RecordingSystemBrightnessClient(initialValue = SETTLED_BRIGHT_UNITS_SOURCE)
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
        )

        component.controller.start()
        runCurrent()

        return Scenario(
            component = component,
            clock = clock,
            sensorClient = sensorClient,
            brightnessClient = brightnessClient,
        )
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

    /** Advances the clock and the virtual time by [millis] without emitting a new reading. */
    private fun TestScope.advance(scenario: Scenario, millis: Long) {
        scenario.clock.advanceBy(millis)
        testScheduler.advanceTimeBy(millis)
        runCurrent()
    }

    private data class Scenario(
        val component: AmbientBrightnessComponent,
        val clock: TestClock,
        val sensorClient: DummyAmbientLightSensorClient,
        val brightnessClient: RecordingSystemBrightnessClient,
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
        const val CLAMP_TICKS = 4
        const val OFFSET_BUDGET_MILLIS = 1_000L

        /** Low light: the curve's low end, so a `-50` offset clamps to the range minimum. */
        const val LIGHT_LUX = 5f

        const val SETTLED_BRIGHT_UNITS_SOURCE = 10
    }
}
