package com.ambientbrightness.core.AutomaticBrightnessControl

/**
 * Context supplied to a post-mapping, pre-clamp [BrightnessTransform].
 *
 * [lux] is the ambient illuminance for the sample being transformed. [range] is the device's
 * supported brightness interval, and [currentBrightness] is the most recently applied value when
 * one is known.
 */
data class BrightnessContext(
    val lux: Float,
    val range: BrightnessRange,
    val currentBrightness: BrightnessValue? = null,
)
