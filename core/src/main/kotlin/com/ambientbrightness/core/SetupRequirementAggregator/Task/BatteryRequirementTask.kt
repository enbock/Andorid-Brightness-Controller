package com.ambientbrightness.core.SetupRequirementAggregator.Task

import com.ambientbrightness.core.AppSetupService.MissingReason
import com.ambientbrightness.core.AppSetupService.RequirementSeverity
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupAction
import com.ambientbrightness.core.AppSetupService.SetupRequirement
import com.ambientbrightness.core.AppSetupService.SetupRequirementId

/**
 * Maps F5's `BatteryOptimizationClient.isExempt` onto the `BatteryOptimization` requirement
 * (US2, AC2.3).
 *
 * The requirement is [RequirementSeverity.Advisory]: the exemption is recommended and explained but
 * never gates Start. A non-exempt app offers [SetupAction.OpenBatteryOptimizationSettings]; a null
 * input means the source has not reported yet and becomes [RequirementState.Unknown].
 */
internal class BatteryRequirementTask {

    fun map(exempt: Boolean?): SetupRequirement = SetupRequirement(
        id = SetupRequirementId.BatteryOptimization,
        severity = RequirementSeverity.Advisory,
        state = when (exempt) {
            null -> RequirementState.Unknown
            true -> RequirementState.Satisfied
            false -> RequirementState.Missing(MissingReason.NotGranted)
        },
        action = if (exempt == false) {
            SetupAction.OpenBatteryOptimizationSettings
        } else {
            SetupAction.None
        },
    )
}
