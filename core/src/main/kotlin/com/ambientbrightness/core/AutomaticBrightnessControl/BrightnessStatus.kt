package com.ambientbrightness.core.AutomaticBrightnessControl

/**
 * Current automatic-brightness controller status.
 *
 * [appliedBrightness] is the last value known to have been applied, [lastLux] is the most recently
 * processed ambient-light reading, and [writeState] reports the latest write/suppression outcome.
 * A failed write is also exposed as [writeError] for straightforward UI presentation.
 */
data class BrightnessStatus(
    val isRunning: Boolean = false,
    val appliedBrightness: BrightnessValue? = null,
    val lastLux: Float? = null,
    val writeState: BrightnessWriteResult? = null,
    val writeError: String? = null,
)
