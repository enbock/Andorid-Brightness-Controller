package com.ambientbrightness.core.BackgroundService

import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue

/**
 * Lifecycle state of the background service (F5).
 *
 * This is the single surface the ongoing notification (US4) and the F6/F7 UI render, and the reason
 * the lifecycle can be driven idempotently. [Running.appliedBrightness] is taken from F2's
 * `BrightnessStatus` so the notification can never disagree with the controller.
 */
sealed interface BackgroundServiceState {

    /**
     * The controller is not running: no foreground service, no notification, and the screen
     * brightness is left untouched (US3).
     */
    data object Stopped : BackgroundServiceState

    /**
     * A start request was accepted but the foreground service and the controller are not up yet
     * — the brief transitional state after [StartReason.UserRequest] (US1).
     */
    data object Starting : BackgroundServiceState

    /**
     * The controller is running under a foreground service and keeps adapting brightness, even
     * when the app is in the background or swiped from recents (US1).
     *
     * @property appliedBrightness the last brightness value F2 applied, or null while it has not
     * applied one yet; the notification shows it (US4, AC4.1).
     */
    data class Running(val appliedBrightness: BrightnessValue? = null) : BackgroundServiceState

    /**
     * The service was terminated outside the app — by the system, an OEM task killer or the
     * foreground-service Task Manager — and is distinguishable from a deliberate user stop so the
     * lifecycle can resume with the stored settings (US2).
     */
    data object StoppedBySystem : BackgroundServiceState

    /**
     * The controller could not continue and reports why, e.g. a revoked `WRITE_SETTINGS` after the
     * next write failed; the service stays alive so the user can stop it explicitly (US3, AC3.4).
     *
     * @property reason a non-technical, caller-supplied explanation for the failure.
     */
    data class Failed(val reason: String) : BackgroundServiceState
}
