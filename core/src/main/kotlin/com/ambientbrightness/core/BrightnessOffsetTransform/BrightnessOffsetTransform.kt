package com.ambientbrightness.core.BrightnessOffsetTransform

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessContext
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessTransform
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.BrightnessOffsetService.BrightnessOffsetSource
import com.ambientbrightness.core.BrightnessOffsetService.OffsetApplicationSink
import kotlin.math.roundToInt

/**
 * F2 [BrightnessTransform] that shifts the mapped brightness by the user's manual offset before F2
 * clamps the result (AC2.4, AC3.1).
 *
 * The offset is read from [offsetSource] as a whole percent of the brightness span and added to the
 * normalized [fraction][BrightnessTransform.apply] as `percent / 100`, so a `+50` and a `-50` offset
 * move the result by half the span in either direction (AC3.1). At `0` the shift is a no-op, so the
 * returned fraction is exactly the one that came in (AC2.4). The return value is the raw shifted
 * fraction, deliberately left unclamped: F2's scaler stays the single clamp, so the value F2
 * ultimately applies is at worst `range.min` — never `0` — or at most `range.max` (AC2.1, AC2.2).
 *
 * `apply(...)` returns only a `Float`, so the clamp indicator (AC2.3) travels through
 * [applicationSink]: for every call the shifted fraction is compared against
 * [BrightnessContext.range] in raw device units and exactly one [BrightnessOffsetApplication] is
 * published — [BrightnessOffsetApplication.ClampedToMinimum] or
 * [BrightnessOffsetApplication.ClampedToMaximum] when the offset drove the result past the
 * corresponding bound, otherwise [BrightnessOffsetApplication.FullyApplied], which is also the
 * outcome at `0` (AC2.4).
 */
class BrightnessOffsetTransform(
    private val offsetSource: BrightnessOffsetSource,
    private val applicationSink: OffsetApplicationSink,
) : BrightnessTransform {

    /**
     * Returns [fraction] shifted by the current offset without clamping it, and publishes how far the
     * offset could be applied to [context]'s range (AC2.1–AC2.4). One [BrightnessOffsetApplication]
     * is published per call so the observable state always reflects the latest sample.
     */
    override fun apply(fraction: Float, context: BrightnessContext): Float {
        val shiftedFraction = fraction + offsetSource.offset.value.percent / PERCENT_SCALE
        applicationSink.publish(applicationFor(shiftedFraction, context.range))
        return shiftedFraction
    }

    /**
     * Classifies how far [shiftedFraction] could be applied within [range].
     *
     * The comparison is done in raw device units, with the same rounding F2's scaler performs, so a
     * fraction outside `0f..1f` is never turned into an out-of-contract brightness value. The
     * unclamped and clamped results differ only when the offset pushed the fraction past an end of
     * the range, which is exactly when the offset could not be applied in full (AC2.3).
     */
    private fun applicationFor(
        shiftedFraction: Float,
        range: BrightnessRange,
    ): BrightnessOffsetApplication {
        val minimum = range.min.value
        val span = range.max.value - minimum
        val unclamped = minimum + (span * shiftedFraction).roundToInt()
        val clamped = minimum + (span * shiftedFraction.coerceIn(0f, 1f)).roundToInt()
        return when {
            clamped == unclamped -> BrightnessOffsetApplication.FullyApplied
            unclamped < minimum -> BrightnessOffsetApplication.ClampedToMinimum
            else -> BrightnessOffsetApplication.ClampedToMaximum
        }
    }

    private companion object {
        /** Percent-to-fraction divisor: `percent / 100` is the offset expressed as a span fraction. */
        const val PERCENT_SCALE = 100f
    }
}
