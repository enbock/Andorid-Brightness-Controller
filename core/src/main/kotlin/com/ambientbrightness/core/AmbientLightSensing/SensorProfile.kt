package com.ambientbrightness.core.AmbientLightSensing

/**
 * Diagnostic description of the ambient light sensor the app is bound to (AC2.2).
 *
 * @param name sensor name as reported by the platform, shown for diagnostics.
 * @param maxRangeLux maximum lux the sensor reports; the upper bound for range clamping (AC3.4).
 * @param minDelayMicros shortest interval the sensor can deliver, in microseconds.
 */
data class SensorProfile(
    val name: String,
    val maxRangeLux: Float,
    val minDelayMicros: Int,
)
