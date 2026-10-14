package com.ambientbrightness.core.SetupRequirementAggregator.Task

import com.ambientbrightness.core.AppSetupService.MissingReason
import com.ambientbrightness.core.AppSetupService.RequirementSeverity
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupAction
import com.ambientbrightness.core.AppSetupService.SetupRequirement
import com.ambientbrightness.core.AppSetupService.SetupRequirementId

/**
 * Maps F2's `hasWriteSettingsPermission` boolean onto the `WriteSettings` requirement (US2, AC2.1).
 *
 * The requirement is [RequirementSeverity.Blocking] because changing the system brightness needs the
 * special permission; a missing grant offers [SetupAction.OpenWriteSettings], a satisfied one
 * carries [SetupAction.None].
 *
 * `Missing(Revoked)` decision: F2's checker exposes only a boolean, so "never granted" and "granted
 * and taken away again" are indistinguishable from a single reading. This task therefore remembers
 * whether it ever observed `true` and reports [MissingReason.Revoked] once that observation is
 * followed by `false` (US4, AC2.4); a `false` without such an observation stays
 * [MissingReason.NotGranted]. The memory is monotonic — once satisfied, always "was satisfied" — so
 * repeated derivations (including [com.ambientbrightness.core.AppSetupService.AppSetupService.refresh])
 * keep reporting the same reason.
 */
internal class WriteSettingsRequirementTask {

    private var observedSatisfied = false

    fun map(granted: Boolean?): SetupRequirement {
        val state = when (granted) {
            null -> RequirementState.Unknown
            true -> {
                observedSatisfied = true
                RequirementState.Satisfied
            }

            false -> RequirementState.Missing(
                if (observedSatisfied) MissingReason.Revoked else MissingReason.NotGranted,
            )
        }

        return SetupRequirement(
            id = SetupRequirementId.WriteSettings,
            severity = RequirementSeverity.Blocking,
            state = state,
            action = if (state is RequirementState.Missing) {
                SetupAction.OpenWriteSettings
            } else {
                SetupAction.None
            },
        )
    }
}
