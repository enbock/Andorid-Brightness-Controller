package com.ambientbrightness.core.AutomaticBrightnessControl

/** Outcome of a requested system brightness write (or deliberate write suppression). */
sealed interface BrightnessWriteResult {

    /** The requested value was successfully written to the system. */
    data class Applied(val value: BrightnessValue) : BrightnessWriteResult

    /** The requested value did not need a system write, for example because it was in the dead-band. */
    data class Suppressed(val requested: BrightnessValue) : BrightnessWriteResult

    /** The system rejected the write; [reason] is suitable for diagnostics and status reporting. */
    data class Failed(val reason: String) : BrightnessWriteResult
}
