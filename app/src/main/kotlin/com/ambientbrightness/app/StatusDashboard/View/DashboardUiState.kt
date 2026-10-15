package com.ambientbrightness.app.StatusDashboard.View

import com.ambientbrightness.core.AmbientLightSensing.LightLevel.LightLevel
import com.ambientbrightness.core.AppSetupService.SetupRequirementId
import com.ambientbrightness.core.AppSetupService.SetupStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue

/**
 * The state of the start/stop control, mapped from F5's `BackgroundServiceState` plus F6's gate
 * (US2, US3).
 *
 * Sealed on purpose: the dashboard branches over it without an `else`, so every state the user can
 * run into must be modelled, including the ones that must not be shown as a healthy "running".
 */
sealed interface ControllerUiState {

    /** The controller is not running; no live reading may be shown (US1, AC1.4). */
    data object Stopped : ControllerUiState

    /** A start was requested and the UI shows it at once, until F5 reports the real state (US2, AC2.1). */
    data object Starting : ControllerUiState

    /** The controller is running; [appliedBrightness] is what F2 last applied, or null before the first write. */
    data class Running(val appliedBrightness: BrightnessValue?) : ControllerUiState

    /**
     * Start is not possible because at least one blocking requirement is unmet; [blockers] names
     * exactly which ones, so the reason can be shown next to the disabled control (US2, AC2.3).
     */
    data class Blocked(val blockers: List<SetupRequirementId>) : ControllerUiState

    /**
     * A brightness write failed, so the dashboard shows an error instead of "running" (US3, AC3.2).
     * The service stays alive, so Stop remains available.
     */
    data class Failed(val reason: String) : ControllerUiState

    /**
     * The system stopped the service; the dashboard reflects that instead of a cached "running"
     * (US3, AC3.3).
     */
    data object StoppedBySystem : ControllerUiState
}

/**
 * The ambient-light readout (US1).
 *
 * [Unavailable] is the only state that carries no value: it covers "controller stopped", "sensor
 * unavailable" and "no reading sampled yet", so the dashboard never shows a stale lux value as if it
 * were current (US1, AC1.4).
 */
sealed interface LightReadout {

    /** No current reading exists. */
    data object Unavailable : LightReadout

    /** A current reading with its qualitative level (US1, AC1.1, AC1.2). */
    data class Reading(val lux: Float, val level: LightLevel) : LightReadout
}

/**
 * Whether the manual offset could be applied in full (US3, AC1.1).
 *
 * [Limited] is shown as a non-blocking hint; it never hides the applied brightness or stops the
 * controller.
 */
sealed interface ClampHint {

    /** The offset was applied in full, or nothing has been evaluated yet. */
    data object None : ClampHint

    /** The offset was cut off; [atMaximum] is true when it hit the device maximum, false at the floor. */
    data class Limited(val atMaximum: Boolean) : ClampHint
}

/**
 * Everything the dashboard screen renders, derived only from the five existing service boundaries.
 *
 * Immutable and comparable so the ViewModel can `distinctUntilChanged`: an equal re-emission does not
 * recompose. All fields come from [DashboardSnapshot] through [Task.DashboardUiStateMapper]; this type
 * holds no Android reference and no resource id (localisation happens in the View).
 */
data class DashboardUiState(
    val controller: ControllerUiState,
    val light: LightReadout,
    val setupSummary: SetupStatus?,
    val canStart: Boolean,
    val offsetPercent: Int,
    val responseSpeedPercent: Int,
    val clampHint: ClampHint,
)
