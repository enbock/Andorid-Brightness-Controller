package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.AmbientLightSensorClient
import com.ambientbrightness.core.SensorAvailability
import com.ambientbrightness.core.SensorProfile
import com.ambientbrightness.core.SensorUnavailableReason

/**
 * Deterministic [AmbientLightSensorClient] for tests and development on hardware without a light
 * sensor. Values are delivered only through [emit], never from the platform.
 *
 * Set [available] to `false` to simulate a device without a usable sensor.
 */
class DummyAmbientLightSensorClient(
    private val profile: SensorProfile = SensorProfile(
        name = "dummy-light-sensor",
        maxRangeLux = 10_000f,
        minDelayMicros = 200_000,
    ),
) : AmbientLightSensorClient {

    /** Whether the dummy sensor pretends to be present and usable. */
    var available: Boolean = true

    private var callback: ((Float) -> Unit)? = null

    override val availability: SensorAvailability
        get() = if (available) {
            SensorAvailability.Available(profile)
        } else {
            SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor)
        }

    override fun register(onLux: (Float) -> Unit) {
        callback = onLux
    }

    override fun unregister() {
        callback = null
    }

    /** Delivers [lux] to the registered callback; silently ignored when unregistered or unavailable. */
    fun emit(lux: Float) {
        if (!available) return
        callback?.invoke(lux)
    }
}
