package com.ambientbrightness.core.AmbientLightSensing

/**
 * Machine-readable reason why ambient light sensing cannot be used on this device (AC2.1, AC2.3).
 *
 * This is the key consumers (F7) branch on and map to localized string resources.
 * [defaultUserMessage] is a non-technical English fallback for logs, tests and consumers without a
 * resource table; it must not be shown when a localized resource for this reason exists.
 */
enum class SensorUnavailableReason(val defaultUserMessage: String) {
    /** The device exposes no ambient light sensor at all (AC2.1). */
    NoSensor("This device has no ambient light sensor, so automatic brightness can't work."),

    /** The sensor exists but produced no reading within the startup timeout (AC2.3). */
    NotUsable("The ambient light sensor on this device isn't reporting readings, so automatic brightness can't work."),
}
