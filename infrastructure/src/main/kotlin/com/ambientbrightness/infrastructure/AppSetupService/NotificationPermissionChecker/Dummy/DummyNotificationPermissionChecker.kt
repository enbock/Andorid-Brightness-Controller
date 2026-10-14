package com.ambientbrightness.infrastructure.AppSetupService.NotificationPermissionChecker.Dummy

import com.ambientbrightness.core.AppSetupService.NotificationPermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory `POST_NOTIFICATIONS` grant state for Dummy composition and tests; it never accesses Android APIs. */
class DummyNotificationPermissionChecker(
    initiallyGranted: Boolean = false,
) : NotificationPermissionChecker {

    private val permissionState = MutableStateFlow(initiallyGranted)

    override val isGranted: StateFlow<Boolean> = permissionState.asStateFlow()

    fun setPermissionGranted(granted: Boolean) {
        permissionState.value = granted
    }
}
