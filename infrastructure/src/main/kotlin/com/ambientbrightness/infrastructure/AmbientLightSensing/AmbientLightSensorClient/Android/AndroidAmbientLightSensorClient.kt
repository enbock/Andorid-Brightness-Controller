package com.ambientbrightness.infrastructure.AmbientLightSensing.AmbientLightSensorClient.Android

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightSensorClient
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorUnavailableReason
import com.ambientbrightness.infrastructure.AmbientLightSensing.SensorProfileFactory.SensorProfileFactory

/**
 * [AmbientLightSensorClient] backed by the Android [SensorManager].
 *
 * Availability is resolved eagerly (without registering a listener) so callers can read it before
 * [register]. Registration is idempotent and delivery can be stopped deterministically via
 * [unregister].
 */
class AndroidAmbientLightSensorClient(
    private val sensorManager: SensorManager,
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT),
    private val samplingPeriodMicros: Int = SensorManager.SENSOR_DELAY_NORMAL,
) : AmbientLightSensorClient {

    private val forwardingListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.values.isEmpty()) return
            callback?.invoke(event.values[0])
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private var callback: ((Float) -> Unit)? = null
    private var registered = false

    override val availability: SensorAvailability = resolveAvailability()

    override fun register(onLux: (Float) -> Unit) {
        callback = onLux
        if (availability !is SensorAvailability.Available) return
        if (registered) return
        sensorManager.registerListener(forwardingListener, sensor, samplingPeriodMicros)
        registered = true
    }

    override fun unregister() {
        if (registered) {
            sensorManager.unregisterListener(forwardingListener)
            registered = false
        }
        callback = null
    }

    private fun resolveAvailability(): SensorAvailability {
        val profile = sensor?.let {
            SensorProfileFactory.create(
                name = it.name,
                maxRangeLux = it.maximumRange,
                minDelayMicros = it.minDelay,
            )
        } ?: return SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor)
        return SensorAvailability.Available(profile)
    }
}
