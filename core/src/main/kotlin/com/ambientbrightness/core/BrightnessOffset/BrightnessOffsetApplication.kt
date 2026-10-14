package com.ambientbrightness.core.BrightnessOffset

/**
 * How much of the current [BrightnessOffset] could actually be applied to the computed brightness.
 *
 * The offset transform publishes this so the app can tell the user "the offset could not be fully
 * applied at the current light level" (AC2.3) instead of silently doing nothing.
 */
sealed interface BrightnessOffsetApplication {

    /** No brightness sample has been evaluated yet, so nothing can be claimed about clamping. */
    data object NotEvaluated : BrightnessOffsetApplication

    /** The offset was applied in full; the result needed no clamping (AC2.4). */
    data object FullyApplied : BrightnessOffsetApplication

    /** The result fell below the allowed minimum and was clamped to the brightness floor (AC2.1). */
    data object ClampedToMinimum : BrightnessOffsetApplication

    /** The result exceeded the device maximum and was clamped to it (AC2.2). */
    data object ClampedToMaximum : BrightnessOffsetApplication
}
