package com.ambientbrightness.core.AutomaticBrightnessControl

/** Reads and writes the system screen-brightness setting without exposing platform APIs to core. */
interface SystemBrightnessClient {
    /** Returns the current system value in device units, or null when it cannot be read. */
    fun readCurrent(): Int?

    /** Writes the requested device brightness value and reports the outcome. */
    fun write(value: BrightnessValue): BrightnessWriteResult
}
