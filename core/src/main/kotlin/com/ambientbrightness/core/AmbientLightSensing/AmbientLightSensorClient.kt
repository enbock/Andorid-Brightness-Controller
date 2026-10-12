package com.ambientbrightness.core.AmbientLightSensing

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.AmbientLightService

/**
 * Raw access to the platform's ambient light sensor; the Android adapter lives in
 * `:infrastructure` (T2).
 *
 * This is an internal detail of F1: consumers outside the use-case must depend on
 * [AmbientLightService] instead, never on this client.
 */
interface AmbientLightSensorClient {

    /**
     * Whether a usable sensor is present. Resolved from the platform without registering a
     * listener, so it can be read before [register].
     */
    val availability: SensorAvailability

    /**
     * Starts delivering raw, unthrottled lux values through [onLux]. The callback runs on the
     * platform's sensor thread and receives the sensor's reported value as-is (no clamping,
     * throttling or stabilising — that is the use-case's job).
     */
    fun register(onLux: (Float) -> Unit)

    /**
     * Stops delivery deterministically and idempotently: after this returns, no further [onLux]
     * callbacks are delivered (AC1.3). Calling it without a preceding [register] is a no-op.
     */
    fun unregister()
}
