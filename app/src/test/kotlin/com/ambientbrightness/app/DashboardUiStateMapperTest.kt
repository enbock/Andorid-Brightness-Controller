package com.ambientbrightness.app

import com.ambientbrightness.app.StatusDashboard.View.ClampHint
import com.ambientbrightness.app.StatusDashboard.View.ControllerUiState
import com.ambientbrightness.app.StatusDashboard.View.DashboardSnapshot
import com.ambientbrightness.app.StatusDashboard.View.LightReadout
import com.ambientbrightness.app.StatusDashboard.View.Task.DashboardUiStateMapper
import com.ambientbrightness.core.AmbientLightSensing.LightLevel.LightLevel
import com.ambientbrightness.core.AmbientLightSensing.SensorAvailability
import com.ambientbrightness.core.AmbientLightSensing.SensorProfile
import com.ambientbrightness.core.AmbientLightSensing.SensorUnavailableReason
import com.ambientbrightness.core.AppSetupService.MissingReason
import com.ambientbrightness.core.AppSetupService.RequirementSeverity
import com.ambientbrightness.core.AppSetupService.RequirementState
import com.ambientbrightness.core.AppSetupService.SetupAction
import com.ambientbrightness.core.AppSetupService.SetupRequirement
import com.ambientbrightness.core.AppSetupService.SetupRequirementId
import com.ambientbrightness.core.AppSetupService.SetupStatus
import com.ambientbrightness.core.AutomaticBrightnessControl.BrightnessValue
import com.ambientbrightness.core.BackgroundService.BackgroundServiceState
import com.ambientbrightness.core.BrightnessOffset.BrightnessOffsetApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * JVM tests of the pure state mapping, over the matrix the design calls out: every
 * [BackgroundServiceState] x sensor availability x offset application.
 */
class DashboardUiStateMapperTest {

    private val mapper = DashboardUiStateMapper()

    @Test
    fun `a running controller shows the applied brightness`() {
        val state = mapper.map(
            snapshot(
                serviceState = BackgroundServiceState.Running(BrightnessValue(123)),
                latestLux = 450f,
            ),
        )

        assertEquals(ControllerUiState.Running(BrightnessValue(123)), state.controller)
    }

    @Test
    fun `a running controller that has not written yet still counts as running`() {
        val state = mapper.map(snapshot(serviceState = BackgroundServiceState.Running(null)))

        assertEquals(ControllerUiState.Running(null), state.controller)
    }

    @Test
    fun `an idle controller with unmet blocking requirements is blocked and names them`() {
        val blockers = listOf(SetupRequirementId.WriteSettings)
        val state = mapper.map(
            snapshot(
                serviceState = BackgroundServiceState.Stopped,
                setupStatus = setupStatus(blockers = blockers),
                canStart = false,
            ),
        )

        assertEquals(ControllerUiState.Blocked(blockers), state.controller)
    }

    @Test
    fun `an idle controller with no blockers is plainly stopped`() {
        val state = mapper.map(
            snapshot(serviceState = BackgroundServiceState.Stopped, setupStatus = setupStatus()),
        )

        assertEquals(ControllerUiState.Stopped, state.controller)
    }

    @Test
    fun `a failed controller reports the reason and never pretends to run`() {
        val state = mapper.map(
            snapshot(serviceState = BackgroundServiceState.Failed("WRITE_SETTINGS revoked")),
        )

        assertEquals(ControllerUiState.Failed("WRITE_SETTINGS revoked"), state.controller)
        assertFalse(state.controller is ControllerUiState.Running)
    }

    @Test
    fun `a system-stopped controller is reported as such`() {
        val state = mapper.map(snapshot(serviceState = BackgroundServiceState.StoppedBySystem))

        assertEquals(ControllerUiState.StoppedBySystem, state.controller)
    }

    @Test
    fun `a starting controller is reported as starting`() {
        val state = mapper.map(snapshot(serviceState = BackgroundServiceState.Starting))

        assertEquals(ControllerUiState.Starting, state.controller)
    }

    @Test
    fun `the start gate does not override a real service state`() {
        val state = mapper.map(
            snapshot(
                serviceState = BackgroundServiceState.Running(BrightnessValue(50)),
                canStart = false,
                setupStatus = setupStatus(blockers = listOf(SetupRequirementId.WriteSettings)),
            ),
        )

        assertEquals(ControllerUiState.Running(BrightnessValue(50)), state.controller)
        assertFalse(state.canStart)
    }

