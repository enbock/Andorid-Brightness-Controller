package com.ambientbrightness.core

/**
 * Whether the app can currently sense ambient light.
 *
 * Consumers branch on [Unavailable.reason] (a stable, machine-readable key) and render the
 * localized explanation; [Unavailable.userMessage] is only the non-technical fallback text
 * (AC2.1, AC2.3).
 */
sealed interface SensorAvailability {

    /** The sensor is present and reports values; [profile] is exposed for diagnostics (AC2.2). */
    data class Available(val profile: SensorProfile) : SensorAvailability

    /**
     * No usable sensor, either because the device has none or because it stayed silent past the
     * startup timeout — both surface as the same user-visible state (AC2.1, AC2.3).
     */
    data class Unavailable(
        val reason: SensorUnavailableReason,
        val userMessage: String = reason.defaultUserMessage,
    ) : SensorAvailability
}
