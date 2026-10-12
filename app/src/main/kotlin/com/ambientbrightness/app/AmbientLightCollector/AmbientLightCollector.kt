package com.ambientbrightness.app

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.AmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import com.ambientbrightness.core.AmbientLightSensing.ScreenStateClient
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Thin app boundary adapter that forwards service samples and its availability/status to a caller.
 * Lifecycle ownership is supplied by the caller; this class does not construct platform clients.
 */
class AmbientLightCollector(
    private val service: AmbientLightService,
    private val screenStateClient: ScreenStateClient,
) {

    fun collect(
        scope: CoroutineScope,
        onSample: suspend (LuxSample) -> Unit,
        onAvailability: (SensorAvailability) -> Unit,
    ): Job = scope.launch {
        coroutineScope {
            launch { service.availability.collect(onAvailability) }
            screenStateClient.isScreenOn.collectLatest { isScreenOn ->
                if (isScreenOn) {
                    service.start().collect(onSample)
                }
            }
        }
    }
}
