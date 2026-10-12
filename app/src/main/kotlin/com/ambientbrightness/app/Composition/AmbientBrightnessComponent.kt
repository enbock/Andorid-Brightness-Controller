package com.ambientbrightness.app.Composition

import com.ambientbrightness.app.AmbientLightSensing.Collector.AmbientLightCollector
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightSensorClient
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.DefaultAmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightUseCase.AmbientLightUseCase
import com.ambientbrightness.core.AmbientLightSensing.ReadingStabiliser.OutlierClampingStabiliser
import com.ambientbrightness.core.AmbientLightSensing.ScreenStateClient
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.Shared.SystemClock

/**
 * App-scoped manual composition root for ambient sensing. Its client parameters also let tests
 * exercise the same controller boundary with deterministic clients instead of device sensors.
 */
class AmbientBrightnessComponent(
    sensorClient: AmbientLightSensorClient,
    screenStateClient: ScreenStateClient,
) {

    private val sensorRangeLux = (sensorClient.availability as? SensorAvailability.Available)
        ?.profile
        ?.maxRangeLux
        ?.takeIf { it.isFinite() && it > 0f }
        ?: DEFAULT_FALLBACK_MAX_RANGE_LUX

    private val useCase = AmbientLightUseCase(
        sensorClient = sensorClient,
        stabiliser = OutlierClampingStabiliser(maxRangeLux = sensorRangeLux),
        clock = SystemClock,
    )

    private val service = DefaultAmbientLightService(
        sensorClient = sensorClient,
        useCase = useCase,
    )

    val collector = AmbientLightCollector(
        service = service,
        screenStateClient = screenStateClient,
    )

    /** Immediately cancels service collection and unregisters any active sensor listener. */
    fun stop() {
        service.stop()
    }

    private companion object {
        const val DEFAULT_FALLBACK_MAX_RANGE_LUX = 1_000f
    }
}
