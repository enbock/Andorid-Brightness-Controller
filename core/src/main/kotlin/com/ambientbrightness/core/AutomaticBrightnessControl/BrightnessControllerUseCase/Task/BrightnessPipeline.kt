package com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessControllerUseCase.Task

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessContext
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessMappingCurve.BrightnessMappingCurve
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRange
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessRangeScaler.BrightnessRangeScaler
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessTransform
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.AutomaticBrightnessControl.HysteresisGate.HysteresisGate

/**
 * Task collaborator that turns one ambient-light reading into a valid device brightness value.
 *
 * The chain is hysteresis gate → mapping curve → registered [BrightnessTransform] hooks →
 * device-range scaling and clamping. The controller only decides *whether* to write the result.
 */
internal class BrightnessPipeline(
    private val curve: BrightnessMappingCurve,
    private val hysteresisGate: HysteresisGate,
) {

    private val transforms = mutableListOf<BrightnessTransform>()

    fun register(transform: BrightnessTransform) {
        transforms += transform
    }

    /**
     * Computes the brightness for [lux], or returns null when the hysteresis gate holds the
     * reading back because the light only oscillates inside its current band (AC3.2).
     */
    fun compute(
        lux: Float,
        range: BrightnessRange,
        currentBrightness: BrightnessValue?,
    ): BrightnessComputation? {
        val acceptedLux = hysteresisGate.accept(lux) ?: return null
        return BrightnessComputation(
            lux = acceptedLux,
            brightness = toBrightness(acceptedLux, range, currentBrightness),
        )
    }

    /**
     * Recomputes an already accepted reading without consulting the gate, so a changed transform
     * result is applied immediately.
     */
    fun recompute(
        lux: Float,
        range: BrightnessRange,
        currentBrightness: BrightnessValue?,
    ): BrightnessValue = toBrightness(lux, range, currentBrightness)

    private fun toBrightness(
        lux: Float,
        range: BrightnessRange,
        currentBrightness: BrightnessValue?,
    ): BrightnessValue {
        val context = BrightnessContext(lux = lux, range = range, currentBrightness = currentBrightness)
        val mapped = transforms.fold(curve.toFraction(lux)) { fraction, transform ->
            transform.apply(fraction, context)
        }
        // A faulty hook must never crash the loop or produce an invalid value (US2).
        val safeFraction = mapped.takeIf { !it.isNaN() } ?: curve.toFraction(lux)
        return BrightnessRangeScaler.scale(safeFraction, range)
    }
}

/** An accepted ambient-light reading together with the device brightness it maps to. */
internal data class BrightnessComputation(val lux: Float, val brightness: BrightnessValue)
