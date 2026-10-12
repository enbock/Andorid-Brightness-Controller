package com.ambientbrightness.core.AutomaticBrightnessControl

/** Extension hook applied after lux mapping and before device-range clamping. */
interface BrightnessTransform {
    /** Transforms the normalized brightness [fraction] using sample and device context. */
    fun apply(fraction: Float, context: BrightnessContext): Float
}
