package com.ambientbrightness.app.BackgroundService.Collector

import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BrightnessBackgroundService.BrightnessBackgroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Thin app boundary adapter that forwards F5's [BackgroundServiceState] and battery-optimization
 * exemption to a caller (F6/F7). It holds no business logic and constructs no platform clients; it
 * only exposes the two streams the boundary of [BrightnessBackgroundService] publishes.
 */
class BrightnessServiceCollector(
    private val backgroundService: BrightnessBackgroundService,
) {

    fun collect(
        scope: CoroutineScope,
        onState: (BackgroundServiceState) -> Unit,
        onBatteryOptimizationExemption: (Boolean) -> Unit,
    ): Job = scope.launch {
        coroutineScope {
            launch { backgroundService.state.collect(onState) }
            launch {
                backgroundService.batteryOptimizationExempt.collect(
                    onBatteryOptimizationExemption,
                )
            }
        }
    }
}
