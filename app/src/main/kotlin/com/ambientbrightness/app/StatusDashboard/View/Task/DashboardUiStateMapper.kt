package com.ambientbrightness.app.StatusDashboard.View.Task

import com.ambientbrightness.app.StatusDashboard.View.ClampHint
import com.ambientbrightness.app.StatusDashboard.View.ControllerUiState
import com.ambientbrightness.app.StatusDashboard.View.DashboardSnapshot
import com.ambientbrightness.app.StatusDashboard.View.DashboardUiState
import com.ambientbrightness.app.StatusDashboard.View.LightReadout
import com.ambientbrightness.core.AmbientLightSensing.LightLevel.LightLevelClassifier
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication

/**
 * Pure `DashboardSnapshot -> DashboardUiState` mapping (US1–US4).
 *
 * It is Android-free and side-effect-free: no resource id, no locale and no service call appears
 * here, which is what keeps the priority rules testable over the full matrix of service states. The
 * View resolves text from the resulting state and [LightLevelLabelResolver].
 *
 * Precedence of the controller state (why this order):
 * 1. `Failed` wins over everything, because an error must never be hidden behind "running" (US3, AC3.2).
 * 2. `StoppedBySystem` wins over the stale start request, so a system stop is not shown as running (US3, AC3.3).
 * 3. `Starting`/`Running` come from F5 alone; the gate does not override a real service state.
 * 4. An idle controller is `Blocked` when F6 says Start is not possible, so the reason is shown next
 *    to the disabled control (US2, AC2.3); otherwise it is plainly `Stopped`.
 */
class DashboardUiStateMapper {

    /** Maps one [snapshot] into the immutable state the dashboard renders. */
    fun map(snapshot: DashboardSnapshot): DashboardUiState = DashboardUiState(
        controller = controllerState(snapshot),
        light = lightReadout(snapshot),
        setupSummary = snapshot.setupStatus,
        canStart = snapshot.canStart,
        offsetPercent = snapshot.offsetPercent,
        responseSpeedPercent = snapshot.responseSpeedPercent,
        clampHint = clampHint(snapshot.offsetApplication),
    )

    private fun controllerState(snapshot: DashboardSnapshot): ControllerUiState =
        when (val state = snapshot.serviceState) {
            is BackgroundServiceState.Failed -> ControllerUiState.Failed(state.reason)
            is BackgroundServiceState.StoppedBySystem -> ControllerUiState.StoppedBySystem
            is BackgroundServiceState.Starting -> ControllerUiState.Starting
            is BackgroundServiceState.Running ->
                ControllerUiState.Running(state.appliedBrightness)
            is BackgroundServiceState.Stopped ->
                if (snapshot.canStart) {
                    ControllerUiState.Stopped
                } else {
                    ControllerUiState.Blocked(snapshot.setupStatus?.blockers.orEmpty())
                }
        }

    /**
     * A live reading is shown only while the controller is actually running and the sensor is
     * available, so a stopped controller and a missing sensor both blank the readout instead of
     * presenting the last value as current (US1, AC1.4).
     */
    private fun lightReadout(snapshot: DashboardSnapshot): LightReadout {
        if (snapshot.availability is SensorAvailability.Unavailable) return LightReadout.Unavailable
        if (snapshot.serviceState !is BackgroundServiceState.Running) return LightReadout.Unavailable
        val lux = snapshot.latestLux?.takeIf { it.isFinite() } ?: return LightReadout.Unavailable
        return LightReadout.Reading(lux = lux, level = LightLevelClassifier.classify(lux))
    }

    /**
     * `NotEvaluated` yields no hint, because F3 has not applied the offset to a sample yet and the app
     * must not claim a clamp that never happened (US3, AC1.1).
     */
    private fun clampHint(application: BrightnessOffsetApplication): ClampHint =
        when (application) {
            is BrightnessOffsetApplication.NotEvaluated -> ClampHint.None
            is BrightnessOffsetApplication.FullyApplied -> ClampHint.None
            is BrightnessOffsetApplication.ClampedToMinimum -> ClampHint.Limited(atMaximum = false)
            is BrightnessOffsetApplication.ClampedToMaximum -> ClampHint.Limited(atMaximum = true)
        }
}
