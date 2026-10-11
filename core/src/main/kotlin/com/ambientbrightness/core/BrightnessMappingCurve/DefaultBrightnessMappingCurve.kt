package com.ambientbrightness.core.BrightnessMappingCurve

import com.ambientbrightness.core.BrightnessMappingCurve
import kotlin.math.log10

/** Default monotonic, log-lux piecewise-linear mapping with a readable low-light floor. */
class DefaultBrightnessMappingCurve : BrightnessMappingCurve {
    override fun toFraction(lux: Float): Float {
        // Invalid low-side values fail safely to the floor; positive infinity saturates high.
        if (lux.isNaN() || lux <= 0f) return ANCHORS.first().fraction.toFloat()
        if (lux == Float.POSITIVE_INFINITY) return 1f

        val transformedLux = log10(1.0 + lux.toDouble())
        for (index in 1 until ANCHORS.size) {
            val upper = ANCHORS[index]
            if (transformedLux <= upper.transformedLux) {
                val lower = ANCHORS[index - 1]
                val progress = (transformedLux - lower.transformedLux) /
                    (upper.transformedLux - lower.transformedLux)
                return (lower.fraction + progress * (upper.fraction - lower.fraction))
                    .toFloat()
                    .coerceIn(0f, 1f)
            }
        }
        return 1f
    }

    private data class Anchor(val lux: Double, val fraction: Double) {
        val transformedLux: Double = log10(1.0 + lux)
    }

    private companion object {
        val ANCHORS = listOf(
            Anchor(0.0, 0.10),
            Anchor(1.0, 0.12),
            Anchor(10.0, 0.18),
            Anchor(100.0, 0.35),
            Anchor(1_000.0, 0.60),
            Anchor(10_000.0, 0.85),
            Anchor(100_000.0, 1.00),
        )
    }
}
