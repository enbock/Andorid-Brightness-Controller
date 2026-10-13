package com.ambientbrightness.app

import com.ambientbrightness.app.Composition.AmbientBrightnessComponent
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessMappingCurve.DefaultBrightnessMappingCurve
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRangeResolver.BrightnessRangeResolver
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRangeScaler.BrightnessRangeScaler
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.AutomaticBrightnessControl.SystemBrightnessClient
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import com.ambientbrightness.core.SettingsStore.SettingsKeys
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
 * End-to-end F4 verification through the real app composition: the dummy sensor feeds the injected F1
 * stabiliser, F2's controller pipeline and F4's ramp, and a recording brightness client observes what
 * is written. The injected [Clock] and the virtual time advance together, so every debounce window and
 * ramp step is deterministic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmbientBrightnessResponseTuningTest {

    @Test
    fun `a transient change is dropped, a sustained one is ramped in bounded monotone writes`() =
        runTest {
            val scenario = scenario(persistedSpeedPercent = null)
            settle(scenario)
            scenario.brightnessClient.writes.clear()

            // One tick of bright light, then back to the previous level: nothing may be applied (AC3.1).
            tick(scenario, BRIGHT_LUX)
            tick(scenario, LIGHT_LUX)
            tick(scenario, LIGHT_LUX)
            assertTrue(
                "a transient change must not reach the system brightness: " +
                    scenario.brightnessClient.writes,
                scenario.brightnessClient.writes.isEmpty(),
            )

            // A change seen for the first time is held for the debounce window (AC3.2).
            tick(scenario, BRIGHT_LUX)
            assertTrue(
                "the change must be held while its debounce window has not elapsed: " +
                    scenario.brightnessClient.writes,
                scenario.brightnessClient.writes.isEmpty(),
            )

            repeat(RAMP_TICKS) { tick(scenario, BRIGHT_LUX) }
            val writes = scenario.brightnessClient.writes.toList()

            assertTrue(
                "the sustained change must be applied in several steps: $writes",
                writes.size >= 3,
            )
            assertEquals("the ramp must not backtrack: $writes", writes.sorted(), writes)
            assertTrue(
                "no step may exceed the anti-flash cap: $writes",
                writes.zipWithNext().all { (previous, next) ->
                    next - previous <= MAX_WRITE_STEP_UNITS
                },
            )
            assertEquals(
                "the ramp must converge on the settled bright value: $writes",
                settledUnitsFor(BRIGHT_LUX),
                writes.last(),
            )
        }

    @Test
    fun `a persisted response speed changes when the pipeline reacts after a restart`() = runTest {
        val fast = firstReactionTickIndex(persistedSpeedPercent = ResponseSpeed.FASTEST_PERCENT)
        val slow = firstReactionTickIndex(persistedSpeedPercent = ResponseSpeed.SLOWEST_PERCENT)

        assertTrue(
            "the fast setting must react within $MAX_REACTION_TICKS ticks: $fast",
            fast in 1..MAX_REACTION_TICKS,
        )
        assertTrue(
            "the slow setting must react within $MAX_REACTION_TICKS ticks: $slow",
            slow in 1..MAX_REACTION_TICKS,
        )
        assertTrue(
            "the slow setting must hold the change longer than the fast one: $slow <= $fast",
            slow > fast,
        )
    }

    @Test
    fun `a persisted response speed is the value the running service exposes`() = runTest {
        val scenario = scenario(persistedSpeedPercent = 80)

        assertEquals(
            ResponseSpeed.of(80),
            scenario.component.responseTuningService.responseSpeed.value,
        )
    }

    /**
     * Builds a fresh component over the persisted speed (the simulated restart), settles it on the low
     * light level and then reports on which tick after the light jump the first system write happens.
     */
    private suspend fun TestScope.firstReactionTickIndex(persistedSpeedPercent: Int): Int {
        val scenario = scenario(persistedSpeedPercent)
        settle(scenario)
        scenario.brightnessClient.writes.clear()

        for (tickIndex in 1..MAX_REACTION_TICKS) {
            tick(scenario, BRIGHT_LUX)
            if (scenario.brightnessClient.writes.isNotEmpty()) return tickIndex
        }

        return NEVER
    }

    private suspend fun TestScope.scenario(persistedSpeedPercent: Int?): Scenario {
        val clock = TestClock()
        val store = InMemorySettingsStore()
        persistedSpeedPercent?.let { store.set(SettingsKeys.RESPONSE_SPEED_PERCENT, it) }
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
            settingsStore = store,
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

    /** The device brightness F2 computes for [lux] once settled, i.e. the value the ramp converges on. */
    private fun settledUnitsFor(lux: Float): Int = BrightnessRangeScaler
        .scale(DefaultBrightnessMappingCurve().toFraction(lux), BrightnessRangeResolver.DEFAULT_RANGE)
        .value

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
        const val RAMP_TICKS = 20
        const val MAX_REACTION_TICKS = 6
        const val NEVER = -1

        /** Low light for the settle phase, and bright light comfortably inside one hysteresis region. */
        const val LIGHT_LUX = 5f
        const val BRIGHT_LUX = 5_000f

        const val SETTLED_BRIGHT_UNITS_SOURCE = 10

        /** 0.15 of the default 1..255 span, plus the unit the rounding may add. */
        const val MAX_WRITE_STEP_UNITS = 39
    }
}
