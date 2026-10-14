package com.ambientbrightness.core.testdoubles

import com.ambientbrightness.core.AppSetupService.NotificationPermissionChecker
import com.ambientbrightness.core.AppSetupService.SetupStateStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull

/**
 * Controllable [NotificationPermissionChecker]. A null [initiallyGranted] withholds the first
 * emission, so a subject sees no value (unknown) until [setGranted] is called.
 */
internal class FakeNotificationPermissionChecker(
    initiallyGranted: Boolean? = null,
) : NotificationPermissionChecker {

    private val mutableGranted = MutableStateFlow(initiallyGranted)

    override val isGranted: Flow<Boolean> = mutableGranted.filterNotNull()

    fun setGranted(granted: Boolean?) {
        mutableGranted.value = granted
    }
}

/** In-memory [SetupStateStore]; the fields are plain properties so a test can preset and assert them. */
internal class FakeSetupStateStore(
    override var setupAcknowledged: Boolean = false,
    override var lastShownAtMillis: Long? = null,
) : SetupStateStore
