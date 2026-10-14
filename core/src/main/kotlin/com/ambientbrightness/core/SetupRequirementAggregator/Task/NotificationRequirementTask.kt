package com.ambientbrightness.core.SetupRequirementAggregator.Task

import com.ambientbrightness.core.AppSetupService.MissingReason
import com.ambientbrightness.core.AppSetupService.RequirementSeverity
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupAction
import com.ambientbrightness.core.AppSetupService.SetupRequirement
import com.ambientbrightness.core.AppSetupService.SetupRequirementId

/**
 * Maps F6's `NotificationPermissionChecker.isGranted` onto the `Notifications` requirement
 * (US4, AC4.3).
 *
 * The requirement is [RequirementSeverity.Advisory]: the foreground service runs without
 * `POST_NOTIFICATIONS`, only the ongoing notification disappears, so it never gates Start. A denied
 * grant offers [SetupAction.RequestNotificationPermission]; a null input means the source has not
 * reported yet and becomes [RequirementState.Unknown].
 */
internal class NotificationRequirementTask {

    fun map(granted: Boolean?): SetupRequirement = SetupRequirement(
        id = SetupRequirementId.Notifications,
        severity = RequirementSeverity.Advisory,
        state = when (granted) {
            null -> RequirementState.Unknown
            true -> RequirementState.Satisfied
            false -> RequirementState.Missing(MissingReason.NotGranted)
        },
        action = if (granted == false) {
            SetupAction.RequestNotificationPermission
        } else {
            SetupAction.None
        },
    )
}