    @Test
    fun `a running controller with an available sensor shows the lux and its level`() {
        val state = mapper.map(
            snapshot(
                serviceState = BackgroundServiceState.Running(BrightnessValue(10)),
                latestLux = 5_000f,
            ),
        )

        assertEquals(LightReadout.Reading(lux = 5_000f, level = LightLevel.BRIGHT), state.light)
    }

    @Test
    fun `a stopped controller shows no live reading`() {
        val state = mapper.map(
            snapshot(
                serviceState = BackgroundServiceState.Stopped,
                latestLux = 5_000f,
                setupStatus = setupStatus(),
            ),
        )

        assertEquals(LightReadout.Unavailable, state.light)
    }

    @Test
    fun `an unavailable sensor never yields a reading`() {
        val state = mapper.map(
            snapshot(
                serviceState = BackgroundServiceState.Running(BrightnessValue(10)),
                availability = unavailableSensor(),
                latestLux = 5_000f,
            ),
        )

        assertEquals(LightReadout.Unavailable, state.light)
    }

    @Test
    fun `a running controller before its first sample shows no reading`() {
        val state = mapper.map(
            snapshot(
                serviceState = BackgroundServiceState.Running(BrightnessValue(10)),
                latestLux = null,
            ),
        )

        assertEquals(LightReadout.Unavailable, state.light)
    }

    @Test
    fun `every offset application maps to the matching clamp hint`() {
        assertEquals(
            ClampHint.None,
            mapper.map(snapshot(offsetApplication = BrightnessOffsetApplication.NotEvaluated)).clampHint,
        )
        assertEquals(
            ClampHint.None,
            mapper.map(snapshot(offsetApplication = BrightnessOffsetApplication.FullyApplied)).clampHint,
        )
        assertEquals(
            ClampHint.Limited(atMaximum = false),
            mapper.map(snapshot(offsetApplication = BrightnessOffsetApplication.ClampedToMinimum)).clampHint,
        )
        assertEquals(
            ClampHint.Limited(atMaximum = true),
            mapper.map(snapshot(offsetApplication = BrightnessOffsetApplication.ClampedToMaximum)).clampHint,
        )
    }

    @Test
    fun `the offsets, speed and setup summary pass straight through`() {
        val setup = setupStatus()

        val state = mapper.map(
            snapshot(
                setupStatus = setup,
                offsetPercent = -17,
                responseSpeedPercent = 80,
            ),
        )

        assertEquals(setup, state.setupSummary)
        assertEquals(-17, state.offsetPercent)
        assertEquals(80, state.responseSpeedPercent)
    }

    private fun snapshot(
        serviceState: BackgroundServiceState = BackgroundServiceState.Running(BrightnessValue(100)),
        availability: SensorAvailability = availableSensor(),
        latestLux: Float? = 300f,
        setupStatus: SetupStatus? = setupStatus(),
        canStart: Boolean = true,
        offsetPercent: Int = 0,
        offsetApplication: BrightnessOffsetApplication = BrightnessOffsetApplication.NotEvaluated,
        responseSpeedPercent: Int = 50,
    ) = DashboardSnapshot(
        serviceState = serviceState,
        availability = availability,
        latestLux = latestLux,
        setupStatus = setupStatus,
        canStart = canStart,
        offsetPercent = offsetPercent,
        offsetApplication = offsetApplication,
        responseSpeedPercent = responseSpeedPercent,
    )

    private fun setupStatus(blockers: List<SetupRequirementId> = emptyList()) = SetupStatus(
        requirements = blockers.map { id ->
            SetupRequirement(
                id = id,
                severity = RequirementSeverity.Blocking,
                state = RequirementState.Missing(MissingReason.NotGranted),
                action = SetupAction.None,
            )
        },
        isReadyToStart = blockers.isEmpty(),
        blockers = blockers,
    )

    private fun availableSensor(): SensorAvailability = SensorAvailability.Available(
        SensorProfile(name = "test sensor", maxRangeLux = 20_000f, minDelayMicros = 1_000),
    )

    private fun unavailableSensor(): SensorAvailability =
        SensorAvailability.Unavailable(SensorUnavailableReason.NoSensor)
}
