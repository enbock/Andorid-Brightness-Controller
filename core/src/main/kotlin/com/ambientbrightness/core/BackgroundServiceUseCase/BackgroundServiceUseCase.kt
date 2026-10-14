package com.ambientbrightness.core.BackgroundServiceUseCase

import com.ambientbrightness.core.AutomaticBrightnessControl.AutomaticBrightnessController
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessWriteResult
import com.ambientbrightness.core.BackgroundNotificationBuilder.BackgroundNotificationBuilder
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BackgroundService.BatteryOptimizationClient
import com.ambientbrightness.core.BackgroundService.ControllerStateStore
import com.ambientbrightness.core.BackgroundService.ForegroundServiceHost
import com.ambientbrightness.core.BackgroundService.StartOutcome
import com.ambientbrightness.core.BackgroundService.StartReason
import com.ambientbrightness.core.BrightnessBackgroundService.BrightnessBackgroundService
import com.ambientbrightness.core.StopBrightnessRestorer.StopBrightnessRestorer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Default [BrightnessBackgroundService] (F5): owns the start/stop/restart lifecycle of the control
 * loop and keeps the foreground notification in step with it.
 *
 * It consumes F1/F2 only through their boundaries ([AutomaticBrightnessController]) and never
 * touches Android; the platform lifetime is delegated to [ForegroundServiceHost]. Every transition
 * runs under [lifecycleLock] on the injected [scope], so calls from the dashboard (F7), the
 * notification action and the boot receiver cannot interleave.
 *
 * Start order (AC1.1): `Starting` → persist `desiredRunning` → foreground start → F2 start; the F2
 * status stream then drives `Running` and refreshes the notification, so the notification can never
 * disagree with the controller. Stop order (AC3.1, AC3.2): capture F2's snapshot → stop F2 (no more
 * writes, sensor released) → restore the snapshot through F2's own client → stop the foreground
 * service → clear the persisted flag → `Stopped`.
 *
 * Idempotency (AC "double start/stop"): a second [start] while active and a second [stop] while
 * stopped are no-ops, and the redundant stop performs no restore, so a brightness the user set after
 * stopping is never overwritten. A runtime-revoked `WRITE_SETTINGS` surfaces as F2's
 * [BrightnessWriteResult.Failed] and is mapped to [BackgroundServiceState.Failed] without throwing,
 * leaving the service alive so the user can stop it explicitly (AC3.4).
 */
class BackgroundServiceUseCase(
    private val controller: AutomaticBrightnessController,
    private val host: ForegroundServiceHost,
    private val controllerStateStore: ControllerStateStore,
    private val batteryOptimizationClient: BatteryOptimizationClient,
    private val restorer: StopBrightnessRestorer,
    private val notificationBuilder: BackgroundNotificationBuilder,
    private val scope: CoroutineScope,
) : BrightnessBackgroundService {

    private val mutableState = MutableStateFlow<BackgroundServiceState>(BackgroundServiceState.Stopped)

    override val state: StateFlow<BackgroundServiceState> = mutableState.asStateFlow()

    override val batteryOptimizationExempt: Flow<Boolean> = batteryOptimizationClient.isExempt

    private val lifecycleLock = Any()

    /** True between a successful [start] and the [stop] that ends it; guards the idempotent transitions. */
    @Volatile
    private var active = false

    @Volatile
    private var statusJob: Job? = null

    override fun start(reason: StartReason): StartOutcome = synchronized(lifecycleLock) {
        if (active) return StartOutcome.Started

        active = true
        mutableState.value = BackgroundServiceState.Starting
        controllerStateStore.desiredRunning = true
        controllerStateStore.lastStartReason = reason
        host.startForeground(notificationBuilder.build(mutableState.value))
        controller.start()
        statusJob = scope.launch { controller.status.collect(::onStatus) }
        StartOutcome.Started
    }

    override fun stop() {
        val job = synchronized(lifecycleLock) {
            if (!active) return
            active = false
            val runningJob = statusJob
            statusJob = null
            runningJob
        }

        job?.cancel()
        val snapshot = controller.snapshot
        controller.stop()
        restorer.restore(snapshot)
        host.stopForeground()
        controllerStateStore.desiredRunning = false
        mutableState.value = BackgroundServiceState.Stopped
    }

    /**
     * Mirrors one F2 status update into the service state and the notification.
     *
     * A failed write (for example a revoked `WRITE_SETTINGS`) becomes [BackgroundServiceState.Failed]
     * instead of a crash, so the user still sees what happened and can stop the service (AC3.4).
     */
    private fun onStatus(status: BrightnessStatus) {
        val writeState = status.writeState
        val next = if (writeState is BrightnessWriteResult.Failed) {
            BackgroundServiceState.Failed(writeState.reason)
        } else {
            BackgroundServiceState.Running(status.appliedBrightness)
        }
        mutableState.value = next
        host.updateNotification(notificationBuilder.build(next))
    }
}
