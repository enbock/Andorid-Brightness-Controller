package com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.Task

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

internal class StartupAvailabilityGate(private val startupTimeoutMillis: Long) {

    init {
        require(startupTimeoutMillis > 0L) { "startupTimeoutMillis must be positive" }
    }

    fun newWatchdog(): FirstSampleWatchdog = FirstSampleWatchdog(startupTimeoutMillis)
}

internal class FirstSampleWatchdog(private val startupTimeoutMillis: Long) {

    private val firstSample = CompletableDeferred<Unit>()

    fun markFirstSample() {
        firstSample.complete(Unit)
    }

    suspend fun awaitFirstSample(): Boolean =
        withTimeoutOrNull(startupTimeoutMillis) {
            firstSample.await()
            true
        } ?: false
}
