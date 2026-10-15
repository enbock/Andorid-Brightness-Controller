package com.ambientbrightness.app.StatusDashboard.Controller

import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.core.BrightnessBackgroundService.BrightnessBackgroundService

/**
 * Turns the dashboard's Start/Stop intents into F5 calls (US2).
 *
 * It holds no state of its own: F5's `BackgroundServiceUseCase.start()` publishes `Starting` to
 * `BrightnessBackgroundService.state` synchronously (verified in F5 T3), and the dashboard renders that
 * state, so the user sees the start immediately and the ≤ 2 s budget of US2 AC1 is met without a second
 * source of truth. There is deliberately no timeout-based "optimistic running": the next real service
 * emission always wins, so the indicator can never lie (US3).
 */
class ControllerCommandHandler(
    private val backgroundService: BrightnessBackgroundService,
) {

    /** Requests a user-initiated start; idempotent while already running. */
    fun start() {
        backgroundService.start(StartReason.UserRequest)
    }

    /** Requests a stop; idempotent while already stopped. */
    fun stop() {
        backgroundService.stop()
    }
}
