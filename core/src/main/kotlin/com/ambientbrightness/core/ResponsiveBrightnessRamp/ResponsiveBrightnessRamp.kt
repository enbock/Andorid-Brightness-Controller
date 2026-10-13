package com.ambientbrightness.core.ResponsiveBrightnessRamp

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessContext
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessTransform
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.ResponseTuning.ResponseProfile
import com.ambientbrightness.core.ResponseTuning.ResponseProfileResolver.ResponseProfileResolver
import com.ambientbrightness.core.ResponseTuning.ResponseSpeed
import com.ambientbrightness.core.Shared.Clock
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sign

/**
 * F4 [BrightnessTransform] that applies a large brightness change as a ramp of bounded steps instead
 * of a single jump, so walking from a dark room into daylight is not seen as a flash (AC2.1, AC2.2).
 *
 * It is F2's post-mapping hook and therefore runs before F2 clamps and dead-bands the result: every
 * intermediate value is clamped and offered to the dead-band on its own. One invocation produces at
 * most one step — there is no ticker of its own — and the step is
 *
 * `clamp(rate × elapsedMillis, one device unit, maxStepFraction)`
 *
 * with `rate = 1 / reactionWindowMillis`. The elapsed time is measured on the injected [Clock]
 * between invocations, and the profile is re-read from the current [responseSpeed] every time, so a
 * changed setting applies to the next sample without a restart (AC1.4).
 *
 * [currentFraction] and [targetFraction] are the ramp's observable state. The first invocation takes
 * its baseline from [BrightnessContext.currentBrightness] — the brightness actually in effect — so a
 * ramp never starts with a jump either, and it falls back to the computed value only when no applied
 * brightness is known. A target arriving mid-ramp retargets from the current value and can never
 * overshoot, because a step is never larger than the distance left to it (AC2.3).
 */
class ResponsiveBrightnessRamp(
    private val responseSpeed: StateFlow<ResponseSpeed>,
    private val clock: Clock,
    private val resolver: ResponseProfileResolver = ResponseProfileResolver(),
) : BrightnessTransform {

    /** Fraction the ramp has actually applied, or null before the first invocation. */
    var currentFraction: Float? = null
        private set

    /** Fraction of the most recently requested target, or null before the first invocation. */
    var targetFraction: Float? = null
        private set

    private var lastCallMillis: Long? = null

    override fun apply(fraction: Float, context: BrightnessContext): Float {
        if (!fraction.isFinite()) return fraction

        val nowMillis = clock.nowMillis()
        targetFraction = fraction

        val baseline = currentFraction ?: fractionOf(context.currentBrightness, context.range)
        if (baseline == null) {
            currentFraction = fraction
            lastCallMillis = nowMillis
            return fraction
        }

        val profile = resolver.resolve(responseSpeed.value)
        val step = allowedStep(
            elapsedMillis = (nowMillis - (lastCallMillis ?: nowMillis)).coerceAtLeast(0L),
            profile = profile,
            range = context.range,
        )
        val remaining = fraction - baseline
        val applied = if (abs(remaining) <= step) fraction else baseline + step * sign(remaining)

        currentFraction = applied
        lastCallMillis = nowMillis
        return applied
    }

    /**
     * The largest step one invocation may take: the reaction window's rate over the elapsed time,
     * floored at one device unit so F2's dead-band cannot swallow ramp progress, and capped at
     * [ResponseProfile.maxStepFraction] of the span so no single step is seen as a flash.
     */
    private fun allowedStep(
        elapsedMillis: Long,
        profile: ResponseProfile,
        range: BrightnessRange,
    ): Float {
        val span = spanOf(range)
        val minimumStep = MINIMUM_STEP_DEVICE_UNITS / span
        val maximumStep = profile.maxStepFraction.coerceAtLeast(minimumStep)
        val rateStep = elapsedMillis.toFloat() / profile.reactionWindowMillis.toFloat()
        return rateStep.coerceIn(minimumStep, maximumStep)
    }

    private fun fractionOf(brightness: BrightnessValue?, range: BrightnessRange): Float? {
        val value = brightness ?: return null
        val span = spanOf(range)
        return ((value.value - range.min.value) / span).coerceIn(0f, 1f)
    }

    private fun spanOf(range: BrightnessRange): Float =
        (range.max.value - range.min.value).coerceAtLeast(1).toFloat()

    private companion object {
        /** One device brightness unit: the smallest step F2's dead-band lets through. */
        const val MINIMUM_STEP_DEVICE_UNITS = 1f
    }
}
