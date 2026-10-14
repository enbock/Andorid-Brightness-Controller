package com.ambientbrightness.core.SetupRequirementAggregator

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.AmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AppSetupService.AppSetupService
import com.ambientbrightness.core.AppSetupService.NotificationPermissionChecker
import com.ambientbrightness.core.AppSetupService.RequirementSeverity
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupRequirementId
import com.ambientbrightness.core.AppSetupService.SetupStateStore
import com.ambientbrightness.core.AppSetupService.SetupStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.WriteSettingsPermissionChecker
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BackgroundService.BatteryOptimizationClient
import com.ambientbrightness.core.BrightnessBackgroundService.BrightnessBackgroundService
import com.ambientbrightness.core.SetupRequirementAggregator.Task.BatteryRequirementTask
import com.ambientbrightness.core.SetupRequirementAggregator.Task.NotificationRequirementTask
import com.ambientbrightness.core.SetupRequirementAggregator.Task.SensorRequirementTask
import com.ambientbrightness.core.SetupRequirementAggregator.Task.WriteSettingsRequirementTask
import com.ambientbrightness.core.Shared.Clock
import com.ambientbrightness.core.Shared.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Composes the four existing permission and support sources into the aggregated [SetupStatus] the
 * checklist renders, and derives the Start gate from it (US2–US4).
 *
 * Reads: F1 [AmbientLightService.availability], F2
 * [WriteSettingsPermissionChecker.hasWriteSettingsPermission], F5
 * [BatteryOptimizationClient.isExempt] and [NotificationPermissionChecker.isGranted]. Each source is
 * mapped by its own `Task/` class, so the aggregator only holds the latest observed value and never
 * a derived requirement: every requirement is recomputed from those values, which is why the state
 * re-checks on resume without a restart (US2, AC2.4).
 *
 * A source that has not reported yet contributes `null` and becomes
 * [RequirementState.Unknown]; a blocking requirement that is unknown keeps Start disabled, so the UI
 * never claims a false "ready" (US3).
 *
 * The optional [backgroundService] closes the loop with F5: its [BackgroundServiceState.Failed]
 * turns the `WriteSettings` requirement into [RequirementState.Missing] with
 * [com.ambientbrightness.core.AppSetupService.MissingReason.Revoked] and keeps it in the blockers
 * after a runtime revocation, and its running state makes the advisory notification warning
 * observable (US4, AC4.2–AC4.3). Without it the aggregator still works, just without those two
 * F5-driven signals.
 *
 * The collection runs in the injected [scope], so the composition root stays the only owner of
 * coroutine scopes.
 */
class SetupRequirementAggregator(
    ambientLightService: AmbientLightService,
    writeSettingsPermissionChecker: WriteSettingsPermissionChecker,
    batteryOptimizationClient: BatteryOptimizationClient,
    notificationPermissionChecker: NotificationPermissionChecker,
    private val stateStore: SetupStateStore,
    scope: CoroutineScope,
    private val clock: Clock = SystemClock,
    backgroundService: BrightnessBackgroundService? = null,
) : AppSetupService {

    private val sensorTask = SensorRequirementTask()
    private val writeSettingsTask = WriteSettingsRequirementTask()
    private val notificationTask = NotificationRequirementTask()
    private val batteryTask = BatteryRequirementTask()

    private val sensor = MutableStateFlow<SensorAvailability?>(null)
    private val writeSettings = MutableStateFlow<Boolean?>(null)
    private val notifications = MutableStateFlow<Boolean?>(null)
    private val batteryExempt = MutableStateFlow<Boolean?>(null)
    private val backgroundServiceState = MutableStateFlow<BackgroundServiceState?>(null)

    private val mutableStatus = MutableStateFlow(deriveStatus())
    override val status: StateFlow<SetupStatus> = mutableStatus.asStateFlow()

    private val mutableIsReadyToStart = MutableStateFlow(mutableStatus.value.isReadyToStart)
    override val isReadyToStart: StateFlow<Boolean> = mutableIsReadyToStart.asStateFlow()

    private val mutableAcknowledged = MutableStateFlow(stateStore.setupAcknowledged)
    override val setupAcknowledged: StateFlow<Boolean> = mutableAcknowledged.asStateFlow()

    private val mutableNotificationWarning = MutableStateFlow(warningFor(mutableStatus.value))
    override val notificationWarning: StateFlow<Boolean> = mutableNotificationWarning.asStateFlow()

    init {
        scope.launch {
            ambientLightService.availability.collect { availability ->
                sensor.value = availability
                publishDerived()
            }
        }
        scope.launch {
            writeSettingsPermissionChecker.hasWriteSettingsPermission.collect { granted ->
                writeSettings.value = granted
                publishDerived()
            }
        }
        scope.launch {
            batteryOptimizationClient.isExempt.collect { exempt ->
                batteryExempt.value = exempt
                publishDerived()
            }
        }
        scope.launch {
            notificationPermissionChecker.isGranted.collect { granted ->
                notifications.value = granted
                publishDerived()
            }
        }
        backgroundService?.let { service ->
            scope.launch {
                service.state.collect { state ->
                    backgroundServiceState.value = state
                    publishDerived()
                }
            }
        }
    }

    /**
     * Re-derives [status], [isReadyToStart] and [notificationWarning] from the latest observed
     * source values and publishes them. The sources are observed continuously, so this is normally
     * idempotent; it exists as the seam the ON_RESUME hook calls, guaranteeing the checklist sees a
     * fresh derivation without the aggregator ever polling or caching a requirement (US2, AC2.4).
     */
    override fun refresh() {
        publishDerived()
    }

    override fun completeSetup() {
        acknowledgeSetup()
    }

    override fun skipSetup() {
        acknowledgeSetup()
    }

    override fun acknowledgeSetup() {
        stateStore.setupAcknowledged = true
        stateStore.lastShownAtMillis = clock.nowMillis()
        mutableAcknowledged.value = true
    }

    private fun publishDerived() {
        val derived = deriveStatus()
        mutableStatus.value = derived
        mutableIsReadyToStart.value = derived.isReadyToStart
        mutableNotificationWarning.value = warningFor(derived)
    }

    private fun deriveStatus(): SetupStatus {
        val requirements = listOf(
            sensorTask.map(sensor.value),
            writeSettingsTask.map(
                granted = writeSettings.value,
                controllerFailed = backgroundServiceState.value is BackgroundServiceState.Failed,
            ),
            notificationTask.map(notifications.value),
            batteryTask.map(batteryExempt.value),
        )
        val blockers = requirements
            .filter { requirement ->
                requirement.severity == RequirementSeverity.Blocking &&
                    requirement.state != RequirementState.Satisfied
            }
            .map { requirement -> requirement.id }

        return SetupStatus(
            requirements = requirements,
            isReadyToStart = blockers.isEmpty(),
            blockers = blockers,
        )
    }

    /**
     * The advisory warning is true only while the notification requirement is missing and the
     * controller has been started, because that is when the ongoing notification should be visible
     * but may not be (US4, AC4.3). It never influences [SetupStatus.isReadyToStart].
     */
    private fun warningFor(status: SetupStatus): Boolean {
        val notificationsMissing = status.requirements
            .any { requirement ->
                requirement.id == SetupRequirementId.Notifications &&
                    requirement.state is RequirementState.Missing
            }
        val controllerStarted = backgroundServiceState.value.let { state ->
            state is BackgroundServiceState.Starting || state is BackgroundServiceState.Running
        }
        return notificationsMissing && controllerStarted
    }
}
