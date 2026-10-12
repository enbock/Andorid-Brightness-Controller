package com.ambientbrightness.core.AutomaticBrightnessControl

/**
 * A device brightness value. Values below [MINIMUM] are not valid brightness values.
 */
@JvmInline
value class BrightnessValue(val value: Int) {
    init {
        require(value >= MINIMUM) { "Brightness must be at least $MINIMUM." }
    }

    companion object {
        const val MINIMUM: Int = 1
    }
}
