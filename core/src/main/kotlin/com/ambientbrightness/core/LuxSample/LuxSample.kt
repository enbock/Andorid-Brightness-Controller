package com.ambientbrightness.core

/**
 * A single stabilised ambient-illuminance reading.
 *
 * [lux] is a non-negative SI lux value clamped to the sensor's documented range before it is
 * wrapped into a sample (AC3.4); `0 lux` is a valid very-low-light reading (AC3.3) and never means
 * "missing". [timestampMillis] is the wall-clock time of the accepted reading, in milliseconds
 * since the Unix epoch.
 */
data class LuxSample(
    val lux: Float,
    val timestampMillis: Long,
)
