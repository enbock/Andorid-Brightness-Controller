package com.ambientbrightness.core.AutomaticBrightnessControl

import kotlinx.coroutines.flow.StateFlow

/** Exposes observable grant state for Android's special WRITE_SETTINGS permission. */
interface WriteSettingsPermissionChecker {
    val hasWriteSettingsPermission: StateFlow<Boolean>
}
