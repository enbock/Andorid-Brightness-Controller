package com.ambientbrightness.app.StatusDashboard.View.Task

import androidx.annotation.StringRes
import com.ambientbrightness.app.R
import com.ambientbrightness.core.AppSetupService.MissingReason
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupRequirementId
import com.ambientbrightness.core.AppSetupService.UnsupportedReason

/**
 * Maps the setup model onto the strings the dashboard shows (US2 AC3, US3, US4).
 *
 * The mapping is kept out of the composables so the panels stay layout-only and this table can be
 * asserted on the JVM. Every lookup is total: an unmapped state is not possible, so the UI can never
 * render a blank reason.
 */
object SetupTextResolver {

    /** The human-readable name of a requirement row. */
    @StringRes
    fun requirementLabel(id: SetupRequirementId): Int = when (id) {
        SetupRequirementId.AmbientLightSensor -> R.string.setup_requirement_sensor
        SetupRequirementId.WriteSettings -> R.string.setup_requirement_write_settings
        SetupRequirementId.Notifications -> R.string.setup_requirement_notifications
        SetupRequirementId.BatteryOptimization -> R.string.setup_requirement_battery
    }

    /** The human-readable state of a requirement row, so "not set up" and "broken" differ. */
    @StringRes
    fun stateLabel(state: RequirementState): Int = when (state) {
        is RequirementState.Satisfied -> R.string.setup_state_satisfied
        is RequirementState.Unknown -> R.string.setup_state_unknown
        is RequirementState.Missing -> when (state.reason) {
            MissingReason.NotGranted -> R.string.setup_state_missing_not_granted
            MissingReason.Revoked -> R.string.setup_state_missing_revoked
        }
        is RequirementState.Unsupported -> when (state.reason) {
            UnsupportedReason.NoAmbientLightSensor -> R.string.setup_state_unsupported
        }
    }
}
