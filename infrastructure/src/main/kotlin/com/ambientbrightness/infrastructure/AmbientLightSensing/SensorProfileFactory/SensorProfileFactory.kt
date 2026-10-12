package com.ambientbrightness.infrastructure.AmbientLightSensing.SensorProfileFactory

import com.ambientbrightness.core.AmbientLightSensing.SensorProfile

/**
 * Builds a [SensorProfile] from the raw values the Android framework reports.
 *
 * Kept free of Android types so the "is this sensor usable?" rule (some devices report a bogus
 * maximum range) is a pure, unit-testable function.
 */
object SensorProfileFactory {

    /**
     * @return a [SensorProfile], or `null` when the sensor is unusable (a non-positive maximum
     * range, including `NaN`). A negative [minDelayMicros] is clamped to `0`.
     */
    fun create(name: String, maxRangeLux: Float, minDelayMicros: Int): SensorProfile? {
        if (!(maxRangeLux > 0f)) return null
        return SensorProfile(
            name = name,
            maxRangeLux = maxRangeLux,
            minDelayMicros = minDelayMicros.coerceAtLeast(0),
        )
    }
}
