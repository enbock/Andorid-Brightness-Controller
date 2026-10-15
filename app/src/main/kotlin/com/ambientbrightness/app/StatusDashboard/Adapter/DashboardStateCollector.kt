package com.ambientbrightness.app.StatusDashboard.Adapter

import com.ambientbrightness.app.StatusDashboard.View.DashboardSnapshot
import com.ambientbrightness.app.StatusDashboard.View.DashboardStateProvider
import com.ambientbrightness.app.StatusDashboard.View.Task.LuxReadoutSampler
import com.ambientbrightness.app.StatusDashboard.View.Task.LuxSampler
import com.ambientbrightness.core.AmbientLightSensing.AmbientLightService.AmbientLightService
import com.ambientbrightness.core.AmbientLightSensing.LuxSample
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AppSetupService.AppSetupService
import com.ambientbrightness.core.AppSetupService.SetupStatus
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BrightnessBackgroundService.BrightnessBackgroundService
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import com.ambientbrightness.core.BrightnessOffsetService.BrightnessOffsetSource
import com.ambientbrightness.core.ResponseTuning.ResponseTuningService.ResponseTuningService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Reads the five service boundaries and combines them into one [DashboardSnapshot] stream (US1–US4).
 *
 * It holds no business rule: it only reads and combines, so every presentation decision stays in
 * `DashboardUiStateMapper` and is testable without these services. F1's high-frequency stream is
 * routed through [LuxSampler], so the raw sensor rate never reaches the UI.
 *
 * The ambient-light stream is collected **only while the controller is active** (F5 `Starting` or
 * `Running`): when F5 leaves those states the inner collection is cancelled, which releases the sensor
 * listener, and the readout becomes `null` so a stopped controller shows no stale value (US1, AC1.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardStateCollector(
    private val ambientLightService: AmbientLightService,
    private val backgroundService: BrightnessBackgroundService,
    private val setupService: AppSetupService,
    private val brightnessOffsetSource: BrightnessOffsetSource,
    private val responseTuningService: ResponseTuningService,
    private val sampler: LuxSampler = LuxReadoutSampler(),
) : DashboardStateProvider {

    override fun snapshots(): Flow<DashboardSnapshot> = combine(
        serviceValues(),
        ambientLightService.availability,
        latestLux(),
    ) { values, availability, latestLux ->
        DashboardSnapshot(
            serviceState = values.serviceState,
            availability = availability,
            latestLux = latestLux,
            setupStatus = values.setupStatus,
            canStart = values.canStart,
            offsetPercent = values.offsetPercent,
            offsetApplication = values.offsetApplication,
            responseSpeedPercent = values.responseSpeedPercent,
        )
    }

    /**
     * The five non-light values. `combine` is nested because it supports at most five flows per
     * overload: the first combine covers F5/F6/F3, the second adds F4's response speed.
     */
    private fun serviceValues(): Flow<ServiceValues> = combine(
        backgroundService.state,
        setupService.status,
        setupService.isReadyToStart,
        brightnessOffsetSource.offset,
        brightnessOffsetSource.application,
    ) { serviceState, setupStatus, canStart, offset, offsetApplication ->
        ServiceValues(
            serviceState = serviceState,
            setupStatus = setupStatus,
            canStart = canStart,
            offsetPercent = offset.percent,
            offsetApplication = offsetApplication,
        )
    }.combine(responseTuningService.responseSpeed) { values, speed ->
        values.copy(responseSpeedPercent = speed.percent)
    }

    /** The sampled ambient-light reading, or `null` while the controller is not active. */
    private fun latestLux(): Flow<Float?> =
        sampler.sample(backgroundService.state.flatMapLatest(::luxWhile))

    private fun luxWhile(state: BackgroundServiceState): Flow<Float?> = when (state) {
        is BackgroundServiceState.Starting, is BackgroundServiceState.Running ->
            ambientLightService.start().map { sample: LuxSample -> sample.lux }
        else -> flowOf<Float?>(null)
    }

    /** The values the dashboard reads that do not change with the ambient light. */
    private data class ServiceValues(
        val serviceState: BackgroundServiceState,
        val setupStatus: SetupStatus,
        val canStart: Boolean,
        val offsetPercent: Int,
        val offsetApplication: BrightnessOffsetApplication,
        val responseSpeedPercent: Int = 0,
    )
}
