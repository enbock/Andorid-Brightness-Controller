package com.ambientbrightness.core.ResponsiveBrightnessRamp

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessContext
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.FakeClock
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResponsiveBrightnessRampTest {

    private val clock = FakeClock()
    private val responseSpeed = MutableStateFlow(ResponseSpeed.of(50))
    private val range = BrightnessRange(min = BrightnessValue(1), max = BrightnessValue(101))

    private fun ramp() = ResponsiveBrightnessRamp(responseSpeed = responseSpeed, clock = clock)

    private fun ResponsiveBrightnessRamp.step(
        fraction: Float,
        afterMillis: Long = SAMPLING_INTERVAL_MILLIS,
        appliedBrightness: BrightnessValue? = null,
    ): Float {
        clock.advanceBy(afterMillis)
        return apply(
            fraction,
            BrightnessContext(lux = 0f, range = range, currentBrightness = appliedBrightness),
        )
    }

    @Test
    fun `AC2_1 - a full-range change becomes several bounded steps, not one jump`() {
        val subject = ramp()
        subject.step(0f, afterMillis = 0L, appliedBrightness = BrightnessValue(1))

        val applied = (1..12).map { subject.step(1f) }

        assertEquals(1f, applied.last())
        assertEquals(0.15f, applied.first(), EPSILON)
        assertEquals(
            15f,
            (applied.first() * SPAN).coerceAtLeast(0f),
            EPSILON,
        )
        assertTrue(
            applied.zipWithNext().all { (previous, next) -> next - previous <= MAX_STEP + EPSILON },
            "no step may exceed the anti-flash cap: $applied",
        )
        assertTrue(applied.distinct().size > 2, "the change must not be a single jump: $applied")
    }

    @Test
    fun `AC2_2 - a long gap between invocations is capped, so a resume is not a jump`() {
        val subject = ramp()
        subject.step(0f, afterMillis = 0L, appliedBrightness = BrightnessValue(1))

        val afterResume = subject.step(1f, afterMillis = 60_000L)

        assertEquals(0.15f, afterResume, EPSILON)
    }

    @Test
    fun `AC2_3 - a target arriving mid-ramp retargets without overshooting`() {
        val subject = ramp()
        subject.step(0f, afterMillis = 0L, appliedBrightness = BrightnessValue(1))
        val onTheWayUp = (1..3).map { subject.step(1f) }

        val retargeted = (1..8).map { subject.step(0.2f) }

        assertEquals(0.45f, onTheWayUp.last(), EPSILON)
        assertTrue(
            retargeted.all { it <= onTheWayUp.last() + EPSILON },
            "the ramp must not move past the value it had when the target changed: $retargeted",
        )
        assertTrue(
            retargeted.all { it >= 0.2f - EPSILON },
            "the ramp must not overshoot the new target: $retargeted",
        )
        assertTrue(
            retargeted.zipWithNext().all { (previous, next) -> next <= previous + EPSILON },
            "the reversed ramp must stay monotone: $retargeted",
        )
        assertEquals(0.2f, retargeted.last(), EPSILON)
    }

    @Test
    fun `the first invocation ramps from the brightness actually applied`() {
        val subject = ramp()

        val first = subject.step(1f, afterMillis = 0L, appliedBrightness = BrightnessValue(51))

        assertEquals(1f, subject.targetFraction)
        assertEquals(0.51f, first, EPSILON)
        assertEquals(0.51f, subject.currentFraction!!, EPSILON)
    }

    @Test
    fun `the step follows the response speed`() {
        responseSpeed.value = ResponseSpeed.of(0)
        val fast = ramp()
        fast.step(0f, afterMillis = 0L, appliedBrightness = BrightnessValue(1))
        val fastStep = fast.step(1f, afterMillis = 50L)

        responseSpeed.value = ResponseSpeed.of(100)
        val slow = ramp()
        slow.step(0f, afterMillis = 0L, appliedBrightness = BrightnessValue(1))
        val slowStep = slow.step(1f, afterMillis = 50L)

        assertEquals(0.15f, fastStep, EPSILON)
        assertEquals(0.0167f, slowStep, EPSILON)
        assertTrue(slowStep < fastStep)
    }

    @Test
    fun `a changed response speed applies to the next invocation`() {
        responseSpeed.value = ResponseSpeed.of(0)
        val subject = ramp()
        subject.step(0f, afterMillis = 0L, appliedBrightness = BrightnessValue(1))
        val fastStep = subject.step(1f, afterMillis = 50L)

        responseSpeed.value = ResponseSpeed.of(100)
        val slowStep = subject.step(1f, afterMillis = 50L) - fastStep

        assertEquals(0.15f, fastStep, EPSILON)
        assertEquals(0.0167f, slowStep, EPSILON)
    }

    @Test
    fun `a step is never smaller than one device unit, so the dead-band cannot swallow it`() {
        responseSpeed.value = ResponseSpeed.of(100)
        val subject = ramp()
        subject.step(0f, afterMillis = 0L, appliedBrightness = BrightnessValue(1))

        val smallestStep = subject.step(1f, afterMillis = 1L)

        assertEquals(1f / SPAN, smallestStep, EPSILON)
    }

    @Test
    fun `a remembered fraction that disagrees with the applied brightness is re-synced, not jumped`() {
        val subject = ramp()
        subject.step(1f, afterMillis = 0L, appliedBrightness = BrightnessValue(1))

        // The controller restarted and the system brightness is now near the top while the target is at
        // the bottom: the ramp must step down from what is really applied instead of writing the target.
        val afterRestart = subject.step(0f, afterMillis = 50L, appliedBrightness = BrightnessValue(101))

        assertEquals(1f - 50f / 1_650f, afterRestart, EPSILON)
        assertEquals(1f - 50f / 1_650f, subject.currentFraction!!, EPSILON)
    }

    @Test
    fun `a non-finite fraction is passed through instead of ramped`() {
        val subject = ramp()

        assertEquals(Float.NaN, subject.apply(Float.NaN, BrightnessContext(0f, range)))
    }

    private companion object {
        const val SAMPLING_INTERVAL_MILLIS = 500L
        const val SPAN = 100f
        const val MAX_STEP = 0.15f
        const val EPSILON = 0.0001f
    }
}
