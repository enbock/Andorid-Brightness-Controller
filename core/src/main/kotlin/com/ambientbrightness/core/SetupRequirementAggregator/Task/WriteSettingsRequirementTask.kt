package com.ambientbrightness.core.SetupRequirementAggregator.Task

import com.ambientbrightness.core.AppSetupService.MissingReason
import com.ambientbrightness.core.AppSetupService.RequirementSeverity
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupAction
import com.ambientbrightness.core.AppSetupService.SetupRequirement
import com.ambientbrightness.core.AppSetupService.SetupRequirementId
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState

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
 *
 * A controller report of [BackgroundServiceState.Failed] proves the permission was revoked while the
 * controller was running, even before the checker re-reads it, so [controllerFailed] forces
 * `Missing(Revoked)` regardless of the last boolean (US4, AC2.4).
 */
internal class WriteSettingsRequirementTask {

    private var observedSatisfied = false

    fun map(granted: Boolean?, controllerFailed: Boolean): SetupRequirement {
        if (controllerFailed) observedSatisfied = true
        val state = when {
            controllerFailed -> RequirementState.Missing(MissingReason.Revoked)
            granted == null -> RequirementState.Unknown
            granted -> {
                observedSatisfied = true
                RequirementState.Satisfied
            }

            else -> RequirementState.Missing(
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
