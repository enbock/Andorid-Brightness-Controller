package com.ambientbrightness.core.AutomaticBrightnessControl

/**
 * In-memory snapshot of the brightness in effect before the controller starts.
 *
 * Exposed for the later restoration behavior (D3); this domain contract does not perform restores.
 */
data class BrightnessSnapshot(val brightness: BrightnessValue)
