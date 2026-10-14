package com.ambientbrightness.app.SetupFlow.Collector

import com.ambientbrightness.core.AppSetupService.AppSetupService
import com.ambientbrightness.core.AppSetupService.SetupStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Thin app boundary adapter that forwards [AppSetupService]'s [SetupStatus] and Start gate to a
 * caller (F7). It holds no business logic and creates no platform clients; it only exposes the two
 * streams the boundary already publishes.
 */
class SetupFlowCollector(
    private val setupService: AppSetupService,
) {

    fun collect(
        scope: CoroutineScope,
        onStatus: (SetupStatus) -> Unit,
        onReadyToStart: (Boolean) -> Unit,
    ): Job = scope.launch {
        coroutineScope {
            launch { setupService.status.collect(onStatus) }
            launch { setupService.isReadyToStart.collect(onReadyToStart) }
        }
    }
}
