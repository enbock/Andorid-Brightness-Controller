package com.ambientbrightness.core

import kotlin.math.abs
import kotlin.math.max

/**
 * Default [ReadingStabiliser]: clamps every reading into the sensor range and drops single
 * readings that lie far outside the recent trend (AC3.1, AC3.3, AC3.4).
 *
 * @param maxRangeLux upper bound of the physical sensor; values above it are clamped (AC3.4).
 * @param outlierFactor multiplier applied to the last accepted value to build the delta band.
 * @param minOutlierDeltaLux absolute floor of the delta band, so a dark-to-lit step is detected
 *   even while the last accepted value is close to zero.
 * @param consecutiveRejectionsBeforeAccept how many outliers in a row are dropped before the next
 *   one is accepted regardless, so a genuine lighting change is eventually followed (recovery rule).
 */
class OutlierClampingStabiliser(
    private val maxRangeLux: Float,
    private val outlierFactor: Float = 3f,
    private val minOutlierDeltaLux: Float = 20f,
    private val consecutiveRejectionsBeforeAccept: Int = 3,
) : ReadingStabiliser {

    private var lastAcceptedValue: Float? = null
    private var consecutiveRejections: Int = 0

    override fun stabilise(sample: LuxSample): LuxSample? {
        val clampedValue = sample.lux.coerceIn(0f, maxRangeLux)
        val reference = lastAcceptedValue

        // The very first accepted value has no trend to compare against, so it is always kept.
        if (reference == null) {
            accept(clampedValue)
            return sample.copy(lux = clampedValue)
        }

        val threshold = max(minOutlierDeltaLux, outlierFactor * reference)
        val isOutlier = abs(clampedValue - reference) > threshold

        if (isOutlier && consecutiveRejections < consecutiveRejectionsBeforeAccept) {
            consecutiveRejections++
            return null
        }

        // Either a steady value inside the noise band or the recovery rule kicked in: both become
        // the new reference, so normal noise never jitters the output (AC3.2).
        accept(clampedValue)
        return sample.copy(lux = clampedValue)
    }

    private fun accept(value: Float) {
        lastAcceptedValue = value
        consecutiveRejections = 0
    }
}
