package com.ambientbrightness.infrastructure

import com.ambientbrightness.core.AutomaticBrightnessControl.WriteSettingsPermissionChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory WRITE_SETTINGS grant state for tests and development; it never accesses Android APIs. */
class DummyWriteSettingsPermissionChecker(
    initiallyGranted: Boolean = false,
) : WriteSettingsPermissionChecker {

    private val permissionState = MutableStateFlow(initiallyGranted)

    override val hasWriteSettingsPermission: StateFlow<Boolean> = permissionState.asStateFlow()

    fun setPermissionGranted(granted: Boolean) {
        permissionState.value = granted
    }
}
