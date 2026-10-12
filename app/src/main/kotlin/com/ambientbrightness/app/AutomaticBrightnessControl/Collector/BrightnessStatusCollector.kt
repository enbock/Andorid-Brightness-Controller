package com.ambientbrightness.app.AutomaticBrightnessControl.Collector

import com.ambientbrightness.core.AutomaticBrightnessControl.AutomaticBrightnessController
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BuiltInAdaptiveBrightnessState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Thin app boundary adapter that forwards the F2 controller's status and adaptive-brightness
 * conflict state to a caller. It holds no business logic and constructs no platform clients.
 */
class BrightnessStatusCollector(
    private val controller: AutomaticBrightnessController,
) {

    fun collect(
        scope: CoroutineScope,
        onStatus: (BrightnessStatus) -> Unit,
        onAdaptiveBrightnessConflict: (BuiltInAdaptiveBrightnessState) -> Unit,
    ): Job = scope.launch {
        coroutineScope {
            launch { controller.status.collect(onStatus) }
            launch { controller.adaptiveBrightnessConflict.collect(onAdaptiveBrightnessConflict) }
        }
    }
}
