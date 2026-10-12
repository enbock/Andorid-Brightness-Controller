package com.ambientbrightness.core.AutomaticBrightnessControl

/** Observable state of the device's built-in adaptive-brightness mode; F2 never changes that mode. */
enum class BuiltInAdaptiveBrightnessState {
    ENABLED,
    DISABLED,
    UNKNOWN,
}
