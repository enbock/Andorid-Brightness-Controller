package com.ambientbrightness.core.AmbientLightSensing.LightLevel

/**
 * Qualitative ambient light level derived from a lux value (Decision D10).
 *
 * [UNKNOWN] is the only member that does not describe a real light level; it marks input the
 * classifier cannot turn into a level (non-finite or negative lux).
 */
enum class LightLevel {
    DARK,
    DIM,
    INDOOR,
    BRIGHT,
    FULL_SUN,
    UNKNOWN,
}
