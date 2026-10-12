package com.ambientbrightness.core.AutomaticBrightnessControl

/**
 * Reports the device's built-in adaptive-brightness mode so the app can surface the conflict (US4).
 *
 * The mode is read only; F2 never changes it (D4). Implementations return
 * [BuiltInAdaptiveBrightnessState.UNKNOWN] when the platform cannot report the mode.
 */
fun interface BuiltInAdaptiveBrightnessClient {
    fun readState(): BuiltInAdaptiveBrightnessState
}
