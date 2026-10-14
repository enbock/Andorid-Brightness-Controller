package com.ambientbrightness.core.BrightnessBackgroundService

import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BackgroundService.StartOutcome
import com.ambientbrightness.core.BackgroundService.StartReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The single boundary through which the rest of the app drives the background service (F5).
 *
 * F6 (permissions & setup) and F7 (status dashboard) depend on this interface only: they observe
 * [state] and [batteryOptimizationExempt] and call [start]/[stop]. They never touch the Android
 * `Service`, the notification or the persisted flag directly.
 */
interface BrightnessBackgroundService {

    /**
     * Starts the controller for the given [reason].
     *
     * Idempotent: starting while already running returns [StartOutcome.Started] without a second
     * foreground service (US1, US2).
     */
    fun start(reason: StartReason): StartOutcome

    /** Stops the controller and tears the foreground service down (US3); idempotent while stopped. */
    fun stop()

    /** The lifecycle state the notification (US4) and the F6/F7 UI render. */
    val state: StateFlow<BackgroundServiceState>

    /** Whether the app is exempt from battery optimization, surfaced for F6/F7 (US2, AC2.2). */
    val batteryOptimizationExempt: Flow<Boolean>
}
