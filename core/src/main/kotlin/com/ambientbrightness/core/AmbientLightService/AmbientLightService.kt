package com.ambientbrightness.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single boundary through which the rest of the app consumes ambient light sensing.
 *
 * F2 (and F4/F5/F7) must depend on this interface only and never on [AmbientLightSensorClient];
 * the boundary hides the sensor adapter, throttling and the injected [ReadingStabiliser].
 */
interface AmbientLightService {

    /** Current availability, kept up to date including the startup-timeout outcome (AC2.1–AC2.3). */
    val availability: StateFlow<SensorAvailability>

    /**
     * Cold stream of stabilised samples. Collection registers the sensor listener; when
     * collection stops, the listener is unregistered (AC1.3). Each sample is clamped and de-noised
     * (AC3.1–AC3.4).
     */
    fun start(): Flow<LuxSample>

    /**
     * Stops sensing and unregisters the listener deterministically. Idempotent: calling it when
     * nothing is running is a no-op (AC1.3).
     */
    fun stop()
}
