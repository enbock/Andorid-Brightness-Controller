package com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRangeScaler

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import kotlin.math.roundToInt

/** Scales a normalized brightness fraction into the inclusive device range. */
object BrightnessRangeScaler {

    /**
     * Scales [fraction] linearly across [range], rounds to the nearest device unit and clamps
     * to both inclusive endpoints. NaN is rejected because it has no meaningful brightness.
     * Infinite and finite out-of-range fractions saturate at the corresponding endpoint.
     */
    fun scale(fraction: Float, range: BrightnessRange): BrightnessValue {
        require(!fraction.isNaN()) { "Brightness fraction must not be NaN." }

        val boundedFraction = fraction.coerceIn(0f, 1f).toDouble()
        val minimum = range.min.value
        val maximum = range.max.value
        val scaled = minimum + ((maximum - minimum) * boundedFraction).roundToInt()
        return BrightnessValue(scaled.coerceIn(minimum, maximum))
    }
}
