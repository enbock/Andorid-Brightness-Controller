package com.ambientbrightness.core.AutomaticBrightnessControl

/**
 * A non-empty supported brightness interval in device units.
 *
 * [min] is always positive because it is a [BrightnessValue], and [max] cannot be below it.
 */
data class BrightnessRange(
    val min: BrightnessValue,
    val max: BrightnessValue,
) {
    init {
        require(max.value >= min.value) { "Brightness range maximum must be at least its minimum." }
    }
}
