package com.ambientbrightness.app

import com.ambientbrightness.core.AmbientLightService
import com.ambientbrightness.core.LuxSample
import com.ambientbrightness.core.SensorAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/**
 * Thin app boundary adapter that forwards service samples and its availability/status to a caller.
 * Lifecycle ownership is supplied by the caller; this class does not construct platform clients.
 */
class AmbientLightCollector(
    private val service: AmbientLightService,
) {

    fun collect(
        scope: CoroutineScope,
        onSample: suspend (LuxSample) -> Unit,
        onAvailability: (SensorAvailability) -> Unit,
    ): Job = scope.launch {
        coroutineScope {
            launch { service.availability.collect(onAvailability) }
            service.start().collect(onSample)
        }
    }
}
