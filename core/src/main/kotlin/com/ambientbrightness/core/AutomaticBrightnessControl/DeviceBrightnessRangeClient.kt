package com.ambientbrightness.core.AutomaticBrightnessControl

/** Discovers the supported device brightness interval independently of system brightness I/O. */
interface DeviceBrightnessRangeClient {
    /** Returns the discovered range, or null when the platform cannot provide one. */
    fun readRange(): BrightnessRange?
}
