package com.ambientbrightness.core

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightSensorClient
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorUnavailableReason

/**
 * Controllable [AmbientLightSensorClient] test double: records register/unregister calls and lets a
 * test push raw lux readings into the registered callback.
 */
class FakeAmbientLightSensorClient(
    override var availability: SensorAvailability =
        SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor),
) : AmbientLightSensorClient {

    var registerCalls: Int = 0
        private set

    var unregisterCalls: Int = 0
        private set

    /** When `true`, only the first pushed reading is ever delivered to the callback. */
    var deliverOnlyFirstReading: Boolean = false

    private var onLux: ((Float) -> Unit)? = null
    private var hasDeliveredReading: Boolean = false

    val isRegistered: Boolean
        get() = onLux != null

    override fun register(onLux: (Float) -> Unit) {
        registerCalls++
        this.onLux = onLux
    }

    override fun unregister() {
        unregisterCalls++
        onLux = null
    }

    /** Simulates the sensor reporting [lux]; ignored while nothing is registered. */
    fun push(lux: Float) {
        val callback = onLux ?: return
        if (deliverOnlyFirstReading && hasDeliveredReading) return
        hasDeliveredReading = true
        callback(lux)
    }
}
