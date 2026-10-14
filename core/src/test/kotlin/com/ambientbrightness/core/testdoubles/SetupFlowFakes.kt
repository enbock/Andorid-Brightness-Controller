package com.ambientbrightness.core.testdoubles

import com.ambientbrightness.core.AppSetupService.NotificationPermissionChecker
import com.ambientbrightness.core.AppSetupService.SetupStateStore
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BackgroundService.StartOutcome
import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.core.BrightnessBackgroundService.BrightnessBackgroundService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

/**
 * F5 double whose [state] a test drives directly, so the aggregator's degraded-path derivation can
 * be exercised without the real background service: publish [BackgroundServiceState.Failed] for a
 * revoked `WRITE_SETTINGS` or `Running` for the advisory notification warning.
 */
internal class FakeBrightnessBackgroundService(
    initial: BackgroundServiceState = BackgroundServiceState.Stopped,
) : BrightnessBackgroundService {

    private val mutableState = MutableStateFlow(initial)

    override val state: StateFlow<BackgroundServiceState> = mutableState.asStateFlow()

    override val batteryOptimizationExempt: Flow<Boolean> = MutableStateFlow(true)

    override fun start(reason: StartReason): StartOutcome = StartOutcome.Started

    override fun stop() = Unit

    fun setState(state: BackgroundServiceState) {
        mutableState.value = state
    }
}
