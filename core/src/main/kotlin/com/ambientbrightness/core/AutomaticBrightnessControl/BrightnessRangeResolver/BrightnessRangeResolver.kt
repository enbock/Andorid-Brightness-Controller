package com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRangeResolver

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue

/**
 * Resolves a reported device range, using a positive-minimum fallback when it is unavailable
 * or invalid. Hardware range discovery remains the responsibility of infrastructure.
 */
class BrightnessRangeResolver {

    fun resolve(reportedRange: BrightnessRange?): BrightnessRange =
        reportedRange
            ?.takeIf { it.min.value >= BrightnessValue.MINIMUM && it.max.value >= it.min.value }
            ?: DEFAULT_RANGE

    companion object {
        /** Positive-minimum core fallback range, used when no valid device range is reported. */
        val DEFAULT_RANGE = BrightnessRange(BrightnessValue(1), BrightnessValue(255))
    }
}
