package com.ambientbrightness.core.SetupRequirementAggregator

import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.AmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AppSetupService.AppSetupService
import com.ambientbrightness.core.AppSetupService.NotificationPermissionChecker
import com.ambientbrightness.core.AppSetupService.RequirementSeverity
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupStateStore
import com.ambientbrightness.core.AppSetupService.SetupStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.WriteSettingsPermissionChecker
import com.ambientbrightness.core.BackgroundService.BatteryOptimizationClient
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
) : AppSetupService {

    private val sensorTask = SensorRequirementTask()
    private val writeSettingsTask = WriteSettingsRequirementTask()
    private val notificationTask = NotificationRequirementTask()
    private val batteryTask = BatteryRequirementTask()

    private val sensor = MutableStateFlow<SensorAvailability?>(null)
    private val writeSettings = MutableStateFlow<Boolean?>(null)
    private val notifications = MutableStateFlow<Boolean?>(null)
    private val batteryExempt = MutableStateFlow<Boolean?>(null)

    private val mutableStatus = MutableStateFlow(deriveStatus())
    override val status: StateFlow<SetupStatus> = mutableStatus.asStateFlow()

    private val mutableIsReadyToStart = MutableStateFlow(mutableStatus.value.isReadyToStart)
    override val isReadyToStart: StateFlow<Boolean> = mutableIsReadyToStart.asStateFlow()

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
    }

    /**
     * Re-derives [status] and [isReadyToStart] from the latest observed source values and publishes
     * them. The sources are observed continuously, so this is normally idempotent; it exists as the
     * seam the ON_RESUME hook calls, guaranteeing the checklist sees a fresh derivation without the
     * aggregator ever polling or caching a requirement (US2, AC2.4).
     */
    override fun refresh() {
        publishDerived()
    }

    override fun acknowledgeSetup() {
        stateStore.setupAcknowledged = true
        stateStore.lastShownAtMillis = clock.nowMillis()
    }

    private fun publishDerived() {
        val derived = deriveStatus()
        mutableStatus.value = derived
        mutableIsReadyToStart.value = derived.isReadyToStart
    }

    private fun deriveStatus(): SetupStatus {
        val requirements = listOf(
            sensorTask.map(sensor.value),
            writeSettingsTask.map(writeSettings.value),
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
}
