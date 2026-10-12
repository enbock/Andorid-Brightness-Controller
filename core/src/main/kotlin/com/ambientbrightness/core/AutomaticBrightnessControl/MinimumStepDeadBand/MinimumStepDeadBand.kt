package com.ambientbrightness.core.AutomaticBrightnessControl.MinimumStepDeadBand

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import kotlin.math.abs

/**
 * Identifies writes too close to the last successfully applied value.
 *
 * A suppressed request does not change [lastApplied]; callers update it only after a write
 * actually succeeds by calling [recordApplied]. The default configured minimum step is one
 * device brightness unit.
 */
class MinimumStepDeadBand(
    private val minimumStep: Int = DEFAULT_MINIMUM_STEP,
) {
    var lastApplied: BrightnessValue? = null
        private set

    init {
        require(minimumStep >= 1) { "Minimum brightness step must be at least one device unit." }
    }

    /**
     * Returns the existing typed suppressed-write status when the request is below the minimum
     * step, or null when the request should proceed to the writer.
     */
    fun suppressionFor(requested: BrightnessValue): BrightnessWriteResult.Suppressed? {
        val applied = lastApplied ?: return null
        val difference = abs(requested.value.toLong() - applied.value.toLong())
        return if (difference < minimumStep.toLong()) {
            BrightnessWriteResult.Suppressed(requested)
        } else {
            null
        }
    }

    /** Records a value only after its system write succeeds. */
    fun recordApplied(value: BrightnessValue) {
        lastApplied = value
    }

    /** Forgets the remembered value, so a new controller run compares against a fresh baseline. */
    fun reset() {
        lastApplied = null
    }

    companion object {
        const val DEFAULT_MINIMUM_STEP: Int = 1
    }
}
