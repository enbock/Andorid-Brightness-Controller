package com.ambientbrightness.core.SetupRequirementAggregator.Task

import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AppSetupService.RequirementSeverity
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupAction
import com.ambientbrightness.core.AppSetupService.SetupRequirement
import com.ambientbrightness.core.AppSetupService.SetupRequirementId
import com.ambientbrightness.core.AppSetupService.UnsupportedReason

/**
 * Maps F1's [SensorAvailability] onto the `AmbientLightSensor` requirement (US4, AC4.1).
 *
 * The requirement is [RequirementSeverity.Blocking] because the controller cannot adapt without a
 * usable sensor and no runtime permission is involved, so the row never carries an action. Both
 * unavailable cases ("no sensor" and "sensor not reporting") surface as
 * [RequirementState.Unsupported] with [UnsupportedReason.NoAmbientLightSensor]; a null input means
 * the source has not reported yet and becomes [RequirementState.Unknown] so Start never looks ready
 * by accident.
 */
internal class SensorRequirementTask {

    fun map(availability: SensorAvailability?): SetupRequirement = SetupRequirement(
        id = SetupRequirementId.AmbientLightSensor,
        severity = RequirementSeverity.Blocking,
        state = when (availability) {
            null -> RequirementState.Unknown
            is SensorAvailability.Available -> RequirementState.Satisfied
            is SensorAvailability.Unavailable ->
                RequirementState.Unsupported(UnsupportedReason.NoAmbientLightSensor)
        },
        action = SetupAction.None,
    )
}
