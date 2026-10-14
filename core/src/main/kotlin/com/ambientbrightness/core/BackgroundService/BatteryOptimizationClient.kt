package com.ambientbrightness.core.BackgroundService

import kotlinx.coroutines.flow.Flow

/**
 * Boundary for the battery-optimization exemption, exposed for F6/F7 (US2, AC2.2).
 *
 * `:core` only exposes the capability; how the user is guided and which system screen opens are
 * details of the `:infrastructure` implementation. There is no UI here.
 */
interface BatteryOptimizationClient {

    /** Emits whether the app is currently exempt from battery optimization, and re-emits on change. */
    val isExempt: Flow<Boolean>

    /** Asks the platform to grant the exemption, or opens the system screen that does so. */
    fun requestExemption()

    /** Opens the system battery-optimization settings for this app. */
    fun openSettings()
}
